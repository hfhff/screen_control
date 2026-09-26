# Android 앱 차단 앱 조사

조사 기준일: 2026-09-27

## 결론

유사 앱은 하나의 권한으로 모든 기능을 처리하지 않고 기능별로 나눈다. 기본 앱 차단에는 대체로 사용 정보 접근으로 전면 앱을 찾고, 다른 앱 위에 표시 권한으로 차단 화면을 띄운다. 접근성 서비스는 웹 주소·화면 요소·설정 화면처럼 더 깊은 감지가 필요한 기능에서 사용한다.

월간 차단 MVP에는 웹사이트나 화면 요소 차단이 없으므로 접근성 서비스가 필요하지 않다. `사용 정보 접근 + 다른 앱 위에 표시 + 실행 중 알림`이 요구 기능에 맞는 최소 권한 구조다.

## 비교

| 제품 | 일정과 차단 | 해제·마찰 UX | 권한·배포에서 확인한 점 |
| --- | --- | --- | --- |
| ScreenZen | 앱별 목표, 요일·시간대, 짧은 사용 구간 | 실행 전 대기, 의도 확인 문구, 제한된 임시 사용 | Google Play에서 배포하며 웹사이트 차단에 접근성 권한을 쓴다고 명시 |
| AppBlock | 반복 일정, 빠른 차단, 사용량 제한 | 일시정지와 Strict Mode를 분리 | 기본 전면 앱 감지는 사용 정보 접근, 잠금 화면은 오버레이, 고급 웹·설정 차단은 접근성 사용 |
| Stay Focused | 여러 차단 프로필과 사용자 일정 | Strict/Lock Mode로 설정 변경 제한 | 강제 잠금은 선택 기능이며 Device Admin·접근성 같은 추가 권한 사용 |
| one sec | 선택 앱을 열 때 개입 화면 표시 | 호흡·반성 질문으로 무의식적인 실행 중단 | 차단보다 짧은 마찰을 강조하며 Android 권한 상태가 개입 신뢰성에 영향 |
| Android Digital Wellbeing | Focus Mode 자동 일정 | 정해진 시간 동안 잠시 휴식 | 즉시 영구 해제보다 자동 만료되는 일시 해제가 표준적인 UX |

## 이번 구현에 반영한 내용

- 기본 앱 차단에서 접근성 서비스를 제거했다.
- 사용 정보 접근으로 현재 앱의 패키지 이름만 확인한다.
- 다른 앱 위에 표시 권한으로 전체 화면 차단 안내를 표시한다.
- 차단 감시가 켜진 동안 지속 알림을 표시한다.
- 각 민감 권한을 요청하기 직전에 용도, 처리 데이터, 거절 시 영향을 따로 설명한다.
- 차단 화면에 `지금 꼭 열어야 하나요?`라는 의도 확인 문구를 추가했다.
- 사용자가 요구한 즉시 해제를 유지하되 현재 앱의 현재 일정 구간에만 적용한다.
- 재부팅 후 사용자가 켜 둔 차단 감시를 복구한다.

## 이번에 넣지 않은 기능

- 접근성 기반 웹사이트·릴스·설정 화면 차단.
- Device Admin을 이용한 삭제 방지.
- 설정을 일정 시간 바꿀 수 없게 하는 Strict Mode.
- 해제 전 강제 대기나 PIN.

이 기능들은 권한과 잠금 사고 위험을 크게 늘리고, 현재 요구사항인 `즉시 해제`와 맞지 않는다.

## 근거

- [ScreenZen Google Play](https://play.google.com/store/apps/details?id=com.screenzen) — 실행 전 대기, 짧은 사용 구간, 앱별 요일·시간 설정, 웹 차단용 접근성 권한.
- [AppBlock Android 권한 안내](https://appblock.app/help/android/settings/) — Usage Access, 오버레이, 알림, 접근성, Device Admin의 기능별 사용 목적.
- [AppBlock Android 도움말](https://appblock.app/help/android/) — 일정, 일시정지, Strict Mode와 제조사별 백그라운드 제약.
- [Stay Focused 공식 사이트](https://www.stayfocused.me/) — 차단 프로필과 Strict/Lock Mode.
- [one sec FAQ](https://one-sec.app/faq/) — 앱 실행 전 개입과 의도적인 사용 유도.
- [Android UsageStatsManager](https://developer.android.com/reference/android/app/usage/UsageStatsManager) — 사용 정보 조회 API.
- [Android 오버레이 설정 API](https://developer.android.com/reference/android/provider/Settings#canDrawOverlays(android.content.Context)) — 다른 앱 위에 표시 권한 확인과 설정 화면.
- [Google Play AccessibilityService 정책](https://support.google.com/googleplay/android-developer/answer/10964491) — 접근성 도구가 아닌 앱의 별도 고지·동의·신고 요건.
- [Google Play 중요 고지 안내](https://support.google.com/googleplay/android-developer/answer/11150561) — 민감 권한 요청 직전 앱 내부 고지와 적극적 동의 요건.

## 한계

일반 소비자용 Android 앱은 사용자가 권한을 끄거나 앱을 강제 종료·삭제하는 것까지 완전히 막을 수 없다. 제조사별 절전 정책과 팝업·분할 화면 동작도 실제 기기 검증이 필요하다. Play Store 배포 시에는 오버레이와 `specialUse` foreground service의 핵심 기능 적합성을 Play Console에 정확히 신고해야 한다.
