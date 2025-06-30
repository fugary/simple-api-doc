UPDATE t_api_project_info t
    JOIN (
    SELECT project_id, MIN(id) AS min_id
    FROM t_api_project_info
    GROUP BY project_id
    ) AS sub
ON t.project_id = sub.project_id
SET t.default_flag = CASE WHEN t.id = sub.min_id THEN 1 ELSE 0 END;
