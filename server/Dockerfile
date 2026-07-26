# Sources:
# https://spring.io/guides/gs/spring-boot-docker
# https://docs.docker.com/reference/dockerfile/

FROM eclipse-temurin:21
COPY build/libs/server-0.0.1-SNAPSHOT.jar server-0.0.1-SNAPSHOT.jar
EXPOSE 5672
ENTRYPOINT ["java", "-jar", "server-0.0.1-SNAPSHOT.jar"]