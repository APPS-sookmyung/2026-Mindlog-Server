-- Preserve historical IDs. The default guide list and the AI worker both read active rows.
LOCK TABLE distortion_tags, positive_solutions IN SHARE ROW EXCLUSIVE MODE;

WITH guide_visibility AS (
    SELECT id, name IN (
        '개인화', '과잉 일반화', '긍정적 측면 묵살', '낙인찍기', '당위적 사고', '마음 읽기',
        '비난하기', '예언적 사고', '정서적 추론', '정신적 여과', '파국화', '흑백 논리'
    ) AS should_be_active
    FROM distortion_tags
)
UPDATE distortion_tags AS tag
SET active = guide.should_be_active, updated_at = greatest(tag.updated_at, CURRENT_TIMESTAMP)
FROM guide_visibility AS guide
WHERE tag.id = guide.id AND tag.active <> guide.should_be_active;

-- V5 allowed duplicate cards. Keep one visible ID for each reviewed common card,
-- preferring a previously visible row, then the lowest ID.
WITH card_visibility AS (
    SELECT id, row_number() OVER (PARTITION BY title ORDER BY active DESC, id) = 1 AS should_be_active
    FROM positive_solutions
    WHERE situation_type_id IS NULL AND category_code = 'resilience'
      AND title IN ('다시 일어서는 힘', '조용한 명상', '충분한 휴식과 숙면')
)
UPDATE positive_solutions AS solution
SET active = card.should_be_active, updated_at = greatest(solution.updated_at, CURRENT_TIMESTAMP)
FROM card_visibility AS card
WHERE solution.id = card.id AND solution.active <> card.should_be_active;
