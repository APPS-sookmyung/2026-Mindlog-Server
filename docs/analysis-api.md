# After 분석 요청과 폴링

POST /api/after-logs/{id}/analyses는 Idempotency-Key와 정수 inputVersion만 받는다. 서버 원문·Before·증상 스냅샷으로 작업을 등록하고 202/Retry-After:2를 반환한다. 사용자 원문을 별도로 받지 않는다.

소유권 확인 뒤 동일 키 응답을 재생한다. 최초 요청에만 DRAFT/현재 버전/같은 종류 실행 중 여부와 호출 한도를 검사한다. 현재 피드백 행이 있다는 이유로 재분석을 막지 않는다. 등록 실패는 피드백 PENDING·작업·한도 차감·멱등 예약을 함께 롤백한다.

GET /api/after-logs/{id}/analyses/{jobId}는 본인 경로·작업 종류·대상·generation을 확인하고 실패도 200/FAILED로 반환한다. 완료 외 result=null이고 FAILED의 failure에는 안전한 이유와 재시도 가능 여부만 있다. 내부 입력 스냅샷은 노출하지 않는다.

현재 PR은 요청/조회이며 생성 워커는 후속 이슈에서 연결한다. 실제 인증/User/Before와 LLM 제공자는 대기 중이다. 운영 한도 기본 0은 차단이며 테스트에서만 명시한 한도로 실행했다. 연결 완료 전 실제 AI 생성 완료를 의미하지 않는다.
