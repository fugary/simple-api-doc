CREATE TABLE t_api_project
(
    id           int(11)   NOT NULL AUTO_INCREMENT,
    user_name    varchar(255),
    project_code varchar(255) UNIQUE,
    project_name varchar(4096),
    status       tinyint(3) DEFAULT 0,
    private_flag bit(1)     DEFAULT 1,
    description  text,
    creator      varchar(255),
    modifier     varchar(255),
    create_date  timestamp NULL,
    modify_date  timestamp NULL,
    PRIMARY KEY (id)
);
CREATE TABLE t_api_folder
(
    id          int(11)   NOT NULL AUTO_INCREMENT,
    project_id  int(10)   NOT NULL,
    folder_name varchar(8192),
    root_flag   bit(1),
    status      tinyint(3) DEFAULT 0,
    sort_id     int(11),
    parent_id   int(10),
    description text,
    creator     varchar(255),
    modifier    varchar(255),
    create_date timestamp NULL,
    modify_date timestamp NULL,
    PRIMARY KEY (id)
);
CREATE TABLE t_api_doc
(
    id           int(11)   NOT NULL AUTO_INCREMENT,
    project_id   int(10)   NOT NULL,
    folder_id    int(10),
    info_id      int(11),
    doc_type     varchar(255),
    doc_name     varchar(8192),
    doc_content  text,
    doc_key      varchar(2048),
    status       tinyint(3),
    sort_id      int(11),
    operation_id varchar(8192),
    spec_version varchar(255),
    deprecated   bit(1),
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
    schema_key     varchar(4096),
    schema_name    varchar(4096),
    content_type   varchar(255),
    description    text,
    creator        varchar(255),
    modifier       varchar(255),
    create_date    timestamp NULL,
    modify_date    timestamp NULL,
    PRIMARY KEY (id)
);
CREATE TABLE t_api_project_info
(
    id           int(11)   NOT NULL AUTO_INCREMENT,
    project_id   varchar(255),
    folder_id    int(11),
    import_type  varchar(255),
    file_name    varchar(8192),
    source_type  varchar(255),
    url          varchar(8192),
    env_content  varchar(8192),
    auth_type    varchar(255),
    auth_content varchar(8192),
    version      varchar(255),
    oas_version  varchar(255),
    spec_version varchar(255),
    creator      varchar(255),
    modifier     varchar(255),
    modify_date  timestamp NULL,
    create_date  timestamp NULL,
    PRIMARY KEY (id)
);
CREATE TABLE t_api_project_info_detail
(
    id             int(11)   NOT NULL AUTO_INCREMENT,
    project_id     varchar(255),
    info_id        int(11),
    body_type      varchar(255),
    schema_content text,
    schema_name    varchar(4096),
    schema_key     varchar(4096),
    content_type   varchar(255),
    description    text,
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
CREATE TABLE t_api_user
(
    id            int(11)   NOT NULL AUTO_INCREMENT,
    user_name     varchar(255),
    user_password varchar(255),
    status        tinyint(3) DEFAULT 0,
    nick_name     varchar(255),
    user_email    varchar(255),
    creator       varchar(255),
    modifier      varchar(255),
    create_date   timestamp NULL,
    modify_date   timestamp NULL,
    PRIMARY KEY (id)
);
