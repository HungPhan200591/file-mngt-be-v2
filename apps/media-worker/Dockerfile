FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup \
 && mkdir -p /app/logs \
 && chown -R appuser:appgroup /app

USER appuser

COPY --chown=appuser:appgroup apps/media-worker/target/*.jar app.jar

ENV MEDIA_WORKER_SERVER_PORT=18104
EXPOSE 18104

HEALTHCHECK --interval=15s --timeout=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:18104/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
