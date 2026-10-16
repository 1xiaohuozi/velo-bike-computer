param([string]$Compiler='g++')
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$out=Join-Path $root 'work\firmware-tests'
New-Item -ItemType Directory -Force $out | Out-Null
foreach($case in @('nav-math-check','nav-stage-check','nav-presentation-check','road-frame-check')){
  $exe=Join-Path $out "$case.exe"
  & $Compiler -std=c++17 (Join-Path $root "tests\$case.cpp") -o $exe
  if($LASTEXITCODE -ne 0){throw "Compile failed: $case"}
  & $exe
  if($LASTEXITCODE -ne 0){throw "Test failed: $case"}
}
