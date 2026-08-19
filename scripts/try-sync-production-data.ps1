$ErrorActionPreference = 'Stop'

try {
    & (Join-Path $PSScriptRoot 'sync-production-data.ps1')
}
catch {
    # 本地生产数据镜像是开发辅助能力，不应阻断小程序源码编译。
    # 数据库、Docker 或密码尚未准备好时继续使用现有本地数据，并明确给出提示。
    Write-Warning "Skipped production data sync: $($_.Exception.Message)"
}
