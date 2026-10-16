param([ValidateSet('native','ride','preview','route','clear')][string]$Suite='clear',[string]$Device='emulator-5554')
$ErrorActionPreference='Stop'
if($Device -notlike 'emulator-*'){throw 'These tests modify app data. Use a disposable emulator only.'}
$root=Split-Path $PSScriptRoot -Parent
$sdk=if($env:ANDROID_SDK_ROOT){$env:ANDROID_SDK_ROOT}elseif($env:ANDROID_HOME){$env:ANDROID_HOME}else{Join-Path $env:LOCALAPPDATA 'Android\Sdk'}
if($env:JAVA_HOME){$env:PATH=(Join-Path $env:JAVA_HOME 'bin')+';'+$env:PATH}
$key=if($env:VELO_SIGNING_KEY){$env:VELO_SIGNING_KEY}else{Join-Path $root 'private-signing\velo-test.keystore'}
if(-not $env:VELO_KEYSTORE_PASSWORD){throw 'Set VELO_KEYSTORE_PASSWORD.'}
if(-not $env:VELO_KEY_PASSWORD){$env:VELO_KEY_PASSWORD=$env:VELO_KEYSTORE_PASSWORD}
$alias=if($env:VELO_KEY_ALIAS){$env:VELO_KEY_ALIAS}else{'velo'}
$names=@{native=@('NativeAppTest','native-test-manifest.xml');ride=@('RideUiTest','ride-test-manifest.xml');preview=@('BoardPreviewTest','board-preview-manifest.xml');route=@('RouteChoiceTest','route-choice-manifest.xml');clear=@('ClearUiTest','clear-ui-manifest.xml')}
$class,$manifest=$names[$Suite]
$bt=Join-Path $sdk 'build-tools\35.0.0';$android=Join-Path $sdk 'platforms\android-35\android.jar';$adb=Join-Path $sdk 'platform-tools\adb.exe'
$build=Join-Path $root "work\test-$Suite"
New-Item -ItemType Directory -Force "$build\classes","$build\dex" | Out-Null
& javac -encoding UTF-8 -source 8 -target 8 -cp "$android;$root\work\phone-build-v100\classes" -d "$build\classes" "$root\tests\$class.java"
if($LASTEXITCODE -ne 0){throw 'Test compile failed. Build the Android app first.'}
& jar --create --file "$build\test.jar" -C "$build\classes" local
& "$bt\d8.bat" --lib $android --min-api 26 --output "$build\dex" "$build\test.jar"
& "$bt\aapt2.exe" link -o "$build\base.apk" --manifest "$root\tests\$manifest" -I $android -A "$root\tests\assets"
& jar --update --file "$build\base.apk" -C "$build\dex" classes.dex
& "$bt\zipalign.exe" -f 4 "$build\base.apk" "$build\aligned.apk"
& "$bt\apksigner.bat" sign --ks $key --ks-key-alias $alias --ks-pass env:VELO_KEYSTORE_PASSWORD --key-pass env:VELO_KEY_PASSWORD --out "$build\test.apk" "$build\aligned.apk"
if($LASTEXITCODE -ne 0){throw 'Test APK build failed.'}
& $adb -s $Device install -r "$root\dist\VELO-PhoneBridge-v1.0.4.apk"
& $adb -s $Device install -r "$build\test.apk"
foreach($permission in @('ACCESS_FINE_LOCATION','ACCESS_COARSE_LOCATION','POST_NOTIFICATIONS')){& $adb -s $Device shell pm grant local.velo.bridge "android.permission.$permission"}
if($Suite -in @('native','clear','route','preview')){& $adb -s $Device shell pm revoke local.velo.bridge android.permission.BLUETOOTH_CONNECT}else{& $adb -s $Device shell pm grant local.velo.bridge android.permission.BLUETOOTH_CONNECT}
$result=& $adb -s $Device shell am instrument -w "local.velo.test/local.velo.bridge.$class"
$result | Write-Output
if(($result -join "`n") -notmatch 'INSTRUMENTATION_CODE: -1'){throw 'Instrumentation did not report success.'}
