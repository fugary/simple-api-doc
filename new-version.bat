@echo off
chcp 65001
if "%1" equ "" goto paramError
pushd "%~dp0"
echo 更新simple-api-doc版本号
call mvn versions:set -DnewVersion=%1 -DgenerateBackupPoms=false
if errorlevel 1 goto updateError
cd simple-api-doc-ui
echo 更新simple-api-doc-ui版本号
call npm version %1 --no-git-tag-version --allow-same-version --ignore-scripts
if errorlevel 1 goto updateError
popd
echo 执行版本更新成功：%1
exit /b 0
:updateError
popd
echo 版本更新失败，请检查上面的错误信息。
exit /b 1
:paramError
echo 没有指定版本号，请使用new-version.bat 1.0.0格式调用
exit /b 1

