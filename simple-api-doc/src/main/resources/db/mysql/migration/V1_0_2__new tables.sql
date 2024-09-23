ALTER TABLE t_api_project_schema
    ADD COLUMN source_type varchar(255);
ALTER TABLE t_api_project_schema
    ADD COLUMN import_type varchar(255);

CREATE TABLE t_api_doc
(
    id           int(11)   NOT NULL AUTO_INCREMENT,
    project_id   int(10)   NOT NULL,
    folder_id    int(10),
    doc_type     varchar(255),
    doc_name     varchar(8192),
    doc_content  text,
    status       tinyint(3),
    operation_id varchar(8192),
    method       varchar(255),
    url          varchar(8192),
    summary      text,
    description  text,
    creator      varchar(255),
    modifier     varchar(255),
    create_date  timestamp NULL,
    modify_date  timestamp NULL,
    PRIMARY KEY (id)
);
CREATE TABLE t_api_doc_schema
(
    id             int(11)   NOT NULL AUTO_INCREMENT,
    doc_id         int(11),
    body_type      varchar(255),
    schema_content text,
    content_type   varchar(255),
    description    text,
    creator        varchar(255),
    modifier       varchar(255),
    create_date    timestamp NULL,
    modify_date    timestamp NULL,
    PRIMARY KEY (id)
);
CREATE TABLE t_api_folder
(
    id          int(11)   NOT NULL AUTO_INCREMENT,
    project_id  int(10)   NOT NULL,
    folder_name varchar(4096),
    root_flag   bit(1),
    status      tinyint(3) DEFAULT 0,
    parent_id   int(10),
    creator     varchar(255),
    modifier    varchar(255),
    create_date timestamp NULL,
    modify_date timestamp NULL,
    PRIMARY KEY (id)
);
CREATE TABLE t_api_project_task
(
    id             int(11)   NOT NULL AUTO_INCREMENT,
    project_id     int(10),
    task_type      varchar(255),
    source_type    varchar(255),
    source_url     varchar(8192),
    schedule_rate  int(10),
    task_name      varchar(4096),
    to_folder      int(10),
    overwrite_mode int(10),
    auth_type      varchar(255),
    auth_content   varchar(8192),
    status         tinyint(3) DEFAULT 0,
    exec_date      timestamp NULL,
    creator        varchar(255),
    modifier       varchar(255),
    modify_date    timestamp NULL,
    create_date    timestamp NULL,
    PRIMARY KEY (id)
);
CREATE TABLE t_api_project_share
(
    id             int(11)     NOT NULL AUTO_INCREMENT,
    project_id     int(11),
    share_id       varchar(32) NOT NULL UNIQUE,
    share_name     varchar(255),
    export_enabled bit(1),
    debug_enabled  bit(1),
    env_content    varchar(8192),
    expire_date    timestamp   NULL,
    share_password varchar(255),
    status         tinyint(3) DEFAULT 0,
    creator        varchar(255),
    modifier       varchar(255),
    modify_date    timestamp   NULL,
    create_date    timestamp   NULL,
    PRIMARY KEY (id)
);

