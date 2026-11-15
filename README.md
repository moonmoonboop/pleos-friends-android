# Pleos Friends

## 앱 이름 및 한 줄 소개

**Pleos Friends** —3C로 완성하는 함께하는 여행 — Connectivity, Convenience, Contents.

## 실행 방법

저장소 루트의 `android/` 폴더를 Android Studio에서 열고 빌드 변형 **`demoRelease`**(심사 APK) 또는 **`demoDebug`**(시연·테스트)를 선택합니다. JDK 21(17 가능), Gradle Wrapper 8.13, Android SDK 35, minSdk 28.

```sh
cd android
bash ./gradlew :app:assembleDemoRelease
adb install -r ../apk/RoDdo_PleosFriends_release.apk
adb shell am start -n com.pleosfriends.demo/com.pleosfriends.MainActivity --es scenario busan
```

시연 JSON은 [`android/scenarios/busan-roadtrip.json`](android/scenarios/busan-roadtrip.json)입니다. 일정 추가는 주소 검색 한 칸과 **Gleo AI**만 있습니다. 개발용 디버그 빌드는 `:app:assembleDemoDebug` 후 `app/build/outputs/apk/demo/debug/app-demo-debug.apk`를 설치합니다.

`android/local.properties`에 `sdk.dir=` 을 넣으면 로컬 SDK를 찾습니다. 이 파일은 커밋하지 않습니다. API 키와 백엔드는 없습니다.

## 테스트 환경 및 필요한 설정

- 차량을 **주차**한 뒤 여행 설정 · 경유 · 게임 · 플픽을 조작하세요. 주행 제한은 AAOS `CarUxRestrictions`입니다.
- 단위 테스트: `bash ./gradlew :app:testDemoDebugUnitTest`
- 계측 테스트(주차된 에뮬레이터): `bash ./gradlew :app:connectedDemoDebugAndroidTest`
- `demo` 변형은 오프라인 시연입니다. 실제 SMS, 클라우드 STT, LLM, 미공개 SDK는 없습니다.
- `connect` 변형은 공개 Pleos SDK만 사용합니다: **Vehicle 2.3.2**, **NaviHelper 3.0.5**, **FusedLocation 1.1.3**, **SpeechToText 3.3.1**(ON_DEVICE), **TextToSpeech 3.3.1**(ON_DEVICE).
- 심사 제출 APK는 `android/keystore/`의 공모전용 키로 서명한 **release**입니다. 디버그 서명 APK를 제출 파일로 쓰지 않습니다.

## 개발에 사용한 Pleos Connect 에뮬레이터 버전과 화면 규격

**Pleos Connect Emulator v2.1.0, API 34, Connect-S-17 (2560×1440, Driving View : app 1:2).** Cold Boot, RAM 4 GB, 주차 상태를 권장합니다. UI는 앱 창만 차지하고, Driving View · 상태바 · 독은 차량 크롬입니다. 액센트 `#5A46FA`, 텍스트 Gray/900 `#131417`.

클라우드 탭스루 VM에는 공식 Pleos 시스템 이미지가 없어 **Google API 34 Automotive** 1920×1080 AVD(`PleosFriends_S17`)로 설치·실행을 확인했습니다. 심사 시연은 Pleos Connect Emulator v2.1.0을 기준으로 합니다.

## APK 파일 위치 또는 GitHub Release 링크

[`apk/RoDdo_PleosFriends_release.apk`](apk/RoDdo_PleosFriends_release.apk)
