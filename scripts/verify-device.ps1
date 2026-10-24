param([Parameter(Mandatory = $true)][string]$Serial)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
Push-Location $projectRoot
try {
    & adb -s $Serial install -r 'app/build/outputs/apk/debug/app-debug.apk'
    if ($LASTEXITCODE -ne 0) { throw 'Debug app installation failed; do not uninstall an existing signed app automatically.' }
    & adb -s $Serial install -r -t 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
    if ($LASTEXITCODE -ne 0) { throw 'Test APK installation failed.' }
    $result = & adb -s $Serial shell am instrument -w -r 'dev.sajancodes.hardblock.test/androidx.test.runner.AndroidJUnitRunner' 2>&1
    $result | Tee-Object -FilePath 'build/instrumentation-direct.txt'
    if ($LASTEXITCODE -ne 0 -or ($result -join "`n") -notmatch 'OK \(([1-9][0-9]*) tests?\)') {
        throw 'Instrumentation did not report a positive number of passing tests. See build/instrumentation-direct.txt.'
    }
} finally { Pop-Location }
