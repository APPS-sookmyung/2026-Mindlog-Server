# 페이즈 3 · AI 연결과 집계 함수

개발계획의 페이즈 3 범위다. OpenAI를 먼저 연결하되 제공자 교체는 `LlmTransport`와 설정 빈으로 한정했다. 사용법·실제 호출 검증 명령은 [LLM 연결](llm-client.md)에 있다. 리포트 HTTP API와 알림·푸시는 다음 페이즈다.

기준: [개발계획](https://app.notion.com/p/e5367858ace1839b839101befb91d69e), [기능명세서](https://app.notion.com/p/f3067858ace1826ea48781fb048bf958), [API 명세서](https://app.notion.com/p/dec67858ace182a3b3d301a6cda55f54).

## 홈·마이페이지에서 호출할 함수

세 빈은 호출자가 인증·온보딩 검사를 마친 **현재 사용자 ID**로 호출한다. 클라이언트의 임의 사용자 ID를 그대로 넘기지 않는다. 다른 담당 영역의 Repository를 주입하지 않고 사용자 ID로 제한한 읽기 SQL을 사용한다.

| 함수 | 반환형 | 기준 |
| --- | --- | --- |
| `RecordListQuery.recent(userId)` | `List<RecordSummary>` | Before 생성일 내림차순, 동률이면 ID 내림차순, 최대 3개 |
| `WeeklySummaryCalculator.calculate(userId)` | `Summary` | 사용자 설정 sun/mon, 서울 시간의 이번 주 |
| `StreakCalculator.calculate(userId)` | `Streak` | 서울 날짜로 오늘 또는 어제에서 이어지는 활동일 |

최근 기록은 Before 하나가 한 항목이다. After가 없거나 DRAFT이면 `IN_PROGRESS`, 실제 점수는 `null`이다. FINALIZED의 실제 0점은 `0`으로 반환한다. 기록이 없으면 빈 배열이다.

```json
[
  {"beforeLogId":23,"afterLogId":31,"recordStatus":"FINALIZED","title":"발표","situationTypeName":"발표","date":"2026-11-23","expectedScore":80,"actualScore":0},
  {"beforeLogId":22,"afterLogId":null,"recordStatus":"IN_PROGRESS","title":null,"situationTypeName":"발표","date":"2026-11-22","expectedScore":60,"actualScore":null}
]
```

주간 실제 평균·일별 막대·지난주 평균은 **FINALIZED After의 finalized_at**과 actual_score만 사용한다. 주 시작 00:00은 포함하고 다음 주 시작 00:00은 제외한다. 평균은 기록 전체의 합/건수로 계산하며 일별 평균을 다시 평균하지 않는다. Before 개수·예상 평균은 Before 생성일을 사용한다. 빈 집계는 `null`, 실제 0점은 `0`이다. 차이는 양쪽 평균을 각각 HALF_UP 정수 반올림한 뒤 뺀 값이다. 주간 조회는 하나의 REPEATABLE_READ 스냅샷을 사용한다.

월요일에 0점 한 건이 확정됐고 지난주 기록이 없는 예:

```json
{
  "periodStart":"2026-11-23","periodEnd":"2026-11-29","weekStartDay":"mon",
  "beforeLogCount":1,"afterLogCount":1,"averageExpectedScore":80,"averageActualScore":0,
  "dailyBars":[
    {"date":"2026-11-23","dayOfWeek":"MON","averageActualScore":0,"recordCount":1},
    {"date":"2026-11-24","dayOfWeek":"TUE","averageActualScore":null,"recordCount":0},
    {"date":"2026-11-25","dayOfWeek":"WED","averageActualScore":null,"recordCount":0},
    {"date":"2026-11-26","dayOfWeek":"THU","averageActualScore":null,"recordCount":0},
    {"date":"2026-11-27","dayOfWeek":"FRI","averageActualScore":null,"recordCount":0},
    {"date":"2026-11-28","dayOfWeek":"SAT","averageActualScore":null,"recordCount":0},
    {"date":"2026-11-29","dayOfWeek":"SUN","averageActualScore":null,"recordCount":0}
  ],
  "previousWeekAverageActualScore":null,"differencePoints":null,
  "trend":"NO_PREVIOUS","trendMessage":"지난 주보다 0점 상승했어요"
}
```

이번 주가 비면 `trend=NO_RECORD`, `trendMessage="불안한 상황이 없었어요"`다. 지난주만 비면 수치 차이는 `null`을 유지하고 명세에 따라 문구만 지난주 0점으로 계산한다. 차이가 0이면 `SAME`과 `지난 주보다 0점 상승했어요`를 반환한다.

연속 기록 응답 예: `{"current":3,"longest":5,"lastActiveDate":"2026-11-23"}`. 같은 날 여러 번 저장해도 하루로 세며, 오늘 기록이 없으면 어제부터 센다. 오늘·어제 모두 비면 current=0, 이력이 없으면 longest=0과 lastActiveDate=null이다. DRAFT도 활동일에는 포함된다.

## V10 · 활동 날짜 보존

기존 `updated_at`만으로는 여러 날의 임시 저장을 복원할 수 없다. V1~V9를 수정하지 않고 `record_activity_days(before_log_id, activity_date)`를 추가했다. Before 생성·내용 수정, After 생성·입력 변경·최종 저장을 DB 트리거로 같은 트랜잭션에서 기록한다. 비동기 피드백 저장과 시간만 바뀐 메타데이터 갱신은 새 활동일을 만들지 않는다. 날짜 PK로 중복을 막고 Before 삭제에 CASCADE해 초기화·탈퇴 이후 기록이 남지 않게 한다.

기존 DB에서는 Before/After의 created_at·updated_at·finalized_at에 남아 있는 날짜만 이관한다. 덮어써진 과거 수정 날짜는 복원할 수 없으므로 배포 전 과거 streak는 완전하지 않을 수 있다. 기존 updated_at의 사용자 저장 여부를 구분할 이력이 없어 해당 날짜는 활동으로 포함한다. 적용 이후에는 사용자 저장 날짜를 보존한다. [ERD](https://app.notion.com/p/63667858ace18267ab3e015c8614b4b5)에 V10 추가 구조와 이관 한계를 리뷰 중 상태로 기록했다.

## 확인 범위

2026-11-23 검증: Java 21 / Gradle 9.7.1 컴파일 통과. Docker가 없는 실행 환경에서 테스트 소스의 컨테이너 생성 부분만 임시 복사본에서 로컬 PostgreSQL 16.2로 연결해 **전체 339개 통과, 실패·생략 0개**를 확인했다. 테스트 단언과 운영 코드는 그대로 사용했으며 이 임시 실행 도구는 저장소에 반영하지 않았다. 저장소에는 기존 Testcontainers 실행 방식을 유지한다.

- 로컬 HTTP 서버로 실제 RestClient 전송, strict schema, 오류 분류, 헤더/본문 시간 초과, 응답 크기 상한을 검증한다.
- After 흐름은 테스트 계정·Before 어댑터를 사용한다. 같은 키 동시 분석 요청, 분석·초안 폴링, 최종 저장 재전송, 인사이트 중복 방지와 한도 차감을 확인한다.
- 집계는 실제 PostgreSQL에서 소유권 필터, 정렬, 자정/주 경계, 0/null, DRAFT 제외, 저장 날짜 보존, V9→V10 이관과 롤백을 확인한다.
- 이 작업 환경에는 Docker와 OpenAI 키·모델 설정이 없다. Docker 경로와 실제 OpenAI 호출은 실행하지 않았다. 기본 테스트를 실제 호출로 오인하지 않는다.
