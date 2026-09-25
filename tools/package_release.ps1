<#
.SYNOPSIS
    Script officiel de packaging et de release pour PHANTOM by The Great Corporation.

.DESCRIPTION
    Construit l'archive cliente propre distribuable pour Windows (PHANTOM-Server-Windows-vX.X.X.zip)
    et calcule les sommes de contrôle SHA-256 de certification.

.PARAMETER Version
    Numéro de version de la release (par défaut : 1.0.0).

.EXAMPLE
    .\tools\package_release.ps1 -Version "1.0.0"
#>

[CmdletBinding()]
param (
    [string]$Version = "1.0.0"
)

$ErrorActionPreference = "Stop"

# Configuration des répertoires
$RootDir = Split-Path -Parent $PSScriptRoot
$DistDir = Join-Path $RootDir "dist"
$PackageName = "PHANTOM-Server-Windows-v$Version"
$StagingDir = Join-Path $DistDir "staging\$PackageName"
$ZipFile = Join-Path $DistDir "$PackageName.zip"
$ChecksumFile = Join-Path $DistDir "SHA256SUMS.txt"

Write-Host ""
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host "   THE GREAT CORPORATION™ — PHANTOM RELEASE PACKAGING TOOL      " -ForegroundColor Yellow
Write-Host "   Version cible : $Version                                      " -ForegroundColor White
Write-Host "=================================================================" -ForegroundColor Cyan
Write-Host ""

# Nettoyage préalable
if (Test-Path $StagingDir) {
    Remove-Item -Recurse -Force $StagingDir
}
if (Test-Path $ZipFile) {
    Remove-Item -Force $ZipFile
}
if (!(Test-Path $DistDir)) {
    New-Item -ItemType Directory -Path $DistDir | Out-Null
}

New-Item -ItemType Directory -Path $StagingDir -Force | Out-Null

Write-Host "[1/4] Préparation de l'arborescence épurée..." -ForegroundColor Cyan

# Copie des fichiers racine essentiels
Copy-Item (Join-Path $RootDir "Lancer_Serveur_Phantom_TGC.bat") $StagingDir
Copy-Item (Join-Path $RootDir "install.ps1") $StagingDir
Copy-Item (Join-Path $RootDir "README.md") $StagingDir
Copy-Item (Join-Path $RootDir "LICENSE") $StagingDir
Copy-Item -Recurse (Join-Path $RootDir "docs") (Join-Path $StagingDir "docs")

# Copie propre du module serveur (sans cache ni logs)
$TargetServerDir = Join-Path $StagingDir "server"
New-Item -ItemType Directory -Path $TargetServerDir -Force | Out-Null

Get-ChildItem -Path (Join-Path $RootDir "server") | ForEach-Object {
    if ($_.Name -notmatch "__pycache__|\.pytest_cache|\.pyc$") {
        Copy-Item -Recurse -Path $_.FullName -Destination (Join-Path $TargetServerDir $_.Name)
    }
}

# Suppression des fichiers __pycache__ résiduels récursifs
Get-ChildItem -Path $StagingDir -Recurse -Directory -Filter "__pycache__" | Remove-Item -Recurse -Force -ErrorAction SilentlyContinue
Get-ChildItem -Path $StagingDir -Recurse -Filter "*.pyc" | Remove-Item -Force -ErrorAction SilentlyContinue

Write-Host "[2/4] Compression de l'archive de distribution..." -ForegroundColor Cyan
Compress-Archive -Path "$StagingDir\*" -DestinationPath $ZipFile -CompressionLevel Optimal

# Nettoyage du dossier staging intermédiaire
Remove-Item -Recurse -Force (Join-Path $DistDir "staging")

Write-Host "[3/4] Calcul de l'empreinte cryptographique SHA-256..." -ForegroundColor Cyan
$Hash = (Get-FileHash -Path $ZipFile -Algorithm SHA256).Hash
$ChecksumLine = "$Hash  $PackageName.zip"
Set-Content -Path $ChecksumFile -Value $ChecksumLine

$ZipSizeMb = [math]::Round(((Get-Item $ZipFile).Length / 1MB), 2)

Write-Host "[4/4] Livrable final généré avec succès !" -ForegroundColor Green
Write-Host ""
Write-Host "-----------------------------------------------------------------" -ForegroundColor Gray
Write-Host "  Fichier généré : $ZipFile ($ZipSizeMb Mo)" -ForegroundColor White
Write-Host "  SHA-256        : $Hash" -ForegroundColor Yellow
Write-Host "  Checksums      : $ChecksumFile" -ForegroundColor White
Write-Host "-----------------------------------------------------------------" -ForegroundColor Gray
Write-Host ""
Write-Host "Prestige & Perfection — Signé The Great Corporation." -ForegroundColor Cyan
