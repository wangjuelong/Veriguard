-- Phase 11.5: 二开移除 Channel / Challenge / Article / Email 注入器附属表。
-- V1__Init.sql 是 baseline 历史 schema, 这里在新环境上把 model 已不再持有的表 drop 掉，
-- 让数据库与 model 保持一致。

-- inject_expectations 上残留的外键列（article_id / challenge_id）先解绑再删
ALTER TABLE IF EXISTS public.injects_expectations DROP COLUMN IF EXISTS article_id;
ALTER TABLE IF EXISTS public.injects_expectations DROP COLUMN IF EXISTS challenge_id;

-- 关联表与正表（CASCADE 让外键随表一起消失）
DROP TABLE IF EXISTS public.articles_documents CASCADE;
DROP TABLE IF EXISTS public.challenges_documents CASCADE;
DROP TABLE IF EXISTS public.challenges_tags CASCADE;
DROP TABLE IF EXISTS public.challenges_flags CASCADE;
DROP TABLE IF EXISTS public.challenge_attempts CASCADE;
DROP TABLE IF EXISTS public.articles CASCADE;
DROP TABLE IF EXISTS public.challenges CASCADE;
DROP TABLE IF EXISTS public.channels CASCADE;
