ALTER TABLE t_api_project
    ADD COLUMN env_content text;
ALTER TABLE t_api_project
    ADD COLUMN api_version varchar(40);
ALTER TABLE t_api_project_info
    ADD COLUMN default_flag bit;
