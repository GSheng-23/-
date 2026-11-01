# 构建 debug APK 并安装到已连接手机。
# 用法： powershell -ExecutionPolicy Bypass -File .\tools\build.ps1
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = Join-Path $root 'tools\jdk17\current'
$env:ANDROID_HOME = Join-Path $root 'tools\android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = Join-Path $root 'tools\android-home'
$env:GRADLE_USER_HOME = Join-Path $root 'tools\gradle-home'
New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME | Out-Null
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$root\tools\gradle-8.11.1\bin;$env:PATH"

Write-Host "== assembleDebug =="
& (Join-Path $root 'tools\gradle-8.11.1\bin\gradle.bat') :app:assembleDebug --no-daemon --no-watch-fs --console=plain
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$apk = Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk'
Write-Host "== adb install =="
$installOutput = & (Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe') install --no-streaming -r $apk 2>&1
$installOutput | ForEach-Object { Write-Host $_ }
if (($installOutput | Out-String) -notmatch 'Success') { exit 1 }
exit 0
