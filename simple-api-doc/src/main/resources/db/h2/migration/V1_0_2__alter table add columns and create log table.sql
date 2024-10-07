ALTER TABLE t_api_doc_schema
    ADD COLUMN status_code int; -- http状态码
ALTER TABLE t_api_doc_schema
    ADD COLUMN examples text; -- 示例数据
ALTER TABLE t_api_project_share
    ADD COLUMN default_theme varchar(255); -- 默认主题
ALTER TABLE t_api_project_share
    ADD COLUMN default_show_label varchar(255); -- label展示
CREATE TABLE t_api_log  -- 日志表
(
    id          int,
    user_name   varchar(255),
    project_id  int,
    log_name    varchar(8192),
    data_id     int,
    log_message text,
    log_type    varchar(255),
    task_type   varchar(255),
    log_result  varchar(255),
    log_data    text,
    exceptions  text,
    extend1     varchar(255),
    extend2     varchar(255),
    creator     varchar(255),
    create_date datetime
);
