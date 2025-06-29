UPDATE t_api_project_info t
SET default_flag = CASE
                       WHEN t.id = (
                           SELECT MIN(t2.id)
                           FROM t_api_project_info t2
                           WHERE t2.project_id = t.project_id
                           ) THEN 1
                       ELSE 0 END;