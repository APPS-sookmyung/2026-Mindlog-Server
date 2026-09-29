# 2026-Mindlog-Server
사회적 행동 도움 서비스 &lt;MindLog> Backend Repository

## 로컬 실행 (Windows PowerShell)

Java 21과 실행 중인 Docker Desktop이 필요합니다.

```powershell
docker compose up -d --wait
$env:SPRING_PROFILES_ACTIVE = 'local'
.\gradlew.bat bootRun
```

서버는 기본적으로 `http://localhost:8080`에서 실행됩니다. 아직 인증 구현 전이므로
Spring Security 기본 설정에 의해 인증 없는 요청은 거절될 수 있습니다.

개발용 PostgreSQL 16은 `localhost:15432`를 사용합니다. DB 이름과 사용자는
`mindlog`, 비밀번호는 `mindlog-local-only`입니다. 이 공개된 개발용 계정은
로컬 전용이며 DB 포트는 `127.0.0.1`에만 열립니다. 데이터는 전용 Docker 볼륨에 보관됩니다.
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 환경변수가 있으면 로컬 기본값보다 우선합니다.
운영 환경에서는 `local` 프로필을 사용하지 않고 세 환경변수를 별도로 설정해야 합니다.

## 테스트

DB 연결이 필요한 애플리케이션 기동 테스트를 포함한 전체 테스트:

```powershell
docker compose up -d --wait
$env:SPRING_PROFILES_ACTIVE = 'local'
.\gradlew.bat test
```

현재 기동 테스트는 위 개발용 DB를 사용합니다. 데이터 변경을 검증하는 통합 테스트를
추가할 때는 기술 스택에 따라 Testcontainers로 별도 PostgreSQL을 사용합니다.

DB 없이 공통 예외 처리 테스트만 실행:

```powershell
.\gradlew.bat test --tests com.apps.mindlog.global.error.GlobalExceptionHandlerTest
```

DB 종료는 `docker compose stop`, 컨테이너 제거는 `docker compose down`입니다.
볼륨은 유지되므로 다시 실행하면 기존 데이터로 시작합니다.

## 비동기 작업과 매일 계산

JobRunr는 애플리케이션 PostgreSQL DataSource에 자체 실행 테이블을 생성합니다.
가상 스레드 워커는 기본 8개이며 `JOBRUNR_WORKER_COUNT`로 조정합니다.
대시보드는 기본 비활성화입니다. 내구성이 필요한 AI 작업은 `JobScheduler`로 등록하고,
`@Async`로 내구성·재시도를 대신하지 않습니다.

리포트 계산 서비스 구현 시 시작 단계에서
`schedulerConfig.scheduleDaily("report-daily", reportService::computeDaily)`를 호출합니다.
같은 ID의 재등록은 기존 일정을 갱신하며 서울 시간 매일 04:00에 실행합니다.
아직 리포트 서비스가 없으므로 빈 계산 작업은 등록하지 않습니다.
JobRunr 실행 메타데이터와 API의 ai_jobs/30분 임시 결과 보관 정책은 별도로 관리합니다.
실제 계산의 중복 방지와 재시도 정책은 후속 리포트 구현에서 처리합니다.
