param([switch]$Testar)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
& (Join-Path $PSScriptRoot 'preparar.ps1')
$maven = Join-Path $PSScriptRoot '.tools/apache-maven-3.9.16/bin/mvn.cmd'
if ($Testar) {
    & $maven '-B' 'verify'
    if ($LASTEXITCODE -ne 0) { throw 'A verificacao falhou. Confira a mensagem acima.' }
} else {
    & $maven '-B' '-q' '-DskipTests' 'package'
    if ($LASTEXITCODE -ne 0) { throw 'A compilacao falhou. Confira a mensagem acima.' }
}
Write-Host 'Abra http://localhost:8080 (ou a porta configurada em PORT). Ctrl+C encerra.'
& java '--enable-native-access=ALL-UNNAMED' '-Dorg.bytedeco.javacpp.cachedir=.tools/native' '-Xmx1g' '-jar' (Join-Path $PSScriptRoot 'target/validadoc-1.0.0.jar') '--debug=false'
if ($LASTEXITCODE -ne 0) { throw 'A aplicacao foi encerrada com erro. Verifique a porta e a configuracao.' }
