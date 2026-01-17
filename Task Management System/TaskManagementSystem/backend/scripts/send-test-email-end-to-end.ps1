param(
  [string]$BaseUrl = "http://localhost:8081",
  [Parameter(Mandatory = $true)][string]$RecipientEmail,
  [string]$AdminEmail = "admin@example.com",
  [string]$AdminPassword = "AdminPassword123!",
  [string]$BackendDir = "",
  [int]$StartupTimeoutSeconds = 90
)

$ErrorActionPreference = 'Stop'

function Read-DotEnvValue([string]$FilePath, [string]$Key) {
  if (!(Test-Path $FilePath)) { return $null }
  $line = Get-Content $FilePath | Where-Object { $_ -match ("^\s*" + [Regex]::Escape($Key) + "\s*=") } | Select-Object -First 1
  if (-not $line) { return $null }
  return ($line -split "=", 2)[1].Trim()
}

function Ensure-Config([string]$DotEnvPath) {
  $mailEnabled = Read-DotEnvValue $DotEnvPath 'MAIL_ENABLED'
  $smtpHost = Read-DotEnvValue $DotEnvPath 'SMTP_HOST'
  $smtpUser = Read-DotEnvValue $DotEnvPath 'SMTP_USERNAME'
  $smtpPass = Read-DotEnvValue $DotEnvPath 'SMTP_PASSWORD'
  $mailFrom = Read-DotEnvValue $DotEnvPath 'MAIL_FROM'

  $adminEmail = Read-DotEnvValue $DotEnvPath 'ADMIN_EMAIL'
  $adminPass = Read-DotEnvValue $DotEnvPath 'ADMIN_PASSWORD'

  $missing = @()
  if ($mailEnabled -ne 'true') { $missing += 'MAIL_ENABLED=true' }
  if (-not $smtpHost) { $missing += 'SMTP_HOST' }
  if (-not $smtpUser) { $missing += 'SMTP_USERNAME' }
  if (-not $smtpPass) { $missing += 'SMTP_PASSWORD' }
  if (-not $mailFrom) { $missing += 'MAIL_FROM' }
  if (-not $adminEmail) { $missing += 'ADMIN_EMAIL' }
  if (-not $adminPass) { $missing += 'ADMIN_PASSWORD' }

  if ($missing.Count -gt 0) {
    Write-Host "Missing/invalid backend/.env settings:" -ForegroundColor Yellow
    $missing | ForEach-Object { Write-Host (" - {0}" -f $_) -ForegroundColor Yellow }
    Write-Host "\nEdit backend/.env (based on backend/.env.example), then re-run." -ForegroundColor Yellow
    throw "backend/.env not configured for email test"
  }
}

function Wait-ForBackend([string]$HealthUrl, [int]$TimeoutSeconds) {
  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    try {
      $resp = Invoke-RestMethod -Method Get -Uri $HealthUrl -TimeoutSec 3
      if ($resp -and $resp.status -eq 'UP') {
        return
      }
    } catch {
      Start-Sleep -Milliseconds 500
    }
  }
  throw "Backend did not become healthy within ${TimeoutSeconds}s at $HealthUrl"
}

$scriptDir = $PSScriptRoot
$backendDirResolved = $BackendDir
if (-not $backendDirResolved) {
  $backendDirResolved = (Resolve-Path (Join-Path $scriptDir '..')).Path
}

$dotEnvPath = Join-Path $backendDirResolved '.env'
Ensure-Config $dotEnvPath

# Start backend detached if it's not already healthy.
$healthUrl = "$BaseUrl/actuator/health"
$needsStart = $false
try {
  $probe = Invoke-RestMethod -Method Get -Uri $healthUrl -TimeoutSec 2
  if (-not $probe -or $probe.status -ne 'UP') { $needsStart = $true }
} catch {
  $needsStart = $true
}

if ($needsStart) {
  Write-Host "Starting backend (detached): $backendDirResolved" -ForegroundColor Cyan
  $gradlew = Join-Path $backendDirResolved 'gradlew.bat'
  if (!(Test-Path $gradlew)) {
    throw "Could not find gradlew.bat at: $gradlew"
  }

  Start-Process -FilePath $gradlew -ArgumentList 'bootRun' -WorkingDirectory $backendDirResolved -WindowStyle Hidden | Out-Null
  Wait-ForBackend $healthUrl $StartupTimeoutSeconds
  Write-Host "Backend is UP." -ForegroundColor Green
} else {
  Write-Host "Backend already UP." -ForegroundColor Green
}

$assignScript = Join-Path $scriptDir 'send-test-assignment-email.ps1'
Write-Host "Triggering assignment email to: $RecipientEmail" -ForegroundColor Cyan
& $assignScript -BaseUrl $BaseUrl -AdminEmail $AdminEmail -AdminPassword $AdminPassword -RecipientEmail $RecipientEmail | Out-Host

Write-Host "Done. Check inbox (and spam)." -ForegroundColor Green
