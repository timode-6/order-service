FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle .
COPY settings.gradle .
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon
COPY src/ src/
RUN ./gradlew bootJar --no-daemon 

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S appgroup && \
    adduser -S appuser -G appgroup
COPY --from=builder /app/build/libs/*.jar app.jar
RUN chown appuser:appgroup app.jar
USER appuser
EXPOSE 8082
ENV JAVA_OPTS="\
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -Djava.security.egd=file:/dev/./urandom"
ENV SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/order_db
ENV SPRING_DATASOURCE_USERNAME=order_db
ENV SPRING_DATASOURCE_PASSWORD=postgres
ENV SPRING_JPA_HIBERNATE_DDL_AUTO=update

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]