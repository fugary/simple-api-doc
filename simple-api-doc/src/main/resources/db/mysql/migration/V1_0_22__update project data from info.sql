update t_api_project p
set
    p.api_version = (
        select t.version
        from t_api_project_info t
        where t.project_id = p.id
        order by t.id asc
        limit 1
    ),
    p.env_content = (
        select t.env_content
        from t_api_project_info t
        where t.project_id = p.id
        order by t.id asc
        limit 1
    )
where exists (
    select 1 from t_api_project_info t where t.project_id = p.id
    );
