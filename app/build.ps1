# MuseGear build script (no Gradle required)
# Usage:  $env:ANDROID_HOME = "C:\Android\sdk"; .\build.ps1
param(
  [string]$Sdk = $env:ANDROID_HOME,
  [string]$AppDir = $PSScriptRoot
)
$ErrorActionPreference = "Stop"
if (-not $Sdk) { throw "ANDROID_HOME not set" }

$bt   = Get-ChildItem "$Sdk\build-tools" -Directory | Sort-Object Name -Descending | Select-Object -First 1
$plat = Get-ChildItem "$Sdk\platforms" -Directory | Sort-Object Name -Descending | Select-Object -First 1
$jar  = Join-Path $plat.FullName "android.jar"
Write-Host "build-tools: $($bt.Name)   platform: $($plat.Name)"

$work = Join-Path $AppDir "build"
$cls  = Join-Path $work "classes"
$out  = Join-Path $work "out"
Remove-Item -Recurse -Force $work -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $cls, $out | Out-Null

$srcs = Get-ChildItem (Join-Path $AppDir "src") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
javac -source 8 -target 8 -nowarn -encoding UTF-8 -bootclasspath $jar -d $cls $srcs
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

$cj = Join-Path $work "classes.jar"
Push-Location $cls; jar cf $cj .; Pop-Location

& (Join-Path $bt.FullName "d8.bat") --release --lib $jar --min-api 26 --output $out $cj
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

$base = Join-Path $work "base.apk"
& (Join-Path $bt.FullName "aapt2.exe") link -o $base -I $jar `
    --manifest (Join-Path $AppDir "AndroidManifest.xml") `
    --min-sdk-version 26 --target-sdk-version 33
if ($LASTEXITCODE -ne 0) { throw "aapt2 failed" }

Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::Open($base, "Update")
$entry = $zip.CreateEntry("classes.dex", [System.IO.Compression.CompressionLevel]::Optimal)
$fs = $entry.Open()
$bytes = [System.IO.File]::ReadAllBytes((Join-Path $out "classes.dex"))
$fs.Write($bytes, 0, $bytes.Length); $fs.Close(); $zip.Dispose()

$aligned = Join-Path $work "aligned.apk"
& (Join-Path $bt.FullName "zipalign.exe") -f -p 4 $base $aligned

$ks = Join-Path $AppDir "debug.keystore"
if (-not (Test-Path $ks)) {
  keytool -genkeypair -keystore $ks -alias muse -keyalg RSA -keysize 2048 `
    -validity 10000 -storepass android -keypass android `
    -dname "CN=MuseGear, OU=lab, O=lab, L=SZ, C=CN" -noprompt
}

$final = Join-Path $AppDir "MuseGear.apk"
& (Join-Path $bt.FullName "apksigner.bat") sign --ks $ks --ks-pass pass:android `
    --key-pass pass:android --out $final $aligned
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

Write-Host "OK -> $final"