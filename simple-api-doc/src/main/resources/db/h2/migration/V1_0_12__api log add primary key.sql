ALTER TABLE t_api_log
    alter column id int GENERATED BY DEFAULT AS IDENTITY;
ALTER TABLE t_api_log
    ADD PRIMARY KEY (id);