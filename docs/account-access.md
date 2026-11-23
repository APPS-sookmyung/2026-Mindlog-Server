# 계정 연결 계약과 온보딩 차단

팀원의 인증/User 구현은 아직 없으므로 `AccountAccess` 인터페이스만 제공한다. current()는 검증된 인증에서 활성 사용자를 조회하며 사용자 ID를 요청 본문이나 검증되지 않은 토큰에서 가져오지 않는다. lock(id)는 호출자의 기존 트랜잭션에서 활성 계정 행을 잠그고 현재 generation/온보딩/닉네임을 반환한다. 탈퇴·누락은 empty다. 구현은 User 서비스에 위임하고 다른 도메인 Repository를 직접 호출하지 않는다.

기록 변경 및 AI 결과 적용의 잠금 순서는 계정→Before/After 기록→AI 작업이다. 계정 초기화·탈퇴도 동일한 계정 잠금을 사용해야 한다. 인터셉터 검사 이후 상태가 바뀔 수 있어 변경 서비스는 트랜잭션에서 다시 lock 및 온보딩 검사를 한다.

OnboardingRequiredInterceptor는 /api/before-logs, /api/before-drafts, /api/after-logs 및 하위 경로에 적용한다. 미완료는 409 onboarding-required다. 마스터·온보딩 저장·인증 API는 이 인터셉터 대상이 아니다. 미인증은 Security에서 401이며 인터셉터 자체도 미인증을 거부한다.

AccountAccess 빈이 연결되지 않은 기록 핸들러는 안전한 500으로 실패하고 통과시키지 않는다. 이후 After 서비스/컨트롤러 활성화에는 실제 AccountAccess와 Before 연결이 필요하다. 테스트의 계정 어댑터는 운영용이 아니며 실제 JWT/User 연동 검증은 남아 있다.
