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
# Consumed at runtime as sentry.release (see application.yaml) so error events are tagged with a
# version. Left empty rather than a placeholder like "unknown" when not passed at build time, so
# Sentry falls back to no release instead of lumping unrelated builds under one fake release.
ARG APPLICATION_VERSION=
ENV APPLICATION_VERSION=${APPLICATION_VERSION}
EXPOSE 8080
CMD ./bin/aam-backend-service
