param(
    [string]$FrontendUrl = "http://localhost:5174",
    [string]$ApiUrl = "http://localhost:8081/api",
    [string]$OutputDir = "docs\screenshots",
    [string]$EdgePath = "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    [int]$DebugPort = 9254,
    [int]$Width = 1440,
    [int]$Height = 950
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $EdgePath)) {
    throw "未找到 Edge：$EdgePath"
}

function Invoke-ApiLogin {
    $body = @{ username = "admin"; password = "123456" } | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$ApiUrl/auth/login" -Method Post -ContentType "application/json" -Body $body
    if ($response.code -ne 200) {
        throw "截图脚本登录失败：$($response.message)"
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

function Invoke-Js {
    param(
        [System.Net.WebSockets.ClientWebSocket]$Socket,
        [ref]$NextId,
        [System.Collections.Generic.List[object]]$Events,
        [string]$Expression
    )
    $result = Invoke-Cdp -Socket $Socket -NextId $NextId -Events $Events -Method "Runtime.evaluate" -Params @{
        expression = $Expression
        awaitPromise = $true
        returnByValue = $true
    }
    if ($result.exceptionDetails) {
        $details = $result.exceptionDetails
        $message = $details.exception.description
        if ([string]::IsNullOrWhiteSpace($message)) {
            $message = $details.text
        }
        throw "页面脚本执行失败：$message"
    }
    return $result.result.value
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

function Wait-RouteReady {
    param(
        [System.Net.WebSockets.ClientWebSocket]$Socket,
        [ref]$NextId,
        [System.Collections.Generic.List[object]]$Events,
        [string]$ExpectedTitle
    )
    $safeTitle = $ExpectedTitle.Replace("'", "\'")
    $expression = @"
(() => {
  const title = document.querySelector('.current-title')?.textContent?.trim() || '';
  const pageTitle = document.querySelector('h1')?.textContent?.trim() || '';
  const hasCanvas = Boolean(document.querySelector('canvas'));
  const hasTable = Boolean(document.querySelector('.el-table'));
  const bodyText = document.body ? document.body.innerText : '';
  return {
    title,
    pageTitle,
    ready: title === '$safeTitle' && bodyText.includes('$safeTitle') && (hasCanvas || hasTable || document.querySelector('.work-panel'))
  };
})()
"@
    for ($i = 0; $i -lt 90; $i++) {
        $value = Invoke-Js -Socket $Socket -NextId $NextId -Events $Events -Expression $expression
        if ($value.ready -eq $true) {
            return $value
        }
        Start-Sleep -Milliseconds 150
    }
    throw "路由未在预期时间内渲染：$ExpectedTitle"
}

function Save-Screenshot {
    param(
        [System.Net.WebSockets.ClientWebSocket]$Socket,
        [ref]$NextId,
        [System.Collections.Generic.List[object]]$Events,
        [string]$FilePath
    )
    $result = Invoke-Cdp -Socket $Socket -NextId $NextId -Events $Events -Method "Page.captureScreenshot" -Params @{
        format = "png"
        fromSurface = $true
        captureBeyondViewport = $false
    }
    [System.IO.File]::WriteAllBytes($FilePath, [Convert]::FromBase64String($result.data))
}

$login = Invoke-ApiLogin
$userJson = (@{
    userId = $login.userId
    username = $login.username
    realName = $login.realName
    roles = @($login.roles)
} | ConvertTo-Json -Compress)

$pages = @(
    @{ path = "/dashboard"; title = "质量看板"; file = "01-dashboard.png" },
    @{ path = "/master"; title = "主数据维护"; file = "02-master-data.png" },
    @{ path = "/batches"; title = "批次台账"; file = "03-batch-ledger.png" },
    @{ path = "/production"; title = "生产工单"; file = "04-production-orders.png" },
    @{ path = "/inspection"; title = "检验任务"; file = "05-inspection-tasks.png" },
    @{ path = "/defects"; title = "缺陷管理"; file = "06-defects.png" },
    @{ path = "/trace"; title = "追溯查询"; file = "07-trace-query.png" },
    @{ path = "/recalls"; title = "召回管理"; file = "08-recalls.png" },
    @{ path = "/reports"; title = "统计报表"; file = "09-reports.png" }
)

$resolvedOutputDir = $ExecutionContext.SessionState.Path.GetUnresolvedProviderPathFromPSPath($OutputDir)
$tempProfile = Join-Path $env:TEMP ("quality-trace-screenshots-" + [guid]::NewGuid().ToString("N"))
$edge = $null
$socket = $null

try {
    New-Item -ItemType Directory -Path $tempProfile | Out-Null
    New-Item -ItemType Directory -Path $resolvedOutputDir -Force | Out-Null

    $edgeArgs = @(
        "--headless=new",
        "--disable-gpu",
        "--remote-debugging-port=$DebugPort",
        "--user-data-dir=$tempProfile",
        "--no-first-run",
        "--no-default-browser-check",
        "about:blank"
    )
    $edge = Start-Process -FilePath $EdgePath -ArgumentList $edgeArgs -WindowStyle Hidden -PassThru
    $endpoint = "http://127.0.0.1:$DebugPort"
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
    Invoke-Cdp -Socket $socket -NextId ([ref]$nextId) -Events $events -Method "Emulation.setDeviceMetricsOverride" -Params @{
        width = $Width
        height = $Height
        deviceScaleFactor = 1
        mobile = $false
    } | Out-Null

    $initScript = @"
localStorage.setItem('quality_trace_token', '$($login.token)');
localStorage.setItem('quality_trace_user', '$($userJson.Replace("'", "\'"))');
"@
    Invoke-Cdp -Socket $socket -NextId ([ref]$nextId) -Events $events -Method "Page.addScriptToEvaluateOnNewDocument" -Params @{ source = $initScript } | Out-Null

    $rows = @()
    foreach ($page in $pages) {
        $events.Clear()
        $url = "$FrontendUrl/#$($page.path)"
        Invoke-Cdp -Socket $socket -NextId ([ref]$nextId) -Events $events -Method "Page.navigate" -Params @{ url = $url } | Out-Null
        $state = Wait-RouteReady -Socket $socket -NextId ([ref]$nextId) -Events $events -ExpectedTitle $page.title
        Invoke-Js -Socket $socket -NextId ([ref]$nextId) -Events $events -Expression "window.scrollTo(0, 0); true" | Out-Null
        Start-Sleep -Milliseconds 1200
        Invoke-Js -Socket $socket -NextId ([ref]$nextId) -Events $events -Expression "true" | Out-Null
        Assert-NoBadEvents -Events $events -Context $page.title
        $filePath = Join-Path $resolvedOutputDir $page.file
        Save-Screenshot -Socket $socket -NextId ([ref]$nextId) -Events $events -FilePath $filePath
        $rows += [pscustomobject]@{
            path = $page.path
            title = $state.title
            file = $filePath
            result = "OK"
        }
    }

    [pscustomobject]@{
        outputDir = $resolvedOutputDir
        screenshots = $rows
        screenshotCount = $rows.Count
        result = "REPORT_SCREENSHOTS_OK"
    } | ConvertTo-Json -Depth 5
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
