FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY backend/pom.xml ./pom.xml
RUN mvn -q -DskipTests dependency:go-offline

COPY backend/src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

ENV TZ=Asia/Shanghai
ENV JAVA_OPTS=""

COPY --from=build /workspace/target/quality-trace.jar /app/quality-trace.jar

EXPOSE 8081

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/quality-trace.jar"]

