# After 수정과 무효화

PATCH /api/after-logs/{id}는 inputVersion을 요구하고 freeWriting/bodySymptomIds는 전달된 항목만 변경한다. 명시적인 null·빈 원문/증상·중복/허용되지 않은 증상·상황/최종문 등 금지 필드는 400이다. 원문은 공백 정리 후, 증상은 ID 집합으로 비교한다. 같은 입력과 순서만 바뀐 증상은 버전을 올리지 않는다.

계정→Before→After 순으로 잠근 뒤 DRAFT와 현재 버전을 확인한다. 현재 실행 중인 after_analysis 작업이 있으면 409 analysis-in-progress다. 실제 변화가 있으면 inputVersion을 한 번 올리고 피드백을 INVALIDATED로 바꾸며 요약·완료 시각·스냅샷·기존 근거·분석 카드·기록 인사이트를 지운다.

이전 버전 ai_jobs는 FAILED/STALE_INPUT으로 전환하고 임시 입력/결과와 시도 토큰을 제거한다. 이미 실행 중인 일기 초안이 늦게 도착해도 토큰과 버전 검증으로 반영하지 못한다. 수정/무효화는 같은 트랜잭션이므로 실패 시 원문과 이전 결과를 부분 변경하지 않는다.

실제 인증/User/Before 연결은 대기 중이며 통합 테스트는 테스트 전용 어댑터를 사용한다.
