-- 添加 AI 缓存的详细追踪信息
ALTER TABLE t_ai_cache ADD COLUMN prompt TEXT;
ALTER TABLE t_ai_cache ADD COLUMN project_id VARCHAR(50);
ALTER TABLE t_ai_cache ADD COLUMN doc_id VARCHAR(50);
ALTER TABLE t_ai_cache ADD COLUMN error_message TEXT;
ALTER TABLE t_ai_cache ADD COLUMN user_name VARCHAR(50);
ALTER TABLE t_ai_cache ADD COLUMN cache_type VARCHAR(50);
ALTER TABLE t_ai_cache ADD COLUMN prompt_tokens INT;
ALTER TABLE t_ai_cache ADD COLUMN completion_tokens INT;
ALTER TABLE t_ai_cache ADD COLUMN total_tokens INT;
ALTER TABLE t_ai_cache ADD COLUMN raw_response TEXT;
ALTER TABLE t_ai_cache ADD COLUMN updated_at DATETIME;
ALTER TABLE t_ai_cache ADD COLUMN client_ip VARCHAR(50);
