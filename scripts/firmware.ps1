param([ValidateSet('build','upload')][string]$Action='build',[string]$Port='COM8',[string]$Cli='arduino-cli')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$sketch=Join-Path $root 'firmware\BikeDash'
if(-not(Get-Command $Cli -ErrorAction SilentlyContinue)){throw 'Install arduino-cli and put it on PATH, or pass -Cli with its full path.'}
if($Action -eq 'build'){
  $out=Join-Path $root 'work\firmware-build'
  & $Cli compile --fqbn esp32:esp32:esp32:UploadSpeed=115200 --libraries (Join-Path $root 'dependencies\arduino-libraries') --output-dir $out $sketch
}else{
  if($Port -notin [IO.Ports.SerialPort]::GetPortNames()){throw "Port not found: $Port. Select the new computer's actual port."}
  & $Cli upload -p $Port --fqbn esp32:esp32:esp32:UploadSpeed=115200 --input-dir (Join-Path $root 'firmware\prebuilt-v0.10.1') $sketch
}
if($LASTEXITCODE -ne 0){throw "Firmware $Action failed."}
