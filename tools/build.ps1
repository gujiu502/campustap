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
    Push-Location 'X:\'
    try {
        & .\gradlew.bat --no-daemon :Application:assembleDebug :Application:testDebugUnitTest :Application:lintDebug
        if ($LASTEXITCODE -ne 0) { throw 'Build or validation failed.' }
    } finally { Pop-Location }
} finally {
    & subst X: /D
    if (Test-Path 'X:\') { throw 'Temporary drive cleanup failed.' }
}
