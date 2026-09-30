# 일기 초안

이슈 #60. `POST /api/after-logs/{id}/diary-drafts`는 필수 Idempotency-Key와 `{inputVersion}`을 받으며 202, Retry-After: 2, jobId/afterLogId/status/inputVersion을 반환한다. 같은 키/본문의 재전송은 원래 응답을 재사용하여 쿼터를 추가 차감하지 않는다.

소유자 DRAFT, 현재 버전, COMPLETED 분석을 확인한다. 같은 초안 작업이 진행 중인 다른 키 요청은 기존 `analysis-in-progress` 409를 사용한다. 이 중복 방어는 API 표의 공통 오류에 추가한 정책이다. 서버에 저장된 Before/원문/분석으로만 생성한다.

`GET /api/after-logs/{id}/diary-drafts/{jobId}`는 `draft.content`를 반환한다. 출력은 공백 제거 후 보이는 글자 1~5000자이며 완료 후 30분만 보관한다. 만료된 본문은 지워도 완료 메타데이터는 최종 저장 검증을 위해 유지한다. 초안 생성 자체는 finalDiary, actualScore, FINALIZED 상태를 변경하지 않는다.

완료 직전 계정 generation, 소유권, 입력 버전, DRAFT와 현재 분석 상태를 다시 확인한다. 원문 변경은 기존 작업을 STALE_INPUT으로 무효화한다. 제공자 오류/잘못된 출력은 안전한 FAILED 상태로 폴링된다.

실제 JWT/User/Before/LLM 어댑터 연결은 미완료이며 테스트 대체 구현만 사용한다. 운영 모델·쿼터 설정 전 실제 모델 호출은 실행하지 않는다.
