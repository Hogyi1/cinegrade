# [AI-GENERATED] Builds the Windows installer: target\installer\CineGrade-<version>.exe
# Run from the repository root:  powershell -ExecutionPolicy Bypass -File packaging\package.ps1 -Version 1.0.0
# Needs: JDK 25 (jpackage), Maven, and WiX (dotnet tool install --global wix, see packaging\README.md).
param(
    # Windows wants numbers only (no -SNAPSHOT); raise it for every release so the installer upgrades
    [string]$Version = "1.0.0"
)
$ErrorActionPreference = "Stop"
Set-Location (Split-Path $PSScriptRoot -Parent)

# Maven: from PATH, otherwise the one the Maven wrapper / IntelliJ downloaded
$mvn = (Get-Command mvn -ErrorAction SilentlyContinue).Source
if (-not $mvn) {
    $mvn = Get-ChildItem "$env:USERPROFILE\.m2\wrapper\dists" -Recurse -Filter mvn.cmd -ErrorAction SilentlyContinue |
            Select-Object -First 1 -ExpandProperty FullName
}
if (-not $mvn) { throw "Maven not found. Put mvn on PATH or build once with the IntelliJ Maven tool window." }

$inputDir = "target\jpackage-input"
$dest = "target\installer"

Write-Host "1/3 Building the jar and collecting the libraries"
& $mvn -q clean package -DskipTests
if ($LASTEXITCODE -ne 0) { throw "Maven build failed" }
& $mvn -q dependency:copy-dependencies "-DincludeScope=runtime" "-DoutputDirectory=$inputDir\lib"
if ($LASTEXITCODE -ne 0) { throw "Copying the dependencies failed" }

$jar = Get-ChildItem target -Filter "cinegrade-*.jar" | Where-Object { $_.Name -notmatch "sources|javadoc" } | Select-Object -First 1
Copy-Item $jar.FullName "$inputDir\lib\"

Write-Host "2/3 Adding the native tools (LibRaw, FFmpeg, ExifTool)"
# They end up in <install>\app\bin; NativeBinaryLocator finds them through -Dapp.dir below
Copy-Item -Recurse -Force "bin" "$inputDir\bin"

Write-Host "3/3 Running jpackage"
if (Test-Path $dest) { Remove-Item -Recurse -Force $dest }
jpackage `
    --type exe `
    --name CineGrade `
    --app-version $Version `
    --vendor "CineGrade" `
    --description "CineGrade colour grading" `
    --icon packaging\cinegrade.ico `
    --input $inputDir `
    --main-jar "lib\$($jar.Name)" `
    --main-class hu.elte.ik.thesis.cinegrade.app.main.Launcher `
    --java-options "--enable-native-access=ALL-UNNAMED" `
    --java-options "--sun-misc-unsafe-memory-access=allow" `
    --java-options '-Dapp.dir=$APPDIR' `
    --file-associations packaging\cgproj.properties `
    --win-per-user-install `
    --win-dir-chooser `
    --win-menu `
    --win-menu-group CineGrade `
    --win-shortcut `
    --win-shortcut-prompt `
    --win-upgrade-uuid 91039d0c-9eef-4915-84dd-f754a6154185 `
    --dest $dest
if ($LASTEXITCODE -ne 0) { throw "jpackage failed" }

Get-ChildItem $dest | ForEach-Object { Write-Host "Done: $($_.FullName) ($([math]::Round($_.Length / 1MB)) MB)" }
