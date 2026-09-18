FROM eclipse-temurin:24-jdk AS build
WORKDIR /src
COPY pom.xml .
COPY keypass-common/pom.xml keypass-common/pom.xml
COPY keypass-server/pom.xml keypass-server/pom.xml
COPY keypass-car-sim/pom.xml keypass-car-sim/pom.xml
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw
COPY keypass-common/src keypass-common/src
COPY keypass-server/src keypass-server/src
RUN ./mvnw -B -pl keypass-server -am -DskipTests package

FROM eclipse-temurin:24-jre
RUN groupadd --system app && useradd --system --gid app app
WORKDIR /app
COPY --from=build /src/keypass-server/target/keypass-server-*.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
