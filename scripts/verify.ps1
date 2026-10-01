param([switch]$Full)
$ErrorActionPreference = 'Stop'
$probeRoot = Split-Path -Parent $PSScriptRoot
Push-Location $probeRoot
try {
    if ($Full) { & .\gradlew.bat build test lint --console=plain }
    else { & .\gradlew.bat compileDebugKotlin testDebugUnitTest lintDebug --console=plain }
    if ($LASTEXITCODE -ne 0) { throw "Gradle checks failed (exit $LASTEXITCODE)" }
} finally { Pop-Location }
