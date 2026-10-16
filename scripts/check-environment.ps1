$ErrorActionPreference='Stop'
$sdk=if($env:ANDROID_SDK_ROOT){$env:ANDROID_SDK_ROOT}elseif($env:ANDROID_HOME){$env:ANDROID_HOME}else{Join-Path $env:LOCALAPPDATA 'Android\Sdk'}
if($env:JAVA_HOME){$env:PATH=(Join-Path $env:JAVA_HOME 'bin')+';'+$env:PATH}
foreach($name in @('java','javac','jar','arduino-cli')){Write-Host ($name+': '+[bool](Get-Command $name -ErrorAction SilentlyContinue))}
foreach($path in @('platforms\android-35\android.jar','build-tools\35.0.0\aapt2.exe','platform-tools\adb.exe')){Write-Host ($path+': '+(Test-Path -LiteralPath (Join-Path $sdk $path)))}
Write-Host 'Android source build requires the separately backed-up signing key. See docs/MIGRATION.md.'
