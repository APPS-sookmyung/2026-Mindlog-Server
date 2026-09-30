# 마스터 조회 API

`X-API-Version: 1`과 인증이 필요한 GET API이며 응답 목록은 `items`로 감싼다. 페이징 없이 displayOrder, id 순으로 반환한다.

- /api/situation-types: context=before(기본)/onboarding
- /api/body-symptoms: context=after(기본)/onboarding
- /api/emotion-characters, /api/anxiety-patterns, /api/distortion-tags
- /api/distortion-tags/{id}: 비활성 항목도 직접 조회 가능, 없는 ID는 404
- /api/positive-solutions: category는 명세의 한국어 태그

목록은 includeInactive=false가 기본이다. 잘못된 context/category는 400이다. 가이드·대처 카드·불안 패턴은 승인된 시드가 아직 없어 빈 목록을 반환한다. 테스트용 문구는 운영 시드에 포함하지 않는다.

JWT 구현은 팀원 작업 대기 중이며 통합 테스트는 Mock 인증을 사용한다. 실제 토큰 발급·인증 연동 완료를 의미하지 않는다.
