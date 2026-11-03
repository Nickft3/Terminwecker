# Baut die signierte Release-APK und legt sie als Terminwecker.apk neben dieses Skript.
# Voraussetzung: JDK 17 und Android SDK wie unter %USERPROFILE%\android-toolchain eingerichtet.
$ErrorActionPreference = 'Stop'
$tc = "$env:USERPROFILE\android-toolchain"
$env:JAVA_HOME = (Get-ChildItem "$tc\jdk" -Directory)[0].FullName
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$buildRoot = "$tc\build\tw"

Push-Location $PSScriptRoot
try {
    & .\gradlew.bat ":app:assembleRelease" "-Pterminwecker.buildRoot=$($buildRoot -replace '\\','/')"
    if ($LASTEXITCODE -ne 0) { throw "Build fehlgeschlagen" }
    Copy-Item "$buildRoot\app\outputs\apk\release\app-release.apk" "$PSScriptRoot\Terminwecker.apk" -Force
    Write-Host "Fertig: $PSScriptRoot\Terminwecker.apk"
} finally {
    Pop-Location
}
