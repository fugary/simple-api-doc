CREATE TABLE `t_ai_config` (
  `id` int NOT NULL AUTO_INCREMENT,
  `config_name` varchar(100) NOT NULL COMMENT '配置名称',
  `base_url` varchar(255) NOT NULL COMMENT '接口地址',
  `api_key` varchar(255) COMMENT '认证Token',
  `default_model` varchar(100) NOT NULL COMMENT '默认模型',
  `provider` varchar(50) DEFAULT 'OPENAI' COMMENT '提供商标识',
  `status` int DEFAULT 1 COMMENT '状态：1启用，0禁用',
  `is_default` int DEFAULT 0 COMMENT '是否默认',
  `config_version` int DEFAULT 1 COMMENT '版本号，每次修改递增',
  `modify_from` int DEFAULT NULL COMMENT '历史版本对应的原记录ID',
  `creator` varchar(32) COMMENT '创建人',
  `create_date` datetime DEFAULT NULL COMMENT '创建时间',
  `modifier` varchar(32) COMMENT '修改人',
  `modify_date` datetime DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idx_ai_config_modify_from` (`modify_from`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI服务配置表';
