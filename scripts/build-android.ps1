$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$veloRoot=Split-Path $PSScriptRoot -Parent
$veloAndroid=Join-Path $veloRoot 'android'
$veloSdk=if($env:ANDROID_SDK_ROOT){$env:ANDROID_SDK_ROOT}elseif($env:ANDROID_HOME){$env:ANDROID_HOME}else{Join-Path $env:LOCALAPPDATA 'Android\Sdk'}
if($env:JAVA_HOME){$env:PATH=(Join-Path $env:JAVA_HOME 'bin')+';'+$env:PATH}
foreach($tool in @('java','javac','jar')){if(-not(Get-Command $tool -ErrorAction SilentlyContinue)){throw "JDK tool missing: $tool. Set JAVA_HOME."}}
$veloPassword=$env:VELO_KEYSTORE_PASSWORD
$veloKeyPassword=if($env:VELO_KEY_PASSWORD){$env:VELO_KEY_PASSWORD}else{$veloPassword}
$env:VELO_KEY_PASSWORD=$veloKeyPassword
$veloAlias=if($env:VELO_KEY_ALIAS){$env:VELO_KEY_ALIAS}else{'velo'}
if(-not $veloPassword){throw 'Set VELO_KEYSTORE_PASSWORD. The signing key is kept separately, outside GitHub.'}
New-Item -ItemType Directory -Force (Join-Path $veloRoot 'dist') | Out-Null
$veloBuild=Join-Path $veloRoot 'work\phone-build-v100'
$veloKey=if($env:VELO_SIGNING_KEY){$env:VELO_SIGNING_KEY}else{Join-Path $veloRoot 'private-signing\velo-test.keystore'}
if(-not(Test-Path -LiteralPath $veloKey)){throw 'Signing key not found. Restore the separate private signing backup.'}
$veloTools=Join-Path $veloSdk 'build-tools\35.0.0'
$veloAndroidJar=Join-Path $veloSdk 'platforms\android-35\android.jar'
New-Item -ItemType Directory -Force -Path "$veloBuild\classes","$veloBuild\dex" | Out-Null
$sources=Get-ChildItem (Join-Path $veloAndroid 'src') -Recurse -Filter '*.java' | Select-Object -ExpandProperty FullName
& javac -encoding UTF-8 -source 8 -target 8 -classpath $veloAndroidJar -d "$veloBuild\classes" $sources
if($LASTEXITCODE -ne 0){throw 'Java compile failed'}
& jar --create --file "$veloBuild\classes.jar" -C "$veloBuild\classes" local
if($LASTEXITCODE -ne 0){throw 'Java archive failed'}
& "$veloTools\d8.bat" --lib $veloAndroidJar --min-api 26 --output "$veloBuild\dex" "$veloBuild\classes.jar"
if($LASTEXITCODE -ne 0){throw 'Dex compile failed'}
& "$veloTools\aapt2.exe" compile --dir (Join-Path $veloAndroid 'res') -o "$veloBuild\resources.zip"
if($LASTEXITCODE -ne 0){throw 'Resource compile failed'}
& "$veloTools\aapt2.exe" link -o "$veloBuild\base.apk" --manifest (Join-Path $veloAndroid 'AndroidManifest.xml') -I $veloAndroidJar -A (Join-Path $veloAndroid 'assets') --min-sdk-version 26 --target-sdk-version 34 "$veloBuild\resources.zip"
if($LASTEXITCODE -ne 0){throw 'APK link failed'}
& jar --update --file "$veloBuild\base.apk" -C "$veloBuild\dex" classes.dex
if($LASTEXITCODE -ne 0){throw 'Dex package failed'}
# Some Windows aapt2 builds put backslashes into nested asset ZIP names.
# AssetManager requires forward slashes on Android.
$veloZip=[IO.Compression.ZipFile]::Open("$veloBuild\base.apk",[IO.Compression.ZipArchiveMode]::Update)
try {
  $veloEntries=@($veloZip.Entries | Where-Object {$_.FullName.Contains('\')})
  foreach($veloEntry in $veloEntries){
    $veloNormalized=$veloEntry.FullName.Replace('\','/')
    if($null -eq $veloZip.GetEntry($veloNormalized)){
      $veloMemory=[IO.MemoryStream]::new()
      $veloInput=$veloEntry.Open()
      try {$veloInput.CopyTo($veloMemory)} finally {$veloInput.Dispose()}
      $veloNew=$veloZip.CreateEntry($veloNormalized,[IO.Compression.CompressionLevel]::Optimal)
      $veloOutput=$veloNew.Open()
      try {$veloMemory.Position=0;$veloMemory.CopyTo($veloOutput)} finally {$veloOutput.Dispose();$veloMemory.Dispose()}
    }
    $veloEntry.Delete()
  }
} finally {$veloZip.Dispose()}
& "$veloTools\zipalign.exe" -f -p 4 "$veloBuild\base.apk" "$veloBuild\aligned.apk"
if($LASTEXITCODE -ne 0){throw 'APK alignment failed'}
$veloApk=Join-Path $veloRoot 'dist\VELO-PhoneBridge-v1.0.4.apk'
& "$veloTools\apksigner.bat" sign --ks $veloKey --ks-key-alias $veloAlias --ks-pass env:VELO_KEYSTORE_PASSWORD --key-pass env:VELO_KEY_PASSWORD --out $veloApk "$veloBuild\aligned.apk"
if($LASTEXITCODE -ne 0){throw 'APK signing failed'}
& "$veloTools\apksigner.bat" verify --verbose $veloApk
if($LASTEXITCODE -ne 0){throw 'APK signature verification failed'}
Write-Host "Built: $veloApk"
