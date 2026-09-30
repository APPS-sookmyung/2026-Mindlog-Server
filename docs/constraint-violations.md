# DB 제약 위반과 API 오류

ConstraintViolationMapper는 PostgreSQL SQLSTATE 23505와 구조화된 제약 이름이 일치할 때만 변환합니다.
Hibernate 제약 예외, PostgreSQL 서버 메타데이터, Spring 원인 체인과 JDBC batch의 다음 예외를 지원합니다.
SQL·예외 메시지·입력값을 파싱하거나 응답에 넣지 않습니다. 순환 예외 체인은 한 번만 방문합니다.

| 제약 이름 | 409 오류 |
| --- | --- |
| ux_users_email_lower | email-already-exists |
| ux_after_logs_before_log_id | after-log-already-exists |
| ux_notifications_user_dedup | notification-already-scheduled |
| ux_ai_jobs_active_after_kind | analysis-in-progress |

V1의 이름과 동기화해야 합니다. 제약 이름을 바꾸면 반드시 매핑과 PostgreSQL 테스트도 변경합니다.
알 수 없는 UNIQUE 및 FK/CHECK/NOT NULL 오류는 내부 정보를 숨긴 500으로 반환합니다.
본문 참조 ID의 400과 경로 소유권의 404는 서비스가 먼저 검증합니다.

ux_af_ai_feedbacks_after_log_id는 최신 결과 행 하나를 유지하는 제약이며 재분석을 막는 잠금이 아닙니다.
따라서 이 제약을 analysis-in-progress로 변환하지 않습니다.
ux_ai_jobs_request는 같은 요청 키의 동일 입력 재생인지 다른 입력 충돌인지 DB 제약만으로 알 수 없어
공통 매퍼에서 idempotency-conflict로 단정하지 않습니다. 후속 멱등성 처리가 구분해야 합니다.

테스트는 단위·MVC 검증과 별도 PostgreSQL 16에서 실제 발생한 UNIQUE 메타데이터 검증을 포함합니다.
PostgreSQL 드라이버를 컴파일 의존성으로 두는 이유는 PSQLException의 구조화된 서버 정보를 읽기 위해서입니다.
