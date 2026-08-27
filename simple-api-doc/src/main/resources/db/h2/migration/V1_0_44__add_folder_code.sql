ALTER TABLE t_api_folder ADD COLUMN folder_code VARCHAR(2048);
UPDATE t_api_folder SET folder_code = folder_name WHERE folder_code IS NULL;
UPDATE t_api_folder SET folder_code = 'root' WHERE root_flag = TRUE;
