# After 조회

GET /api/after-logs는 본인 기록의 DRAFT와 FINALIZED를 함께 반환한다. 기본 page=0/size=20, 최대 size=100이며 page<0/size<1은 400이다. 생성 시각 내림차순, 같은 시각은 ID 내림차순으로 정렬한다. from/to는 서울 시간 After 생성일 기준 양 끝 날짜를 포함한다. 잘못된 형식·역전 범위·없는 상황 ID는 400이다.

목록은 After 소유 읽기 모델에서 Before/상황/최신 피드백을 조인하여 건수와 페이지를 조회한다. 다른 도메인 Repository를 호출하지 않으며 사용자 ID 조건을 반드시 포함한다. 상세는 BeforeAccess의 소유 스냅샷을 사용한다. 타인/없는 After는 동일한 404다.

기존 기록의 상황·증상이 비활성화되어도 표시와 필터는 유지한다. 새 선택의 활성 여부 검증과 과거 표시를 구분한다. DRAFT 실제 점수와 점수 차이는 null이며, FINALIZED의 scoreDiff는 actualScore-expectedScore를 읽을 때 계산한다. 원문과 최종 일기를 둘 다 반환하며 분석 본문은 별도 API다. 현재 버전과 다른 피드백은 INVALIDATED다.

실제 인증/User/Before 서비스 어댑터 연결은 팀원 구현 대기다. API 테스트는 테스트 전용 계정/Before 어댑터를 사용했다.
