FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml ./
COPY backend ./backend
COPY services ./services
RUN mvn -B -pl services/gateway-service -am -DskipTests package

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S erp && adduser -S erp -G erp
WORKDIR /app
COPY --from=build /build/services/gateway-service/target/gateway-service-0.1.0-SNAPSHOT.jar app.jar
USER erp
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
