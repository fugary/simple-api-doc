@echo off
chcp 65001
if "%1" equ "" goto paramError
echo 更新simple-api-doc版本号
call mvn versions:set -DnewVersion=%1
cd simple-api-doc-ui
echo 更新simple-api-doc-ui版本号
call npm version %1
cd ..
echo 执行版本更新成功：%1
goto :EOF
:paramError
echo 没有指定版本号，请示用new-version.bat 1.0.0格式调用

