param()
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
Set-Location -LiteralPath $PSScriptRoot
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'Instale um JDK 17 ou superior e adicione java ao PATH.' }
New-Item -ItemType Directory -Force -Path '.tools/tessdata' | Out-Null

$mavenVersion = '3.9.16'
$mavenDir = Join-Path $PSScriptRoot ".tools/apache-maven-$mavenVersion"
if (-not (Test-Path -LiteralPath (Join-Path $mavenDir 'bin/mvn.cmd'))) {
    Write-Host 'Baixando o Maven local...'
    $archive = Join-Path $PSScriptRoot '.tools/apache-maven.zip'
    $baseUrl = "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/$mavenVersion/apache-maven-$mavenVersion-bin.zip"
    Invoke-WebRequest -Uri $baseUrl -OutFile $archive -UseBasicParsing
    $checksum = (Invoke-WebRequest -Uri "$baseUrl.sha512" -UseBasicParsing).Content.Trim().Split(' ')[0]
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA512).Hash -ne $checksum) { throw 'Falha na verificacao SHA512 do Maven.' }
    Expand-Archive -LiteralPath $archive -DestinationPath (Join-Path $PSScriptRoot '.tools') -Force
}

$language = Join-Path $PSScriptRoot '.tools/tessdata/por.traineddata'
$languageSha = 'C4932B937207A9514B7514D518B931A99938C02A28A5A5A553F8599ED58B7DEB'
if (-not (Test-Path -LiteralPath $language)) {
    Write-Host 'Baixando o idioma portugues do Tesseract...'
    Invoke-WebRequest -Uri 'https://raw.githubusercontent.com/tesseract-ocr/tessdata_fast/4.1.0/por.traineddata' -OutFile $language -UseBasicParsing
}
if ((Get-FileHash -LiteralPath $language -Algorithm SHA256).Hash -ne $languageSha) { throw 'Modelo OCR invalido. Remova .tools/tessdata/por.traineddata e execute novamente.' }
Write-Host 'Preparacao concluida. Maven e OCR ficam apenas na pasta .tools do projeto.'
