UPDATE T_API_PROJECT p
SET
    p.api_version = (
        SELECT t.version
        FROM T_API_PROJECT_INFO t
        WHERE t.project_id = p.id
        ORDER BY t.id ASC
        LIMIT 1
    ),
    p.env_content = (
        SELECT t.env_content
        FROM T_API_PROJECT_INFO t
        WHERE t.project_id = p.id
        ORDER BY t.id ASC
        LIMIT 1
    )
WHERE EXISTS (
    SELECT 1 FROM T_API_PROJECT_INFO t WHERE t.project_id = p.id
    );