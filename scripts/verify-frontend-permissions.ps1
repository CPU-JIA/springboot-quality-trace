param(
    [string]$FrontendUrl = "http://localhost:5174",
    [string]$ApiUrl = "http://localhost:8081/api",
    [string]$EdgePath = "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    [int]$DebugPort = 9234
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $EdgePath)) {
    throw "未找到 Edge：$EdgePath"
}

function Invoke-ApiLogin {
    param([string]$Username)
    $body = @{ username = $Username; password = "123456" } | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$ApiUrl/auth/login" -Method Post -ContentType "application/json" -Body $body
    if ($response.code -ne 200) {
        throw "前端权限巡检登录失败：$Username / $($response.message)"
    }
    return $response.data
}

function Wait-DevTools {
    param([string]$Endpoint)
    for ($i = 0; $i -lt 40; $i++) {
        try {
            return Invoke-RestMethod -Uri "$Endpoint/json/version" -TimeoutSec 1
        } catch {
            Start-Sleep -Milliseconds 250
        }
    }
    throw "Edge DevTools 未在预期时间内启动"
}

function New-CdpTab {
    param([string]$Endpoint)
    $encoded = [uri]::EscapeDataString("about:blank")
    try {
        return Invoke-RestMethod -Uri "$Endpoint/json/new?$encoded" -Method Put -TimeoutSec 3
    } catch {
        $tabs = Invoke-RestMethod -Uri "$Endpoint/json/list" -TimeoutSec 3
        if (@($tabs).Count -eq 0) {
            throw "无法创建或读取 Edge 调试标签页"
        }
        return @($tabs)[0]
    }
}

function Send-Cdp {
    param(
        [System.Net.WebSockets.ClientWebSocket]$Socket,
        [int]$Id,
        [string]$Method,
        [object]$Params = @{}
    )
    $payload = @{ id = $Id; method = $Method; params = $Params } | ConvertTo-Json -Depth 20 -Compress
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($payload)
    [void]$Socket.SendAsync([ArraySegment[byte]]::new($bytes), [System.Net.WebSockets.WebSocketMessageType]::Text, $true, [Threading.CancellationToken]::None).GetAwaiter().GetResult()
}

function Receive-Cdp {
    param([System.Net.WebSockets.ClientWebSocket]$Socket)
    $buffer = New-Object byte[] 1048576
    $builder = [System.Text.StringBuilder]::new()
    do {
        $result = $Socket.ReceiveAsync([ArraySegment[byte]]::new($buffer), [Threading.CancellationToken]::None).GetAwaiter().GetResult()
        if ($result.MessageType -eq [System.Net.WebSockets.WebSocketMessageType]::Close) {
            return $null
        }
        [void]$builder.Append([System.Text.Encoding]::UTF8.GetString($buffer, 0, $result.Count))
    } while (-not $result.EndOfMessage)
    return ($builder.ToString() | ConvertFrom-Json)
}

function Wait-CdpResponse {
    param(
        [System.Net.WebSockets.ClientWebSocket]$Socket,
        [int]$Id,
        [System.Collections.Generic.List[object]]$Events
    )
    while ($true) {
        $message = Receive-Cdp $Socket
        if ($null -eq $message) {
            throw "CDP 连接已关闭"
        }
        if ($message.id -eq $Id) {
            return $message
        }
        if ($message.method) {
            $Events.Add($message)
        }
    }
}

function Invoke-Cdp {
    param(
        [System.Net.WebSockets.ClientWebSocket]$Socket,
        [ref]$NextId,
        [System.Collections.Generic.List[object]]$Events,
        [string]$Method,
        [object]$Params = @{}
    )
    $id = $NextId.Value
    $NextId.Value = $NextId.Value + 1
    Send-Cdp -Socket $Socket -Id $id -Method $Method -Params $Params
    $response = Wait-CdpResponse -Socket $Socket -Id $id -Events $Events
    if ($response.error) {
        throw "CDP 调用失败 $Method：$($response.error.message)"
    }
    return $response.result
}

function Wait-Title {
    param(
        [System.Net.WebSockets.ClientWebSocket]$Socket,
        [ref]$NextId,
        [System.Collections.Generic.List[object]]$Events,
        [string]$ExpectedTitle
    )
    $expression = @"
(() => {
  const title = document.querySelector('.current-title')?.textContent?.trim() || '';
  const bodyText = document.body ? document.body.innerText : '';
  return { title, ready: title === '$ExpectedTitle' && bodyText.includes('$ExpectedTitle') };
})()
"@
    for ($i = 0; $i -lt 80; $i++) {
        $result = Invoke-Cdp -Socket $Socket -NextId $NextId -Events $Events -Method "Runtime.evaluate" -Params @{
            expression = $expression
            returnByValue = $true
        }
        $value = $result.result.value
        if ($value.ready -eq $true) {
            return $value
        }
        Start-Sleep -Milliseconds 150
    }
    throw "路由未在预期时间内进入：$ExpectedTitle"
}

function Get-PageState {
    param(
        [System.Net.WebSockets.ClientWebSocket]$Socket,
        [ref]$NextId,
        [System.Collections.Generic.List[object]]$Events
    )
    $expression = @"
(() => {
  const visible = (el) => {
    const style = window.getComputedStyle(el);
    const rect = el.getBoundingClientRect();
    return style.visibility !== 'hidden' && style.display !== 'none' && rect.width > 0 && rect.height > 0;
  };
  const menus = Array.from(document.querySelectorAll('.side-menu .el-menu-item'))
    .filter(visible)
    .map((el) => (el.getAttribute('title') || el.textContent || '').trim())
    .filter(Boolean);
  const buttons = Array.from(document.querySelectorAll('button, .el-button'))
    .filter(visible)
    .map((el) => (el.textContent || '').replace(/\s+/g, ' ').trim())
    .filter(Boolean);
  const title = document.querySelector('.current-title')?.textContent?.trim() || '';
  return { title, menus, buttons };
})()
"@
    $result = Invoke-Cdp -Socket $Socket -NextId $NextId -Events $Events -Method "Runtime.evaluate" -Params @{
        expression = $expression
        returnByValue = $true
    }
    return $result.result.value
}

function Assert-SetEqual {
    param(
        [string[]]$Actual,
        [string[]]$Expected,
        [string]$Message
    )
    $missing = @($Expected | Where-Object { $Actual -notcontains $_ })
    $unexpected = @($Actual | Where-Object { $Expected -notcontains $_ })
    if ($missing.Count -gt 0 -or $unexpected.Count -gt 0) {
        throw "$Message。缺失：$($missing -join '、')；多出：$($unexpected -join '、')"
    }
}

function Assert-Buttons {
    param(
        [object]$State,
        [string[]]$Include = @(),
        [string[]]$Exclude = @(),
        [string]$Context
    )
    foreach ($text in $Include) {
        if ($State.buttons -notcontains $text) {
            throw "$Context 未显示应有按钮：$text；当前按钮：$($State.buttons -join '、')"
        }
    }
    foreach ($text in $Exclude) {
        if ($State.buttons -contains $text) {
            throw "$Context 显示了不应出现的按钮：$text"
        }
    }
}

function Assert-NoBadEvents {
    param(
        [System.Collections.Generic.List[object]]$Events,
        [string]$Context
    )
    $badEvents = @($Events | Where-Object {
        ($_.method -eq "Runtime.exceptionThrown") -or
        ($_.method -eq "Runtime.consoleAPICalled" -and @("error", "warning") -contains $_.params.type) -or
        ($_.method -eq "Log.entryAdded" -and @("error", "warning") -contains $_.params.entry.level) -or
        ($_.method -eq "Network.loadingFailed") -or
        ($_.method -eq "Network.responseReceived" -and $_.params.response.status -ge 400)
    })
    if ($badEvents.Count -gt 0) {
        $messages = $badEvents | ForEach-Object {
            if ($_.method -eq "Runtime.exceptionThrown") {
                $_.params.exceptionDetails.text
            } elseif ($_.method -eq "Runtime.consoleAPICalled") {
                $parts = @($_.params.args) | ForEach-Object { if ($_.value) { $_.value } else { $_.description } } | Where-Object { $_ }
                "$($_.params.type): $($parts -join ' ')"
            } elseif ($_.method -eq "Network.loadingFailed") {
                "网络请求失败：$($_.params.errorText)"
            } elseif ($_.method -eq "Network.responseReceived") {
                "HTTP $($_.params.response.status)：$($_.params.response.url)"
            } else {
                $_.params.entry.text
            }
        }
        throw "$Context 存在控制台错误/警告：$($messages -join ' | ')"
    }
}

function Invoke-RoleCase {
    param(
        [object]$Case,
        [int]$Port
    )
    $login = Invoke-ApiLogin $Case.username
    $userJson = (@{
        userId = $login.userId
        username = $login.username
        realName = $login.realName
        roles = @($login.roles)
    } | ConvertTo-Json -Compress)

    $tempProfile = Join-Path $env:TEMP ("quality-trace-perm-" + [guid]::NewGuid().ToString("N"))
    $edge = $null
    $socket = $null
    try {
        New-Item -ItemType Directory -Path $tempProfile | Out-Null
        $edgeArgs = @(
            "--headless=new",
            "--disable-gpu",
            "--remote-debugging-port=$Port",
            "--user-data-dir=$tempProfile",
            "--no-first-run",
            "--no-default-browser-check",
            "about:blank"
        )
        $edge = Start-Process -FilePath $EdgePath -ArgumentList $edgeArgs -WindowStyle Hidden -PassThru
        $endpoint = "http://127.0.0.1:$Port"
        $null = Wait-DevTools $endpoint
        $tab = New-CdpTab $endpoint

        $socket = [System.Net.WebSockets.ClientWebSocket]::new()
        [void]$socket.ConnectAsync([uri]$tab.webSocketDebuggerUrl, [Threading.CancellationToken]::None).GetAwaiter().GetResult()
        $events = [System.Collections.Generic.List[object]]::new()
        $nextId = 1

        Invoke-Cdp -Socket $socket -NextId ([ref]$nextId) -Events $events -Method "Runtime.enable" | Out-Null
        Invoke-Cdp -Socket $socket -NextId ([ref]$nextId) -Events $events -Method "Page.enable" | Out-Null
        Invoke-Cdp -Socket $socket -NextId ([ref]$nextId) -Events $events -Method "Log.enable" | Out-Null
        Invoke-Cdp -Socket $socket -NextId ([ref]$nextId) -Events $events -Method "Network.enable" | Out-Null

        $initScript = @"
localStorage.setItem('quality_trace_token', '$($login.token)');
localStorage.setItem('quality_trace_user', '$($userJson.Replace("'", "\'"))');
"@
        Invoke-Cdp -Socket $socket -NextId ([ref]$nextId) -Events $events -Method "Page.addScriptToEvaluateOnNewDocument" -Params @{ source = $initScript } | Out-Null

        $events.Clear()
        Invoke-Cdp -Socket $socket -NextId ([ref]$nextId) -Events $events -Method "Page.navigate" -Params @{ url = "$FrontendUrl/#/dashboard" } | Out-Null
        $null = Wait-Title -Socket $socket -NextId ([ref]$nextId) -Events $events -ExpectedTitle "质量看板"
        Start-Sleep -Milliseconds 500
        Invoke-Cdp -Socket $socket -NextId ([ref]$nextId) -Events $events -Method "Runtime.evaluate" -Params @{ expression = "true"; returnByValue = $true } | Out-Null
        Assert-NoBadEvents -Events $events -Context "$($Case.username) 看板"
        $state = Get-PageState -Socket $socket -NextId ([ref]$nextId) -Events $events
        Assert-SetEqual -Actual @($state.menus) -Expected @($Case.menus) -Message "$($Case.username) 菜单权限不一致"

        $pageResults = @()
        foreach ($page in $Case.pages) {
            $events.Clear()
            Invoke-Cdp -Socket $socket -NextId ([ref]$nextId) -Events $events -Method "Page.navigate" -Params @{ url = "$FrontendUrl/#$($page.path)" } | Out-Null
            $null = Wait-Title -Socket $socket -NextId ([ref]$nextId) -Events $events -ExpectedTitle $page.title
            Start-Sleep -Milliseconds 600
            Invoke-Cdp -Socket $socket -NextId ([ref]$nextId) -Events $events -Method "Runtime.evaluate" -Params @{ expression = "true"; returnByValue = $true } | Out-Null
            Assert-NoBadEvents -Events $events -Context "$($Case.username) $($page.path)"
            $pageState = Get-PageState -Socket $socket -NextId ([ref]$nextId) -Events $events
            Assert-Buttons -State $pageState -Include @($page.include) -Exclude @($page.exclude) -Context "$($Case.username) $($page.path)"
            $pageResults += [pscustomobject]@{
                path = $page.path
                title = $pageState.title
                result = "OK"
            }
        }

        return [pscustomobject]@{
            username = $Case.username
            menus = @($state.menus)
            pages = $pageResults
            result = "OK"
        }
    } finally {
        if ($socket) {
            try { $socket.Dispose() } catch {}
        }
        if ($edge -and -not $edge.HasExited) {
            Stop-Process -Id $edge.Id -Force -ErrorAction SilentlyContinue
        }
        if (Test-Path -LiteralPath $tempProfile) {
            Remove-Item -LiteralPath $tempProfile -Recurse -Force -ErrorAction SilentlyContinue
        }
    }
}

$cases = @(
    @{
        username = "admin"
        menus = @("质量看板", "用户管理", "主数据维护", "批次台账", "生产工单", "检验任务", "缺陷管理", "出货管理", "追溯查询", "召回管理", "统计报表")
        pages = @(
            @{ path = "/users"; title = "用户管理"; include = @("新增用户"); exclude = @() },
            @{ path = "/master"; title = "主数据维护"; include = @("新增物料"); exclude = @() }
        )
    },
    @{
        username = "zhangsan"
        menus = @("质量看板", "批次台账", "出货管理", "追溯查询", "统计报表")
        pages = @(
            @{ path = "/batches"; title = "批次台账"; include = @("原材料入库"); exclude = @() },
            @{ path = "/shipments"; title = "出货管理"; include = @("成品出货"); exclude = @() },
            @{ path = "/production"; title = "质量看板"; include = @(); exclude = @("创建工单") }
        )
    },
    @{
        username = "lisi"
        menus = @("质量看板", "批次台账", "生产工单", "追溯查询", "统计报表")
        pages = @(
            @{ path = "/production"; title = "生产工单"; include = @("创建工单"); exclude = @() },
            @{ path = "/shipments"; title = "质量看板"; include = @(); exclude = @("成品出货") }
        )
    },
    @{
        username = "wangwu"
        menus = @("质量看板", "批次台账", "检验任务", "缺陷管理", "追溯查询", "统计报表")
        pages = @(
            @{ path = "/defects"; title = "缺陷管理"; include = @("登记缺陷"); exclude = @("处置") },
            @{ path = "/recalls"; title = "质量看板"; include = @(); exclude = @("发起召回") }
        )
    },
    @{
        username = "zhaoliu"
        menus = @("质量看板", "批次台账", "生产工单", "检验任务", "缺陷管理", "出货管理", "追溯查询", "召回管理", "统计报表")
        pages = @(
            @{ path = "/production"; title = "生产工单"; include = @(); exclude = @("创建工单", "领料", "完工入库") },
            @{ path = "/inspection"; title = "检验任务"; include = @(); exclude = @("领取", "执行") },
            @{ path = "/shipments"; title = "出货管理"; include = @(); exclude = @("成品出货") },
            @{ path = "/defects"; title = "缺陷管理"; include = @("登记缺陷"); exclude = @() },
            @{ path = "/recalls"; title = "召回管理"; include = @("发起召回"); exclude = @() }
        )
    }
)

$results = @()
for ($i = 0; $i -lt $cases.Count; $i++) {
    $results += Invoke-RoleCase -Case $cases[$i] -Port ($DebugPort + $i)
}

[pscustomobject]@{
    users = $results
    userCount = $results.Count
    result = "FRONTEND_PERMISSIONS_OK"
} | ConvertTo-Json -Depth 8
