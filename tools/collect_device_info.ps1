param([string]$Serial)
$ErrorActionPreference = 'Stop'
if (-not $Serial) {
    $devices = @(& adb devices | Where-Object { $_ -match '^\S+\s+device$' })
    if ($devices.Count -ne 1) { throw 'Need exactly one authorized device, or specify -Serial.' }
    $Serial = ($devices[0] -split '\s+')[0]
}
function Read-Adb([string[]]$DeviceArgs) {
    $result = & adb -s $Serial @DeviceArgs 2>&1
    if ($LASTEXITCODE -ne 0) { throw "ADB command failed: $($DeviceArgs[0])" }
    return ($result -join "`n").Trim()
}
$model = Read-Adb @('shell','getprop','ro.product.model')
$features = Read-Adb @('shell','pm','list','features')
$packages = Read-Adb @('shell','pm','list','packages')
$nfc = Read-Adb @('shell','dumpsys','nfc')
$wallet = Read-Adb @('shell','cmd','role','get-role-holders','android.app.role.WALLET')
$report = [ordered]@{
    collectedAt = [DateTimeOffset]::UtcNow.ToString('o')
    device = [ordered]@{
        model = $model
        device = Read-Adb @('shell','getprop','ro.product.device')
        rom = Read-Adb @('shell','getprop','ro.build.version.incremental')
        android = Read-Adb @('shell','getprop','ro.build.version.release')
        sdk = [int](Read-Adb @('shell','getprop','ro.build.version.sdk'))
        bootloaderLocked = (Read-Adb @('shell','getprop','ro.boot.flash.locked')) -eq '1'
    }
    usbAdb = @{ authorized = $true; serialRedacted = $true }
    nfc = [ordered]@{
        hardwarePresent = $features -match '(?m)^feature:android.hardware.nfc\s*$'
        enabled = $nfc -match '(?m)^mState=on\s*$'
        hceSupported = $features -match '(?m)^feature:android.hardware.nfc.hce\s*$'
        eseFeatureDeclared = $features -match '(?m)^feature:android.hardware.nfc.ese\s*$'
        secureNfcEnabled = $nfc -match '(?m)^mIsSecureNfcEnabled=true\s*$'
        screenState = if ($nfc -match '(?m)^mScreenState=(\w+)') { $Matches[1] } else { 'UNKNOWN' }
        walletRoleHolders = @($wallet -split "`n" | Where-Object { $_ })
        oemDoorCardComponentInstalled = $packages -match '(?m)^package:com.miui.tsmclient\s*$'
        miWalletInstalled = $packages -match '(?m)^package:com.mipay.wallet\s*$'
        easyWalletInstalled = $packages -match '(?m)^package:com.easycard.wallet\s*$'
    }
    studentAccess = @{ realmeReportedWorking = $true; cardTechnology = 'AWAITING_READ_ONLY_PROBE'; issuerMobileCredential = 'UNKNOWN'; actualDoorUnlock = 'NOT_TESTED' }
}
$directory = Join-Path $PSScriptRoot '..\artifacts\device'
New-Item -ItemType Directory -Force -Path $directory | Out-Null
$json = $report | ConvertTo-Json -Depth 6
$json | Set-Content -LiteralPath (Join-Path $directory 'capability_report.json') -Encoding utf8
$json
