FROM eclipse-temurin:21-jre

WORKDIR /app

COPY novel-crawl.jar /app/

# Tắt ShardingSphere mặc định để tương thích phiên bản cũ
ENV JDBC_DRIVER=com.mysql.cj.jdbc.Driver

ENTRYPOINT ["sh","-c","exec java \
-Dspring.datasource.driver-class-name=${JDBC_DRIVER} \
-jar /app/novel-crawl.jar"]
