ALTER TABLE t_api_log
    ADD COLUMN headers text;
ALTER TABLE t_api_log
    ADD COLUMN request_url text;
ALTER TABLE t_api_log
    ADD COLUMN response_body longtext;
