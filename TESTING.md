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

## 6-2. 재디자인 확인 (토스 디자인 시스템 + Pleos UI 가이드)
| 확인 | 기대 결과 |
|---|---|
| 글꼴 | Asta Sans. 헤드라인 60·제목 44·본문 32sp, 헤드라인·제목은 Bold에 자간이 살짝 좁음, 본문은 Regular 행간 1.5. 운전석에서 팔 뻗은 거리에서 본문이 읽혀야 함 |
| 낮/밤 | 에뮬레이터 Settings > Display 다크모드 토글 → 낮: 연회색 캔버스(#F2F4F6)에 흰 카드 / 밤: #202027 캔버스에 #2C2C35 카드. 글자 대비 유지 |
| 정차 홈 구조 | 위에서부터 큰 `안녕하세요` + 언어 chip 3개(선택 = 연파랑 바탕 파란 글자) → `마지막 안내` 카드 → 파란 pill `조작 안내 다시 듣기`(언어 선택 후) → 요약·체크리스트 카드 → 2×2 타일(아이콘 + 제목만: 둘러보기 / 사전 / 긴급(빨강) / 설정) → 접힌 Developer |
| 단일 강조색 | 화면에 파란 채움 버튼은 하나뿐. 보조 버튼은 회색, 긴급은 빨강 |
| 카드 | 평면, 그림자 없음, 1dp 헤어라인, 모서리 24dp. 카드 안 행은 가는 구분선으로 나뉨 |
| 눌림 | 버튼·행을 누르면 어두운 반투명 오버레이(그림자 아님) |
| 화면 전환 | 타일 탭 → 서브 화면이 오른쪽에서 320ms 슬라이드로 들어오고 홈은 살짝 왼쪽으로 밀리며 흐려짐. 뒤로 → 반대 방향. 튕김 없음. 기어 D/P 전환은 200ms 페이드 |
| 펼침 | 요약·사전 행을 펼치면 높이가 200ms로 부드럽게 늘고 화살표가 180° 돎 |
| 서브 화면 | 상단에 ← 와 제목. 사전의 표지판 행은 한국어 원문이 파란 chip, 경고등 행은 주황 경고 아이콘(자동 설명 대상) 또는 회색 정보 아이콘 |
| 설정 | 카테고리 4개 switch(켜짐 = 파랑) + 리마인더 간격 segmented control(선택 = 흰 조각이 떠 보임) |
| 긴급 | 검정(밤: 흰) 카드에 큰 한국어 문구, 연락처 행의 번호는 파란 글자 + 파란 원형 통화 버튼 |
| 주행 화면 | 큰 아이콘 칩 + 안내 1개(3줄 넘으면 …), 버튼·메뉴 없음, 움직이는 요소 없음 |
| 문구 | 한국어가 전부 해요체(`~해요`, `~이에요`). `~합니다`가 보이면 버그 |

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
| 정차 화면 `긴급 상황` 타일(빨강) 탭 | 전체 화면 전환. 상단 ← 와 제목, 검정(밤: 흰) 카드 안에 힌트와 큰 한국어 문구. 선택 언어가 ko가 아니면 아래에 번역 |
| 연락처 | 경찰 112, 구급 119, 관광통역 1330에 통화 버튼. 렌터카·보험사는 `미등록`이고 버튼 없음 |
| 통화 버튼 탭 | 에뮬레이터에 다이얼러가 있으면 열림. 없으면 `Emergency: no dialer for 112` 로그만, 앱 안 죽음 |
| `사고 때 할 일 듣기` | `faq_accident` 재생 |
| 상단 ← 또는 시스템 뒤로 | 정차 화면으로. 언어·요약 유지 |
| 기어 D(화면 제한 걸리면) | 주행 화면으로 바뀌고, P 복귀 시 긴급 화면이 아니라 정차 화면 |

## 6-7. 안내 설정
| 단계 | 기대 결과 |
|---|---|
| `안내 설정` 타일 탭 → 설정 화면 | 스위치 4개(어린이보호구역·단속 카메라·구간단속·우회전 일시정지) 모두 ON, 간격 10분 선택 |
| `단속 카메라` OFF → `section_camera.json` 재생 | `camera_fixed_80` **없음**, `section_*`는 그대로. 이후 요약에 단속 카메라 집계 없음 |
| `우회전 일시정지` OFF → Virtual Console RIGHT | 안내 없음 |
| 간격 5분 선택 → RIGHT → OFF → 5분 뒤 RIGHT | `right_turn_reminder` (10분 기다릴 필요 없음) |
| `force-stop` 후 재실행 | 설정이 그대로 남아 있음 (`shared_prefs/settings.xml`) |
| `pm clear` | 기본값으로 돌아감 |

## 6-8. 차량 조작 둘러보기
| 단계 | 조작 | 기대 결과 |
|---|---|---|
| 1 | 정차 화면 `차량 조작 둘러보기` 타일 | 새 화면. 가로로 긴 타일(약 2.2:1) 8개가 4×2로: 시동 / 기어(컬럼 레버) / 주차 브레이크 / 회생제동 패들 / 와이퍼·워셔 / 전조등 / 충전구 / 하이패스. 첫 타일이 선택된 상태, 아래 카드에 제목+설명 |
| 2 | 타일 탭 | 타일이 파란 테두리·연파랑으로 바뀌고, 아래 카드가 그 항목으로 바뀌며(`assets/tour/<id>.png` 가 있으면 사진이 타일을 채우고 제목은 아래 띠, 카드에는 16:9 사진) 음성(로그) 재생 |
| 3 | 언어 chip 바꾼 뒤 다시 들어가기 | 표제어·본문이 그 언어로 |
| 4 | 상단 ← 또는 BACK | 정차 화면으로 |

## 6-9. 표지판·경고등 사전
| 단계 | 조작 | 기대 결과 |
|---|---|---|
| 1 | 정차 화면 `표지판·경고등 사전` 타일 | `도로 표지판·노면 글자` 카드 19행(한국어 원문이 파란 chip + 표제어), 그 아래 `계기판 경고등` 카드 24행 |
| 2 | `비보호` 탭 | 비보호 좌회전 설명 펼침 + 재생 |
| 3 | Virtual Console > Safety > Telltales > **LOW_EV_BATTERY**(또는 TPMS) On | 사전에 안 들어가 있어도 정차 화면 안내 카드에 `telltale_low_ev_battery` 설명이 뜨고 재생. 로그 `signal Telltale(type=LOW_EV_BATTERY, on=true)` |
| 4 | 같은 등 Off → On | 다시 1회 재생 (꺼지면 재무장) |
| 5 | Telltales > **HEADLAMP_HIGH** On | 무음. 표시등은 자동 설명 대상이 아님 |
| 6 | 개발자 메뉴 `low_battery.json` (13초) | 1초 `telltale_low_ev_battery` → 4초 중복 무음 → 6초 `telltale_tpms` → 9초 상향등 무음 → 13초 `telltale_low_ev_battery` 다시 |

Virtual Console에서 텔테일 이름이 다르면 상단 검색창에 `TELLTALE`을 검색한다. 로그에 `telltales unavailable`이나 `telltales failed`가 찍히면 `CAR_SAFETY` 퍼미션 미승인 → 시나리오로만 확인.

## 6-10. 반납 체크리스트
| 단계 | 조작 | 기대 결과 |
|---|---|---|
| 1 | 6번 반납 요약이 나온 상태 | 요약 카드 아래 `반납 체크리스트`: 연료·충전량 / 하이패스 카드 / 소지품 / 사진 / P·주차 브레이크·전조등 |
| 2 | `low_battery.json` 재생 후 | 충전량 행에 `배터리  12%`. 재생 전이거나 차가 값을 안 주면 `계기판을 확인하세요` 문구 |
| 3 | 항목 탭 | 체크 토글. 화면 회전 후에도 유지 |
| 4 | 로그 | `energy unavailable`이 있으면 `CAR_ENERGY` 미승인. 실차에서 `Energy(percent=…)` 신호 확인 |

## 6-11. 안내 줄이기 질문 (신호 대기 중)
같은 종류 안내를 3번 들으면, 다음에 차가 멈췄을 때(기어 D + 브레이크 페달 또는 오토홀드) 또는 정차(P) 상태에서 한 번 묻는다. 움직이면 질문은 내려가고 다음 정지 때 다시 뜬다. 답하면 다시 묻지 않는다.

| 단계 | 조작 | 기대 결과 |
|---|---|---|
| 1 | `force-stop` 후 앱 데이터 삭제(`adb shell pm clear ai.driverhelper`)로 카운터 초기화 | — |
| 2 | 개발자 메뉴 `ask_camera.json` (30초) | 1·5·9초 카메라 안내 3번. 14초 브레이크 페달 → `ask_camera` 음성("단속 카메라 안내를 세 번 들으셨어요…") + 화면에 `계속 듣기`(파랑) / `그만 듣기` 버튼. 로그 `signal BrakePedal(pressed=true)` |
| 3 | 답하지 않고 30초 경과(페달 해제) | 버튼이 사라짐. 그 뒤 Virtual Console 브레이크 페달을 밟으면(기어 D) 다시 뜸 |
| 4 | `그만 듣기` | 버튼 사라짐. 설정 화면에서 `단속 카메라` 스위치가 꺼져 있음. 이후 카메라 시나리오는 무음 |
| 5 | 설정에서 다시 켜고 카메라 안내 3번 더 | 다시 묻지 않음(이미 답한 종류) |
| 6 | 기어 P 상태에서 질문이 대기 중이면 | 정차 홈 맨 위 카드로 같은 질문 |

실차에서는 브레이크 페달 상태(`CAR_DRIVE` 퍼미션)나 AUTO_HOLD 경고등으로 정지를 감지한다. 둘 다 안 오면 정차(P)에서만 묻는다. 카운터는 앱 데이터를 지우기 전까지 유지된다.

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
- 실제 차의 텔테일·연료 값: 에뮬레이터 Virtual Console과 시나리오로만. `CAR_SAFETY`·`CAR_ENERGY` 퍼미션 승인 여부도 실차/승인 후 확인.
- **오디오 포커스와 Gleo 호출 시 즉시 정지:** 실제 음성 재생이 있어야 의미가 있다. wav 파일이 들어온 뒤에 테스트한다.
- **150~200ms 무음:** 녹음 파일에 직접 넣는 방식이라 파일이 나온 뒤에 확인한다.
- **실제 내비 카메라 이벤트:** 에뮬레이터 내비에서 경로 안내가 될 때만 확인할 수 있다.

---

결과를 보고할 때는 단계 번호와 실패한 줄의 로그를 같이 붙인다.
