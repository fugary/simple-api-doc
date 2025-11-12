INSERT INTO t_api_project_info_detail (project_id,
                                       info_id,
                                       doc_id,
                                       body_type,
                                       schema_content,
                                       schema_name,
                                       schema_key,
                                       content_type,
                                       description,
                                       creator,
                                       modifier,
                                       create_date,
                                       modify_date,
                                       status_code,
                                       examples)
SELECT d.project_id,
       d.info_id,
       s.doc_id,
       CASE
           WHEN s.body_type = 'security_requirements' THEN 'doc_security_requirements'
           ELSE s.body_type
           END AS body_type,
       s.schema_content,
       s.schema_name,
       s.schema_key,
       s.content_type,
       s.description,
       s.creator,
       s.modifier,
       s.create_date,
       s.modify_date,
       s.status_code,
       s.examples
FROM t_api_doc_schema s
         LEFT JOIN t_api_doc d ON s.doc_id = d.id;
