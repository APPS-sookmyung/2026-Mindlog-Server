-- The design duplicated another guide's copy; these three lines were confirmed on 2026-11-16.
UPDATE distortion_tags SET
    example_thoughts = jsonb_build_array('발표에서 한 번 말이 막히면 발표를 전부 망칠 거야.'),
    self_questions = jsonb_build_array('잠깐 말이 막히면 실제로 어떤 일이 생길 수 있을까요?'),
    reframes = jsonb_build_array('잠깐 멈추더라도 메모를 보고 다음 문장부터 이어갈 수 있어요.'),
    updated_at = greatest(updated_at, timestamptz '2026-11-16 00:00:00+09')
WHERE name = '파국화';
