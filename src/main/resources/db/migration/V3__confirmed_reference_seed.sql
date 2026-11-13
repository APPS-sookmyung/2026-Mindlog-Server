-- Confirmed choices from feature specification 2.5, 2.7, 2.8, 4.1, 4.8 and API section 8.
-- Timestamps identify this catalog revision; they are not runtime current-time values.
-- TODO 기능명세서 6.3, 5.7, 6.7: reviewed distortion guides, coping cards and anxiety patterns remain unseeded.

INSERT INTO emotion_characters(id,code,name,display_order,active,created_at,updated_at) VALUES
    (1,'anger','화남',1,true,'2026-09-30T00:00:00+09:00'::timestamptz,'2026-09-30T00:00:00+09:00'::timestamptz),
    (2,'neutral','보통',2,true,'2026-09-30T00:00:00+09:00'::timestamptz,'2026-09-30T00:00:00+09:00'::timestamptz),
    (3,'happiness','행복',3,true,'2026-09-30T00:00:00+09:00'::timestamptz,'2026-09-30T00:00:00+09:00'::timestamptz),
    (4,'sadness','슬픔',4,true,'2026-09-30T00:00:00+09:00'::timestamptz,'2026-09-30T00:00:00+09:00'::timestamptz),
    (5,'surprise','놀람',5,true,'2026-09-30T00:00:00+09:00'::timestamptz,'2026-09-30T00:00:00+09:00'::timestamptz),
    (6,'embarrassment','창피함',6,true,'2026-09-30T00:00:00+09:00'::timestamptz,'2026-09-30T00:00:00+09:00'::timestamptz),
    (7,'stress','스트레스',7,true,'2026-09-30T00:00:00+09:00'::timestamptz,'2026-09-30T00:00:00+09:00'::timestamptz);

INSERT INTO situation_types(id,name,onboarding_selectable,before_selectable,display_order,active,created_at,updated_at) VALUES
    (1,'발표',true,true,1,true,'2026-09-30T00:00:00+09:00'::timestamptz,'2026-09-30T00:00:00+09:00'::timestamptz),
    (2,'팀플',true,false,2,true,'2026-09-30T00:00:00+09:00'::timestamptz,'2026-09-30T00:00:00+09:00'::timestamptz),
    (3,'시험',true,true,3,true,'2026-09-30T00:00:00+09:00'::timestamptz,'2026-09-30T00:00:00+09:00'::timestamptz),
    (4,'교수님 면담',true,false,4,true,'2026-09-30T00:00:00+09:00'::timestamptz,'2026-09-30T00:00:00+09:00'::timestamptz),
    (5,'인간관계',true,true,5,true,'2026-09-30T00:00:00+09:00'::timestamptz,'2026-09-30T00:00:00+09:00'::timestamptz),
    (6,'건강',true,true,6,true,'2026-09-30T00:00:00+09:00'::timestamptz,'2026-09-30T00:00:00+09:00'::timestamptz),
    (7,'업무',true,true,7,true,'2026-09-30T00:00:00+09:00'::timestamptz,'2026-09-30T00:00:00+09:00'::timestamptz),
    (8,'돈',true,true,8,true,'2026-09-30T00:00:00+09:00'::timestamptz,'2026-09-30T00:00:00+09:00'::timestamptz),
    (9,'미래·진로',true,true,9,true,'2026-09-30T00:00:00+09:00'::timestamptz,'2026-09-30T00:00:00+09:00'::timestamptz),
    (10,'기타',true,true,10,true,'2026-09-30T00:00:00+09:00'::timestamptz,'2026-09-30T00:00:00+09:00'::timestamptz);

INSERT INTO body_symptoms(id,name,description,symptom_type,onboarding_selectable,after_selectable,display_order,active,created_at,updated_at) VALUES
    (1,'손 떨림',NULL,'physical',true,true,1,true,'2026-09-30T00:00:00+09:00'::timestamptz,'2026-09-30T00:00:00+09:00'::timestamptz),
    (2,'식은땀',NULL,'physical',true,true,2,true,'2026-09-30T00:00:00+09:00'::timestamptz,'2026-09-30T00:00:00+09:00'::timestamptz),
    (3,'어지러움',NULL,'physical',true,true,3,true,'2026-09-30T00:00:00+09:00'::timestamptz,'2026-09-30T00:00:00+09:00'::timestamptz),
    (4,'복통',NULL,'physical',true,true,4,true,'2026-09-30T00:00:00+09:00'::timestamptz,'2026-09-30T00:00:00+09:00'::timestamptz),
    (5,'긴장',NULL,'physical',true,true,5,true,'2026-09-30T00:00:00+09:00'::timestamptz,'2026-09-30T00:00:00+09:00'::timestamptz),
    (6,'목소리 떨림',NULL,'physical',true,true,6,true,'2026-09-30T00:00:00+09:00'::timestamptz,'2026-09-30T00:00:00+09:00'::timestamptz),
    (7,'심박수 증가',NULL,'physical',true,true,7,true,'2026-09-30T00:00:00+09:00'::timestamptz,'2026-09-30T00:00:00+09:00'::timestamptz),
    (8,'회피',NULL,'behavioral',true,false,8,true,'2026-09-30T00:00:00+09:00'::timestamptz,'2026-09-30T00:00:00+09:00'::timestamptz),
    (9,'기타',NULL,'other',true,true,9,true,'2026-09-30T00:00:00+09:00'::timestamptz,'2026-09-30T00:00:00+09:00'::timestamptz);

SELECT setval(pg_get_serial_sequence('emotion_characters','id'), (SELECT max(id) FROM emotion_characters), true);
SELECT setval(pg_get_serial_sequence('situation_types','id'), (SELECT max(id) FROM situation_types), true);
SELECT setval(pg_get_serial_sequence('body_symptoms','id'), (SELECT max(id) FROM body_symptoms), true);
