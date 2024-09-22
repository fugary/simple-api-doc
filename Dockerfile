# Start with base image
FROM adoptopenjdk/openjdk11:alpine-jre

# Add Maintainer Info
LABEL maintainer="fugary"

# Add a temporary volume
VOLUME /tmp

# Expose Port 9086
EXPOSE 9086

ENV JAVA_OPTS="-Xmx512M"
# 类型支持h2和mysql
ENV DB_TYPE="h2"
# h2数据库
ENV DB_DATA_DIR="/data"
ENV DB_H2_CONSOLE=false
#MySQL数据库
ENV DB_MYSQL_SERVER='localhost'
ENV DB_MYSQL_PORT=3306
ENV DB_MYSQL_DBNAME=apidoc-db
#通用
ENV DB_USERNAME="root"
ENV DB_PASSWORD="12345678"
ENV DB_POOL_SIZE=5
EXPOSE 9086

# Application Jar File
ARG JAR_FILE=simple-api-doc/target/simple-api-doc*.jar

# Add Application Jar File to the Container
ADD ${JAR_FILE} simple-api-doc.jar

# Run the JAR file
ENTRYPOINT [ "sh", "-c", "java $JAVA_OPTS -jar /simple-api-doc.jar"]
