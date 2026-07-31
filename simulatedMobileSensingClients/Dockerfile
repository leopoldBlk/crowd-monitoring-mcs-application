# Sources:
# https://spring.io/guides/gs/spring-boot-docker
# https://docs.docker.com/reference/dockerfile/

FROM eclipse-temurin:21
COPY build/libs/simulatedMobileSensingClients-0.0.1-SNAPSHOT.jar simulatedMobileSensingClients-0.0.1-SNAPSHOT.jar
ENTRYPOINT ["java", "-jar", "simulatedMobileSensingClients-0.0.1-SNAPSHOT.jar"]