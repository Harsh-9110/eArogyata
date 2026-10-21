FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /build
COPY backend/pom.xml .
RUN mvn -B --no-transfer-progress dependency:go-offline
COPY backend/src ./src
RUN mvn -B --no-transfer-progress -DskipTests package

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S hospital && adduser -S hospital -G hospital
WORKDIR /app
COPY --from=build --chown=hospital:hospital /build/target/hospital-api-1.0.0.jar app.jar
USER hospital
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
