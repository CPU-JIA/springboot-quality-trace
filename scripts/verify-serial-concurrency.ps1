param(
    [string]$Mysql = "",
    [string]$User = "root",
    [string]$Password = "123456",
    [int]$Count = 20,
    [string]$SerialKey = "",
    [switch]$KeepTestKey
)

$ErrorActionPreference = "Stop"

function Resolve-MysqlExe {
    if ($Mysql) {
        return $Mysql
    }
    $command = Get-Command mysql -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }
    $candidates = @(
        "E:\mysql\mysql-8.4.10-winx64\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe",
        "C:\Program Files\MySQL\MySQL Server 5.7\bin\mysql.exe"
    )
    foreach ($candidate in $candidates) {
        if (Test-Path $candidate) {
            return $candidate
        }
    }
    throw "未找到 mysql.exe。可通过 -Mysql 参数显式传入路径。"
}

function Escape-SqlLiteral([string]$value) {
    return $value.Replace("'", "''")
}

function Invoke-MysqlSql([string]$sql) {
    $output = & $mysqlExe "-u$User" "--default-character-set=utf8mb4" "-N" "-B" "-e" $sql
    if ($LASTEXITCODE -ne 0) {
        throw "执行 SQL 失败：$sql"
    }
    return @($output)
}

if ($Count -lt 2) {
    throw "Count 至少为 2，才能验证并发分配。"
}

$mysqlExe = Resolve-MysqlExe
$previousMysqlPwd = $env:MYSQL_PWD
$env:MYSQL_PWD = $Password
if ([string]::IsNullOrWhiteSpace($SerialKey)) {
    $SerialKey = "TEST-SN-" + (Get-Date -Format "yyyyMMddHHmmssfff")
}
$escapedKey = Escape-SqlLiteral $SerialKey

try {
    Invoke-MysqlSql "USE quality_trace; DELETE FROM serial_number WHERE serial_key = '$escapedKey';" | Out-Null

    $jobs = for ($i = 1; $i -le $Count; $i++) {
        Start-Job -ScriptBlock {
            param($mysqlExe, $user, $password, $key)

            $env:MYSQL_PWD = $password
            $sql = "USE quality_trace; INSERT INTO serial_number (serial_key, current_value) VALUES ('$key', LAST_INSERT_ID(1)) ON DUPLICATE KEY UPDATE current_value = LAST_INSERT_ID(current_value + 1); SELECT LAST_INSERT_ID();"
            $output = & $mysqlExe "-u$user" "--default-character-set=utf8mb4" "-N" "-B" "-e" $sql
            if ($LASTEXITCODE -ne 0) {
                throw "并发分配 SQL 执行失败"
            }
            [pscustomobject]@{
                seq = [int](@($output) | Select-Object -Last 1)
            }
        } -ArgumentList $mysqlExe, $User, $Password, $escapedKey
    }

    $null = Wait-Job -Job $jobs
    $results = @()
    foreach ($job in $jobs) {
        $results += Receive-Job -Job $job
    }
    Remove-Job -Job $jobs -Force

    if ($results.Count -ne $Count) {
        throw "并发分配结果条数不正确，期望 $Count，实际 $($results.Count)"
    }

    $seqs = @($results | ForEach-Object { [int]$_.seq } | Sort-Object)
    $uniqueCount = @($seqs | Select-Object -Unique).Count
    if ($uniqueCount -ne $Count) {
        throw "并发分配出现重复序号：$($seqs -join ',')"
    }
    if ($seqs[0] -ne 1 -or $seqs[$seqs.Count - 1] -ne $Count) {
        throw "并发分配序号不连续：$($seqs -join ',')"
    }

    $current = [int]((Invoke-MysqlSql "USE quality_trace; SELECT current_value FROM serial_number WHERE serial_key = '$escapedKey';") | Select-Object -First 1)
    if ($current -ne $Count) {
        throw "流水表当前值不正确，期望 $Count，实际 $current"
    }

    [pscustomobject]@{
        serialKey = $SerialKey
        requestCount = $Count
        minSeq = $seqs[0]
        maxSeq = $seqs[$seqs.Count - 1]
        uniqueCount = $uniqueCount
        currentValue = $current
        result = "SERIAL_CONCURRENCY_OK"
    } | ConvertTo-Json
} finally {
    if (-not $KeepTestKey -and $mysqlExe -and $escapedKey) {
        Invoke-MysqlSql "USE quality_trace; DELETE FROM serial_number WHERE serial_key = '$escapedKey';" | Out-Null
    }
    $env:MYSQL_PWD = $previousMysqlPwd
}
