# Build Stage
FROM eclipse-temurin:17-jdk-jammy AS builder
WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./

RUN chmod +x ./gradlew
RUN ./gradlew dependencies --no-daemon > /dev/null 2>&1 || true

COPY . .
RUN chmod +x ./gradlew
RUN ./gradlew build --no-daemon -x test

# Run Stage
FROM eclipse-temurin:17-jdk-jammy
WORKDIR /app

COPY --from=builder /app/build/libs/app.jar ./app.jar

ENV TZ=Asia/Seoul
ENV HOSTNAME=0.0.0.0
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]