param(
  [string]$BaseUrl = "http://localhost:8081",
  [string]$AdminEmail = "admin@example.com",
  [string]$AdminPassword = "AdminPassword123!",
  [Parameter(Mandatory = $true)][string]$RecipientEmail,
  [string]$RecipientPassword = "UserPassword123!",
  [string]$Title = "Email test (assignment)",
  [string]$Description = "If you received this, SMTP is working.",
  [ValidateSet('LOW','MEDIUM','HIGH')][string]$Priority = "MEDIUM"
)

$ErrorActionPreference = 'Stop'

function Invoke-JsonPost($Url, $Body, $Headers = @{}) {
  return Invoke-RestMethod -Method Post -Uri $Url -Headers $Headers -ContentType 'application/json' -Body ($Body | ConvertTo-Json -Depth 10)
}

Write-Host "[1/3] Ensure recipient exists: $RecipientEmail" -ForegroundColor Cyan
try {
  Invoke-JsonPost "$BaseUrl/api/auth/register" @{ email = $RecipientEmail; password = $RecipientPassword } | Out-Null
  Write-Host "Recipient registered." -ForegroundColor Green
} catch {
  # Register returns 409 when already exists; accept that.
  $msg = $_.Exception.Message
  Write-Host "Recipient register skipped (maybe already exists)." -ForegroundColor Yellow
}

Write-Host "[2/3] Login as admin: $AdminEmail" -ForegroundColor Cyan
$auth = Invoke-JsonPost "$BaseUrl/api/auth/login" @{ email = $AdminEmail; password = $AdminPassword }
if (-not $auth -or -not $auth.token) {
  throw "Admin login failed: token missing"
}
$token = $auth.token

Write-Host "[3/3] Assign task to recipient (should send email if MAIL_ENABLED=true)" -ForegroundColor Cyan
$headers = @{ Authorization = "Bearer $token" }
Invoke-JsonPost "$BaseUrl/api/admin/tasks/assign/user" @{ assigneeEmail = $RecipientEmail; title = $Title; description = $Description; priority = $Priority; dueDate = $null } $headers | Out-Null

Write-Host "Assignment created. Check inbox (and spam)." -ForegroundColor Green
