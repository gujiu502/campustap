param([Parameter(Mandatory=$true)][string]$Serial)
$ErrorActionPreference = 'Stop'
$model = (& adb -s $Serial shell getprop ro.product.model).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($model)) { throw 'Selected device is not available or authorized.' }
$apk = Join-Path $PSScriptRoot '..\Application\build\outputs\apk\debug\Application-debug.apk'
if (-not (Test-Path $apk)) { throw 'Run build.ps1 first.' }
& adb -s $Serial install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'APK install failed; check the phone for an authorization prompt.' }
& adb -s $Serial shell am start -W -n 'tw.campustap.diagnostics/com.example.android.cardemulation.CardsActivity'
if ($LASTEXITCODE -ne 0) { throw 'Activity launch failed.' }
