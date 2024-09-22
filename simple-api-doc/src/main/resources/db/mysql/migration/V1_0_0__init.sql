CREATE TABLE t_api_project
(
    id           int(11)   NOT NULL AUTO_INCREMENT comment '主键',
    user_name    varchar(255) comment '所属用户',
    project_name varchar(255) comment '项目名称',
    project_code varchar(255) comment '项目代码',
    status       tinyint(3) comment '状态是否启用，0——禁用，1——启用',
    private_flag bit(1),
    description  varchar(1024) comment '描述信息',
    creator      varchar(255) comment '创建人',
    create_date  timestamp NULL comment '创建时间',
    modifier     varchar(255) comment '修改人',
    modify_date  timestamp NULL comment '修改时间',
    PRIMARY KEY (id)
);
CREATE TABLE t_api_project_schema
(
    id             int(10)   NOT NULL AUTO_INCREMENT,
    project_id     int(11),
    url            varchar(8192),
    file_name      varchar(255),
    content_type   varchar(255),
    env_content    varchar(8192),
    content_schema text,
    auth_type      varchar(255),
    auth_content   varchar(8192),
    creator        varchar(255) comment '创建人',
    create_date    timestamp NULL comment '创建时间',
    modifier       varchar(255) comment '修改人',
    modify_date    timestamp NULL comment '修改时间',
    PRIMARY KEY (id)
);
CREATE TABLE t_api_user
(
    id            int(11)   NOT NULL AUTO_INCREMENT comment '主键',
    user_name     varchar(255) comment '用户名',
    user_password varchar(255) comment '密码',
    status        int(11) comment '状态',
    user_email    varchar(2049) comment '邮箱',
    nick_name     varchar(255) comment '昵称',
    creator       varchar(255) comment '创建人',
    create_date   timestamp NULL comment '创建时间',
    modifier      varchar(255) comment '修改人',
    modify_date   timestamp NULL comment '修改时间',
    PRIMARY KEY (id)
);
