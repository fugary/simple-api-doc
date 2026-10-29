ALTER TABLE t_ai_cache ADD COLUMN config_id INT;
ALTER TABLE t_ai_cache ADD COLUMN share_record_id INT;
ALTER TABLE t_ai_cache ADD COLUMN execution_detail LONGTEXT;
CREATE INDEX idx_ai_cache_config_id ON t_ai_cache(config_id);
