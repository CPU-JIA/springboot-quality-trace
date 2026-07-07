param(
    [string]$FrontendUrl = "http://localhost:5174",
    [string]$ApiUrl = "http://localhost:8081/api",
    [string]$EdgePath = "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    [int]$DebugPort = 9224
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $EdgePath)) {
    throw "未找到 Edge：$EdgePath"
}

function Invoke-ApiLogin {
    $body = @{ username = "admin"; password = "123456" } | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$ApiUrl/auth/login" -Method Post -ContentType "application/json" -Body $body
    if ($response.code -ne 200) {
        throw "前端巡检登录失败：$($response.message)"
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
    $segment = [ArraySegment[byte]]::new($bytes)
    [void]$Socket.SendAsync($segment, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, [Threading.CancellationToken]::None).GetAwaiter().GetResult()
}

function Receive-Cdp {
    param([System.Net.WebSockets.ClientWebSocket]$Socket)
    $buffer = New-Object byte[] 1048576
    $builder = [System.Text.StringBuilder]::new()
    do {
        $segment = [ArraySegment[byte]]::new($buffer)
        $result = $Socket.ReceiveAsync($segment, [Threading.CancellationToken]::None).GetAwaiter().GetResult()
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

function Wait-RouteReady {
    param(
        [System.Net.WebSockets.ClientWebSocket]$Socket,
        [ref]$NextId,
        [System.Collections.Generic.List[object]]$Events,
        [string]$ExpectedTitle
    )
    $expression = @"
(() => {
  const title = document.querySelector('.current-title')?.textContent?.trim() || '';
  const pageTitle = document.querySelector('.page-title h1, .page-title .title, h1')?.textContent?.trim() || '';
  const hasTable = Boolean(document.querySelector('.el-table'));
  const hasChart = Boolean(document.querySelector('.chart-canvas canvas, .chart-canvas'));
  const hasPanel = Boolean(document.querySelector('.work-panel, .metric-grid'));
  const bodyText = document.body ? document.body.innerText : '';
  return {
    title,
    pageTitle,
    ready: title === '$ExpectedTitle' && (hasTable || hasChart || hasPanel || bodyText.includes('$ExpectedTitle')),
    bodyText
  };
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
    throw "路由未在预期时间内渲染：$ExpectedTitle"
}

$login = Invoke-ApiLogin
$userJson = (@{
    userId = $login.userId
    username = $login.username
    realName = $login.realName
    roles = @($login.roles)
} | ConvertTo-Json -Compress)

$routes = @(
    @{ path = "/dashboard"; title = "质量看板" },
    @{ path = "/users"; title = "用户管理" },
    @{ path = "/master"; title = "主数据维护" },
    @{ path = "/batches"; title = "批次台账" },
    @{ path = "/production"; title = "生产工单" },
    @{ path = "/inspection"; title = "检验任务" },
    @{ path = "/defects"; title = "缺陷管理" },
    @{ path = "/shipments"; title = "出货管理" },
    @{ path = "/trace"; title = "追溯查询" },
    @{ path = "/recalls"; title = "召回管理" },
    @{ path = "/reports"; title = "统计报表" }
)

$tempProfile = Join-Path $env:TEMP ("quality-trace-edge-" + [guid]::NewGuid().ToString("N"))
$edge = $null
$socket = $null

try {
    New-Item -ItemType Directory -Path $tempProfile | Out-Null
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

    $initScript = @"
localStorage.setItem('quality_trace_token', '$($login.token)');
localStorage.setItem('quality_trace_user', '$($userJson.Replace("'", "\'"))');
"@
    Invoke-Cdp -Socket $socket -NextId ([ref]$nextId) -Events $events -Method "Page.addScriptToEvaluateOnNewDocument" -Params @{ source = $initScript } | Out-Null

    $rows = @()
    foreach ($route in $routes) {
        $events.Clear()
        $url = "$FrontendUrl/#$($route.path)"
        Invoke-Cdp -Socket $socket -NextId ([ref]$nextId) -Events $events -Method "Page.navigate" -Params @{ url = $url } | Out-Null
        Start-Sleep -Milliseconds 350
        $state = Wait-RouteReady -Socket $socket -NextId ([ref]$nextId) -Events $events -ExpectedTitle $route.title
        Start-Sleep -Milliseconds 600
        Invoke-Cdp -Socket $socket -NextId ([ref]$nextId) -Events $events -Method "Runtime.evaluate" -Params @{
            expression = "true"
            returnByValue = $true
        } | Out-Null

        $badEvents = @($events | Where-Object {
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
            throw "路由 $($route.path) 存在控制台错误/警告：$($messages -join ' | ')"
        }

        $rows += [pscustomobject]@{
            path = $route.path
            title = $state.title
            pageTitle = $state.pageTitle
            result = "OK"
        }
    }

    [pscustomobject]@{
        routes = $rows
        routeCount = $rows.Count
        result = "FRONTEND_ROUTES_OK"
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
