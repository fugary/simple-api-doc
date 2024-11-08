ALTER TABLE t_api_doc
    ADD COLUMN doc_version int(11);
CREATE TABLE t_api_doc_history
(
    id          int(11)   NOT NULL AUTO_INCREMENT,
    doc_id      int(11),
    doc_type    varchar(40),
    doc_name    text,
    doc_content longtext,
    doc_key     varchar(2048),
    status      tinyint(3),
    sort_id     int(11),
    description text,
    doc_version int(11),
    creator     varchar(255),
    create_date timestamp NULL,
    PRIMARY KEY (id)
);
