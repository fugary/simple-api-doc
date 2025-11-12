update t_api_project_info_detail set locked=1 where doc_id is null and content_type='manual';
update t_api_project_info_detail set content_type=null where doc_id is null and content_type in ('manual', 'auto');
