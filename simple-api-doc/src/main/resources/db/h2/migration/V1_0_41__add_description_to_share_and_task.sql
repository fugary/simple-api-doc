ALTER TABLE t_api_project_share ADD COLUMN description VARCHAR(500) DEFAULT NULL COMMENT '描述';
ALTER TABLE t_api_project_task ADD COLUMN description VARCHAR(500) DEFAULT NULL COMMENT '描述';
