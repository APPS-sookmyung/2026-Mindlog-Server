# 경로 리소스 소유권 검증

OwnershipGuard는 호출한 도메인 서비스가 조회한 리소스의 소유자를 확인합니다.
리소스가 없거나, 소유자가 다르거나, 사용자/소유자 ID가 null이면 동일한 RESOURCE_NOT_FOUND를 발생시킵니다.
원본 ID나 실제 소유자를 오류 설명에 넣지 않습니다. 숫자 객체의 참조가 아닌 값으로 비교합니다.

```java
var record = OwnershipGuard.requireOwned(repository.findById(id), userId, Record::getUserId);
OwnershipGuard.requireOwner(userId, record.getUserId());
```

인증은 앞단 Security가 수행합니다. 이 도구는 JWT 검증이나 인증 사용자 조회를 대신하지 않습니다.
본문 속 참조 ID가 없거나 허용되지 않은 경우의 400 검증에는 사용하지 않습니다.
다른 도메인의 기록은 그 도메인의 서비스를 통해 확인해야 하며 Repository를 직접 호출하지 않습니다.
변경 작업에서는 소유권 확인과 저장 사이에 데이터가 변경되지 않도록 해당 서비스의 트랜잭션·잠금 정책을 적용합니다.
