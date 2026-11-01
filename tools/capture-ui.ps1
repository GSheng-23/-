# 抓取当前手机界面的 uiautomator（压缩模式）与截图。
# 用法： powershell -ExecutionPolicy Bypass -File .\tools\capture-ui.ps1 -Name signin_before -WaitSeconds 8
param(
    [Parameter(Mandatory = $true)][string]$Name,
    [int]$WaitSeconds = 5
)

$ErrorActionPreference = 'Stop'
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
. (Join-Path $PSScriptRoot 'env.ps1')

$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path $root 'artifacts'
New-Item -ItemType Directory -Force -Path $out | Out-Null

Start-Sleep -Seconds $WaitSeconds
$remote = "/sdcard/$Name.xml"
adb shell uiautomator dump --compressed $remote
adb pull $remote (Join-Path $out "$Name.xml")
$png = Join-Path $out "$Name.png"
cmd /c "adb exec-out screencap -p > `"$png`""

Write-Host "captured: $Name.xml / $Name.png"
