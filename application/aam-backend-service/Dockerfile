FROM gradle:8.10.2-jdk21-alpine AS BUILD
WORKDIR /opt/app
COPY . .
RUN gradle installDist

FROM amazoncorretto:21-alpine
WORKDIR /opt/app
COPY --from=BUILD /opt/app/build/install/aam-backend-service /opt/app
EXPOSE 8080
CMD ./bin/aam-backend-service
