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
