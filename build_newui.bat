@echo off
chcp 65001
cd simple-api-doc-ui
echo "===========================build simple-api-doc-ui ==========================="
call npm install && npm run build && cd .. &&^
echo "copy simple-api-doc-ui static resources............" &&^
rd /Q/S simple-api-doc\src\main\resources\static\ &^
XCOPY simple-api-doc-ui\dist simple-api-doc\src\main\resources\static\ /S /E /Y
