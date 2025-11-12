ALTER TABLE t_api_project_info_detail
    ADD COLUMN locked bit(1);
ALTER TABLE t_api_project_info_detail
    ADD COLUMN doc_id int;
ALTER TABLE t_api_project_info_detail
    ADD COLUMN status_code int;
ALTER TABLE t_api_project_info_detail
    ADD COLUMN examples longtext;
