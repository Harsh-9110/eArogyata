# One public URL: Spring Boot serves the React build and the API.
# Keep backend/Dockerfile for the existing separate-frontend setup.
FROM node:24-alpine AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
ARG VITE_HOSPITAL_TIMEZONE=Asia/Kolkata
ENV VITE_HOSPITAL_TIMEZONE=$VITE_HOSPITAL_TIMEZONE
ENV VITE_DEMO_MODE=false
RUN npm run build:vercel

FROM maven:3.9-eclipse-temurin-17 AS api
WORKDIR /build
COPY backend/pom.xml ./
RUN mvn -B --no-transfer-progress dependency:go-offline
COPY backend/src/ ./src/
COPY --from=web /web/dist/ ./src/main/resources/static/
# Run the deployment filter-chain tests and the dependency-free workflow tests.
# The full integration suite requires Docker/MySQL and remains in CI.
RUN mvn -B --no-transfer-progress -Dtest=DeploymentSecurityTest,WorkflowRulesTest package

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S hospital && adduser -S hospital -G hospital
WORKDIR /app
COPY --from=api --chown=hospital:hospital /build/target/hospital-api-1.0.0.jar app.jar
COPY --chown=hospital:hospital deploy/start.sh /app/start.sh
RUN chmod 0555 /app/start.sh
# Leave room for the JVM's native memory within Render's 512 MB free instance.
ENV JAVA_TOOL_OPTIONS="-Xms32m -Xmx224m -Xss512k -XX:+UseSerialGC -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=32m -XX:MaxDirectMemorySize=32m"
ENV SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=3
ENV SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE=1
ENV SERVER_TOMCAT_THREADS_MAX=30
ENV SERVER_TOMCAT_THREADS_MIN_SPARE=2
ENV COOKIE_SECURE=true
USER hospital
EXPOSE 8080
ENTRYPOINT ["/app/start.sh"]
