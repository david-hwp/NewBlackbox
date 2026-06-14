FROM eclipse-temurin:21-jre

WORKDIR /app
COPY admin/backend/target/admin-backend-1.0.0.jar /app/admin-backend.jar
COPY admin/scripts /app/admin/scripts
RUN chmod +x /app/admin/scripts/*.sh || true

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/admin-backend.jar"]
