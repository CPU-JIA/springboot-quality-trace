param(
    [string]$BaseUrl = "http://localhost:8081/api",
    [string]$Mysql = "",
    [string]$User = "root",
    [string]$Password = "123456",
    [switch]$NoReset,
    [switch]$KeepGeneratedData
)

$ErrorActionPreference = "Stop"

$workDate = "2026-07-05"
$planEndDate = "2026-07-06"
$beforeWorkDate = "2026-07-04"
$futureDate = "2099-01-01"
$resetScript = Join-Path $PSScriptRoot "reset-db.ps1"

function Reset-Database {
    & $resetScript -Mysql $Mysql -User $User -Password $Password | Out-Null
}

function Login([string]$username) {
    $body = @{ username = $username; password = "123456" } | ConvertTo-Json
    $response = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method Post -ContentType "application/json" -Body $body
    if ($response.code -ne 200) {
        throw "登录失败：$username / $($response.message)"
    }
    return $response.data.token
}

function Invoke-Api([string]$token, [string]$method, [string]$path, $body = $null) {
    $params = @{
        Uri = "$BaseUrl$path"
        Method = $method
        Headers = @{ Authorization = "Bearer $token" }
    }
    if ($null -ne $body) {
        $params.ContentType = "application/json"
        $params.Body = $body | ConvertTo-Json -Depth 12
    }

    $response = Invoke-RestMethod @params
    if ($response.code -ne 200) {
        throw "$method $path 调用失败：$($response.message)"
    }
    return $response.data
}

function Invoke-ApiResult([string]$token, [string]$method, [string]$path, $body = $null) {
    $params = @{
        Uri = "$BaseUrl$path"
        Method = $method
        Headers = @{ Authorization = "Bearer $token" }
    }
    if ($null -ne $body) {
        $params.ContentType = "application/json"
        $params.Body = $body | ConvertTo-Json -Depth 12
    }
    return Invoke-RestMethod @params
}

function Assert-True([bool]$condition, [string]$message) {
    if (-not $condition) {
        throw $message
    }
}

function Assert-Equal($actual, $expected, [string]$message) {
    if ("$actual" -ne "$expected") {
        throw "$message。期望：$expected，实际：$actual"
    }
}

function Assert-DecimalEqual($actual, [decimal]$expected, [string]$message) {
    if ([decimal]$actual -ne $expected) {
        throw "$message。期望：$expected，实际：$actual"
    }
}

function Assert-FailedResult($response, [int]$expectedCode, [string]$messageContains, [string]$message) {
    Assert-Equal $response.code $expectedCode $message
    if ($messageContains -and "$($response.message)" -notlike "*$messageContains*") {
        throw "$message。返回消息不包含[$messageContains]，实际：$($response.message)"
    }
}

function To-IdString($value) {
    return "$value"
}

function Get-MaterialByCode([string]$token, [string]$code) {
    $keyword = [uri]::EscapeDataString($code)
    $page = Invoke-Api $token "Get" "/materials?current=1&size=100&keyword=$keyword"
    $material = @($page.records) |
        Where-Object { $_.materialCode -eq $code } |
        Select-Object -First 1
    Assert-True ($null -ne $material) "未找到物料 $code"
    return $material
}

function Build-PassRecords($items) {
    $records = foreach ($item in @($items)) {
        $row = [ordered]@{
            inspectionItemId = $item.id
        }
        if ([int]$item.isQuantitative -eq 1) {
            $measuredValue = switch ($item.itemCode) {
                "II-009" { 1.000; break }
                "II-012" { 268.000; break }
                default {
                    if ($null -ne $item.lowerLimit -and $null -ne $item.upperLimit) {
                        ([decimal]$item.lowerLimit + [decimal]$item.upperLimit) / 2
                    } elseif ($null -ne $item.upperLimit) {
                        [decimal]$item.upperLimit
                    } elseif ($null -ne $item.lowerLimit) {
                        [decimal]$item.lowerLimit
                    } else {
                        1.000
                    }
                    break
                }
            }
            $row.measuredValue = $measuredValue
            $row.resultDesc = "端到端验证合格"
        } else {
            $row.resultDesc = "端到端验证合格"
            $row.isPass = 1
        }
        $row
    }
    return @($records)
}

function Build-FailRecords($items) {
    $records = @(Build-PassRecords $items)
    $firstItem = @($items) | Select-Object -First 1
    Assert-True ($null -ne $firstItem) "未提供检验项目，无法构造不合格明细"

    $records[0]["resultDesc"] = "端到端验证不合格"
    if ([int]$firstItem.isQuantitative -eq 1) {
        if ($null -ne $firstItem.upperLimit) {
            $records[0]["measuredValue"] = [decimal]$firstItem.upperLimit + 1
        } elseif ($null -ne $firstItem.lowerLimit) {
            $records[0]["measuredValue"] = [decimal]$firstItem.lowerLimit - 1
        } else {
            $records[0]["measuredValue"] = 0.000
        }
    } else {
        $records[0]["isPass"] = 0
    }
    return @($records)
}

function Assert-PendingDefectByBatch([string]$token, $batchId, $taskId, [string]$severity) {
    $defects = Invoke-Api $token "Get" "/defects?current=1&size=100&batchId=$batchId&handleStatus=PENDING"
    $defect = @($defects.records) |
        Where-Object { (To-IdString $_.inspectionTaskId) -eq (To-IdString $taskId) } |
        Select-Object -First 1
    Assert-True ($null -ne $defect) "批次 $batchId 的不合格检验未自动生成待处置缺陷"
    Assert-Equal $defect.severity $severity "自动缺陷严重度不正确"
    Assert-Equal $defect.handleStatus "PENDING" "自动缺陷应为待处置状态"
    Assert-True ($null -ne $defect.batchStatus) "批次缺陷列表未返回批次状态，前端无法过滤有效处置方式"
    Assert-True ($null -ne $defect.batchSourceType) "批次缺陷列表未返回批次来源，前端无法过滤退货处置方式"
}

function Assert-PendingDefectByProcess([string]$token, $processRecordId, $taskId, [string]$severity) {
    $defects = Invoke-Api $token "Get" "/defects?current=1&size=100&processRecordId=$processRecordId&handleStatus=PENDING"
    $defect = @($defects.records) |
        Where-Object { (To-IdString $_.inspectionTaskId) -eq (To-IdString $taskId) } |
        Select-Object -First 1
    Assert-True ($null -ne $defect) "工序 $processRecordId 的不合格 IPQC 未自动生成过程缺陷"
    Assert-Equal $defect.severity $severity "自动过程缺陷严重度不正确"
    Assert-Equal $defect.handleStatus "PENDING" "自动过程缺陷应为待处置状态"
}

function Complete-InspectionTask([string]$token, $taskId, [string]$remark) {
    $null = Invoke-Api $token "Post" "/inspection-tasks/$taskId/claim"
    $items = Invoke-Api $token "Get" "/inspection-tasks/$taskId/items"
    $records = Build-PassRecords $items
    Assert-True (@($records).Count -gt 0) "检验任务 $taskId 未返回检验项目"

    $body = [ordered]@{
        conclusion = "QUALIFIED"
        remark = $remark
        records = @($records)
    }
    $null = Invoke-Api $token "Post" "/inspection-tasks/$taskId/submit" $body
}

function Find-PendingTaskByBatch([string]$token, $batchId, [string]$inspectType) {
    $tasks = Invoke-Api $token "Get" "/inspection-tasks?current=1&size=100&status=PENDING&inspectType=$inspectType"
    $task = @($tasks.records) |
        Where-Object { (To-IdString $_.batchId) -eq (To-IdString $batchId) } |
        Sort-Object { [int64]$_.id } -Descending |
        Select-Object -First 1
    Assert-True ($null -ne $task) "未找到批次 $batchId 的 $inspectType 待检任务"
    return $task
}

function Find-PendingTaskByProcess([string]$token, $processRecordId) {
    $tasks = Invoke-Api $token "Get" "/inspection-tasks?current=1&size=100&status=PENDING&inspectType=IPQC"
    $task = @($tasks.records) |
        Where-Object { (To-IdString $_.processRecordId) -eq (To-IdString $processRecordId) } |
        Sort-Object { [int64]$_.id } -Descending |
        Select-Object -First 1
    Assert-True ($null -ne $task) "未找到工序记录 $processRecordId 的 IPQC 待检任务"
    return $task
}

function Get-ProcessRecordByStep($orderDetail, [int]$stepNo) {
    $record = @($orderDetail.processRecords) |
        Where-Object { [int]$_.stepNo -eq $stepNo } |
        Select-Object -First 1
    Assert-True ($null -ne $record) "工单 $($orderDetail.order.id) 缺少第 $stepNo 道工序"
    return $record
}

function Get-MaterialRequirementByCode($orderDetail, [string]$materialCode) {
    $requirement = @($orderDetail.materialRequirements) |
        Where-Object { $_.materialCode -eq $materialCode } |
        Select-Object -First 1
    Assert-True ($null -ne $requirement) "工单 $($orderDetail.order.id) 缺少物料 $materialCode 的 BOM 领料达成行"
    return $requirement
}

function Assert-MaterialRequirement($orderDetail, [string]$materialCode, [decimal]$required, [decimal]$issued, [bool]$issuedEnough) {
    $requirement = Get-MaterialRequirementByCode $orderDetail $materialCode
    Assert-DecimalEqual $requirement.requiredQuantity $required "物料 $materialCode 的计划需求量不正确"
    Assert-DecimalEqual $requirement.issuedQuantity $issued "物料 $materialCode 的已领数量不正确"
    Assert-Equal $requirement.issuedEnough $issuedEnough "物料 $materialCode 的足额领料状态不正确"
}

function Create-ProductionOrder([string]$token, [long]$materialId, [decimal]$quantity, [string]$remark) {
    $body = [ordered]@{
        materialId = $materialId
        planQuantity = $quantity
        planStartDate = $workDate
        planEndDate = $planEndDate
        remark = $remark
    }
    return Invoke-Api $token "Post" "/production-orders" $body
}

function Issue-Materials([string]$token, $orderId, $items) {
    $body = @{
        items = @($items)
    }
    $null = Invoke-Api $token "Post" "/production-orders/$orderId/issue-materials" $body
}

function Run-Process([string]$productionToken, [string]$inspectorToken, $record, [bool]$needIpqc) {
    $null = Invoke-Api $productionToken "Post" "/production-orders/process-records/$($record.id)/start"
    $null = Invoke-Api $productionToken "Post" "/production-orders/process-records/$($record.id)/complete" @{
        remark = "端到端验证报工"
    }
    if ($needIpqc) {
        $task = Find-PendingTaskByProcess $inspectorToken $record.id
        Complete-InspectionTask $inspectorToken $task.id "端到端验证 IPQC 合格"
    }
}

if (-not $NoReset) {
    Reset-Database
}

$summary = $null

try {
    $admin = Login "admin"
    $warehouse = Login "zhangsan"
    $production = Login "lisi"
    $inspector = Login "wangwu"
    $quality = Login "zhaoliu"
    $warehouseProfile = Invoke-Api $warehouse "Get" "/auth/profile"
    $productionProfile = Invoke-Api $production "Get" "/auth/profile"

    $missingApi = Invoke-ApiResult $quality "Get" "/__missing-endpoint"
    Assert-FailedResult $missingApi 404 "接口不存在" "未知接口未返回中文404"
    $missingParam = Invoke-ApiResult $quality "Get" "/process-routes"
    Assert-FailedResult $missingParam 400 "materialId" "缺少必要参数未返回中文400"
    $badPathId = Invoke-ApiResult $warehouse "Get" "/batches/not-a-number"
    Assert-FailedResult $badPathId 400 "id" "非法路径ID未返回中文400"
    $badEnum = Invoke-ApiResult $warehouse "Get" "/batches?status=NOT_A_STATUS"
    Assert-FailedResult $badEnum 400 "status" "非法枚举参数未返回中文400"
    $anonymousProfile = Invoke-RestMethod -Uri "$BaseUrl/auth/profile" -Method Get
    Assert-FailedResult $anonymousProfile 401 "未登录" "未携带令牌仍可访问当前用户接口"
    $nonAdminUsers = Invoke-ApiResult $warehouse "Get" "/users?current=1&size=10"
    Assert-FailedResult $nonAdminUsers 403 "无权" "非管理员仍可访问用户管理接口"

    $roleRows = Invoke-Api $admin "Get" "/users/roles"
    $adminRole = @($roleRows) | Where-Object { $_.roleCode -eq "ADMIN" } | Select-Object -First 1
    $warehouseRole = @($roleRows) | Where-Object { $_.roleCode -eq "WAREHOUSE" } | Select-Object -First 1
    Assert-True ($null -ne $adminRole) "未找到管理员角色，无法验证当前账号自降权保护"
    Assert-True ($null -ne $warehouseRole) "未找到仓库角色，无法验证停用令牌失效"
    $adminUserPage = Invoke-Api $admin "Get" "/users?current=1&size=10&keyword=admin"
    $adminUser = @($adminUserPage.records) | Where-Object { $_.username -eq "admin" } | Select-Object -First 1
    Assert-True ($null -ne $adminUser) "未找到管理员用户，无法验证当前账号自降权保护"
    $selfDemotion = Invoke-ApiResult $admin "Put" "/users/$($adminUser.id)" @{
        username = $adminUser.username
        realName = $adminUser.realName
        phone = $adminUser.phone
        roleIds = @($warehouseRole.id)
    }
    Assert-FailedResult $selfDemotion 400 "不能移除自己的管理员角色" "管理员仍可移除自己的ADMIN角色"
    $adminProfileAfterDemotionGuard = Invoke-Api $admin "Get" "/auth/profile"
    Assert-True (@($adminProfileAfterDemotionGuard.roles) -contains "ADMIN") "自降权拦截后管理员角色丢失"
    $disabledTokenUsername = "tmp_disabled_e2e"
    $null = Invoke-Api $admin "Post" "/users" @{
        username = $disabledTokenUsername
        password = "123456"
        realName = "停用令牌验证"
        phone = "13800000000"
        roleIds = @($warehouseRole.id)
    }
    $disabledToken = Login $disabledTokenUsername
    $disabledUserPage = Invoke-Api $admin "Get" "/users?current=1&size=10&keyword=$disabledTokenUsername"
    $disabledUser = @($disabledUserPage.records) | Where-Object { $_.username -eq $disabledTokenUsername } | Select-Object -First 1
    Assert-True ($null -ne $disabledUser) "未找到停用令牌验证用户"
    $null = Invoke-Api $admin "Put" "/users/$($disabledUser.id)/status" @{
        status = 0
    }
    $disabledProfile = Invoke-ApiResult $disabledToken "Get" "/auth/profile"
    Assert-FailedResult $disabledProfile 401 "账号已被禁用" "账号停用后旧令牌仍可继续访问"
    $badCurrent = Invoke-ApiResult $admin "Get" "/materials?current=0&size=10"
    Assert-FailedResult $badCurrent 400 "页码必须从1开始" "非法页码未返回中文400"
    $badSize = Invoke-ApiResult $admin "Get" "/materials?current=1&size=101"
    Assert-FailedResult $badSize 400 "每页条数必须在1到100之间" "超大分页未返回中文400"
    $badTrendMonths = Invoke-ApiResult $quality "Get" "/stats/pass-rate-trend?months=25"
    Assert-FailedResult $badTrendMonths 400 "统计月份范围不能超过24个月" "统计趋势月份上限未被拒绝"
    $badStatus = Invoke-ApiResult $admin "Put" "/materials/1/status" @{
        status = 2
    }
    Assert-FailedResult $badStatus 400 "状态取值只能为0或1" "非法启停状态未返回中文400"
    $materialsForEditGuard = Invoke-Api $admin "Get" "/materials?current=1&size=100"
    $materialWithStock = @($materialsForEditGuard.records) |
        Where-Object { (To-IdString $_.id) -eq "1" } |
        Select-Object -First 1
    Assert-True ($null -ne $materialWithStock) "未找到用于物料编辑停用校验的物料"
    $editDisableMaterial = Invoke-ApiResult $admin "Put" "/materials/$($materialWithStock.id)" @{
        materialCode = $materialWithStock.materialCode
        name = $materialWithStock.name
        category = $materialWithStock.category
        spec = $materialWithStock.spec
        unit = $materialWithStock.unit
        shelfLifeDays = $materialWithStock.shelfLifeDays
        status = 0
    }
    Assert-FailedResult $editDisableMaterial 400 "不可停用" "物料编辑接口绕过在库批次停用校验"
    $auditMaterial = Get-MaterialByCode $admin "SF-001"
    $missingMaterialStatus = Invoke-ApiResult $admin "Put" "/materials/$($auditMaterial.id)" @{
        materialCode = $auditMaterial.materialCode
        name = $auditMaterial.name
        category = $auditMaterial.category
        spec = $auditMaterial.spec
        unit = $auditMaterial.unit
        shelfLifeDays = $auditMaterial.shelfLifeDays
    }
    Assert-FailedResult $missingMaterialStatus 400 "状态不能为空" "物料编辑漏传状态未返回明确中文提示"
    $forgedTimestamp = "2000-01-01 00:00:00"
    $null = Invoke-Api $admin "Put" "/materials/$($auditMaterial.id)" @{
        materialCode = $auditMaterial.materialCode
        name = $auditMaterial.name
        category = $auditMaterial.category
        spec = $auditMaterial.spec
        unit = $auditMaterial.unit
        shelfLifeDays = $auditMaterial.shelfLifeDays
        status = $auditMaterial.status
        createdAt = $forgedTimestamp
        updatedAt = $forgedTimestamp
    }
    $auditMaterialAfter = Get-MaterialByCode $admin "SF-001"
    Assert-True ("$($auditMaterialAfter.createdAt)" -ne $forgedTimestamp) "物料编辑接口不应允许客户端覆盖创建时间"
    Assert-True ("$($auditMaterialAfter.updatedAt)" -ne $forgedTimestamp) "物料编辑接口不应允许客户端覆盖更新时间"
    $missingRouteMaterial = Invoke-ApiResult $admin "Get" "/process-routes?materialId=999999"
    Assert-FailedResult $missingRouteMaterial 400 "物料不存在或已被删除" "不存在物料查询路线未返回中文400"
    $rawMaterialRoute = Invoke-ApiResult $admin "Get" "/process-routes?materialId=1"
    Assert-FailedResult $rawMaterialRoute 400 "原材料为外购物料" "原材料查询生产路线未被拒绝"
    $duplicateRouteProcess = Invoke-ApiResult $admin "Put" "/process-routes" @{
        materialId = 6
        steps = @(
            @{ processDefId = 1; stepNo = 1 },
            @{ processDefId = 1; stepNo = 2 }
        )
    }
    Assert-FailedResult $duplicateRouteProcess 400 "重复选择同一工序" "同一工艺路线重复工序未被拒绝"
    $duplicateBom = Invoke-ApiResult $admin "Post" "/boms" @{
        parentMaterialId = 6
        childMaterialId = 3
        quantity = 1.000
    }
    Assert-FailedResult $duplicateBom 400 "已存在相同子项BOM行" "重复BOM父子项未返回明确业务提示"

    $null = Invoke-Api $admin "Post" "/materials" @{
        materialCode = "TMP-SF-ROUTE"
        name = "路线停用校验半成品"
        category = "SEMI"
        spec = "E2E"
        unit = "个"
        shelfLifeDays = $null
        status = 1
    }
    $routeStoppedMaterial = Get-MaterialByCode $admin "TMP-SF-ROUTE"
    $null = Invoke-Api $admin "Put" "/materials/$($routeStoppedMaterial.id)/status" @{
        status = 0
    }
    $stoppedRouteSave = Invoke-ApiResult $admin "Put" "/process-routes" @{
        materialId = $routeStoppedMaterial.id
        steps = @(
            @{ processDefId = 1; stepNo = 1 }
        )
    }
    Assert-FailedResult $stoppedRouteSave 400 "已停用" "停用物料仍可维护工艺路线"

    $null = Invoke-Api $admin "Post" "/materials" @{
        materialCode = "TMP-RAW-BOM"
        name = "BOM停用校验原材料"
        category = "RAW"
        spec = "E2E"
        unit = "个"
        shelfLifeDays = $null
        status = 1
    }
    $bomStoppedChild = Get-MaterialByCode $admin "TMP-RAW-BOM"
    $null = Invoke-Api $admin "Put" "/materials/$($bomStoppedChild.id)/status" @{
        status = 0
    }
    $stoppedParentBom = Invoke-ApiResult $admin "Post" "/boms" @{
        parentMaterialId = $routeStoppedMaterial.id
        childMaterialId = 3
        quantity = 1.000
    }
    Assert-FailedResult $stoppedParentBom 400 "父项物料已停用" "停用父项物料仍可新增BOM行"
    $stoppedChildBom = Invoke-ApiResult $admin "Post" "/boms" @{
        parentMaterialId = 6
        childMaterialId = $bomStoppedChild.id
        quantity = 1.000
    }
    Assert-FailedResult $stoppedChildBom 400 "子项物料已停用" "停用子项物料仍可新增BOM行"

    $null = Invoke-Api $admin "Post" "/materials" @{
        materialCode = "TMP-SF-ORDER"
        name = "工单停用BOM校验半成品"
        category = "SEMI"
        spec = "E2E"
        unit = "个"
        shelfLifeDays = $null
        status = 1
    }
    $null = Invoke-Api $admin "Post" "/materials" @{
        materialCode = "TMP-RAW-ORDER"
        name = "工单停用BOM校验原材料"
        category = "RAW"
        spec = "E2E"
        unit = "个"
        shelfLifeDays = $null
        status = 1
    }
    $orderParentMaterial = Get-MaterialByCode $admin "TMP-SF-ORDER"
    $orderChildMaterial = Get-MaterialByCode $admin "TMP-RAW-ORDER"
    $null = Invoke-Api $admin "Post" "/boms" @{
        parentMaterialId = $orderParentMaterial.id
        childMaterialId = $orderChildMaterial.id
        quantity = 1.000
    }
    $null = Invoke-Api $admin "Put" "/process-routes" @{
        materialId = $orderParentMaterial.id
        steps = @(
            @{ processDefId = 1; stepNo = 1 }
        )
    }
    $missingFqcOrder = Invoke-ApiResult $production "Post" "/production-orders" @{
        materialId = $orderParentMaterial.id
        planQuantity = 1.000
        planStartDate = $workDate
        planEndDate = $planEndDate
        remark = "端到端验证：缺少FQC标准不能创建工单"
    }
    Assert-FailedResult $missingFqcOrder 400 "FQC检验标准" "未配置FQC标准的生产物料仍可创建工单"
    $null = Invoke-Api $admin "Post" "/inspection-items" @{
        itemCode = "TMP-II-FQC-ORDER"
        itemName = "临时成品检验项"
        inspectType = "FQC"
        materialId = $orderParentMaterial.id
        processDefId = $null
        isQuantitative = 0
        standardDesc = "端到端验证FQC标准"
        lowerLimit = $null
        upperLimit = $null
        unit = $null
    }
    $null = Invoke-Api $production "Post" "/production-orders" @{
        materialId = $orderParentMaterial.id
        planQuantity = 1.000
        planStartDate = $workDate
        planEndDate = $planEndDate
        remark = "端到端验证：未关闭工单冻结工序IPQC配置"
    }
    $openOrderBomCreate = Invoke-ApiResult $admin "Post" "/boms" @{
        parentMaterialId = $orderParentMaterial.id
        childMaterialId = 3
        quantity = 1.000
    }
    Assert-FailedResult $openOrderBomCreate 400 "未关闭生产工单" "未关闭工单对应父项物料仍可新增BOM行"
    $openOrderBomRows = Invoke-Api $admin "Get" "/boms?parentMaterialId=$($orderParentMaterial.id)"
    $openOrderBomRow = @($openOrderBomRows) | Select-Object -First 1
    Assert-True ($null -ne $openOrderBomRow) "未找到用于验证未关闭工单BOM删除保护的BOM行"
    $openOrderBomDelete = Invoke-ApiResult $admin "Delete" "/boms/$($openOrderBomRow.id)"
    Assert-FailedResult $openOrderBomDelete 400 "未关闭生产工单" "未关闭工单对应父项物料仍可删除BOM行"
    $processRows = Invoke-Api $admin "Get" "/processes?current=1&size=100&keyword=PR-INJ"
    $processInRoute = @($processRows.records) | Where-Object { $_.processCode -eq "PR-INJ" } | Select-Object -First 1
    Assert-True ($null -ne $processInRoute) "未找到用于验证IPQC配置漂移保护的工序"
    $openOrderIpqcChange = Invoke-ApiResult $admin "Put" "/processes/$($processInRoute.id)" @{
        processCode = $processInRoute.processCode
        processName = $processInRoute.processName
        needIpqc = 1
        description = $processInRoute.description
    }
    Assert-FailedResult $openOrderIpqcChange 400 "未关闭生产工单" "未关闭工单引用的工序仍可修改IPQC开关"
    $null = Invoke-Api $admin "Put" "/materials/$($orderChildMaterial.id)/status" @{
        status = 0
    }
    $stoppedBomChildOrder = Invoke-ApiResult $production "Post" "/production-orders" @{
        materialId = $orderParentMaterial.id
        planQuantity = 1.000
        planStartDate = $workDate
        planEndDate = $planEndDate
        remark = "端到端验证：BOM子项停用不能创建工单"
    }
    Assert-FailedResult $stoppedBomChildOrder 400 "BOM子项物料" "BOM子项停用后仍可创建生产工单"
    $missingMaterialDelete = Invoke-ApiResult $admin "Delete" "/materials/999999"
    Assert-FailedResult $missingMaterialDelete 400 "物料不存在或已被删除" "删除不存在物料未返回中文400"
    $missingSupplierDelete = Invoke-ApiResult $admin "Delete" "/suppliers/999999"
    Assert-FailedResult $missingSupplierDelete 400 "供应商不存在或已被删除" "删除不存在供应商未返回中文400"
    $missingCustomerDelete = Invoke-ApiResult $admin "Delete" "/customers/999999"
    Assert-FailedResult $missingCustomerDelete 400 "客户不存在或已被删除" "删除不存在客户未返回中文400"
    $supplierStatusPage = Invoke-Api $admin "Get" "/suppliers?current=1&size=1"
    $supplierStatusGuard = @($supplierStatusPage.records) | Select-Object -First 1
    Assert-True ($null -ne $supplierStatusGuard) "未找到用于供应商状态漏传校验的供应商"
    $missingSupplierStatus = Invoke-ApiResult $admin "Put" "/suppliers/$($supplierStatusGuard.id)" @{
        supplierCode = $supplierStatusGuard.supplierCode
        name = $supplierStatusGuard.name
        contactPerson = $supplierStatusGuard.contactPerson
        phone = $supplierStatusGuard.phone
        address = $supplierStatusGuard.address
    }
    Assert-FailedResult $missingSupplierStatus 400 "状态不能为空" "供应商编辑漏传状态未返回明确中文提示"
    $customerStatusPage = Invoke-Api $admin "Get" "/customers?current=1&size=1"
    $customerStatusGuard = @($customerStatusPage.records) | Select-Object -First 1
    Assert-True ($null -ne $customerStatusGuard) "未找到用于客户状态漏传校验的客户"
    $missingCustomerStatus = Invoke-ApiResult $admin "Put" "/customers/$($customerStatusGuard.id)" @{
        customerCode = $customerStatusGuard.customerCode
        name = $customerStatusGuard.name
        contactPerson = $customerStatusGuard.contactPerson
        phone = $customerStatusGuard.phone
        address = $customerStatusGuard.address
    }
    Assert-FailedResult $missingCustomerStatus 400 "状态不能为空" "客户编辑漏传状态未返回明确中文提示"
    $missingProcessDelete = Invoke-ApiResult $admin "Delete" "/processes/999999"
    Assert-FailedResult $missingProcessDelete 400 "工序不存在或已被删除" "删除不存在工序未返回中文400"
    $missingInspectionItemDelete = Invoke-ApiResult $admin "Delete" "/inspection-items/999999"
    Assert-FailedResult $missingInspectionItemDelete 400 "检验项目不存在或已被删除" "删除不存在检验项目未返回中文400"

    $doubleTargetDefect = Invoke-ApiResult $inspector "Post" "/defects" @{
        batchId = 1
        processRecordId = 1
        defectType = "OTHER"
        severity = "MINOR"
        quantity = 1.000
        description = "端到端验证：错误双载体缺陷"
    }
    Assert-FailedResult $doubleTargetDefect 400 "缺陷载体只能选择批次或工序其一" "缺陷双载体未被拒绝"

    $pendingDefectBatch = Invoke-Api $warehouse "Post" "/batches/purchase-inbound" @{
        materialId = 1
        supplierId = 1
        quantity = 1.000
        productionDate = $workDate
        warehouseLocation = "A区-检验流程验证"
        remark = "端到端验证：待检批次不能绕过检验任务登记缺陷"
    }
    $pendingBatchDefect = Invoke-ApiResult $inspector "Post" "/defects" @{
        batchId = $pendingDefectBatch.id
        defectType = "OTHER"
        severity = "MINOR"
        quantity = 1.000
        description = "端到端验证：待检批次应走检验任务"
    }
    Assert-FailedResult $pendingBatchDefect 400 "检验流程" "待检批次仍可绕过检验任务登记人工缺陷"

    $manualDefectBatch = Invoke-Api $warehouse "Post" "/batches/purchase-inbound" @{
        materialId = 1
        supplierId = 1
        quantity = 1.000
        productionDate = $workDate
        warehouseLocation = "A区-缺陷隔离验证"
        remark = "端到端验证：人工缺陷登记后冻结隔离"
    }
    $manualDefectTask = Find-PendingTaskByBatch $inspector $manualDefectBatch.id "IQC"
    Complete-InspectionTask $inspector $manualDefectTask.id "端到端验证人工缺陷前先放行"
    $overBatchDefect = Invoke-ApiResult $inspector "Post" "/defects" @{
        batchId = $manualDefectBatch.id
        defectType = "OTHER"
        severity = "MINOR"
        quantity = 2.000
        description = "端到端验证：缺陷数量超过批次数量"
    }
    Assert-FailedResult $overBatchDefect 400 "不能超过批次初始数量" "超出批次数量的人工缺陷未被拒绝"
    $null = Invoke-Api $inspector "Post" "/defects" @{
        batchId = $manualDefectBatch.id
        defectType = "OTHER"
        severity = "MINOR"
        quantity = 1.000
        description = "端到端验证：人工缺陷应冻结可用批次"
    }
    $manualDefectLocated = Invoke-Api $quality "Get" "/trace/by-no/$($manualDefectBatch.batchNo)"
    Assert-Equal $manualDefectLocated.status "FROZEN" "人工登记待处置缺陷后可用批次未冻结隔离"

    $null = Invoke-Api $quality "Put" "/defects/3/handle" @{
        handleMethod = "SCRAP"
    }
    $duplicateHandle = Invoke-ApiResult $quality "Put" "/defects/3/handle" @{
        handleMethod = "SCRAP"
    }
    Assert-FailedResult $duplicateHandle 400 "已处置" "重复处置缺陷未被拒绝"

    $futureInbound = Invoke-ApiResult $warehouse "Post" "/batches/purchase-inbound" @{
        materialId = 1
        supplierId = 1
        quantity = 1.000
        productionDate = $futureDate
        warehouseLocation = "A区-日期边界验证"
        remark = "端到端验证：未来入库日期应被拒绝"
    }
    Assert-FailedResult $futureInbound 400 "入库日期不能晚于今天" "未来入库日期未被拒绝"

    $null = Invoke-Api $admin "Post" "/materials" @{
        materialCode = "TMP-RAW-NO-IQC"
        name = "未配置IQC标准原材料"
        category = "RAW"
        spec = "E2E"
        unit = "个"
        shelfLifeDays = $null
        status = 1
    }
    $rawWithoutIqc = Get-MaterialByCode $admin "TMP-RAW-NO-IQC"
    $missingIqcInbound = Invoke-ApiResult $warehouse "Post" "/batches/purchase-inbound" @{
        materialId = $rawWithoutIqc.id
        supplierId = 1
        quantity = 1.000
        productionDate = $workDate
        warehouseLocation = "A区-标准项验证"
        remark = "端到端验证：缺少IQC标准不能入库"
    }
    Assert-FailedResult $missingIqcInbound 400 "IQC检验标准" "未配置IQC标准的原材料仍可入库"

    $guardBatch = Invoke-Api $warehouse "Post" "/batches/purchase-inbound" @{
        materialId = 1
        supplierId = 1
        quantity = 2.000
        productionDate = $workDate
        warehouseLocation = "A区-状态机验证"
        remark = "端到端验证：检验中召回防降级"
    }
    $guardTask = Find-PendingTaskByBatch $inspector $guardBatch.id "IQC"
    $null = Invoke-Api $inspector "Post" "/inspection-tasks/$($guardTask.id)/claim"
    $duplicateClaim = Invoke-ApiResult $inspector "Post" "/inspection-tasks/$($guardTask.id)/claim"
    Assert-FailedResult $duplicateClaim 400 "只有待领取任务可以领取" "重复领取检验任务未被拒绝"
    $openTaskItemCreate = Invoke-ApiResult $admin "Post" "/inspection-items" @{
        itemCode = "TMP-II-OPEN-IQC"
        itemName = "在途任务标准漂移校验"
        inspectType = "IQC"
        materialId = 1
        processDefId = $null
        isQuantitative = 0
        standardDesc = "端到端验证：在途任务禁止新增标准"
        lowerLimit = $null
        upperLimit = $null
        unit = $null
    }
    Assert-FailedResult $openTaskItemCreate 400 "待检或检验中任务" "在途IQC任务存在时仍可新增同对象检验标准"
    $openTaskIqcItems = Invoke-Api $admin "Get" "/inspection-items?current=1&size=100&inspectType=IQC&materialId=1"
    $openTaskIqcItem = @($openTaskIqcItems.records) | Select-Object -First 1
    Assert-True ($null -ne $openTaskIqcItem) "未找到用于检验标准漂移保护的IQC标准项"
    $openTaskItemUpdate = Invoke-ApiResult $admin "Put" "/inspection-items/$($openTaskIqcItem.id)" @{
        itemCode = $openTaskIqcItem.itemCode
        itemName = $openTaskIqcItem.itemName
        inspectType = $openTaskIqcItem.inspectType
        materialId = $openTaskIqcItem.materialId
        processDefId = $openTaskIqcItem.processDefId
        isQuantitative = $openTaskIqcItem.isQuantitative
        standardDesc = "端到端验证：在途任务禁止调整标准"
        lowerLimit = $openTaskIqcItem.lowerLimit
        upperLimit = $openTaskIqcItem.upperLimit
        unit = $openTaskIqcItem.unit
    }
    Assert-FailedResult $openTaskItemUpdate 400 "待检或检验中任务" "在途IQC任务存在时仍可调整同对象检验标准"
    $openTaskItemDelete = Invoke-ApiResult $admin "Delete" "/inspection-items/$($openTaskIqcItem.id)"
    Assert-FailedResult $openTaskItemDelete 400 "待检或检验中任务" "在途IQC任务存在时仍可删除同对象检验标准"
    $guardItems = Invoke-Api $inspector "Get" "/inspection-tasks/$($guardTask.id)/items"
    $guardRecords = Build-PassRecords $guardItems
    Assert-True (@($guardRecords).Count -gt 0) "防降级检验任务未返回检验项目"
    $submitByOtherUser = Invoke-ApiResult $admin "Post" "/inspection-tasks/$($guardTask.id)/submit" @{
        conclusion = "QUALIFIED"
        remark = "端到端验证：非领取人不能提交检验结论"
        records = @($guardRecords)
    }
    Assert-FailedResult $submitByOtherUser 400 "领取人" "非领取人仍可提交检验结论"
    $allPassUnqualified = Invoke-ApiResult $inspector "Post" "/inspection-tasks/$($guardTask.id)/submit" @{
        conclusion = "UNQUALIFIED"
        remark = "端到端验证：全项合格不能判不合格"
        records = @($guardRecords)
    }
    Assert-FailedResult $allPassUnqualified 400 "至少需要一项检验明细不通过" "全项合格仍可提交不合格结论"
    $allPassConcession = Invoke-ApiResult $inspector "Post" "/inspection-tasks/$($guardTask.id)/submit" @{
        conclusion = "CONCESSION"
        remark = "端到端验证：全项合格不能判让步接收"
        records = @($guardRecords)
        concessionDefect = @{
            defectType = "OTHER"
            quantity = 1.000
            description = "端到端验证：全项合格让步应被拒绝"
        }
    }
    Assert-FailedResult $allPassConcession 400 "至少需要一项检验明细不通过" "全项合格仍可提交让步接收结论"
    $null = Invoke-Api $quality "Post" "/recalls" @{
        sourceBatchId = $guardBatch.id
        recallLevel = "III"
        reason = "端到端验证：检验中批次召回后禁止提交结论回写合格"
    }
    $guardSubmit = Invoke-ApiResult $inspector "Post" "/inspection-tasks/$($guardTask.id)/submit" @{
        conclusion = "QUALIFIED"
        remark = "端到端验证：召回后尝试提交合格"
        records = @($guardRecords)
    }
    Assert-FailedResult $guardSubmit 400 "已召回" "已召回批次提交检验未被状态机拒绝"
    $guardLocated = Invoke-Api $quality "Get" "/trace/by-no/$($guardBatch.batchNo)"
    Assert-Equal $guardLocated.status "RECALLED" "已召回批次被检验结论错误降级"

    $badIqcBatch = Invoke-Api $warehouse "Post" "/batches/purchase-inbound" @{
        materialId = 2
        supplierId = 2
        quantity = 2.000
        productionDate = $workDate
        warehouseLocation = "A区-IQC不合格验证"
        remark = "端到端验证：IQC不合格自动缺陷"
    }
    Assert-Equal $badIqcBatch.createdBy $warehouseProfile.userId "采购入库批次登记人未由当前仓库用户自动填充"
    $badIqcTask = Find-PendingTaskByBatch $inspector $badIqcBatch.id "IQC"
    $null = Invoke-Api $inspector "Post" "/inspection-tasks/$($badIqcTask.id)/claim"
    $badIqcItems = Invoke-Api $inspector "Get" "/inspection-tasks/$($badIqcTask.id)/items"
    $badIqcRecords = Build-FailRecords $badIqcItems
    $missingUnqualifiedDefect = Invoke-ApiResult $inspector "Post" "/inspection-tasks/$($badIqcTask.id)/submit" @{
        conclusion = "UNQUALIFIED"
        remark = "端到端验证：不合格结论缺少缺陷留痕"
        records = @($badIqcRecords)
    }
    Assert-FailedResult $missingUnqualifiedDefect 400 "必须同步登记缺陷" "不合格结论缺少缺陷留痕仍可提交"
    $overUnqualifiedDefect = Invoke-ApiResult $inspector "Post" "/inspection-tasks/$($badIqcTask.id)/submit" @{
        conclusion = "UNQUALIFIED"
        remark = "端到端验证：不合格缺陷数量超过批次数量"
        records = @($badIqcRecords)
        unqualifiedDefect = @{
            defectType = "FUNCTION"
            severity = "MAJOR"
            quantity = 3.000
            description = "端到端验证：不合格缺陷数量超出批次初始数量"
        }
    }
    Assert-FailedResult $overUnqualifiedDefect 400 "不能超过批次初始数量" "超出批次数量的不合格缺陷留痕未被拒绝"
    $null = Invoke-Api $inspector "Post" "/inspection-tasks/$($badIqcTask.id)/submit" @{
        conclusion = "UNQUALIFIED"
        remark = "端到端验证：IQC不合格冻结并生成缺陷"
        records = @($badIqcRecords)
        unqualifiedDefect = @{
            defectType = "FUNCTION"
            severity = "MAJOR"
            quantity = 2.000
            description = "端到端验证：加热管冷态电阻超差"
        }
    }
    $badIqcLocated = Invoke-Api $quality "Get" "/trace/by-no/$($badIqcBatch.batchNo)"
    Assert-Equal $badIqcLocated.status "FROZEN" "IQC不合格后批次未冻结"
    Assert-PendingDefectByBatch $quality $badIqcBatch.id $badIqcTask.id "MAJOR"

    $overIssueOrder = Create-ProductionOrder $production 6 1 "端到端验证：超额领料应被拒绝"
    $overIssue = Invoke-ApiResult $production "Post" "/production-orders/$($overIssueOrder.id)/issue-materials" @{
        items = @(
            @{ batchId = 3; quantity = 2.000 }
        )
    }
    Assert-FailedResult $overIssue 400 "累计领料超过工单BOM需求" "超额领料未被拒绝"

    $notIssuedOrder = Create-ProductionOrder $production 6 1 "端到端验证：未领料不能开工"
    $notIssuedDetail = Invoke-Api $production "Get" "/production-orders/$($notIssuedOrder.id)"
    Assert-MaterialRequirement $notIssuedDetail "RM-003" 1.000 0.000 $false
    Assert-MaterialRequirement $notIssuedDetail "RM-004" 0.150 0.000 $false
    $overProcessDefect = Invoke-ApiResult $inspector "Post" "/defects" @{
        processRecordId = (Get-ProcessRecordByStep $notIssuedDetail 1).id
        defectType = "FUNCTION"
        severity = "MAJOR"
        quantity = 2.000
        description = "端到端验证：过程缺陷数量超过工单计划"
    }
    Assert-FailedResult $overProcessDefect 400 "不能超过工单计划数量" "超出工单计划数量的过程缺陷未被拒绝"
    $notStartedProcessDefect = Invoke-ApiResult $inspector "Post" "/defects" @{
        processRecordId = (Get-ProcessRecordByStep $notIssuedDetail 1).id
        defectType = "FUNCTION"
        severity = "MINOR"
        quantity = 1.000
        description = "端到端验证：未开工工序不能登记过程缺陷"
    }
    Assert-FailedResult $notStartedProcessDefect 400 "生产中的工单" "未开工工单仍可登记过程缺陷"
    $notIssuedStart = Invoke-ApiResult $production "Post" "/production-orders/process-records/$((Get-ProcessRecordByStep $notIssuedDetail 1).id)/start"
    Assert-FailedResult $notIssuedStart 400 "尚未领料" "未领料工单仍能开工"

    $missingMaterialOrder = Create-ProductionOrder $production 6 1 "端到端验证：未足额领料不能完工入库"
    Issue-Materials $production $missingMaterialOrder.id @(
        @{ batchId = 3; quantity = 0.001 },
        @{ batchId = 4; quantity = 0.001 }
    )
    $missingMaterialDetail = Invoke-Api $production "Get" "/production-orders/$($missingMaterialOrder.id)"
    Assert-MaterialRequirement $missingMaterialDetail "RM-003" 1.000 0.001 $false
    Assert-MaterialRequirement $missingMaterialDetail "RM-004" 0.150 0.001 $false
    Run-Process $production $inspector (Get-ProcessRecordByStep $missingMaterialDetail 1) $false
    $missingMaterialDetail = Invoke-Api $production "Get" "/production-orders/$($missingMaterialOrder.id)"
    Run-Process $production $inspector (Get-ProcessRecordByStep $missingMaterialDetail 2) $false
    $futureComplete = Invoke-ApiResult $production "Post" "/production-orders/$($missingMaterialOrder.id)/complete" @{
        actualQuantity = 1.000
        productionDate = $futureDate
        warehouseLocation = "C区-日期边界验证"
        remark = "端到端验证：未来生产日期应被拒绝"
    }
    Assert-FailedResult $futureComplete 400 "生产日期不能晚于今天" "未来生产日期未被拒绝"
    $beforePlanComplete = Invoke-ApiResult $production "Post" "/production-orders/$($missingMaterialOrder.id)/complete" @{
        actualQuantity = 1.000
        productionDate = $beforeWorkDate
        warehouseLocation = "C区-日期边界验证"
        remark = "端到端验证：早于计划开始的生产日期应被拒绝"
    }
    Assert-FailedResult $beforePlanComplete 400 "生产日期不能早于计划开始日期" "早于计划开始日期的完工入库未被拒绝"
    $missingMaterialComplete = Invoke-ApiResult $production "Post" "/production-orders/$($missingMaterialOrder.id)/complete" @{
        actualQuantity = 1.000
        productionDate = $workDate
        warehouseLocation = "C区-异常验证"
        remark = "端到端验证：未领料尝试完工"
    }
    Assert-FailedResult $missingMaterialComplete 400 "尚未足额领料" "未足额领料工单仍能完工入库"

    $semiOrder = Create-ProductionOrder $production 6 11 "端到端验证：生产半成品壶体组件"
    Issue-Materials $production $semiOrder.id @(
        @{ batchId = 3; quantity = 11.000 },
        @{ batchId = 4; quantity = 1.650 }
    )

    $semiDetail = Invoke-Api $production "Get" "/production-orders/$($semiOrder.id)"
    Assert-MaterialRequirement $semiDetail "RM-003" 11.000 11.000 $true
    Assert-MaterialRequirement $semiDetail "RM-004" 1.650 1.650 $true
    Run-Process $production $inspector (Get-ProcessRecordByStep $semiDetail 1) $false
    $semiDetail = Invoke-Api $production "Get" "/production-orders/$($semiOrder.id)"
    Run-Process $production $inspector (Get-ProcessRecordByStep $semiDetail 2) $false

    $semiBatch = Invoke-Api $production "Post" "/production-orders/$($semiOrder.id)/complete" @{
        actualQuantity = 11.000
        productionDate = $workDate
        warehouseLocation = "C区-端到端验证"
        remark = "端到端验证半成品入库"
    }
    Assert-Equal $semiBatch.createdBy $productionProfile.userId "半成品完工批次登记人未由当前生产用户自动填充"
    $semiFqc = Find-PendingTaskByBatch $inspector $semiBatch.id "FQC"
    Complete-InspectionTask $inspector $semiFqc.id "端到端验证半成品 FQC 合格"
    $semiFqcItems = Invoke-Api $inspector "Get" "/inspection-tasks/$($semiFqc.id)/items"
    $duplicateSubmit = Invoke-ApiResult $inspector "Post" "/inspection-tasks/$($semiFqc.id)/submit" @{
        conclusion = "QUALIFIED"
        remark = "端到端验证：重复提交半成品 FQC"
        records = @(Build-PassRecords $semiFqcItems)
    }
    Assert-FailedResult $duplicateSubmit 400 "只有检验中的任务可以提交结论" "重复提交检验结论未被拒绝"

    $semiDetail = Invoke-Api $production "Get" "/production-orders/$($semiOrder.id)"
    Assert-Equal $semiDetail.order.status "CLOSED" "半成品工单 FQC 后未关闭"
    $closedOrderProcessDefect = Invoke-ApiResult $inspector "Post" "/defects" @{
        processRecordId = (Get-ProcessRecordByStep $semiDetail 1).id
        defectType = "FUNCTION"
        severity = "MINOR"
        quantity = 1.000
        description = "端到端验证：已关闭工单历史工序不能登记过程缺陷"
    }
    Assert-FailedResult $closedOrderProcessDefect 400 "批次缺陷" "已关闭工单历史工序仍可登记过程缺陷"

    $ipqcFailOrder = Create-ProductionOrder $production 7 1 "端到端验证：IPQC不合格返修复检"
    Issue-Materials $production $ipqcFailOrder.id @(
        @{ batchId = $semiBatch.id; quantity = 1.000 },
        @{ batchId = 1; quantity = 1.000 },
        @{ batchId = 2; quantity = 1.000 },
        @{ batchId = 5; quantity = 1.000 }
    )
    $ipqcFailDetail = Invoke-Api $production "Get" "/production-orders/$($ipqcFailOrder.id)"
    $ipqcFailStep1 = Get-ProcessRecordByStep $ipqcFailDetail 1
    $null = Invoke-Api $production "Post" "/production-orders/process-records/$($ipqcFailStep1.id)/start"
    $null = Invoke-Api $production "Post" "/production-orders/process-records/$($ipqcFailStep1.id)/complete" @{
        remark = "端到端验证：IPQC不合格前报工"
    }
    $badIpqcTask = Find-PendingTaskByProcess $inspector $ipqcFailStep1.id
    $null = Invoke-Api $inspector "Post" "/inspection-tasks/$($badIpqcTask.id)/claim"
    $badIpqcItems = Invoke-Api $inspector "Get" "/inspection-tasks/$($badIpqcTask.id)/items"
    $badIpqcRecords = Build-FailRecords $badIpqcItems
    $null = Invoke-Api $inspector "Post" "/inspection-tasks/$($badIpqcTask.id)/submit" @{
        conclusion = "UNQUALIFIED"
        remark = "端到端验证：IPQC不合格退回返修"
        records = @($badIpqcRecords)
        unqualifiedDefect = @{
            defectType = "FUNCTION"
            severity = "MAJOR"
            quantity = 1.000
            description = "端到端验证：总装扭矩/功能不合格"
        }
    }
    $ipqcFailDetail = Invoke-Api $production "Get" "/production-orders/$($ipqcFailOrder.id)"
    $ipqcFailStep1 = Get-ProcessRecordByStep $ipqcFailDetail 1
    Assert-Equal $ipqcFailStep1.status "IN_PROGRESS" "IPQC不合格后工序未退回返修状态"
    Assert-Equal $ipqcFailStep1.ipqcReleased $false "IPQC不合格后工序错误放行"
    Assert-PendingDefectByProcess $quality $ipqcFailStep1.id $badIpqcTask.id "MAJOR"
    $ipqcBlockedNext = Invoke-ApiResult $production "Post" "/production-orders/process-records/$((Get-ProcessRecordByStep $ipqcFailDetail 2).id)/start"
    Assert-FailedResult $ipqcBlockedNext 400 "前道工序尚未完工" "IPQC不合格后仍能流转下道工序"
    $null = Invoke-Api $production "Post" "/production-orders/process-records/$($ipqcFailStep1.id)/complete" @{
        remark = "端到端验证：返修后重新报工"
    }
    $recheckIpqcTask = Find-PendingTaskByProcess $inspector $ipqcFailStep1.id
    Complete-InspectionTask $inspector $recheckIpqcTask.id "端到端验证 IPQC 返修复检合格"
    $ipqcFailDetail = Invoke-Api $production "Get" "/production-orders/$($ipqcFailOrder.id)"
    Assert-Equal (Get-ProcessRecordByStep $ipqcFailDetail 1).ipqcReleased $true "IPQC返修复检合格后未放行"

    $productOrder = Create-ProductionOrder $production 7 10 "端到端验证：生产成品电热水壶"
    Issue-Materials $production $productOrder.id @(
        @{ batchId = $semiBatch.id; quantity = 10.000 },
        @{ batchId = 1; quantity = 10.000 },
        @{ batchId = 2; quantity = 10.000 },
        @{ batchId = 5; quantity = 10.000 }
    )

    $productDetail = Invoke-Api $production "Get" "/production-orders/$($productOrder.id)"
    Assert-MaterialRequirement $productDetail "SF-001" 10.000 10.000 $true
    Assert-MaterialRequirement $productDetail "RM-001" 10.000 10.000 $true
    Assert-MaterialRequirement $productDetail "RM-002" 10.000 10.000 $true
    Assert-MaterialRequirement $productDetail "RM-005" 10.000 10.000 $true
    Run-Process $production $inspector (Get-ProcessRecordByStep $productDetail 1) $true
    $productDetail = Invoke-Api $production "Get" "/production-orders/$($productOrder.id)"
    Assert-Equal (Get-ProcessRecordByStep $productDetail 1).ipqcReleased $true "第1道 IPQC 合格后详情未标记放行"
    $productDetail = Invoke-Api $production "Get" "/production-orders/$($productOrder.id)"
    Run-Process $production $inspector (Get-ProcessRecordByStep $productDetail 2) $true
    $productDetail = Invoke-Api $production "Get" "/production-orders/$($productOrder.id)"
    Assert-Equal (Get-ProcessRecordByStep $productDetail 2).ipqcReleased $true "第2道 IPQC 合格后详情未标记放行"
    $productDetail = Invoke-Api $production "Get" "/production-orders/$($productOrder.id)"
    Run-Process $production $inspector (Get-ProcessRecordByStep $productDetail 3) $false

    $productBatch = Invoke-Api $production "Post" "/production-orders/$($productOrder.id)/complete" @{
        actualQuantity = 10.000
        productionDate = $workDate
        warehouseLocation = "D区-端到端验证"
        remark = "端到端验证成品入库"
    }
    Assert-Equal $productBatch.createdBy $productionProfile.userId "成品完工批次登记人未由当前生产用户自动填充"
    $productFqc = Find-PendingTaskByBatch $inspector $productBatch.id "FQC"
    Complete-InspectionTask $inspector $productFqc.id "端到端验证成品 FQC 合格"

    $productDetail = Invoke-Api $production "Get" "/production-orders/$($productOrder.id)"
    Assert-Equal $productDetail.order.status "CLOSED" "成品工单 FQC 后未关闭"

    $futureShipment = Invoke-ApiResult $warehouse "Post" "/shipments" @{
        batchId = $productBatch.id
        customerId = 1
        quantity = 1.000
        shipDate = $futureDate
        remark = "端到端验证：未来出货日期应被拒绝"
    }
    Assert-FailedResult $futureShipment 400 "出货日期不能晚于今天" "未来出货日期未被拒绝"
    $beforeProductionShipment = Invoke-ApiResult $warehouse "Post" "/shipments" @{
        batchId = $productBatch.id
        customerId = 1
        quantity = 1.000
        shipDate = $beforeWorkDate
        remark = "端到端验证：早于生产日期的出货应被拒绝"
    }
    Assert-FailedResult $beforeProductionShipment 400 "出货日期不能早于批次生产日期" "早于批次生产日期的出货未被拒绝"

    $shipment = Invoke-Api $warehouse "Post" "/shipments" @{
        batchId = $productBatch.id
        customerId = 1
        quantity = 3.000
        shipDate = $workDate
        remark = "端到端验证出货"
    }

    $locatedBatch = Invoke-Api $warehouse "Get" "/trace/by-no/$($productBatch.batchNo)"
    Assert-Equal $locatedBatch.id $productBatch.id "按批次号追溯定位错误"
    Assert-Equal $locatedBatch.status "QUALIFIED" "成品出货后状态应仍为合格在库"
    Assert-DecimalEqual $locatedBatch.remainingQuantity 7.000 "成品出货后余量不正确"

    $upstream = Invoke-Api $warehouse "Get" "/trace/upstream/$($productBatch.id)"
    $downstream = Invoke-Api $warehouse "Get" "/trace/downstream/1"
    $newUpstreamNos = @($upstream | Where-Object {
            $_.batchNo -in @($semiBatch.batchNo, "RM-20260601-001", "RM-20260601-002", "RM-20260601-003", "RM-20260601-004", "RM-20260601-005")
        })
    $newDownstreamRows = @($downstream | Where-Object { (To-IdString $_.batchId) -eq (To-IdString $productBatch.id) })

    Assert-True ($newUpstreamNos.Count -ge 6) "成品批次反向追溯未覆盖半成品和原材料"
    Assert-True ($newDownstreamRows.Count -ge 1) "原料批次正向追溯未覆盖新成品批次"
    Assert-True (@($newDownstreamRows | Where-Object { $_.shipmentNo -eq $shipment.shipmentNo }).Count -eq 1) "正向追溯未联接新出货记录"

    $recall = Invoke-Api $quality "Post" "/recalls" @{
        sourceBatchId = $productBatch.id
        recallLevel = "III"
        reason = "端到端验证：成品批次客户流向与在库余量召回"
    }
    $duplicateRecall = Invoke-ApiResult $quality "Post" "/recalls" @{
        sourceBatchId = $productBatch.id
        recallLevel = "III"
        reason = "端到端验证：重复召回应被拒绝"
    }
    Assert-Equal $duplicateRecall.code 400 "重复发起同一源头批次召回未被拒绝"

    $recallDetail = Invoke-Api $quality "Get" "/recalls/$($recall.id)"
    Assert-Equal $recallDetail.sourceBatchNo $productBatch.batchNo "召回单源头批次不正确"
    Assert-True (@($recallDetail.details).Count -eq 2) "成品召回应包含已出货明细和在库隔离明细"
    Assert-True (@($recallDetail.details | Where-Object { $_.shipmentNo -eq $shipment.shipmentNo }).Count -eq 1) "召回明细未关联端到端出货单"
    Assert-True (@($recallDetail.details | Where-Object { $null -eq $_.shipmentNo -and $_.remark -like "*在库*" }).Count -eq 1) "召回明细缺少在库隔离记录"

    $shippedRecallDetail = @($recallDetail.details | Where-Object { $_.shipmentNo -eq $shipment.shipmentNo }) | Select-Object -First 1
    $inStockRecallDetail = @($recallDetail.details | Where-Object { $null -eq $_.shipmentNo }) | Select-Object -First 1
    Assert-True ($null -ne $shippedRecallDetail) "未找到已出货召回明细，无法验证客户通知状态机"
    Assert-True ($null -ne $inStockRecallDetail) "未找到在库隔离召回明细，无法验证在库直达终态"
    $customerDirectRecovered = Invoke-ApiResult $quality "Put" "/recalls/details/$($shippedRecallDetail.id)" @{
        recoveryStatus = "RECOVERED"
        remark = "端到端验证：客户明细跳过通知直接回收应被拒绝"
    }
    Assert-FailedResult $customerDirectRecovered 400 "先通知客户" "已出货召回明细仍可跳过通知直接进入终态"
    $null = Invoke-Api $quality "Put" "/recalls/details/$($shippedRecallDetail.id)" @{
        recoveryStatus = "NOTIFIED"
        remark = "端到端验证：已通知客户"
    }
    $recallStatusRegression = Invoke-ApiResult $quality "Put" "/recalls/details/$($shippedRecallDetail.id)" @{
        recoveryStatus = "PENDING"
        remark = "端到端验证：状态回退应被拒绝"
    }
    Assert-FailedResult $recallStatusRegression 400 "不允许" "召回明细状态仍可从已通知回退为待通知"
    $unrecoverableWithoutRemark = Invoke-ApiResult $quality "Put" "/recalls/details/$($shippedRecallDetail.id)" @{
        recoveryStatus = "UNRECOVERABLE"
        remark = ""
    }
    Assert-FailedResult $unrecoverableWithoutRemark 400 "无法回收必须填写原因" "无法回收缺少原因仍可保存"
    $null = Invoke-Api $quality "Put" "/recalls/details/$($shippedRecallDetail.id)" @{
        recoveryStatus = "RECOVERED"
        remark = "端到端验证：首条已回收"
    }
    $terminalRegression = Invoke-ApiResult $quality "Put" "/recalls/details/$($shippedRecallDetail.id)" @{
        recoveryStatus = "NOTIFIED"
        remark = "端到端验证：终态回退应被拒绝"
    }
    Assert-FailedResult $terminalRegression 400 "不允许" "召回明细终态仍可回退"
    $prematureRecallComplete = Invoke-ApiResult $quality "Post" "/recalls/$($recall.id)/complete"
    Assert-FailedResult $prematureRecallComplete 400 "未达到终态" "仍有召回明细未终态时仍可关闭召回单"

    $null = Invoke-Api $quality "Put" "/recalls/details/$($inStockRecallDetail.id)" @{
        recoveryStatus = "RECOVERED"
        remark = "端到端验证：在库隔离直接回收"
    }

    $recallDetail = Invoke-Api $quality "Get" "/recalls/$($recall.id)"
    foreach ($detail in @($recallDetail.details | Where-Object { $_.recoveryStatus -notin @("RECOVERED", "UNRECOVERABLE") })) {
        $null = Invoke-Api $quality "Put" "/recalls/details/$($detail.id)" @{
            recoveryStatus = "RECOVERED"
            remark = "端到端验证：已回收/隔离"
        }
    }
    $null = Invoke-Api $quality "Post" "/recalls/$($recall.id)/complete"
    $completedRecall = Invoke-Api $quality "Get" "/recalls/$($recall.id)"
    Assert-Equal $completedRecall.status "COMPLETED" "召回明细全部终态后未能关闭召回单"
    $completedOrderDetailUpdate = Invoke-ApiResult $quality "Put" "/recalls/details/$($shippedRecallDetail.id)" @{
        recoveryStatus = "RECOVERED"
        remark = "端到端验证：召回单完成后尝试修改明细"
    }
    Assert-FailedResult $completedOrderDetailUpdate 400 "召回单已完成" "召回单完成后仍可修改召回明细"

    $depletionSemiOrder = Create-ProductionOrder $production 6 1 "端到端验证：全量出货耗尽前置半成品"
    Issue-Materials $production $depletionSemiOrder.id @(
        @{ batchId = 3; quantity = 1.000 },
        @{ batchId = 4; quantity = 0.150 }
    )
    $depletionSemiDetail = Invoke-Api $production "Get" "/production-orders/$($depletionSemiOrder.id)"
    Run-Process $production $inspector (Get-ProcessRecordByStep $depletionSemiDetail 1) $false
    $depletionSemiDetail = Invoke-Api $production "Get" "/production-orders/$($depletionSemiOrder.id)"
    Run-Process $production $inspector (Get-ProcessRecordByStep $depletionSemiDetail 2) $false
    $depletionSemiBatch = Invoke-Api $production "Post" "/production-orders/$($depletionSemiOrder.id)/complete" @{
        actualQuantity = 1.000
        productionDate = $workDate
        warehouseLocation = "C区-耗尽验证"
        remark = "端到端验证：全量出货耗尽前置半成品入库"
    }
    $depletionSemiFqc = Find-PendingTaskByBatch $inspector $depletionSemiBatch.id "FQC"
    Complete-InspectionTask $inspector $depletionSemiFqc.id "端到端验证耗尽半成品 FQC 合格"

    $depletionProductOrder = Create-ProductionOrder $production 7 1 "端到端验证：全量出货耗尽成品"
    Issue-Materials $production $depletionProductOrder.id @(
        @{ batchId = $depletionSemiBatch.id; quantity = 1.000 },
        @{ batchId = 1; quantity = 1.000 },
        @{ batchId = 2; quantity = 1.000 },
        @{ batchId = 5; quantity = 1.000 }
    )
    $depletedSemiLocatedBatch = Invoke-Api $production "Get" "/trace/by-no/$($depletionSemiBatch.batchNo)"
    Assert-Equal $depletedSemiLocatedBatch.status "DEPLETED" "半成品全量领用后批次未置为已耗尽"
    Assert-DecimalEqual $depletedSemiLocatedBatch.remainingQuantity 0.000 "半成品全量领用后余量应为0"
    $depletionProductDetail = Invoke-Api $production "Get" "/production-orders/$($depletionProductOrder.id)"
    Run-Process $production $inspector (Get-ProcessRecordByStep $depletionProductDetail 1) $true
    $depletionProductDetail = Invoke-Api $production "Get" "/production-orders/$($depletionProductOrder.id)"
    Run-Process $production $inspector (Get-ProcessRecordByStep $depletionProductDetail 2) $true
    $depletionProductDetail = Invoke-Api $production "Get" "/production-orders/$($depletionProductOrder.id)"
    Run-Process $production $inspector (Get-ProcessRecordByStep $depletionProductDetail 3) $false
    $depletionProductBatch = Invoke-Api $production "Post" "/production-orders/$($depletionProductOrder.id)/complete" @{
        actualQuantity = 1.000
        productionDate = $workDate
        warehouseLocation = "D区-耗尽验证"
        remark = "端到端验证：全量出货耗尽成品入库"
    }
    $depletionProductFqc = Find-PendingTaskByBatch $inspector $depletionProductBatch.id "FQC"
    Complete-InspectionTask $inspector $depletionProductFqc.id "端到端验证耗尽成品 FQC 合格"
    $depletionShipment = Invoke-Api $warehouse "Post" "/shipments" @{
        batchId = $depletionProductBatch.id
        customerId = 1
        quantity = 1.000
        shipDate = $workDate
        remark = "端到端验证：全量出货后批次耗尽"
    }
    $depletedLocatedBatch = Invoke-Api $warehouse "Get" "/trace/by-no/$($depletionProductBatch.batchNo)"
    Assert-Equal $depletedLocatedBatch.status "DEPLETED" "成品全量出货后批次未置为已耗尽"
    Assert-DecimalEqual $depletedLocatedBatch.remainingQuantity 0.000 "成品全量出货后余量应为0"
    $shipmentAfterDepleted = Invoke-ApiResult $warehouse "Post" "/shipments" @{
        batchId = $depletionProductBatch.id
        customerId = 1
        quantity = 0.001
        shipDate = $workDate
        remark = "端到端验证：已耗尽批次禁止再次出货"
    }
    Assert-FailedResult $shipmentAfterDepleted 400 "不是合格在库状态或余量不足" "已耗尽批次仍可再次出货"

    $summary = [ordered]@{
        semiOrderNo = $semiOrder.orderNo
        semiBatchNo = $semiBatch.batchNo
        productOrderNo = $productOrder.orderNo
        productBatchNo = $productBatch.batchNo
        shipmentNo = $shipment.shipmentNo
        depletedProductBatchNo = $depletionProductBatch.batchNo
        depletedShipmentNo = $depletionShipment.shipmentNo
        recallNo = $recall.recallNo
        recallStatus = $completedRecall.status
        recallDetailCount = @($completedRecall.details).Count
        productOrderStatus = $productDetail.order.status
        productRemainingQuantity = $locatedBatch.remainingQuantity
        depletedProductRemainingQuantity = $depletedLocatedBatch.remainingQuantity
        upstreamRows = @($upstream).Count
        downstreamRowsFromRmBatch1 = @($downstream).Count
        result = "E2E_OK"
    }
} finally {
    if (-not $NoReset -and -not $KeepGeneratedData) {
        Reset-Database
    }
}

$summary | ConvertTo-Json -Depth 6
