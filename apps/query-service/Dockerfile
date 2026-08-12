FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup \
 && mkdir -p /app/logs \
 && chown -R appuser:appgroup /app

USER appuser

COPY --chown=appuser:appgroup apps/query-service/target/*.jar app.jar

ENV QUERY_SERVER_PORT=18103
EXPOSE 18103

HEALTHCHECK --interval=15s --timeout=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:18103/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
