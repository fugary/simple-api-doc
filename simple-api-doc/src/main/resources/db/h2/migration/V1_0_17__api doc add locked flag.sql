ALTER TABLE t_api_doc
    DROP COLUMN deprecated;
ALTER TABLE t_api_doc
    ADD COLUMN locked bit;
