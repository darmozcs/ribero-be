FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# uid/gid 1000 para poder leer la llave publica JWT montada de solo lectura
# desde el host (owner darmoz, uid 1000).
RUN addgroup -g 1000 spring && adduser -D -u 1000 -G spring spring
COPY --from=build /build/target/ribero-be.jar app.jar
USER spring

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
