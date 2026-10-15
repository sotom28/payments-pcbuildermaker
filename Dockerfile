# Build stage: compila el jar dentro de la imagen para que `docker build .`
# funcione sin necesidad de un `mvn package` previo (este servicio todavía no
# tiene pipeline CI/CD; los servicios que sí lo tienen reciben el jar del build).
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Capa de dependencias: se cachea mientras el pom no cambie.
COPY pom.xml .
RUN mvn -q dependency:go-offline

COPY src ./src
RUN mvn -q clean package -DskipTests
# Normaliza el nombre del jar (excluye el .jar.original).
RUN cp "$(ls target/*.jar | grep -v original)" /app/app.jar

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=build /app/app.jar app.jar

EXPOSE 8086

# Las variables (SPRING_PROFILES_ACTIVE, DB_HOST, DB_USER, DB_PASSWORD,
# SERVER_PORT) se pasan en tiempo de ejecución con --env-file .env.
# Nunca copiar el archivo .env dentro de la imagen.
ENTRYPOINT ["java", "-jar", "app.jar"]
