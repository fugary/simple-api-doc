ALTER TABLE t_api_project_info
    ALTER COLUMN project_id int;
ALTER TABLE t_api_project_info_detail
    ALTER COLUMN project_id int;

CREATE INDEX idx_api_folder_project_sort ON t_api_folder (project_id, sort_id);
CREATE INDEX idx_api_folder_project_root ON t_api_folder (project_id, root_flag);

CREATE INDEX idx_api_doc_project_modify_sort ON t_api_doc (project_id, modify_from, sort_id);
CREATE INDEX idx_api_doc_project_status_modify_sort ON t_api_doc (project_id, status, modify_from, sort_id);
CREATE INDEX idx_api_doc_folder_modify ON t_api_doc (folder_id, modify_from);
CREATE INDEX idx_api_doc_info_modify ON t_api_doc (info_id, modify_from);

CREATE INDEX idx_api_project_info_project ON t_api_project_info (project_id);
CREATE INDEX idx_api_project_info_folder ON t_api_project_info (folder_id);

CREATE INDEX idx_api_info_detail_doc_modify ON t_api_project_info_detail (doc_id, modify_from);
CREATE INDEX idx_api_info_detail_project_info_type ON t_api_project_info_detail (project_id, info_id, modify_from, body_type);
CREATE INDEX idx_api_info_detail_info_doc_type ON t_api_project_info_detail (info_id, doc_id, body_type);
