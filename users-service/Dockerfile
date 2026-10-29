# Dos etapas: se compila con el JDK y se ejecuta con el JRE. Así nadie necesita Java ni Maven
# instalados para levantar el proyecto, y la imagen final no lleva Maven ni el código fuente.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
# Primero solo lo que define las dependencias: mientras el pom no cambie, Docker reusa esta capa
# y no las vuelve a bajar. No se usa la caché de BuildKit (--mount=type=cache) porque necesita
# el plugin buildx, y no todas las instalaciones de Docker lo traen.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline
COPY src src
# Los tests no corren acá porque necesitan MongoDB y Redis, que no existen durante el build.
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
