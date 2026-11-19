# AI 문자

Android 8 이상. 기본 문자 앱을 교체하지 않는, 지정 연락처 전용 Gemini SMS 자동 응답 앱입니다. APK가 아니라 Android Studio/Gradle 소스입니다. 실제 기기 및 API 테스트는 수행하지 못했습니다.

## APK 빌드

- Windows: Android Studio에서 Android SDK Platform 35 및 Build Tools 35를 설치한 뒤 `build-apk.bat` 실행. Gradle 8.9는 스크립트에서 다운로드하며, 완료되면 이 폴더에 `AI-SMS.apk`가 생성됩니다. JDK 17 이상 필요.
- Android Studio: 이 폴더 열기, Gradle 동기화, Build > Generate App Bundles or APKs > Build APK(s). 결과 `app/build/outputs/apk/debug/app-debug.apk`.
- GitHub: 새 저장소에 이 폴더의 내용(숨김 `.github` 포함)을 업로드 → Actions → Build APK → Run workflow → 완료된 실행에서 AI-SMS-APK 다운로드. APK는 디버그 서명된 개인 설치용입니다. GitHub 실행마다 서명이 달라질 수 있어 기존 앱 업데이트 시 기존 데이터 백업/제거가 필요할 수 있습니다.

## 사용

연락처 선택 → 이전 문자 개수 0~100 설정 (기본 10, 새 문자 제외) → Gemini API 키 입력 → ON → 권한 및 개인정보 동의. 키 수정 시 안전을 위해 OFF가 되므로 다시 ON을 켜세요. 문자 전송에는 일반 SMS가 사용되며, 자동 응답임을 알리는 문구가 붙습니다. 수신 및 발신 SMS를 포함하며 MMS/RCS는 처리하지 않습니다. 같은 연락처로만 답장합니다. 듀얼 SIM에서는 수신 SIM 정보가 제공되면 해당 SIM을 사용합니다.

API 키는 Android Keystore의 AES-GCM 키를 사용해 암호화합니다. 문자 내용과 이전 문자는 Gemini API로 전송됩니다. 앱은 기본 문자함을 변경하지 않으며 백업을 비활성화합니다. 상대방 동의를 받은 번호만 사용하세요. 오류가 발생하면 ON 아래 간략 상태를 표시합니다. 중복 및 자동응답 루프 방지를 위해 동일한 수신 이벤트를 24시간 중복 처리하지 않으며, 번호별 분당 최대 6건으로 제한합니다. 10분 이상 지연된 작업은 답장하지 않습니다. 백그라운드 실행은 네트워크와 Android JobScheduler에 의존하여 지연될 수 있고 강제 종료 또는 재부팅 후 대기 중이던 작업은 유실될 수 있습니다. 앱을 강제 종료했다면 다시 실행하세요.

Android SMS 권한은 설치 프로그램 및 기기 정책에 의해 제한될 수 있습니다. 권한이 승인되지 않으면 앱을 ON으로 전환하지 않습니다. 회사/학교 등 관리 기기에서 설치나 권한을 허용하지 않을 경우 동작하지 않습니다. Google Play 배포 정책에 별도의 제한이 있습니다.

## 릴리스 서명 APK (GitHub Actions)

기존 디버그 빌드는 유지하며, 별도의 **Build Signed Release APK** 워크플로에서 소유자 본인의 릴리스 키로 APK를 서명합니다. 서명은 개발자 신원 및 APK 무결성을 유지하기 위한 것으로 보안 심사 또는 Play Protect 설치 차단을 해제하는 수단이 아닙니다. 설치가 차단된 경우 검사 결과를 확인하고 공식 이의신청 절차를 이용하세요.

1. 이미 보유한 릴리스 키가 있다면 재사용합니다. 없으면 PC의 Android Studio (Build > Generate Signed Bundle / APK)에서 생성하거나 JDK의 다음 명령을 실행해 생성합니다. 비밀번호는 화면의 프롬프트에서 입력합니다.

   ```powershell
   keytool -genkeypair -v -keystore auto-message-release.jks -storetype JKS -alias auto-message -keyalg RSA -keysize 3072 -validity 10000
   ```

2. 저장소 Settings > Secrets and variables > Actions > New repository secret에 아래 4개를 직접 등록합니다. 키 파일은 로컬에서 Base64로 변환합니다. Base64는 암호화가 아니므로 파일 및 문자열을 공개하거나 커밋하지 마세요.

   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes((Resolve-Path .\auto-message-release.jks).Path)) | Set-Clipboard
   ```

   | Secret | 값 |
   | --- | --- |
   | RELEASE_KEYSTORE_BASE64 | 키스토어 Base64 문자열 |
   | RELEASE_STORE_PASSWORD | 키스토어 비밀번호 |
   | RELEASE_KEY_ALIAS | 생성 시 지정한 alias (예: auto-message) |
   | RELEASE_KEY_PASSWORD | 키 비밀번호 |

3. Actions > Build Signed Release APK > Run workflow (main) > 성공한 실행의 Artifacts > AI-SMS-Signed-Release를 내려받습니다. APK 파일 이름은 AI-SMS-release.apk입니다. 서명 및 정렬 검증 실패 시 APK를 게시하지 않습니다.

키스토어와 비밀번호를 오프라인에 백업하고 향후 업데이트에서도 같은 키를 사용합니다. 실제 배포 시 versionCode도 올려야 합니다. 디버그 키와 릴리스 키는 서로 달라 디버그 설치본을 그대로 덮어써 업데이트할 수 없습니다.


## 1.2 변경 사항

- 고정 [AI 자동응답] 접두사 제거. 사용 전에 상대방에게 AI 자동 응답임을 알리고 동의를 받으세요.
- 최소 응답 글자 수 0~700: 부족하면 한 번 재생성, 여전히 부족하면 전송하지 않습니다.
- Android 11 이상, RCS·MMS 알림 연동을 선택하고 시스템의 알림 접근 권한을 허용하면 삼성 메시지 또는 Google 메시지의 1:1 알림에 포함된 텍스트와 직접 답장 기능으로 SMS/MMS/RCS에 대응합니다. 메시지 종류는 기본 메시지 앱이 결정합니다. 이 모드에서는 기존 SMS 브로드캐스트 처리 대신 알림만 사용합니다. 메시지 앱이 알림을 게시하지 않거나 직접 답장 기능을 제공하지 않거나 발신자 전화번호를 확실히 식별할 수 없으면 자동 응답하지 않습니다. 이전 문자는 알림에서 확인 가능한 항목까지만 이용하며 사진·영상·단체방은 제외합니다. OS 또는 기기 정책이 접근을 막으면 기능을 사용할 수 없습니다. 실제 RCS/MMS 기기 테스트 필요.

## 1.4 응답 대상

기본값은 기존에 선택한 연락처만 응답입니다. 모든 사람을 켜면 미등록 전화번호도 포함됩니다. 기존 선택 목록은 유지합니다. 식별 가능한 일반 전화번호만 처리하며 인증번호·일부 광고·자동 알림을 휴리스틱으로 제외하고 이전 기록도 동일하게 필터링합니다. 완전한 스팸 차단은 보장하지 않습니다. 모든 사람 모드는 전체 10분당 30건 제한이 추가됩니다. 미선택 상대에게는 AI임을 자연스럽게 알리도록 프롬프트를 적용합니다. RCS/MMS 알림 연동의 기존 제약은 동일합니다.

## 1.5 잠금화면·채팅창·알림 없이 수신

- SMS는 SMS_RECEIVED 수신 경로가 항상 실행됩니다. 문자창이 열려 있거나 잠금 상태이며 앱 알림이 없어도 SMS를 처리합니다. Android 권한이 유지되고 기기 정책이 허용하는 범위에서 작동합니다.
- RCS/MMS 알림 모드에서도 SMS 수신 권한을 요청하며, SMS와 알림의 중복 답장은 30초 범위에서 억제합니다.
- 메시지 알림 연동 상태에서 MMS 본문이 단말기의 MMS 저장소에 내려받아진 경우, 알림이 없어도 문자 텍스트를 감지해 **SMS로 답장**합니다. MMS 형식으로 발송하는 것은 아닙니다. MMS 저장소 접근과 백그라운드 알림 리스너 연결은 휴대전화/메시지 앱/관리 정책에 따라 제한될 수 있습니다. 첨부파일과 그룹 메시지는 지원하지 않습니다.
- RCS(채팅+)는 외부 앱에 범용 직접 수신/발송 API가 없어 알림이 전혀 생성되지 않으면 자동응답을 보장할 수 없습니다. 메시지 앱이 알림과 원격 답장을 제공할 때만 그 경로를 사용합니다. 잠금화면에서 알림 내용 또는 원격 답장이 차단된 경우 RCS는 자동응답하지 못합니다.
- 이 업데이트 이후 알림 연동 모드 사용자는 SMS 읽기·수신·발송 권한을 허용하고 자동 응답을 다시 ON으로 설정하세요.
