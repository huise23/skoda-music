$ErrorActionPreference = "Stop"

$isRepo = git rev-parse --is-inside-work-tree 2>$null
if ($LASTEXITCODE -ne 0 -or $isRepo.Trim() -ne "true") {
    exit 0
}

$staged = git diff --cached --name-only --diff-filter=ACMR
if (-not $staged) {
    exit 0
}

$memoryFiles = @("CURRENT_STATUS.md", "HANDOFF.md")
$supportFiles = @(
    "CHANGELOG.md",
    "DECISIONS.md",
    "NEXT_STEPS.md",
    "PROJECT_BRIEF.md",
    "TASKS.md"
)

function Test-ContentChange([string]$path) {
    if ($memoryFiles -contains $path) { return $false }
    if ($supportFiles -contains $path) { return $false }
    if ($path.StartsWith(".githooks/")) { return $false }
    if ($path.StartsWith("scripts/")) { return $false }
    return $true
}

$contentChanged = $false
$memoryStaged = $false

foreach ($file in $staged) {
    if (Test-ContentChange $file) { $contentChanged = $true }
    if ($memoryFiles -contains $file) { $memoryStaged = $true }
}

if ($contentChanged -and -not $memoryStaged) {
    Write-Host "Blocked commit: project files changed but CURRENT_STATUS.md / HANDOFF.md were not staged." -ForegroundColor Red
    Write-Host "Please update and stage at least one of them." -ForegroundColor Yellow
    exit 1
}

exit 0
