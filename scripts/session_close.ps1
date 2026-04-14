param(
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"

$root = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

function Get-FileText([string]$path) {
    return Get-Content -LiteralPath $path -Raw -Encoding UTF8
}

function Set-FileText([string]$path, [string]$text) {
    Set-Content -LiteralPath $path -Value $text -Encoding UTF8
}

function Set-LastUpdated([string]$content, [string]$stamp) {
    return [regex]::Replace(
        $content,
        "(Last Updated:\s*<!-- AUTO:LAST_UPDATED -->\s*).+",
        "`${1}$stamp"
    )
}

function Set-BlockContent([string]$content, [string]$start, [string]$end, [string[]]$lines) {
    $body = if ($lines -and $lines.Count -gt 0) { ($lines -join "`n") } else { "- (none)" }
    $pattern = [regex]::Escape($start) + "\r?\n.*?\r?\n" + [regex]::Escape($end)
    $replacement = "$start`n$body`n$end"
    return [regex]::Replace($content, $pattern, $replacement, [System.Text.RegularExpressions.RegexOptions]::Singleline)
}

function Get-NextActions([string]$nextStepsPath) {
    if (-not (Test-Path -LiteralPath $nextStepsPath)) {
        return @("- [ ] Define next actions in NEXT_STEPS.md")
    }
    $actions = New-Object System.Collections.Generic.List[string]
    foreach ($line in (Get-Content -LiteralPath $nextStepsPath -Encoding UTF8)) {
        $s = $line.Trim()
        if ($s.StartsWith("- [ ]")) {
            $actions.Add($s)
        }
        if ($actions.Count -ge 5) { break }
    }
    if ($actions.Count -eq 0) {
        return @("- [ ] Define next actions in NEXT_STEPS.md")
    }
    return $actions.ToArray()
}

function Get-ChangedFiles() {
    $isRepo = git rev-parse --is-inside-work-tree 2>$null
    if ($LASTEXITCODE -ne 0 -or $isRepo.Trim() -ne "true") {
        return @("- (no local file changes)")
    }

    $unstaged = git diff --name-only
    $staged = git diff --cached --name-only
    $all = @($unstaged) + @($staged) | Where-Object { $_ -and $_.Trim() -ne "" } | Sort-Object -Unique
    if (-not $all -or $all.Count -eq 0) {
        return @("- (no local file changes)")
    }
    return $all | Select-Object -First 12 | ForEach-Object { "- ``$_``" }
}

$stamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$statusPath = Join-Path $root "CURRENT_STATUS.md"
$handoffPath = Join-Path $root "HANDOFF.md"
$nextStepsPath = Join-Path $root "NEXT_STEPS.md"
$changelogPath = Join-Path $root "CHANGELOG.md"

if (-not (Test-Path -LiteralPath $statusPath) -or -not (Test-Path -LiteralPath $handoffPath)) {
    throw "CURRENT_STATUS.md or HANDOFF.md is missing."
}

$statusText = Set-LastUpdated (Get-FileText $statusPath) $stamp
$handoffText = Set-LastUpdated (Get-FileText $handoffPath) $stamp

$nextActions = Get-NextActions $nextStepsPath
$changedFiles = Get-ChangedFiles

$handoffText = Set-BlockContent $handoffText "<!-- AUTO:NEXT_ACTIONS_START -->" "<!-- AUTO:NEXT_ACTIONS_END -->" $nextActions
$handoffText = Set-BlockContent $handoffText "<!-- AUTO:CHANGED_FILES_START -->" "<!-- AUTO:CHANGED_FILES_END -->" $changedFiles

$date = $stamp.Split(" ")[0]
$entryLines = New-Object System.Collections.Generic.List[string]
$entryLines.Add("## $date")
$entryLines.Add("- Session close update at $stamp.")
foreach ($item in $changedFiles) {
    $entryLines.Add("- Updated: $($item.Substring(2))")
}
$entry = ($entryLines -join "`n") + "`n"

if ($DryRun) {
    Write-Host "[Dry run] CURRENT_STATUS.md preview:`n"
    Write-Host $statusText
    Write-Host "`n[Dry run] HANDOFF.md preview:`n"
    Write-Host $handoffText
    Write-Host "`n[Dry run] CHANGELOG append preview:`n"
    Write-Host $entry
    exit 0
}

Set-FileText $statusPath $statusText
Set-FileText $handoffPath $handoffText

if (Test-Path -LiteralPath $changelogPath) {
    $changelog = Get-FileText $changelogPath
} else {
    $changelog = "# CHANGELOG`n"
}
if (-not $changelog.EndsWith("`n")) {
    $changelog += "`n"
}
$changelog += "`n$entry"
Set-FileText $changelogPath $changelog

Write-Host "Session close files updated."
