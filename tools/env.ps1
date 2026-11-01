# 在当前 PowerShell 会话中加载本项目自带的 Android 工具链。
# 用法： . .\tools\env.ps1
$root = Split-Path -Parent $PSScriptRoot
$env:JAVA_HOME = Join-Path $root 'tools\jdk17\current'
$env:ANDROID_HOME = Join-Path $root 'tools\android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = Join-Path $root 'tools\android-home'
$env:GRADLE_USER_HOME = Join-Path $root 'tools\gradle-home'
New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME | Out-Null
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$root\tools\gradle-8.11.1\bin;$env:PATH"
Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "ANDROID_HOME=$env:ANDROID_HOME"
Write-Host "ANDROID_USER_HOME=$env:ANDROID_USER_HOME"
Write-Host "GRADLE_USER_HOME=$env:GRADLE_USER_HOME"
