ALTER TABLE t_api_log
    ADD COLUMN ip_address varchar(1024);
ALTER TABLE t_api_project_share
    ADD COLUMN show_tree_icon bit DEFAULT '1';
