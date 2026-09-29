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
.\gradlew.bat test
```

Docker Desktop을 실행한 상태에서 테스트합니다. 기동 및 스키마 테스트는 Testcontainers로
별도의 PostgreSQL 16을 사용하므로 개발용 DB와 볼륨을 변경하지 않습니다.

DB 없이 공통 예외 처리 테스트만 실행:

```powershell
.\gradlew.bat test --tests com.apps.mindlog.global.error.GlobalExceptionHandlerTest
```

DB 종료는 `docker compose stop`, 컨테이너 제거는 `docker compose down`입니다.
볼륨은 유지되므로 다시 실행하면 기존 데이터로 시작합니다.

## 데이터베이스 마이그레이션

빈 DB에는 Flyway V1이 자동 적용되고 Hibernate가 매핑을 검증합니다.
기존 테이블에 Flyway 이력이 없으면 자동 baseline 없이 시작을 중단합니다.
기존 DB를 삭제하지 말고 [초기 스키마와 적용 정책](docs/database.md)을 확인하세요.
