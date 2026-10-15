param([switch]$Distribution)
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (Test-Path 'X:\') { throw 'Drive X: is in use. Build from an ASCII path or free that mapping first.' }
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
if (-not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { throw 'JDK 17 not found.' }
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
if (-not (Test-Path (Join-Path $sdk 'platforms\android-35'))) { throw 'Android SDK 35 not found.' }
('sdk.dir=' + $sdk.Replace('\','/').Replace(':','\:')) | Set-Content -LiteralPath (Join-Path $project 'local.properties') -Encoding ascii
& subst X: $project
if ($LASTEXITCODE -ne 0) { throw 'Could not create the temporary ASCII build path.' }
try {
    if ($Distribution) {
        $signingFile = Join-Path $env:USERPROFILE '.android\debug.keystore'
        if (-not (Test-Path -LiteralPath $signingFile)) { throw 'Existing test signing key is required to preserve installed cards; build Debug first.' }
        $env:CAMPUSTAP_KEYSTORE = $signingFile
        $env:CAMPUSTAP_STORE_PASSWORD = 'android'
        $env:CAMPUSTAP_KEY_ALIAS = 'androiddebugkey'
        $env:CAMPUSTAP_KEY_PASSWORD = 'android'
    }
    Push-Location 'X:\'
    try {
        $buildTasks = @(':Application:assembleDebug', ':Application:testDebugUnitTest', ':Application:lintDebug')
        if ($Distribution) { $buildTasks += @(':Application:assembleRelease', ':Application:testReleaseUnitTest', ':Application:lintRelease') }
        & .\gradlew.bat --no-daemon @buildTasks
        if ($LASTEXITCODE -ne 0) { throw 'Build or validation failed.' }
    } finally { Pop-Location }
} finally {
    if ($Distribution) {
        Remove-Item Env:CAMPUSTAP_KEYSTORE, Env:CAMPUSTAP_STORE_PASSWORD, Env:CAMPUSTAP_KEY_ALIAS, Env:CAMPUSTAP_KEY_PASSWORD -ErrorAction SilentlyContinue
    }
    & subst X: /D
    if (Test-Path 'X:\') { throw 'Temporary drive cleanup failed.' }
}
