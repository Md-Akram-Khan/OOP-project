# Builds the two runnable programs into release/:
#   release/client/  the desktop app (no database code, no database driver)
#   release/server/  the backend API (the only part that talks to the database)
# Usage, from the project folder:  powershell -ExecutionPolicy Bypass -File scripts\build-release.ps1
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$pkg = 'src/electricity/billing/system'
$gson = 'lib/gson-2.11.0.jar'
$flatlaf = 'lib/flatlaf-3.7.2.jar'
$inter = 'lib/flatlaf-fonts-inter-4.1.jar'
$pg = 'lib/postgresql-42.7.8.jar'
$work = 'build/release'

function Invoke-Checked([string]$exe, [string[]]$arguments) {
    & $exe @arguments
    if ($LASTEXITCODE -ne 0) { throw "$exe failed with exit code $LASTEXITCODE" }
}

function Get-Sources([string[]]$folders) {
    $files = @()
    foreach ($folder in $folders) { $files += Get-ChildItem "$pkg/$folder" -Filter *.java -File | ForEach-Object { $_.FullName } }
    return $files
}

function New-Jar([string]$name, [string[]]$sources, [string]$classpath, [string]$mainClass, [string[]]$libs, [string]$outDir, [bool]$withIcons) {
    $classes = "$work/$name"
    if (Test-Path $classes) { Remove-Item -Recurse -Force $classes }
    New-Item -ItemType Directory -Force $classes | Out-Null
    Invoke-Checked 'javac' (@('--release', '17', '-nowarn', '-encoding', 'UTF-8', '-d', $classes, '-cp', $classpath) + $sources)
    if ($withIcons) { Copy-Item -Recurse 'src/icon' "$classes/icon" }

    $libNames = ($libs | ForEach-Object { 'lib/' + (Split-Path $_ -Leaf) }) -join ' '
    $manifest = "$work/$name.mf"
    Set-Content -Encoding ascii $manifest "Main-Class: $mainClass`r`nClass-Path: $libNames`r`n"

    New-Item -ItemType Directory -Force "$outDir/lib" | Out-Null
    Invoke-Checked 'jar' @('cfm', "$outDir/$name.jar", $manifest, '-C', $classes, '.')
    foreach ($lib in $libs) { Copy-Item -Force $lib "$outDir/lib/" }
    Write-Host "Built $outDir/$name.jar"
}

$shared = @('common', 'model')

$ui = @('ui', 'ui/theme', 'ui/components', 'ui/pages/admin', 'ui/pages/customer')
New-Jar 'Electricity_Billing_System' `
    (Get-Sources (@('.', 'client') + $ui + $shared)) "$gson;$flatlaf;$inter" `
    'electricity.billing.system.Main' @($gson, $flatlaf, $inter) 'release/client' $false

New-Jar 'ebs-server' `
    (Get-Sources (@('server', 'service', 'db', 'security', 'billing') + $shared)) "$gson;$pg" `
    'electricity.billing.system.server.ApiServer' @($gson, $pg) 'release/server' $false

Write-Host 'Done.'
