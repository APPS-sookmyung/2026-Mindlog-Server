# 마스터 참조 검증

다른 도메인은 reference.service.ReferenceValidator를 호출합니다. 마스터 Repository를 직접 가져가지 않습니다.

- requireEmotion(id), requireEmotionCode(code): 존재하는 활성 감정만 허용합니다.
- requireSituation(id, SituationContext), requireSituations(ids, SituationContext): 온보딩/Before 허용 여부를 확인합니다.
- requireSymptoms(ids, SymptomContext): 온보딩/After 허용 여부를 확인합니다. 회피는 After에서 거절합니다.
- requireDistortionTag(id): 활성 왜곡 유형을 확인합니다.

목록은 null·빈 값·null ID·중복·없는 ID·비활성·잘못된 화면 선택을 모두 400 validation-error로 처리합니다.
반환 목록은 displayOrder, id 순서이며 변경할 수 없습니다. 데이터 변경은 하지 않습니다.
경로 리소스의 소유권 404는 OwnershipGuard의 영역이며 이 서비스와 혼용하지 않습니다.
상황 목록·증상 없음 선택지는 명세의 현재 제안값을 따릅니다(TODO 기능명세서 2.7, 2.8, 4.1).
