cd ..
title @project.name@
echo Starting the @project.name@ ...
set JAVA_OPTS=-Xms256M -Xmx512M
set DB_TYPE=mysql
set DB_MYSQL_SERVER=localhost
set DB_MYSQL_PORT=3306
set DB_MYSQL_DBNAME=apidoc-db
set DB_USERNAME=root
set DB_PASSWORD=12345678
java %JAVA_OPTS% -Dfile.encoding=UTF-8 -jar @project.build.finalName@.jar
