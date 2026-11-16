# 마스터 조회 API

모든 요청에 인증과 `X-API-Version: 1` 헤더가 필요하다. 목록은 `{"content":[...]}` 형태이며 `displayOrder`, `id` 순으로 반환한다. `includeInactive`의 기본값은 `false`다.

| GET 경로 | 조회 기준 |
| --- | --- |
| `/api/situation-types` | `context=before`(기본) 또는 `onboarding` |
| `/api/body-symptoms` | `context=after`(기본) 또는 `onboarding` |
| `/api/emotion-characters` | 감정 7종 |
| `/api/anxiety-patterns` | 예약 목록. 신규 DB에서 `content`는 빈 배열이다. |
| `/api/distortion-tags` | 가이드 12종의 이름·설명 |
| `/api/distortion-tags/{id}` | 정의·예시·질문·바꿔보기. 비활성 유형도 직접 조회할 수 있다. |
| `/api/positive-solutions` | 상황에 공통인 카드. 선택 사항인 `category`는 알아차리기·근거 확인·다르게 생각하기·몸 진정하기·회복력 중 하나다. |

지원하지 않는 `context`나 `category`는 400, 존재하지 않는 상세 ID는 404다. 시드 문구와 확인 중인 항목은 [마스터 시드](reference-seed.md)에서 확인할 수 있다.

## 조건부 재조회

1. 첫 GET의 200 응답에서 본문과 `ETag`를 같은 요청 URL에 함께 보관한다. 캐시 유효 기간은 1시간이다.
2. 재조회할 때 저장한 태그를 `If-None-Match`에 그대로 보낸다. 내용이 같으면 서버는 본문 없는 304를 반환한다. 프론트는 저장한 본문을 사용한다.
3. 응답 내용이 달라지면 새 본문과 태그를 담은 200을 반환한다. 화면·분류·비활성 항목 포함 여부가 다르면 해당 URL의 태그를 사용한다.

200과 304의 `Cache-Control`에는 `public`과 `max-age=3600`이 들어간다. 캐시는 `Vary`에 지정한 `Authorization`, `X-API-Version`, `Accept`도 구분한다. [공통 API 규칙](https://app.notion.com/p/2a167858ace183a480690145647a89e9)
