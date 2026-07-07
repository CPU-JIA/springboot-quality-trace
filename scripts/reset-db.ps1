param(
    [string]$Mysql = "",
    [string]$User = "root",
    [string]$Password = "123456"
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$schema = Join-Path $root "sql\01-schema.sql"
$data = Join-Path $root "sql\02-init-data.sql"

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

$mysqlExe = Resolve-MysqlExe

function Invoke-MysqlScript([string]$scriptPath) {
    if (-not (Test-Path $scriptPath)) {
        throw "SQL 文件不存在：$scriptPath"
    }
    $command = "`"$mysqlExe`" -u$User -p$Password --default-character-set=utf8mb4 < `"$scriptPath`""
    cmd.exe /c $command
    if ($LASTEXITCODE -ne 0) {
        throw "执行 SQL 失败：$scriptPath"
    }
}

Invoke-MysqlScript $schema
Invoke-MysqlScript $data

[pscustomobject]@{
    database = "quality_trace"
    schema = "01-schema.sql"
    data = "02-init-data.sql"
    result = "RESET_OK"
} | ConvertTo-Json
