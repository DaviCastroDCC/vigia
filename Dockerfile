# syntax=docker/dockerfile:1
# Etapa 1: compila o projeto
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# O cache guarda as dependências do Maven entre builds: só a primeira build baixa tudo.
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp package -DskipTests

# Etapa 2: imagem final, só com o JRE e o .jar
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
USER app
COPY --from=build /app/target/vigia-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
