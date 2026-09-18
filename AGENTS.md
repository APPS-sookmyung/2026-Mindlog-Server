# Mindlog 개발 기준

- 작업 시작 전에 해당 GitHub 이슈와 최신 Notion 명세를 읽는다. 스프린트 표의 번호와 GitHub 이슈 번호는 다르다.
- 변경은 이슈 범위로 제한한다. `feat/4-global`은 공통 예외 처리, `feat/5-before`는 팀원의 Before 작업이다.
- 기존 Java 21 / Spring Boot 설정과 `com.apps.mindlog` 패키지를 따른다. 빈 클래스가 있다고 구현 완료로 간주하지 않는다.
- Figma에 명시된 화면·입력·흐름을 우선한다. 화면에 없는 서버 규칙은 확정 API 정책을 사용하고 결정 대기를 임의로 확정하지 않는다.
- Before/After 원문은 300자, 최종 일기는 5000자. 사용자 인지 문자 수 정책은 별도 구현 시 함께 검증한다.
- After는 DRAFT 원문 선저장 후 비동기 분석·초안 생성, 마지막 저장에서 FINALIZED다. AI 성공만으로 완료 처리하지 않는다.
- AI API는 202 + jobId + 폴링. 작업 실패도 폴링 응답은 200 + FAILED다.
- 알림 닫기는 영구 제외 + 사용자 전체 600초 억제. 같은 알림을 재생성하거나 재노출하지 않는다.
- 오류는 RFC 9457 Problem Details. 경로의 타인 리소스는 404, 본문 참조 오류는 400. 도메인 오류로 403을 추가하지 않는다.
- 사용자 원문·비밀번호·토큰·DB 오류를 응답이나 로그에 출력하지 않는다.
- 수정에 맞는 테스트와 `git diff --check`를 수행한다. 외부 DB가 필요한 테스트는 실행 조건과 실패 이유를 구분해 보고한다.
- 커밋/PR은 저장소의 `.github` 템플릿을 따른다. 푸시·병합을 자동으로 수행하지 않는다.

## 기준 문서
- 기능명세서: https://app.notion.com/p/01467858ace1827990168186acfca8f9
- API 공통규약: https://app.notion.com/p/3df67858ace18066b3adc76dafc39d67
- API: https://app.notion.com/p/59067858ace18367a79f0128f53ae7b6
- ERD: https://app.notion.com/p/66767858ace18243bf2401e469206783
- 스프린트: https://app.notion.com/p/3df67858ace18083819add727dacd190
- 이슈: https://github.com/APPS-sookmyung/2026-Mindlog-Server/issues
