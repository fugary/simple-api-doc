CREATE TABLE t_ai_cache (
  cache_key VARCHAR(64) NOT NULL,
  cache_value CLOB NOT NULL,
  model_name VARCHAR(50) DEFAULT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (cache_key)
);
