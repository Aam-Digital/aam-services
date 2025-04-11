FROM maven:3-amazoncorretto-23 AS build
WORKDIR /opt/app
COPY . .
RUN ["mvn", "clean", "package"]

FROM scratch
COPY --from=build /opt/app/target/keycloak-third-party-authentication-1.0-SNAPSHOT.jar ./keycloak-third-party-authentication.jar
