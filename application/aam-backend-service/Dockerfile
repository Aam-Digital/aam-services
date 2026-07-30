FROM gradle:9.6.1-jdk21-alpine AS BUILD
WORKDIR /opt/app
COPY . .
RUN gradle installDist

# Pinned to a full JDK patch version on purpose. The floating "21-alpine" tag never changes,
# so Renovate had nothing to update and a JDK security fix produced no signal at all — the
# published release image would keep the old runtime until someone happened to cut a release.
# The alpine base is left floating so OS patches still flow in on every build.
FROM amazoncorretto:21.0.12-alpine
WORKDIR /opt/app
COPY --from=BUILD /opt/app/build/install/aam-backend-service /opt/app
EXPOSE 8080
CMD ./bin/aam-backend-service
