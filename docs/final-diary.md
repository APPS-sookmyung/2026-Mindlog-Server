# 최종 일기 확정

PUT /api/after-logs/{id}/final-diary는 Idempotency-Key와 inputVersion/finalDiary/actualScore를 요구한다. 최종문은 보이는 글자 1~5000자, 실제 수치는 정수 0~100이며 입력 버전도 정수여야 한다.

계정→Before→After를 잠근 후 소유권을 확인하고 멱등 응답을 조회한다. 같은 키/같은 입력은 최초 응답 그대로, 같은 키/다른 입력은 409 idempotency-conflict다. 새로운 키로 이미 확정된 기록을 다시 저장하면 409 final-diary-already-confirmed다.

현재 버전 분석 COMPLETED와 동일 사용자/generation/After/버전의 일기 초안 COMPLETED 메타데이터가 필요하다. 임시 초안 본문이 30분 뒤 정리됐어도 사용자가 확보한 최종문은 저장할 수 있다. 미준비는 409 analysis-not-ready, 오래된 버전은 409 stale-input-version이다.

최종 값/FINALIZED/확정 시각, 해당 사용자의 Before에 대한 미해소 After 알림 취소, record_insight 작업 등록, 멱등 응답 저장은 하나의 트랜잭션이다. 이미 영구 닫힌 알림은 유지한다. 외부 AI를 호출하지 않으며 작업 발송기는 커밋 뒤 등록 행을 본다. 실패하면 DRAFT/알림/멱등 응답을 모두 롤백한다.

비교 수치는 actualScore-expectedScore이며 DOWN/UP/SAME과 절댓값을 제공한다. 예시 점수를 고정하지 않는다. Before/After 코멘트는 기존 저장값을 반환하며 값이 없으면 null이다. 코멘트를 예시 문구로 채우지 않는다.

실제 인증/User/Before 및 LLM 연결은 대기 중이다. 인사이트 실행은 후속 워커에서 구현하며 이 이슈에서는 PENDING 작업까지 원자적으로 등록한다. 알림 서비스는 최종 저장에 필요한 취소만 구현하고 예약·목록·푸시는 이후 스프린트 범위다.
