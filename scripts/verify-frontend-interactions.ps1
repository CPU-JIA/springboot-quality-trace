param(
    [string]$FrontendUrl = "http://localhost:5174",
    [string]$ApiUrl = "http://localhost:8081/api",
    [string]$EdgePath = "C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    [int]$DebugPort = 9244
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $EdgePath)) {
    throw "未找到 Edge：$EdgePath"
}

function Invoke-ApiLogin {
    $body = @{ username = "admin"; password = "123456" } | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$ApiUrl/auth/login" -Method Post -ContentType "application/json" -Body $body
    if ($response.code -ne 200) {
        throw "前端交互巡检登录失败：$($response.message)"
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
  const bodyText = document.body ? document.body.innerText : '';
  return {
    title,
    pageTitle,
    ready: title === '$safeTitle' && bodyText.includes('$safeTitle')
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

function New-InteractionExpression {
    param([string]$Body)
    $prefix = @'
(async () => {
  const steps = [];
  const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));
  const normalize = (text) => String(text || '').replace(/\s+/g, ' ').trim();
  const visible = (el) => {
    if (!el) return false;
    const style = window.getComputedStyle(el);
    const rect = el.getBoundingClientRect();
    return style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 0 && rect.height > 0;
  };
  const enabled = (el) => !el.disabled && !el.classList.contains('is-disabled') && el.getAttribute('aria-disabled') !== 'true';
  const log = (name) => steps.push(name);
  const allBySelector = (selector) => Array.from(document.querySelectorAll(selector)).filter(visible);
  const firstByText = (selector, text, requireEnabled = true) => allBySelector(selector).find((el) => {
    const matches = normalize(el.textContent).includes(text) || normalize(el.getAttribute('title')).includes(text);
    return matches && (!requireEnabled || enabled(el));
  });
  const waitFor = async (predicate, message, attempts = 80, delay = 150) => {
    for (let i = 0; i < attempts; i += 1) {
      const value = predicate();
      if (value) return value;
      await sleep(delay);
    }
    throw new Error(message);
  };
  const clickButton = async (text) => {
    const button = await waitFor(() => firstByText('button, .el-button', text), `未找到可点击按钮：${text}`);
    button.click();
    log(`点击：${text}`);
    await sleep(650);
    return button;
  };
  const clickButtonIfExists = async (text) => {
    const button = firstByText('button, .el-button', text);
    if (!button) return false;
    button.click();
    log(`点击：${text}`);
    await sleep(650);
    return true;
  };
  const clickTab = async (text) => {
    const tab = await waitFor(() => firstByText('.el-tabs__item', text, false), `未找到标签页：${text}`);
    tab.click();
    log(`切换标签页：${text}`);
    await sleep(850);
  };
  const clickText = async (text) => {
    const node = await waitFor(
      () => firstByText('button, .el-button, .el-segmented__item, .el-radio-button, .el-tabs__item', text),
      `未找到可点击文本：${text}`
    );
    node.click();
    log(`点击：${text}`);
    await sleep(650);
    return node;
  };
  const expectDialog = async (title) => {
    await waitFor(
      () => allBySelector('.el-dialog').find((dialog) => normalize(dialog.querySelector('.el-dialog__title')?.textContent).includes(title)),
      `未打开弹窗：${title}`
    );
    log(`弹窗：${title}`);
  };
  const expectDrawer = async (title) => {
    await waitFor(
      () => allBySelector('.el-drawer').find((drawer) => normalize(drawer.querySelector('.el-drawer__title')?.textContent).includes(title)),
      `未打开抽屉：${title}`
    );
    log(`抽屉：${title}`);
  };
  const expectText = async (text) => {
    await waitFor(() => normalize(document.body.innerText).includes(text), `页面未出现文本：${text}`);
    log(`文本：${text}`);
  };
  const closeOverlays = async () => {
    const closers = allBySelector('.el-dialog__headerbtn, .el-drawer__close-btn').reverse();
    for (const closer of closers) closer.click();
    await sleep(450);
  };
  try {
'@
    $suffix = @'
    await sleep(600);
    return { ok: true, steps };
  } catch (error) {
    return { ok: false, message: error.message || String(error), steps };
  }
})()
'@
    return $prefix + "`n" + $Body + "`n" + $suffix
}

function Invoke-InteractionCase {
    param(
        [System.Net.WebSockets.ClientWebSocket]$Socket,
        [ref]$NextId,
        [System.Collections.Generic.List[object]]$Events,
        [string]$Path,
        [string]$Title,
        [string]$Name,
        [string]$Body
    )
    $Events.Clear()
    Invoke-Cdp -Socket $Socket -NextId $NextId -Events $Events -Method "Page.navigate" -Params @{ url = "$FrontendUrl/#$Path" } | Out-Null
    $state = Wait-RouteReady -Socket $Socket -NextId $NextId -Events $Events -ExpectedTitle $Title
    Start-Sleep -Milliseconds 700
    $result = Invoke-Js -Socket $Socket -NextId $NextId -Events $Events -Expression (New-InteractionExpression $Body)
    Invoke-Js -Socket $Socket -NextId $NextId -Events $Events -Expression "true" | Out-Null
    Assert-NoBadEvents -Events $Events -Context $Name
    if ($result.ok -ne $true) {
        throw "$Name 交互失败：$($result.message)。已完成步骤：$($result.steps -join '；')"
    }
    return [pscustomobject]@{
        path = $Path
        title = $state.title
        name = $Name
        steps = @($result.steps)
        result = "OK"
    }
}

$login = Invoke-ApiLogin
$userJson = (@{
    userId = $login.userId
    username = $login.username
    realName = $login.realName
    roles = @($login.roles)
} | ConvertTo-Json -Compress)

$cases = @(
    @{
        path = "/users"
        title = "用户管理"
        name = "用户管理弹窗"
        body = @'
    await clickButton('新增用户');
    await expectDialog('新增用户');
    await closeOverlays();
    await clickButton('编辑');
    await expectDialog('编辑用户');
    await closeOverlays();
    await clickButton('重置密码');
    await expectDialog('重置密码');
    await closeOverlays();
'@
    },
    @{
        path = "/master"
        title = "主数据维护"
        name = "主数据关键弹窗"
        body = @'
    await clickButton('新增物料');
    await expectDialog('新增物料');
    await closeOverlays();
    await clickTab('工序与路线');
    await clickButton('新增工序');
    await expectDialog('新增工序');
    await closeOverlays();
    await clickTab('BOM');
    await clickButton('新增BOM行');
    await expectDialog('新增BOM行');
    await expectText('父项物料');
    await expectText('子项物料');
    await closeOverlays();
    await clickTab('检验标准');
    await clickButton('新增检验项目');
    await expectDialog('新增检验项目');
    await closeOverlays();
'@
    },
    @{
        path = "/batches"
        title = "批次台账"
        name = "批次入库与详情"
        body = @'
    await clickButton('原材料入库');
    await expectDialog('原材料入库');
    await closeOverlays();
    await clickButton('详情');
    await expectDrawer('批次详情');
    await expectText('检验任务历史');
    await closeOverlays();
'@
    },
    @{
        path = "/production"
        title = "生产工单"
        name = "生产工单关键入口"
        body = @'
    await clickButton('创建工单');
    await expectDialog('创建生产工单');
    await closeOverlays();
    await clickButton('详情');
    await expectDrawer('工单详情');
    await expectText('工序流转');
    await closeOverlays();
    if (await clickButtonIfExists('领料')) {
      await expectDialog('生产领料');
      await closeOverlays();
    }
'@
    },
    @{
        path = "/inspection"
        title = "检验任务"
        name = "检验明细抽屉"
        body = @'
    await clickButton('明细');
    await expectDrawer('检验明细');
    await closeOverlays();
'@
    },
    @{
        path = "/defects"
        title = "缺陷管理"
        name = "缺陷登记与处置弹窗"
        body = @'
    await clickButton('登记缺陷');
    await expectDialog('登记缺陷');
    await closeOverlays();
    if (await clickButtonIfExists('处置')) {
      await expectDialog('缺陷处置');
      await closeOverlays();
    }
'@
    },
    @{
        path = "/shipments"
        title = "出货管理"
        name = "出货登记弹窗"
        body = @'
    await clickButton('成品出货');
    await expectDialog('成品出货');
    await closeOverlays();
'@
    },
    @{
        path = "/trace"
        title = "追溯查询"
        name = "追溯图交互"
        body = @'
    await waitFor(() => document.querySelectorAll('.trace-panel canvas').length >= 2, '追溯图未渲染双向画布', 100, 150);
    const actions = allBySelector('.trace-chart-actions .el-button');
    if (actions.length < 6) throw new Error('追溯图缩放/适配按钮数量不足');
    actions[1].click();
    log('点击：反向放大');
    await sleep(300);
    actions[3].click();
    log('点击：正向缩小');
    await sleep(300);
    await clickButton('适配');
'@
    },
    @{
        path = "/recalls"
        title = "召回管理"
        name = "召回发起与详情"
        body = @'
    await clickButton('发起召回');
    await expectDialog('发起召回');
    await expectText('影响面预览');
    await closeOverlays();
    await clickButton('详情');
    await expectDrawer('召回详情');
    await expectText('回收明细');
    await closeOverlays();
'@
    },
    @{
        path = "/reports"
        title = "统计报表"
        name = "统计图表切换"
        body = @'
    await waitFor(() => document.querySelectorAll('.chart-canvas canvas').length >= 2, '统计图表画布未渲染', 100, 150);
    await clickText('近3个月');
    await waitFor(() => normalize(document.body.innerText).includes('近 3 个月'), '趋势区间未切换到近 3 个月');
    await clickText('近12个月');
    await waitFor(() => normalize(document.body.innerText).includes('近 12 个月'), '趋势区间未切换到近 12 个月');
'@
    }
)

$tempProfile = Join-Path $env:TEMP ("quality-trace-interactions-" + [guid]::NewGuid().ToString("N"))
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
    foreach ($case in $cases) {
        $rows += Invoke-InteractionCase `
            -Socket $socket `
            -NextId ([ref]$nextId) `
            -Events $events `
            -Path $case.path `
            -Title $case.title `
            -Name $case.name `
            -Body $case.body
    }

    [pscustomobject]@{
        interactions = $rows
        interactionCount = $rows.Count
        result = "FRONTEND_INTERACTIONS_OK"
    } | ConvertTo-Json -Depth 8
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
