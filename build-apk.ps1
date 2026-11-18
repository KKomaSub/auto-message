$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
if (-not $env:JAVA_HOME) {
  $jbr = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
  if (Test-Path "$jbr\bin\java.exe") { $env:JAVA_HOME = $jbr }
}
if (-not $env:ANDROID_HOME) {
  if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_HOME = $env:ANDROID_SDK_ROOT }
  else { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
}
if (!(Test-Path (Join-Path $env:ANDROID_HOME 'platforms\android-35\android.jar'))) { throw 'Install Android SDK Platform 35 using Android Studio SDK Manager.' }
$dir = Join-Path $env:LOCALAPPDATA 'AI-SMS-build'
New-Item -ItemType Directory -Path $dir -Force | Out-Null
$gradle = Join-Path $dir 'gradle-8.9\bin\gradle.bat'
if (!(Test-Path $gradle)) {
  $zip = Join-Path $dir 'gradle-8.9-bin.zip'
  Invoke-WebRequest 'https://services.gradle.org/distributions/gradle-8.9-bin.zip' -OutFile $zip
  $expected = (Invoke-RestMethod 'https://services.gradle.org/distributions/gradle-8.9-bin.zip.sha256').Trim()
  if ((Get-FileHash -Algorithm SHA256 $zip).Hash -ne $expected) { throw 'Gradle checksum mismatch' }
  Expand-Archive -Path $zip -DestinationPath $dir -Force
}
& $gradle --no-daemon :app:assembleDebug
if ($LASTEXITCODE -ne 0) { throw 'APK build failed' }
Copy-Item 'app\build\outputs\apk\debug\app-debug.apk' 'AI-SMS.apk' -Force
Write-Host 'AI-SMS.apk created.'
