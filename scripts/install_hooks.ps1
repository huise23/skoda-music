$ErrorActionPreference = "Stop"

git config core.hooksPath .githooks
Write-Host "Configured project hooks path: .githooks"
