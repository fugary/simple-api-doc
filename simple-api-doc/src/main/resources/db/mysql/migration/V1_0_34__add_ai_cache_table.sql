CREATE TABLE `t_ai_cache` (
  `cache_key` VARCHAR(64) NOT NULL COMMENT 'SHA256哈希主键',
  `cache_value` LONGTEXT NOT NULL COMMENT 'AI生成的JSON样本数据',
  `model_name` VARCHAR(50) DEFAULT NULL COMMENT '使用的AI模型名称',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`cache_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI生成数据缓存表';
