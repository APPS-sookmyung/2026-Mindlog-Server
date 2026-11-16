-- Verified copy: final feature spec §6.3, API §8-6 and design frames 6-3 and 5-6.
-- Match names on upgrade so that older After records keep their FK targets.
LOCK TABLE distortion_tags, positive_solutions IN SHARE ROW EXCLUSIVE MODE;

-- Explicit historical IDs may be ahead of the identity sequence. Never rewind a sequence.
SELECT setval('distortion_tags_id_seq', max(id), true)
FROM distortion_tags HAVING max(id) >= (SELECT last_value FROM distortion_tags_id_seq);

-- The published first common card has ID 7 on a new catalog.
SELECT setval('positive_solutions_id_seq', greatest(coalesce(max(id), 0), 6), true)
FROM positive_solutions
HAVING greatest(coalesce(max(id), 0), 6) >= (SELECT last_value FROM positive_solutions_id_seq);

WITH guide(display_order, name, definition, thought, question, reframe, illustration) AS (VALUES
    (1, '개인화', '자신이 통제할 수 없었던 내 능력 밖의 일까지 전부 내 탓으로 돌리는 것이에요.', '상대의 기분이 안 좋아 보이면, 내가 뭔가 잘못해서 그런 거라고 넘겨짚을 때가 있어요.', '그렇게 생각할 만한 근거가 있는지, 내가 모르는 다른 이유는 없는지 짚어보세요.', '상대의 기분에는 내가 알지 못하는 다른 이유가 있을 수 있다고 다시 생각해보면 좋아요.', NULL),
    (2, '과잉 일반화', '한두 번의 실패나 거절을 근거로 ‘나는 항상 안 된다’처럼 전체에 대한 결론을 내리는 것을 말해요.', '한 번의 실수를 겪고 나면, 나는 항상 이런 식이라고 단정 짓게 될 때가 있어요.', '''항상'', ''절대''라는 말을 쓰고 있는 건 아닌지, 그렇지 않았던 순간은 없었는지 최근 경험을 떠올려 확인해봐요.', '이번엔 실수했지만, 다음에도 꼭 그렇다는 뜻은 아니라고 다시 적어보면 좋아요.', NULL),
    (3, '긍정적 측면 묵살', '자신의 성취나 칭찬을 운이 좋거나 별거 아니라며 깎아내리는 것을 말해요.', '좋은 결과를 얻고도 그냥 운이 좋았을 뿐이라고 넘겨버릴 때가 있어요.', '내가 직접 노력한 부분은 무엇인지, 같은 상황에서 친구가 해냈다면 뭐라고 말해줄지 생각해봐요.', '운도 따랐지만, 내가 노력한 부분도 결과에 영향을 줬다고 인정해 보면 좋아요.', NULL),
    (4, '낙인찍기', '작은 실수 하나로 자신이나 타인 전체에 부정적 딱지를 붙이는 것이에요.', '한 번의 실수로 나는 원래 이런 사람이라고 스스로에게 딱지를 붙이게 될 때가 있어요.', '이번 행동과 나라는 사람 전체를 같은 걸로 보고 있는 건 아닌지 확인해 볼까요?', '이번엔 아쉬운 부분이 있었을 뿐, 그게 나 전체를 말해주는 건 아니라고 적어보면 좋아요.', NULL),
    (5, '당위적 사고', '‘해야만 한다’식의 사고로, 자신에게 비현실적인 규칙을 강요하는 것을 말해요.', '언제나 완벽하게 해내야 하고, 실수는 절대 안 된다고 스스로를 몰아붙일 때가 있어요.', '''꼭'', ''반드시''라는 기준이 현실적으로 지킬 수 있는 것인지, ''해야 해''를 ''하면 좋겠어''로 바꿔볼 수 있을지 생각해보면 좋아요.', '잘하고 싶은 마음은 맞지만, 실수해도 다시 해볼 수 있다고 다시 적어볼까요?', NULL),
    (6, '마음 읽기', '아무 근거도 없이 타인의 생각을 내 멋대로 단정하는 것을 말해요.', '답장이 늦는다는 이유만으로, 상대가 나를 싫어한다고 확신하게 될 때가 있어요.', '지금 확실히 알고 있는 사실이 무엇인지, 다른 이유가 있을 가능성은 없는지 사실과 추측을 나눠볼까요?', '답장이 늦는 이유는 아직 모르고, 그저 바빴을 수도 있다고 다시 생각해보면 좋아요.', 'distortion-mind-reading'),
    (7, '비난하기', '문제의 원인을 오직 자신 또는 상대방에게만 겨눠 균형을 잃는 것을 말해요.', '일이 잘 풀리지 않으면, 그 책임을 전부 나 또는 상대에게만 몰아서 돌릴 때가 있어요.', '내 몫과 상대 또는 상황의 몫을 나눠서 살펴보고, 지금 할 수 있는 다음 행동이 무엇인지 생각해봐요.', '여러 요인이 겹쳐서 일어난 일이고, 다음엔 이 부분을 조정해보면 된다고 적어보면 좋아요.', NULL),
    (8, '예언적 사고', '충분한 근거 없이 미래를 부정적인 방향으로 단정하는 것을 말해요.', '아직 결과가 나오지 않았는데도, 이번에도 분명 안 될 거라고 미리 확신하게 될 때가 있어요.', '이렇게 확신할 만한 근거가 있는지, 확률로 따지면 몇 퍼센트쯤일지 스스로 가늠해보면 좋아요.', '결과는 아직 정해지지 않았고, 준비한 만큼 해보고 확인하면 된다고 적어보면 좋아요.', NULL),
    (9, '정서적 추론', '나의 주관적인 감정을 객관적인 사실로 착각해 받아들이는 것을 말해요.', '불안한 감정이 든다는 이유만으로, 이 일이 분명 잘못될 거라고 느낄 때가 있어요.', '지금 드는 느낌과 실제로 확인 가능한 사실을 나눠서 살펴보면 좋아요.', '불안한 감정이 든다고 해서 그게 곧 나쁜 결과를 뜻하는 건 아니라고 적어보세요.', NULL),
    (10, '정신적 여과', '하루 중 좋았던 일은 무시하고 딱 한 가지 나빴던 일에만 필터를 걸어 집중하는 것이에요.', '칭찬을 받았어도 지적 하나가 계속 마음에 남아, 잘한 게 없다고 느껴질 때가 있어요.', '지금 놓치고 있는 사실은 없는지, 잘된 부분까지 함께 본다면 어떨지 스스로 짚어볼까요?', '아쉬운 점이 있었지만, 그게 잘한 부분까지 지우는 건 아니라고 정리해봐요.', NULL),
    (11, '파국화', '작은 일이나 불확실한 일을 감당할 수 없는 최악의 결과로 부풀려 생각하는 것을 말해요.', NULL, NULL, NULL, NULL),
    (12, '흑백 논리', '상황을 오직 성공과 실패, 좋음과 나쁨 둘 중 하나로만 보고, 중간 지대를 인정하지 않는 것을 말해요.', '완벽하게 해내지 못했다는 이유로, 그 일 전체를 실패라고 느낄 때가 있어요.', '정말 성공과 실패 두 가지뿐인지, 조금이라도 잘한 부분은 없었는지, 100점 만점이라면 몇 점쯤일까요?', '완벽하진 않았지만 해낸 부분도 분명히 있었다고 생각할 수 있도록 노력해요.', NULL)
)
INSERT INTO distortion_tags AS existing
    (display_order, name, definition, example_thoughts, self_questions, reframes, illustration_key,
     active, created_at, updated_at)
SELECT display_order, name, definition,
       to_jsonb(array_remove(ARRAY[thought], NULL)), to_jsonb(array_remove(ARRAY[question], NULL)),
       to_jsonb(array_remove(ARRAY[reframe], NULL)), illustration,
       true, timestamptz '2026-11-16 00:00:00+09', timestamptz '2026-11-16 00:00:00+09'
FROM guide ORDER BY display_order
ON CONFLICT (name) DO UPDATE SET
    display_order = excluded.display_order, definition = excluded.definition,
    example_thoughts = CASE WHEN excluded.example_thoughts = '[]' THEN existing.example_thoughts ELSE excluded.example_thoughts END,
    self_questions = CASE WHEN excluded.self_questions = '[]' THEN existing.self_questions ELSE excluded.self_questions END,
    reframes = CASE WHEN excluded.reframes = '[]' THEN existing.reframes ELSE excluded.reframes END,
    illustration_key = coalesce(excluded.illustration_key, existing.illustration_key),
    updated_at = greatest(existing.updated_at, excluded.updated_at);

CREATE TEMP TABLE phase2_common_cards ON COMMIT DROP AS
SELECT * FROM (VALUES
    (1, 'resilience', '회복력', '다시 일어서는 힘', '잠깐 흔들려도 금방 돌아올 수 있어요'),
    (2, 'resilience', '회복력', '조용한 명상', '떠오르는 부정적인 생각을 없애려 하기보다, 호흡에 집중하며 잠시 바라보세요. 생각과 거리를 두면 불안에 휩쓸리지 않고 마음을 천천히 가라앉힐 수 있어요.'),
    (3, 'resilience', '회복력', '충분한 휴식과 숙면', '잠들기 전 조명과 자극을 줄이고, 일정한 시간에 몸을 편안하게 쉬게 해주세요. 충분한 휴식은 긴장된 몸과 마음을 회복시키고 불안한 생각을 줄이는 데 도움을 줘요.')
) AS card(display_order, category_code, category_name, title, description);

UPDATE positive_solutions s SET category_name=c.category_name, solution_content=c.description,
    display_order=c.display_order, updated_at=greatest(s.updated_at, timestamptz '2026-11-16 00:00:00+09')
FROM phase2_common_cards c
WHERE s.situation_type_id IS NULL AND s.category_code=c.category_code AND s.title=c.title;

INSERT INTO positive_solutions
    (display_order, category_code, category_name, title, solution_content, active, created_at, updated_at)
SELECT c.display_order, c.category_code, c.category_name, c.title, c.description, true,
       timestamptz '2026-11-16 00:00:00+09', timestamptz '2026-11-16 00:00:00+09'
FROM phase2_common_cards c
WHERE NOT EXISTS (SELECT 1 FROM positive_solutions s WHERE s.situation_type_id IS NULL
                  AND s.category_code=c.category_code AND s.title=c.title)
ORDER BY c.display_order;
