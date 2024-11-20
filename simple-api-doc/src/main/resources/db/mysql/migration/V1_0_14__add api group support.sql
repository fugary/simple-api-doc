CREATE TABLE t_api_group
(
    id          int(11)   NOT NULL AUTO_INCREMENT,
    group_code  varchar(255),
    group_name  varchar(1024),
    description text,
    status      tinyint(3) DEFAULT 0,
    creator     varchar(255),
    modifier    varchar(255),
    create_date timestamp NULL,
    modify_date timestamp NULL,
    CONSTRAINT description
        PRIMARY KEY (id)
);
CREATE TABLE t_api_user_group
(
    id          int(11)   NOT NULL AUTO_INCREMENT,
    user_id     int(11),
    group_code  varchar(255),
    authorities varchar(1024),
    creator     varchar(255),
    create_date timestamp NULL,
    PRIMARY KEY (id)
);
ALTER TABLE t_api_project
    ADD COLUMN group_code varchar(255);
