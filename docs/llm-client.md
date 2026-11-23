# LLM 연결과 호출 한도

## 제공자와 교체 경계

페이즈 3에서는 OpenAI Responses API를 사용한다. `OpenAiLlmTransport`가 RestClient로 HTTP 호출·구조화 출력 스키마 전달·응답 추출을 담당하며, `ValidatingLlmClient`와 기존 도메인 워커는 제공자에 의존하지 않는다. `RecordJsonSchema`는 결과 record DTO에서 공통 JSON Schema를 만든다. 모델 식별자는 `openai/<LLM_MODEL>`로 기록한다.

같은 제공자의 모델 변경은 `LLM_MODEL` 설정으로 처리한다. 다른 제공자로 바꿀 때는 해당 `LlmTransport`와 조건부 설정 빈을 추가하고 `ValidatingLlmClient(transport, validator, "제공자/모델")`를 연결한다. `LLM_PROVIDER`를 새 제공자로 바꾸면 OpenAI 빈이 등록되지 않는다. 워커·멱등 처리·한도 차감·도메인 검증은 재사용한다. 새 제공자의 API 형식이 다를 수 있으므로 URL만 바꾸는 방식으로 교체하지 않는다.

## 실행 설정

| 환경 변수 | 기본값 | 용도 |
| --- | --- | --- |
| `LLM_PROVIDER` | `none` | 실제 연결 시 `openai` |
| `OPENAI_API_KEY` | 없음 | 서버 환경에만 설정하는 키 |
| `LLM_MODEL` | 없음 | Responses의 strict JSON Schema를 지원하는 모델 ID |
| `LLM_MAX_REQUESTS_PER_HOUR` | `0` | 사용자별 시간당 작업 예약 한도 |
| `OPENAI_BASE_URL` | `https://api.openai.com/v1/` | HTTPS 기본 주소; 로컬 테스트만 loopback HTTP 허용 |
| `LLM_TIMEOUT` | `90s` | 호출 전체 제한, 요청별 timeout과 비교해 짧은 값 적용 |
| `LLM_CONNECT_TIMEOUT` | `10s` | 연결 제한 |
| `LLM_MAX_OUTPUT_TOKENS` | `4096` | 한 응답의 출력 토큰 상한 |
| `LLM_MAX_RESPONSE_BYTES` | `1000000` | JSON 파싱 전 HTTP 응답 크기 상한 |

`LLM_PROVIDER=openai`인데 키·모델이 없으면 기동을 실패시킨다. 제공자 선택은 확정했지만 운영 모델과 호출 한도는 아직 설정해야 한다. 테스트의 `test-model`은 실제 모델이 아니다. 키·일기 원문·제공자 오류 본문은 로그나 공개 실패 응답에 넣지 않는다. 요청에는 `store=false`, `stream=false`를 전달한다. `store=false`가 제공자의 모든 데이터 보관을 없앤다는 뜻은 아니다.

전체 호출 제한에는 헤더 대기와 본문 수신을 포함하며 AI 작업 임대 10분보다 짧다. 성공 응답도 상한보다 한 바이트 이상 읽지 않고 차단한다. 오류 본문은 읽어 반환하지 않는다. HTTP 자동 재시도는 하지 않는다. 공통 계층은 JSON 객체·중복 키·후속 JSON·알 수 없는 필드·Bean Validation을 검증한다. 원문 인용 여부나 허용 왜곡 유형 같은 의미 검증은 도메인 워커가 수행한다.

- 정상 응답의 잘못된 결과 JSON·도메인 검증 실패·모델 거절: `INVALID_OUTPUT`, 재시도 불가.
- 타임아웃·연결 오류·HTTP 401/429/5xx·잘못된 제공자 응답 형식·출력 토큰 한도로 미완료: `PROVIDER_UNAVAILABLE`, 재시도 가능. 401은 키 설정을 먼저 고쳐야 한다.
- 서버 사용자별 한도 초과: 기존 `RATE_LIMITED` 처리. 제공자 429와 별개다.

## 호출 한도

`mindlog.ai.max-requests-per-hour`는 사용자당 모든 AI 종류를 합친 시간당 예약 한도다. 사용자 요청은 등록할 때, 최종 저장 후 내부 인사이트는 실행할 때 예약한다. 운영값 미결정이므로 기본 0은 요청 차단이다. 승인된 값으로 설정하기 전 실제 AI 기능을 활성화하지 않는다. 정시 경계의 고정 시간창 정책은 구현 선택이며 제공자 자체 토큰/전역 요금 한도를 대체하지 않는다.

IdempotencyService의 최초 실행 callback 안에서 quota.consume를 호출한 뒤 JobStore.submit을 실행한다. 같은 트랜잭션이므로 제출 실패는 한도 차감도 롤백하며 같은 키 재전송은 다시 차감하지 않는다. 실제 호출 실패는 등록 한도를 환급하지 않는다. 사용자 요청 작업의 서버 중단 재실행은 새 사용자 요청이 아니므로 추가 차감하지 않는다. 내부 RECORD_INSIGHT는 최종 저장을 한도 실패로 되돌리지 않기 위해 시작 트랜잭션에서 매 실행 시도마다 차감한다. 정상 중복 실행은 claim되지 않아 추가 차감하지 않는다. 자세한 정책은 [인사이트 워커](record-insight.md)를 참고한다.

V5는 사용자별 현재 시간창 하나만 유지한다. 여러 서버의 동시 예약은 DB 조건부 갱신으로 보호한다. 계정 초기화는 이 행을 지우지 않아 한도 우회를 막고, 탈퇴는 FK CASCADE로 삭제한다. 현재 계정의 소유권·상태 검증과 잠금은 호출 서비스 책임이다. 429에는 다음 시간창까지 남은 초를 Retry-After로 반환한다.

## 검증

`./gradlew test`는 Testcontainers PostgreSQL 16과 로컬 HTTP 응답 서버를 사용한다. `AfterAiFlowTest`는 실제 RestClient 어댑터를 거쳐 분석 → 초안 → 최종 저장 → 인사이트를 실행한다. 같은 키로 동시에 분석을 요청해 동일한 202를 받고, 중복 실행·최종 저장 재전송 뒤에도 작업 3개 종류에 대한 한도 차감이 총 3회인지 확인한다. 인사이트 3종은 한 작업에서 한 번 저장한다.

실제 OpenAI 검증은 Docker가 실행 중인 환경에서 서버 환경 변수 `OPENAI_API_KEY`, `LLM_MODEL`을 설정한 뒤 아래 명령으로 명시적으로 실행한다. 테스트 전용 가상 기록을 사용하며, 호출 비용이 발생한다. 테스트 내부의 한도 20은 운영 권장값이 아니다.

```bash
MINDLOG_LIVE_LLM_TEST=true ./gradlew test --tests '*AfterAiFlowTest' --rerun-tasks
```

현재 실제 OpenAI 호출 결과는 미확인이다. 인증·Before는 테스트 어댑터로 검증하며 실제 서비스 연결은 별도 확인이 필요하다. 페이즈 3 집계 함수·DB 변경은 [인계 문서](phase3-handoff.md)를 참고한다.

구현 근거: [Responses API](https://developers.openai.com/api/reference/resources/responses/methods/create), [Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs).
