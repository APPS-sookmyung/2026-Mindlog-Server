# After 생성 연결

POST /api/after-logs는 X-API-Version:1과 인증을 요구하며 beforeLogId/freeWriting/bodySymptomIds만 받는다. 201과 Location을 반환하고 AI는 호출하지 않는다. API 5-1은 Idempotency-Key 필수 대상으로 추가하지 않았으며 같은 Before의 재생성은 409다.

AccountAccess로 활성 사용자 계정을 잠근 뒤 BeforeAccess.lockOwned로 해당 Before를 잠근다. 없는/타인 Before는 본문 참조 오류 400이며 중복 조회보다 먼저 검사한다. 소유가 확인된 중복만 afterLogId 확장 필드를 포함한 409를 반환한다. 프리라이팅과 증상 연결은 같은 트랜잭션으로 저장한다.

BeforeAccess의 실제 구현은 팀원 Before 서비스에 위임해야 한다. 상황·예상 수치·제목 등은 읽기 전용 스냅샷이며 After에 복사 저장하지 않는다. User/Before 어댑터가 아직 없어 실제 인증과 연동은 대기 중이다. 테스트의 JDBC 어댑터는 src/test에만 존재한다. 미연결 운영 호출은 안전한 오류로 실패한다.

AfterAccess는 후속 조회/수정에서도 같은 소유권 규칙을 재사용하기 위한 연결 계층이다. 요청 DTO는 상황/최종점수/최종일기 등 허용하지 않은 필드를 400으로 거부한다. 기존 DB UNIQUE 변환은 마지막 동시성 방어로 유지한다.
