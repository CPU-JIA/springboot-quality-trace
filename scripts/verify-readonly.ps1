param(
    [string]$BaseUrl = "http://localhost:8081/api"
)

$ErrorActionPreference = "Stop"

function Login($username) {
    $body = @{ username = $username; password = "123456" } | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method Post -ContentType "application/json" -Body $body
    if ($response.code -ne 200) {
        throw "登录失败：$username / $($response.message)"
    }
    return $response.data.token
}

function Get-Api($token, $path) {
    $response = Invoke-RestMethod -Uri "$BaseUrl$path" -Headers @{ Authorization = "Bearer $token" }
    if ($response.code -ne 200) {
        throw "$path 调用失败：$($response.message)"
    }
    return $response.data
}

function Assert-True([bool]$condition, [string]$message) {
    if (-not $condition) {
        throw $message
    }
}

$admin = Login "admin"
$warehouse = Login "zhangsan"
$production = Login "lisi"
$inspector = Login "wangwu"
$quality = Login "zhaoliu"

$users = Get-Api $admin "/users?current=1&size=10"
$materials = Get-Api $admin "/materials?current=1&size=20"
$activeRawMaterials = Get-Api $admin "/materials?current=1&size=20&category=RAW&status=1"
$suppliers = Get-Api $admin "/suppliers?current=1&size=10"
$activeSuppliers = Get-Api $admin "/suppliers?current=1&size=10&status=1"
$customers = Get-Api $admin "/customers?current=1&size=10"
$activeCustomers = Get-Api $admin "/customers?current=1&size=10&status=1"
$processes = Get-Api $admin "/processes?current=1&size=10"
$inspectionItems = Get-Api $admin "/inspection-items?current=1&size=10"
$boms = Get-Api $admin "/boms"

$product = $materials.records | Where-Object { $_.category -eq "PRODUCT" } | Select-Object -First 1
if ($null -ne $product) {
    $null = Get-Api $admin "/process-routes?materialId=$($product.id)"
    $null = Get-Api $admin "/boms/tree/$($product.id)"
}

$batches = Get-Api $warehouse "/batches?current=1&size=10"
$firstBatch = $batches.records | Select-Object -First 1
if ($null -ne $firstBatch) {
    $null = Get-Api $warehouse "/batches/$($firstBatch.id)"
    Assert-True ($null -ne $firstBatch.materialId) "批次台账未返回 materialId，无法支撑领料批次精确筛选"
    $batchesByMaterial = Get-Api $warehouse "/batches?current=1&size=10&materialId=$($firstBatch.materialId)"
    Assert-True (@($batchesByMaterial.records | Where-Object { "$($_.materialId)" -ne "$($firstBatch.materialId)" }).Count -eq 0) "批次台账 materialId 精确筛选返回了其他物料批次"
}

$orders = Get-Api $production "/production-orders?current=1&size=10"
foreach ($order in @($orders.records)) {
    Assert-True ([string]::IsNullOrWhiteSpace("$($order.materialCode)") -eq $false) "工单列表未返回生产物料编码"
    Assert-True ([string]::IsNullOrWhiteSpace("$($order.materialName)") -eq $false) "工单列表未返回生产物料名称"
}
$firstOrder = $orders.records | Select-Object -First 1
if ($null -ne $firstOrder) {
    $null = Get-Api $production "/production-orders/$($firstOrder.id)"
}

$tasks = Get-Api $inspector "/inspection-tasks?current=1&size=10"
$firstTask = $tasks.records | Select-Object -First 1
if ($null -ne $firstTask) {
    $null = Get-Api $inspector "/inspection-tasks/$($firstTask.id)/records"
    $null = Get-Api $inspector "/inspection-tasks/$($firstTask.id)/items"
}
$batchTask = $tasks.records | Where-Object { $null -ne $_.batchId } | Select-Object -First 1
if ($null -ne $batchTask) {
    $tasksByBatch = Get-Api $inspector "/inspection-tasks?current=1&size=10&batchId=$($batchTask.batchId)"
    Assert-True (@($tasksByBatch.records | Where-Object { "$($_.batchId)" -ne "$($batchTask.batchId)" }).Count -eq 0) "检验任务 batchId 精确筛选返回了其他批次任务"
}
$processTask = $tasks.records | Where-Object { $null -ne $_.processRecordId } | Select-Object -First 1
if ($null -ne $processTask) {
    $tasksByProcess = Get-Api $inspector "/inspection-tasks?current=1&size=10&processRecordId=$($processTask.processRecordId)&inspectType=IPQC"
    Assert-True (@($tasksByProcess.records | Where-Object { "$($_.processRecordId)" -ne "$($processTask.processRecordId)" -or "$($_.inspectType)" -ne "IPQC" }).Count -eq 0) "检验任务 processRecordId/IPQC 精确筛选返回了其他工序任务"
}

$defects = Get-Api $quality "/defects?current=1&size=10"
$processDefectTargets = Get-Api $quality "/defects/process-targets?current=1&size=20"
Assert-True (@($processDefectTargets.records).Count -le 20) "过程缺陷可选工序接口未按分页大小返回"
foreach ($target in @($processDefectTargets.records)) {
    Assert-True ($null -ne $target.id) "过程缺陷可选工序缺少工序记录 ID"
    Assert-True ([string]::IsNullOrWhiteSpace("$($target.orderNo)") -eq $false) "过程缺陷可选工序缺少工单号"
    Assert-True ([string]::IsNullOrWhiteSpace("$($target.processName)") -eq $false) "过程缺陷可选工序缺少工序名称"
    Assert-True ("$($target.status)" -eq "IN_PROGRESS") "过程缺陷可选工序返回了非进行中工序"
}
$shipments = Get-Api $warehouse "/shipments?current=1&size=10"
$recalls = Get-Api $quality "/recalls?current=1&size=10"
$shipmentByBatch = Get-Api $warehouse "/shipments?current=1&size=10&keyword=FP-20260614-001"
$customerKeyword = [uri]::EscapeDataString("苏宁")
$shipmentByCustomer = Get-Api $warehouse "/shipments?current=1&size=10&keyword=$customerKeyword"
$recallBySourceBatch = Get-Api $quality "/recalls?current=1&size=10&keyword=RM-20260610-001"
Assert-True (@($shipmentByBatch.records).Count -eq 2) "出货列表按批次号搜索未返回 FP-20260614-001 的两笔出货"
Assert-True (@($shipmentByCustomer.records | Where-Object { "$($_.customerName)" -like "*苏宁*" }).Count -ge 1) "出货列表按客户名搜索未返回苏宁客户出货"
Assert-True (@($recallBySourceBatch.records | Where-Object { $_.sourceBatchNo -eq "RM-20260610-001" }).Count -eq 1) "召回列表按源头批次号搜索未返回召回单"
$stats = Get-Api $quality "/stats/dashboard"
$null = Get-Api $quality "/stats/pass-rate-trend?months=6"
$currentMonth = Get-Date -Format "yyyy-MM"
$singleMonthTrend = Get-Api $quality "/stats/pass-rate-trend?months=1"
Assert-True (@($singleMonthTrend | Where-Object { $_.month -ne $currentMonth }).Count -eq 0) "合格率趋势 months=1 返回了非本月数据"
$null = Get-Api $quality "/stats/defect-pareto"
$null = Get-Api $quality "/stats/supplier-quality"

Assert-True (@($activeRawMaterials.records | Where-Object { "$($_.category)" -ne "RAW" -or "$($_.status)" -ne "1" }).Count -eq 0) "物料 status/category 筛选返回了非启用原材料"
Assert-True (@($activeSuppliers.records | Where-Object { "$($_.status)" -ne "1" }).Count -eq 0) "供应商 status 筛选返回了停用供应商"
Assert-True (@($activeCustomers.records | Where-Object { "$($_.status)" -ne "1" }).Count -eq 0) "客户 status 筛选返回了停用客户"

$traceBatch = Get-Api $quality "/trace/by-no/RM-20260610-001"
$upstream = Get-Api $quality "/trace/upstream/$($traceBatch.id)"
$downstream = Get-Api $quality "/trace/downstream/$($traceBatch.id)"
$productTraceBatch = Get-Api $quality "/trace/by-no/FP-20260614-001"
$productUpstream = Get-Api $quality "/trace/upstream/$($productTraceBatch.id)"
$productRoot = @($productUpstream) | Where-Object { $_.batchNo -eq "FP-20260614-001" } | Select-Object -First 1
Assert-True ($null -ne $productRoot) "成品批次反向追溯未返回起点批次"
Assert-True ("$($productRoot.productionOrderNo)" -eq "MO-20260612-001") "成品反向追溯未返回产出工单号"
Assert-True ("$($productRoot.processSummary)" -like "*总装*" -and "$($productRoot.processSummary)" -like "*整机测试*") "成品反向追溯未返回生产工序摘要"
Assert-True ("$($productRoot.inspectionSummary)" -like "*IPQC*" -and "$($productRoot.inspectionSummary)" -like "*FQC*" -and "$($productRoot.inspectionSummary)" -like "*合格*") "成品反向追溯未返回检验摘要"

[pscustomobject]@{
    users = $users.records.Count
    materials = $materials.records.Count
    activeRawMaterials = $activeRawMaterials.records.Count
    suppliers = $suppliers.records.Count
    activeSuppliers = $activeSuppliers.records.Count
    customers = $customers.records.Count
    activeCustomers = $activeCustomers.records.Count
    processes = $processes.records.Count
    inspectionItems = $inspectionItems.records.Count
    bomRows = $boms.Count
    batches = $batches.records.Count
    orders = $orders.records.Count
    inspectionTasks = $tasks.records.Count
    defects = $defects.records.Count
    processDefectTargets = $processDefectTargets.records.Count
    firstBatchMaterialId = $firstBatch.materialId
    shipments = $shipments.records.Count
    recalls = $recalls.records.Count
    shipmentSearchByBatch = $shipmentByBatch.records.Count
    shipmentSearchByCustomer = $shipmentByCustomer.records.Count
    recallSearchBySourceBatch = $recallBySourceBatch.records.Count
    activeRecallCount = $stats.activeRecallCount
    traceUpstream = @($upstream).Count
    traceDownstream = @($downstream).Count
    productTraceUpstream = @($productUpstream).Count
    result = "OK"
} | ConvertTo-Json -Depth 4
