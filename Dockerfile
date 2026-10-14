FROM eclipse-temurin:17-jdk

# 세종대 포털 SSL 호환성: 커스텀 보안 설정으로 SHA1 제한 해제
COPY custom.java.security /opt/java/openjdk/conf/security/custom.java.security

COPY ./build/libs/*SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-Djava.security.properties=/opt/java/openjdk/conf/security/custom.java.security", "-jar", "app.jar"]