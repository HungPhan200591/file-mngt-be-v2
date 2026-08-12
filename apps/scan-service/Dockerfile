FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup \
 && mkdir -p /app/logs \
 && chown -R appuser:appgroup /app

USER appuser

COPY --chown=appuser:appgroup apps/scan-service/target/*.jar app.jar

ENV SCAN_SERVER_PORT=18102
EXPOSE 18102

HEALTHCHECK --interval=15s --timeout=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:18102/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
