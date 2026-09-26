$ErrorActionPreference = 'Stop'

$adb = 'E:\game\tools\android-sdk\platform-tools\adb.exe'
$apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\debug\control.apk'

if (-not (Test-Path -LiteralPath $adb)) {
    throw "adb를 찾을 수 없습니다: $adb"
}
Write-Host 'APK를 빌드합니다...'
& (Join-Path $PSScriptRoot 'gradlew.bat') assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'APK 빌드에 실패했습니다.' }
if (-not (Test-Path -LiteralPath $apk)) {
    throw "APK가 없습니다. 먼저 .\gradlew.bat assembleDebug를 실행하세요."
}

$devices = & $adb devices | Select-String "\tdevice$"
if ($devices.Count -ne 1) {
    throw "USB 디버깅을 허용한 Android 기기 한 대를 연결하세요. 현재 연결: $($devices.Count)대"
}

& $adb install -r $apk
if ($LASTEXITCODE -ne 0) {
    throw "설치에 실패했습니다. 휴대전화 화면의 USB 디버깅 허용 여부를 확인하세요."
}

Write-Host '설치 완료: 휴대전화에서 월간 차단을 실행하세요.'
