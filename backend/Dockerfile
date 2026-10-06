FROM maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre@sha256:cff19e6215689161eb6162c11b86b0c60ddf802164f2eaf48d570f8fb79a36c5
WORKDIR /app

RUN addgroup --system whatsappka && adduser --system --ingroup whatsappka whatsappka
COPY --from=build /workspace/target/whatsappka-backend-0.1.0-SNAPSHOT.jar /app/app.jar

USER whatsappka
EXPOSE 8080
# 55% контейнера: API 1024 МиБ дают heap около 560 МиБ, worker 768 МиБ — около 420 МиБ (бюджет PRD §11.1).
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=55", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
