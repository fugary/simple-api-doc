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
    doc_type     varchar(50),
    doc_name     text,
    doc_content  text,
    doc_key      varchar(2048),
    status       tinyint(3),
    sort_id      int(11),
    operation_id varchar(4096),
    spec_version varchar(10),
    deprecated   bit(1),
    method       varchar(50),
    url          text,
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
    body_type      varchar(40),
    schema_content text,
    schema_key     varchar(4096),
    schema_name    varchar(4096),
    content_type   varchar(40),
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
    project_id   varchar(32),
    folder_id    int(11),
    import_type  varchar(40),
    file_name    text,
    source_type  varchar(40),
    url          text,
    env_content  text,
    auth_type    varchar(40),
    auth_content text,
    version      varchar(40),
    oas_version  varchar(40),
    spec_version varchar(40),
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
    body_type      varchar(40),
    schema_content text,
    schema_name    varchar(4096),
    schema_key     varchar(4096),
    content_type   varchar(40),
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
    env_content    text,
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
    task_type      varchar(40),
    source_type    varchar(40),
    source_url     text,
    schedule_rate  int(10),
    task_name      varchar(4096),
    to_folder      int(10),
    overwrite_mode int(10),
    auth_type      varchar(40),
    auth_content   text,
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
