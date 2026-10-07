# Prepara o ambiente para desenvolver o Wireless Automate no Windows.
#
#   .\scripts\setup.ps1              instala o que faltar e compila (build + testes de unidade)
#   .\scripts\setup.ps1 -GameTest    também roda os GameTests num servidor headless
#   .\scripts\setup.ps1 -NoBuild     só instala as ferramentas
#
# Usa o winget para instalar o JDK 21 (Temurin) e o Git, se faltarem.
param(
    [switch]$NoBuild,
    [switch]$GameTest
)
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot

function Log($msg) { Write-Host "==> $msg" -ForegroundColor Cyan }

function Get-JavaMajor($javac) {
    if (-not (Test-Path $javac)) { return 0 }
    $out = & $javac -version 2>&1 | Where-Object { $_ -match '^javac ' } | Select-Object -First 1
    if ($out -match 'javac (\d+)') { return [int]$Matches[1] }
    return 0
}

function Find-Jdk {
    if ($env:JAVA_HOME -and (Get-JavaMajor "$env:JAVA_HOME\bin\javac.exe") -ge 21) { return $env:JAVA_HOME }
    $candidates = Get-ChildItem -Directory -ErrorAction SilentlyContinue `
        'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Java', 'C:\Program Files\Microsoft'
    foreach ($dir in $candidates) {
        if ((Get-JavaMajor "$($dir.FullName)\bin\javac.exe") -ge 21) { return $dir.FullName }
    }
    return $null
}

if (-not (Get-Command winget -ErrorAction SilentlyContinue)) {
    Write-Warning 'winget não encontrado. Instale o JDK 21 e o Git manualmente se faltarem.'
}

if (-not (Get-Command git -ErrorAction SilentlyContinue)) {
    Log 'Instalando o Git'
    winget install --id Git.Git -e --accept-source-agreements --accept-package-agreements
}

$jdk = Find-Jdk
if (-not $jdk) {
    Log 'Instalando o JDK 21 (Temurin)'
    winget install --id EclipseAdoptium.Temurin.21.JDK -e --accept-source-agreements --accept-package-agreements
    $jdk = Find-Jdk
    if (-not $jdk) { throw 'JDK 21 não encontrado após a instalação. Abra um novo terminal e rode de novo.' }
}
$env:JAVA_HOME = $jdk
$env:Path = "$jdk\bin;$env:Path"
Log "Usando JDK em $jdk"

Push-Location $Root
try {
    Log 'Baixando o Gradle do wrapper'
    .\gradlew.bat --version
    if ($LASTEXITCODE -ne 0) { throw 'gradlew falhou' }

    if (-not $NoBuild) {
        Log 'Compilando e rodando os testes de unidade (o primeiro build decompila o Minecraft)'
        .\gradlew.bat build
        if ($LASTEXITCODE -ne 0) { throw 'build falhou' }
    }
    if ($GameTest) {
        Log 'Rodando os GameTests no servidor headless'
        .\gradlew.bat runGameTestServer
        if ($LASTEXITCODE -ne 0) { throw 'GameTests falharam' }
    }
} finally {
    Pop-Location
}

Write-Host ''
Write-Host 'Pronto. Próximos passos:'
Write-Host '  .\gradlew.bat build               compila e roda os testes de unidade (jar em build\libs\)'
Write-Host '  .\gradlew.bat runGameTestServer   roda os GameTests dentro do Minecraft, sem tela'
Write-Host '  .\gradlew.bat runClient           abre o Minecraft com o mod'
