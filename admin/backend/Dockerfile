FROM eclipse-temurin:21-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends bash curl coreutils git ca-certificates \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY admin/backend/target/admin-backend-1.0.0.jar /app/admin-backend.jar
COPY admin/scripts /app/admin/scripts
RUN chmod +x /app/admin/scripts/*.sh

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/admin-backend.jar"]
