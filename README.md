# Driver Helper (Pleos Connect)

외국인 렌터카 운전자용 모국어 주행 도우미. **대상 차량: 현대 아이오닉 5(국내 사양, Pleos Connect 내비 탑재)** — 조작 안내·경고등·체크리스트가 이 차 기준이다.

> **주의** 교통법 FAQ와 안내 문구는 법령 검수 전 초안이며 법적 효력이 없다. 스쿨존 좌표는 가짜 샘플 3곳이다. 실차 적용 전 반드시 검수할 것.
>
> 라이선스: 코드는 [MIT](LICENSE). 번들 폰트 Asta Sans는 42dot의 [SIL OFL 1.1](OFL-AstaSans.txt). Pleos SDK는 저장소에 포함하지 않으며 Maven 좌표로만 참조한다 (Pleos Playground 약관 제15조).

## 빌드
JDK 17 필요 (기본 JDK가 24여도 됨, toolchain이 17로 고정).

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## SDK 버전 메모
문서 표기와 저장소 실제 버전이 다름: `Vehicle 2.3.1 → 2.3.2`, `FusedLocation 1.1.2 → 1.1.3` (저장소에 해당 버전만 존재). `NaviHelper 3.0.5`는 일치.

## 구조
`signal → RuleEngine → AudioFocus → VoiceGuide`
- `signal/`: `DrivingSignalSource` — `PleosVehicleSignalSource` (Pleos `TurnSignal`, `Door`), `CarPropertySignalSource` (AAOS `GEAR_SELECTION`)
- `signal/NaviSignalSource`: `onCameraAlert`(고정·구간단속·스쿨존 카메라), `onCautionPointInfo`(`SCHOOL_ZONE`만). 연결 실패 시 아무것도 안 보냄.
- `signal/FusedLocationSignalSource`: `isSupported()` → `initialize()` → 위치 콜백 + 스쿨존마다 `addProximityAlert`(반경 500m, ENTER/EXIT). 미지원이면 아무것도 안 보냄.
- 스쿨존은 좌표 거리 계산·지오펜스·내비 세 경로로 들어올 수 있음 → 60초 안에 한 번만 안내.
- `signal/RouteBriefingSource`: 출발 전 경로 브리핑 **인터페이스만**. NaviHelper 3.0.5에 경로 전체의 주의 구간 API가 없음.
- `signal/ScenarioSignalSource`: `assets/scenarios/*.json` 재생 (에뮬레이터로 못 만드는 위치·카메라·연료 이벤트용). 이벤트 타입 `turn|door|gear|brake|location|camera|telltale|energy`
- `rule/RuleEngine`: 순수 Kotlin. 스쿨존(반경 300m + 200m 앞에서 1회, 700m 밖으로 나가면 재무장), 카메라(ID당 1회, 제한속도별 문구), 구간 과속 경고(60초 쿨다운). 우회전 OFF→RIGHT 상승 에지에서만 안내. 세션 첫 1회는 전체 규칙(`right_turn_stop`), 이후엔 짧은 리마인더(`right_turn_reminder`)를 10분에 최대 1회.
- `voice/`: `PrerecordedVoiceGuide` (`assets/audio/{ko|en|ja}/{messageId}.wav`, 없으면 `LogVoiceGuide`로 폴백), `AudioFocus` (`ASSISTANCE_NAVIGATION_GUIDANCE` + `GAIN_TRANSIENT_MAY_DUCK`, transient loss 시 즉시 정지, 끝나면 반납)
- 재생 큐: 우선순위 스쿨존 > 카메라 > 우회전 > 기타, 중복 제거. 신호 수집은 재생에 막히지 않음.
- 스쿨존 좌표: `assets/school_zones.json` — **현재 SAMPLE 3곳(가짜 좌표)**. 공공데이터(전국어린이보호구역표준데이터)의 대상시설명·위도·경도로 교체.
- 위치 로그는 `Location.toString()`에서 소수 2자리(~1km)로 마스킹.
- `data/GuidanceRecord`: Room. 주행 안내(스쿨존·카메라·구간·우회전)를 재생할 때마다 시각 + 위치(소수 3자리, ~100m) 저장. 브리핑·언어 안내는 저장 안 함.
- `voice/PleosTtsVoiceGuide`: Pleos TTS 3.3.1, ON_DEVICE, ko-KR/en-US만(SDK에 일본어 없음). 녹음 파일이 없을 때 2차로 쓰고, 초기화·재생 실패나 타임아웃이면 로그로 폴백. 퍼미션 `TTS_SERVICE`는 debug manifest에만.
- `ui/Theme.kt`: **토스 디자인 시스템(TDS) 토큰**을 차량 화면 스케일로 적용. 색은 `@toss/tds-colors` 발행값(라이트: grey-100 캔버스·흰 카드·grey-900 글자·blue-500 단일 강조·red-500 위험·orange-500 경고 / 다크: adaptive 팔레트), 라운드 사다리(카드 24·버튼 pill), 굵기·자간(헤드라인 Bold −2%), 행간 1.5×, pressed = 검정 26% 오버레이, 모션 120/200/320ms. 글꼴은 Pleos 요구대로 Asta Sans 유지(Toss Product Sans는 재배포 불가). 크기는 Pleos 최소값보다 크게(헤드라인 60·제목 44·본문 32·보조 28sp, 터치 96dp). 화면당 파란 CTA 하나(정차: 조작 안내 다시 듣기, 긴급: 사고 때 할 일 듣기).
- 화면 구조(TDS 컴포넌트): 정차 홈 = 인사 + 언어 chip → 마지막 안내 카드 → primary pill → 요약·체크리스트(list-row + badge) → 2×2 grid-list(둘러보기·사전·긴급·설정) → 개발자 메뉴. 서브 화면은 top-app-bar(뒤로 + 제목) + 카드 안 list-row + 헤어라인 구분선. 설정은 switch row + segmented control. 긴급 카드는 inverse surface(grey-900) 위 흰 글자.
- 안내 줄이기 질문: 종류별 재생 횟수를 `Settings`(SharedPreferences `heard_*`)에 누적, 3회(`ASK_AFTER`)가 되면 다음 정지 때 묻는다. "정지" = 기어 D + 브레이크 페달(`Brake.registerBrakePedalStatus`, 퍼미션 `CAR_DRIVE`, debug manifest) 또는 `AUTO_HOLD` 경고등, 또는 P. 주행 중에는 절대 묻지 않고, 움직이면 질문을 내린다. 답은 화면 버튼 2개(계속/그만, 음성 인식 없음)이고 `그만`은 기존 카테고리 off와 같다. 답한 종류는 `asked`에 저장돼 다시 묻지 않는다. 카운터 초기화 시점(반납)은 미정.
- 문구는 **해요체**(TDS 보이스): `messages.json`의 한국어 전부 `~합니다/~입니다` → `~해요/~이에요`. 영어·일본어는 그대로.
- `gleo/`: Gleo Interface 연동(SDK 없음). `assets/schema.json`에 함수 `EXPLAIN_TRAFFIC_RULE(topic enum)` 1개. Gleo가 `MainActivity`를 action=함수명, extra `ACTION_PARAM`(JSON)으로 띄우면 `Faq`에서 메시지 ID를 찾아 재생하고, `APP_ACTION_RESPONSE` 브로드캐스트로 `mode: silent` 응답(모국어는 우리가 읽음). 모르는 topic은 `fail`. **스키마는 Pleos 팀 리뷰 후 활성화됨.**
- 교통법 FAQ 21개(`faq_*`, ko/en/ja): 속도·카메라·안전벨트·음주·휴대전화·횡단보도·좌회전·황색신호·버스전용·유턴·주정차·하이패스·사고·충전·고속도로·긴급차·통학버스·교차로·면허. 법령 검수 전 초안.
- 요약 항목 탭: 반납 요약의 카테고리(`cat_*`)를 탭하면 규칙과 과태료 설명(`fine_*`, 승용차 기준 초안)을 재생하고 펼쳐 보여줌. `MessageId.detail`.
- 긴급 화면: 정차 화면 `긴급 상황` 버튼 → 상대방에게 보여주는 한국어 카드(아래에 운전자 언어 번역) + `assets/contacts.json` 연락처(112·119·1330 공개번호, 렌터카·보험사는 **비어 있음 → 운영사별로 채울 것**) 원터치 다이얼(`ACTION_DIAL`, 다이얼러 없으면 번호만 표시) + `faq_accident` 재생.
- 안내 설정(`Settings.kt`, SharedPreferences): 카테고리 4종 on/off(꺼진 카테고리는 큐에 넣지 않고 기록도 안 함), 우회전 리마인더 간격 5/10/30분(`RuleEngine.reminderCooldownMs`에 즉시 반영).
- 차량 조작 둘러보기(`ui_tour`): 아이오닉 5 기준 8항목(`ctrl_*`: 시동·컬럼식 기어·주차 브레이크·회생제동 패들·와이퍼·전조등·충전구·룸미러 하이패스), 4×2 가로형 타일(사진이 타일을 채우고 제목 띠, 없으면 아이콘 + 제목)을 탭하면 아래 카드에 제목·사진(`assets/tour/<id>.png|jpg`, 16:9, 없으면 글만; 파일명은 `assets/tour/README.md`)·설명이 나오고 재생. 항목 목록은 `data/Guides.kt`. **조작 위치 문구는 실차에서 검증 전.**
- 표지판·경고등 사전(`ui_dictionary`): 표지판·노면 글자 19개(`sign_*`, 한국어 원문을 크게 표시 + 뜻), 계기판 경고등 24종(전기차 기준, 엔진 계열 제외; `telltale_*`, Pleos `TelltaleType` 이름 → 설명 매핑). `PleosVehicleSignalSource`가 `Safety.registerTelltales`로 경고등 변화를 받아, 경고·고장 등(`Guides.autoExplain`)이 켜지면 자동으로 1회 설명하고 꺼지면 재무장. 표시등(상향등·오토홀드·주차 브레이크)은 사전에만 있고 자동 재생 안 함. 퍼미션 `CAR_SAFETY`는 debug manifest.
- 반납 체크리스트: 반납 요약 아래 5항목(`check_*`) 체크박스. 충전량은 `EvBattery.registerEvBatteryStateOfCharge`로 읽어 표시(코드는 `Fuel` 퍼센트도 받지만 아이오닉 5에선 오지 않음)(못 읽으면 "계기판 확인"). 퍼미션 `CAR_ENERGY`는 debug manifest. 체크 상태는 앱 재시작까지 유지.
- 문구: `assets/messages.json`. 사전·둘러보기·체크리스트 항목은 `<id>_title`(표제어) + `<id>`(본문) 쌍. **음성 파일은 앞에 150~200ms 무음을 넣어 제작할 것.**

## Phase 1 테스트: 우회전 안내
1. 앱 설치 후 실행 (언어는 시스템 로케일, ko/en/ja 외에는 en)
2. 로그 보기:
   ```sh
   adb logcat -s GuidancePipeline VoiceGuide PleosVehicleSignal
   ```
   시작 시 `turn signal capability: ...` 가 찍히면 SDK 연결·퍼미션 OK.
3. 우회전 신호 넣기 (둘 중 동작하는 것):
   - **Virtual Console**: `adb forward tcp:8088 tcp:8088` → http://127.0.0.1:8088 → Safety > Telltales > *Turn Signal Right* → On
     (상단 검색창에 `TURN_SIGNAL` 검색해 다른 카드가 있으면 그걸 우선 사용)
   - 개발자 메뉴 `right_turn.json` (Pleos 에뮬레이터는 production 빌드라 `adb root`·VHAL 주입 불가)
4. 기대 결과
   - `signal TurnSignal(direction=RIGHT)` 로그
   - `VoiceGuide: [ko] right_turn_stop: 우회전할 때 ...` 로그 (음성 파일이 없어 Log 폴백)
   - 정차 화면에 안내 문구 표시
   - 첫 RIGHT: 전체 규칙 안내
   - 이후 OFF → RIGHT: 10분 안에는 안내 없음, 10분 후엔 짧은 리마인더

## Phase 2 테스트: 탑승 브리핑 + 언어 선택
탑승 판정 = 기어 P 상태에서 실내 도어(후드·트렁크 제외)가 열렸다가 모두 닫힘. 세션당 1회.
- 언어 미선택 상태에서 탑승 → "언어를 선택하세요"를 ko/en/ja로 안내 → 언어 버튼 탭 → 브리핑
- 언어를 먼저 고른 경우 → 그 즉시 브리핑, 이후 탑승 판정은 무음
- "조작 안내 다시 듣기" 버튼으로 언제든 재생

1. `adb logcat -s GuidancePipeline VoiceGuide PleosVehicleSignal CarPropertySignal`
   - `door capability: ..., areas=[...]` (지원 도어 구역), `gear subscription: true` 확인
2. Virtual Console: Motion > Gear → **P**
3. Convenience > Door → 운전석 도어 열기 → 닫기
4. 기대: `signal Gear(isPark=true)`, `signal DoorOpen(open=true)` → `DoorOpen(open=false)` → `VoiceGuide: ... language_prompt` 3회
5. 화면에서 언어 탭 → `VoiceGuide: [xx] boarding_briefing: ...`
6. 기어 D에서 도어를 열고 닫으면 안내 없음
7. 창 크기/방향 바꿔도 선택한 언어·마지막 안내 유지

**브리핑 문구는 일반 차량 기준 초안.** 실제 렌터카 차종의 기어/주차 브레이크/와이퍼 조작에 맞게 `messages.json`의 `boarding_briefing` 교체 필요.

## Phase 3 테스트: 시나리오 · 스쿨존 · 카메라
개발자 메뉴는 **debug 빌드 + 정차 화면**에서만 보임 (언어 버튼 아래 `Developer · scenarios`).

1. `adb logcat -s GuidancePipeline VoiceGuide`
2. 메뉴에서 시나리오 탭 → 재생, 다시 탭 → 정지. 끝나면 자동으로 정지 상태로 돌아감.

| 시나리오 | 길이 | 기대 로그 (`VoiceGuide`) |
|---|---|---|
| `school_zone.json` | 52초 | 약 9초에 `school_zone` 1회만. 좌표 로그는 `Location(37.56, 126.98)`처럼 마스킹 |
| `section_camera.json` | 24초 | 1초 `camera_fixed_80` (3초의 중복은 무시), 9초 `section_start_100`, 15초 `section_speed_alert` (18초 것은 쿨다운으로 무시), 24초 `section_end` |
| `right_turn.json` | 12초 | 세션 첫 우회전이면 `right_turn_stop`, 두 번째 RIGHT는 쿨다운으로 무시 |

- 시나리오는 실제 신호와 **합쳐서** 들어감. Virtual Console 조작도 같이 동작.
- 규칙 상태는 앱 프로세스 단위. 같은 시나리오를 다시 재생하면 이미 안내한 카메라·스쿨존은 조용함 (스쿨존은 멀리 나갔다 오면 다시 안내). 처음부터 보려면 앱 강제 종료 후 재실행.
- 시나리오 형식: `[{ "t": 시작 후 ms, "type": "turn|door|gear|location|camera", ... }]`. 필드는 `ScenarioSignalSource.kt`의 `parseScenario` 참고.

## Phase 4 테스트: NaviHelper · FusedLocation
퍼미션 `NAVI_ROUTE`, `FUSED_LOCATION`은 **`src/debug/AndroidManifest.xml`에만** 선언 (승인 전). release 빌드는 두 SDK 호출이 실패하고 폴백(시나리오)만 동작. 승인되면 `src/main`으로 옮길 것.

1. `adb logcat -s NaviSignal FusedLocationSignal GuidancePipeline VoiceGuide`
2. 시작 직후 로그 확인 (어느 쪽이든 앱이 죽지 않아야 함):
   - `navi connected` 또는 `navi unavailable; ...`
   - `location registered, 3 proximity alerts requested` 또는 `fused location not supported` / `fused location source failed; continuing without it`
   - 퍼미션이 아직 승인되지 않아 에뮬레이터에서는 `need Permission pleos.car.permission.FUSED_LOCATION`로 실패하는 것이 정상. 소스 하나의 실패는 로그만 남기고 나머지는 계속 동작한다.
3. 에뮬레이터 내비에서 경로 안내를 시작할 수 있으면 → 카메라 접근 시 `camera_fixed_*` / `section_*` 안내가 나오는지
4. 위치 SDK가 동작하면 `signal Location(xx.xx, yy.yy)` (마스킹된 좌표) 주기적으로 찍힘
5. Phase 3 시나리오가 여전히 동작하는지 (폴백 경로)
6. `./gradlew assembleRelease` 후 `aapt dump permissions`로 release APK에 NAVI_ROUTE/FUSED_LOCATION이 없는지

## Phase 5 테스트: 반납 요약
트리거 = 기어가 P가 아니었던 적이 있은 뒤 **기어 P** 또는 **주차 브레이크 ON**(AAOS `PARKING_BRAKE_ON`, 기존 `CAR_POWERTRAIN` 퍼미션). 둘이 연달아 와도 1회. 앱 시작 시의 P는 무시.
요약 범위 = 앱 시작 이후 기록 (렌트 시작 시각을 받을 수 있게 되면 교체).

1. `adb logcat -s GuidancePipeline VoiceGuide CarPropertySignal` → 시작 시 `PARKING_BRAKE_ON subscription: true`
2. 개발자 메뉴에서 `section_camera.json`, `right_turn.json` 재생 (시나리오가 기어를 D로 둠)
3. Virtual Console: Motion > Gear → **P**, 또는 개발자 메뉴 `return_brake.json` (주차 브레이크 ON 포함)
4. 기대
   - `VoiceGuide: [xx] trip_summary: ...`
   - 정차 화면에 `주행 안내 요약`: `단속 카메라 · 1`, `구간단속 · 3`, `우회전 일시정지 · 1` + 각 시각/위치 (시나리오엔 위치가 없어 `—`)
   - `school_zone.json` 뒤에 P → `어린이보호구역 · 1` + `37.5xx, 126.978`
5. 앱 재시작 후에도 DB는 남지만 요약은 새 세션 기록만 보여줌
6. 기록 직접 보기: `adb shell run-as ai.driverhelper ls databases` → `guidance.db`

## 전체 테스트
처음부터 끝까지 확인하는 순서는 [TESTING.md](TESTING.md) 참고.
