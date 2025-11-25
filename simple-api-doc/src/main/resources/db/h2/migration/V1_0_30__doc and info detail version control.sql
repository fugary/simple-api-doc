ALTER TABLE t_api_doc
    ADD COLUMN modify_from int;
ALTER TABLE t_api_project_info_detail
    ADD COLUMN data_version int;
ALTER TABLE t_api_project_info_detail
    ADD COLUMN modify_from int;
