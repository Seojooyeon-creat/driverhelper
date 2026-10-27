# 테스트 가이드

아래 순서대로 진행한다. 규칙 상태(쿨다운, 이미 한 안내)는 앱 프로세스가 살아 있는 동안 유지되므로, 순서가 결과에 영향을 주는 항목이 있다.

## 0. 준비
```sh
cd ~/SCW/driverhelper
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug testDebugUnitTest   # 단위 테스트 21개 통과 확인
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb forward tcp:8088 tcp:8088              # Virtual Console → http://127.0.0.1:8088
```
로그는 별도 터미널에 계속 띄워 둔다.
```sh
adb logcat -s GuidancePipeline VoiceGuide PleosVehicleSignal CarPropertySignal NaviSignal FusedLocationSignal
```
상태 초기화:
```sh
adb shell am force-stop ai.driverhelper    # 규칙 상태 초기화
adb shell pm clear ai.driverhelper         # DB까지 초기화
```

Pleos 에뮬레이터는 production 빌드라 `adb root`가 안 된다. 그래서 VHAL 직접 주입(`inject-vhal-event`)은 쓰지 않는다. 조작은 Virtual Console(기어·방향지시등·도어)과 개발자 메뉴 시나리오(위치·카메라·주차 브레이크)로만 한다.

음성 파일이 아직 없으므로 모든 안내는 `VoiceGuide: [언어] 메시지ID: 문구` 로그로 확인한다.

---

## 1. 실행과 연결
```sh
adb shell am start -W -n ai.driverhelper/.ui.MainActivity
```
| 확인 | 기대 결과 |
|---|---|
| `TotalTime` | 10,000ms 미만 |
| 차량 SDK | `turn signal capability: ...`, `door capability: ..., areas=[...]` |
| AAOS | `GEAR_SELECTION subscription: true`, `PARKING_BRAKE_ON subscription: true` |
| 내비 | `navi connected` 또는 `navi unavailable` (어느 쪽이든 앱이 죽지 않아야 함) |
| 위치 | `location registered, 3 proximity alerts requested` 또는 `fused location not supported` 또는 `fused location source failed; continuing without it` (`need Permission pleos.car.permission.FUSED_LOCATION` — 승인 전이라 정상) |
| 신호 소스 하나가 실패해도 | 앱은 살아 있고 나머지 로그(`GEAR_SELECTION subscription`, `turn signal capability`)는 계속 찍힘 |

## 2. 탑승 브리핑과 언어 선택
| 단계 | 조작 (Virtual Console) | 기대 결과 |
|---|---|---|
| 2-1 | Motion > Gear → **P** | `signal Gear(isPark=true)` |
| 2-2 | Convenience > Door → 운전석 열기 → 닫기 | `language_prompt`가 ko/en/ja로 3번, 화면에 3개 언어 문구 |
| 2-3 | 화면에서 언어 버튼 탭 | 2초 안에 `boarding_briefing` |
| 2-4 | 도어를 다시 열고 닫기 | 안내 없음 (세션당 1회) |
| 2-5 | "조작 안내 다시 듣기" 탭 | `boarding_briefing` 다시 재생 |
| 2-6 | 기어 D → 도어 열고 닫기 | 안내 없음 |

## 3. 우회전 안내 (실제 신호)
Virtual Console의 방향지시등 항목(Safety > Telltales > Turn Signal Right 등, 상단 검색창에 `TURN_SIGNAL` 검색)을 쓴다. Virtual Console로 안 되면 개발자 메뉴의 `right_turn.json`으로 대신한다.
| 단계 | 기대 결과 |
|---|---|
| 3-1 첫 RIGHT | `right_turn_stop` (전체 규칙 안내) |
| 3-2 OFF → RIGHT (10분 안) | 안내 없음 |
| 3-3 LEFT | 안내 없음 |
| 3-4 (선택) 10분 뒤 RIGHT | `right_turn_reminder` |

## 4. 시나리오 (개발자 메뉴, 정차 화면 맨 아래, debug 빌드 전용)
| 시나리오 | 길이 | 기대 결과 |
|---|---|---|
| `school_zone.json` | 52초 | 약 9초에 `school_zone` 한 번만. 좌표 로그는 `Location(37.56, 126.98)`처럼 소수 2자리 |
| `section_camera.json` | 24초 | 1초 `camera_fixed_80` → 9초 `section_start_100` → 15초 `section_speed_alert` → 24초 `section_end`. 3초 중복 카메라와 18초 경고는 무시 |
| `right_turn.json` | 12초 | 3번에서 이미 안내를 들었으면 무음 (쿨다운) |

- 재생 중인 버튼을 다시 누르면 멈춰야 한다. 재생이 끝나면 버튼이 자동으로 ▶ 상태로 돌아와야 한다.
- 시나리오 재생 중에도 Virtual Console 방향지시등이 동작해야 한다.
- 같은 시나리오를 다시 재생하면 무음이 정상이다. 처음부터 보려면 `force-stop` 후 다시 실행한다.

## 5. 주행 중 화면 제한 (DO)
화면 제한은 차량 주행 상태(차속·기어)로 결정되는데, 이 에뮬레이터에서는 차속을 바꿀 수 없다. 시나리오는 앱 내부 신호만 만들기 때문에 화면 제한은 걸지 못한다.

| 확인 | 기대 결과 |
|---|---|
| Virtual Console에서 기어 **D** | 이미지의 UX 제한 설정에 따라 다름. 화면이 큰 글씨 + 마지막 안내만으로 바뀌면 통과. 안 바뀌면 이 에뮬레이터에서는 확인 불가로 기록 |
| 기어 D에서 앱이 차단되지 않음 | DO 선언이 적용된 상태 |
| 다시 기어 P | 정차 화면으로 돌아오고 선택한 언어는 유지됨 |

확인되지 않으면 실차 또는 차속을 조작할 수 있는 환경에서 다시 본다.

## 6. 반납 요약
| 단계 | 조작 | 기대 결과 |
|---|---|---|
| 6-1 | 4번까지 진행한 상태에서 기어 D → **P** | `trip_summary` 음성 |
| 6-2 | 화면 확인 | `주행 안내 요약`과 종류별 횟수. 지점은 시나리오에 위치가 없으면 `—`, `school_zone` 기록은 `37.5xx, 126.978` |
| 6-3 | 개발자 메뉴 `return_brake.json` 재생 (6초: 기어 D → 위치 → 카메라 50 → 주차 브레이크 ON) | 2초 `camera_fixed_50`, 6초 `trip_summary`. 요약에 `단속 카메라`가 1 늘고 지점 `37.561, 126.978` |
| 6-4 | 6-3 직후 Virtual Console 기어 P | 요약이 다시 나오지 않음 (같은 반납) |
| 6-5 | `force-stop` 후 P 상태로 실행 | 요약 없음 |

주차 브레이크는 Virtual Console에서 경고등만 바뀌고 실제 `PARKING_BRAKE_ON` 값은 바뀌지 않으므로 시나리오로 대신한다. 실제 브레이크 신호 경로는 1번 로그의 `PARKING_BRAKE_ON subscription: true`까지만 확인한다.

## 6-2. 재디자인 확인 (Pleos UI 가이드)
| 확인 | 기대 결과 |
|---|---|
| 글꼴 | 시스템 UI와 같은 Asta Sans. 한글·영문 모두 적용 |
| 낮/밤 | 에뮬레이터 Settings > Display 다크모드 토글 → 배경·카드 색이 바뀌고 글자 대비가 유지됨 |
| 정차 화면 구조 | 위에서부터 언어 버튼 3개 → 마지막 안내 카드(아이콘+글) → 요약 카드 → 다시 듣기 → 접힌 `Developer · scenarios` |
| 주행 화면 | 큰 아이콘 + 안내 1개(3줄 넘으면 …), 버튼·메뉴 없음, 움직이는 요소 없음 |
| 아이콘 | 스쿨존 = 경고(주황), 카메라·구간 = 위치 핀(주황), 그 외 = 정보(파랑). 항상 글과 함께 |
| 터치 | 버튼을 누르면 짧은 진동(햅틱) |

## 6-3. Gleo 교통법 FAQ (Gleo 없이 adb로)
Gleo가 하는 일을 adb로 흉내 낸다. 앱이 켜져 있어도 꺼져 있어도 동작해야 한다.
```sh
adb shell am start -n ai.driverhelper/.ui.MainActivity -a EXPLAIN_TRAFFIC_RULE --es ACTION_PARAM '{"topic":"drunk_driving"}'
adb shell am start -n ai.driverhelper/.ui.MainActivity -a EXPLAIN_TRAFFIC_RULE --es ACTION_PARAM '{"topic":"weather"}'
adb logcat -d -s GleoAction VoiceGuide | tail -8
```
| 확인 | 기대 결과 |
|---|---|
| 아는 topic | `GleoAction: action topic=drunk_driving`, `respond {"status":"success","mode":"silent",...}`, `VoiceGuide: [xx] faq_drunk_driving: ...`, 화면 카드에 문구 |
| 모르는 topic | `respond {"status":"fail",...}`, 재생 없음 |
| 중복 실행 | 앱이 새로 뜨지 않고(singleTop) 같은 화면에서 재생 |
| TTS | `tts init failed: PERMISSION_DENIED` 또는 `tts unavailable`이면 승인 전이라 정상. 초기화되면 ko/en은 실제 음성 |

### 6-4. FAQ 문안 검수
정차 화면 맨 아래 `Developer · FAQ review (21)`을 펼친다.
- 항목마다 `topic → 메시지ID`와 한국어·English·日本語 문안이 모두 보인다.
- ▶ 버튼: 그 항목을 선택한 언어로 재생(음성 없으면 `VoiceGuide` 로그). 위쪽 안내 카드에도 문구가 뜬다.
- `Play all in selected language`: 21개를 순서대로 큐에 넣어 연속 재생.
- 고칠 문안은 `app/src/main/assets/messages.json`의 `faq_*` 키를 바꾸고 재설치. 단위 테스트가 3개 언어 누락을 잡아 준다.

실제 Gleo 음성("한국에서 음주운전 기준이 뭐야?")은 `assets/schema.json`이 Pleos 리뷰를 통과한 뒤에만 동작한다.

## 6-5. 요약 항목 탭 → 규칙·과태료
6번 반납 요약이 떠 있는 상태에서.
| 단계 | 기대 결과 |
|---|---|
| `단속 카메라 · 1` 행 탭 | `VoiceGuide: [xx] fine_camera: ...`, 행 아래에 같은 문구가 펼쳐짐. 위 안내 카드에도 뜸 |
| 같은 행 다시 탭 | 접힘, 재생 없음 |
| 다른 카테고리 탭 | 그 카테고리의 `fine_*` 재생, 이전 것은 접힘 |
| 요약 카운트 | `fine_*` 재생은 요약에 **집계되지 않아야** 함 (category 없음) |

## 6-6. 긴급 화면
| 단계 | 기대 결과 |
|---|---|
| 정차 화면 `긴급 상황`(빨간 테두리) 탭 | 전체 화면 전환. 맨 위 힌트, 큰 한국어 카드. 선택 언어가 ko가 아니면 아래에 번역 |
| 연락처 | 경찰 112, 구급 119, 관광통역 1330에 통화 버튼. 렌터카·보험사는 `미등록`이고 버튼 없음 |
| 통화 버튼 탭 | 에뮬레이터에 다이얼러가 있으면 열림. 없으면 `Emergency: no dialer for 112` 로그만, 앱 안 죽음 |
| `사고 때 할 일 듣기` | `faq_accident` 재생 |
| `돌아가기` 또는 시스템 뒤로 | 정차 화면으로. 언어·요약 유지 |
| 기어 D(화면 제한 걸리면) | 주행 화면으로 바뀌고, P 복귀 시 긴급 화면이 아니라 정차 화면 |

## 6-7. 안내 설정
| 단계 | 기대 결과 |
|---|---|
| `안내 설정` 펼침 | 스위치 4개(어린이보호구역·단속 카메라·구간단속·우회전 일시정지) 모두 ON, 간격 10분 선택 |
| `단속 카메라` OFF → `section_camera.json` 재생 | `camera_fixed_80` **없음**, `section_*`는 그대로. 이후 요약에 단속 카메라 집계 없음 |
| `우회전 일시정지` OFF → Virtual Console RIGHT | 안내 없음 |
| 간격 5분 선택 → RIGHT → OFF → 5분 뒤 RIGHT | `right_turn_reminder` (10분 기다릴 필요 없음) |
| `force-stop` 후 재실행 | 설정이 그대로 남아 있음 (`shared_prefs/settings.xml`) |
| `pm clear` | 기본값으로 돌아감 |

## 7. 화면 회전과 창 크기
- portrait와 landscape를 바꾸고 분할 화면에도 넣어 본다. 선택한 언어, 마지막 안내, 요약, 재생 중인 시나리오가 그대로 유지되어야 한다.
- 모든 버튼은 장갑 낀 손가락으로도 누를 수 있는 크기여야 한다(높이 96dp 이상).

## 8. release 빌드와 폴백
```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleRelease
$ANDROID_HOME/build-tools/34.0.0/aapt dump permissions app/build/outputs/apk/release/app-release-unsigned.apk
```
- `NAVI_ROUTE`와 `FUSED_LOCATION`이 **없어야** 한다.
- release APK는 서명되지 않아 바로 설치할 수 없다. 폴백 동작은 1번 로그의 `navi unavailable` / `location failed` 경로로 판단한다.

## 지금은 확인할 수 없는 것
- **오디오 포커스와 Gleo 호출 시 즉시 정지:** 실제 음성 재생이 있어야 의미가 있다. wav 파일이 들어온 뒤에 테스트한다.
- **150~200ms 무음:** 녹음 파일에 직접 넣는 방식이라 파일이 나온 뒤에 확인한다.
- **실제 내비 카메라 이벤트:** 에뮬레이터 내비에서 경로 안내가 될 때만 확인할 수 있다.

---

결과를 보고할 때는 단계 번호와 실패한 줄의 로그를 같이 붙인다.
