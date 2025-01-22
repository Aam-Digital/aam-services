FROM gradle:8.11.1-jdk21-alpine AS build
WORKDIR /opt/app
COPY . .
RUN ["gradle", "installDist"]

FROM amazoncorretto:21-alpine
WORKDIR /opt/app
COPY --from=build /opt/app/build/install/aam-integration /opt/app
EXPOSE 8080
CMD ["./bin/aam-integration"]
