FROM gradle:9.6.1-jdk21-alpine AS BUILD
WORKDIR /opt/app
COPY . .
# When a Sentry auth token is mounted (see the build workflow), the sources of this build are
# bundled and uploaded, so Sentry shows the surrounding source code for our own stack trace
# frames. That has to happen in this Gradle run: the bundle id is generated once per build and
# baked into the jar, so an upload from any other build would not match the shipped jar.
# `installDist` alone does not pull the upload task in. Without a token (e.g. builds from
# forks) only the plain build runs, exactly as before.
RUN --mount=type=secret,id=sentry_auth_token \
    if [ -s /run/secrets/sentry_auth_token ]; then \
      SENTRY_AUTH_TOKEN="$(cat /run/secrets/sentry_auth_token)" \
        gradle installDist sentryUploadSourceBundleJava; \
    else \
      gradle installDist; \
    fi

# Pinned to a full JDK patch version on purpose. The floating "21-alpine" tag never changes,
# so Renovate had nothing to update and a JDK security fix produced no signal at all — the
# published release image would keep the old runtime until someone happened to cut a release.
# The alpine base is left floating so OS patches still flow in on every build.
FROM amazoncorretto:21.0.12-alpine
WORKDIR /opt/app
COPY --from=BUILD /opt/app/build/install/aam-backend-service /opt/app
# Consumed at runtime as sentry.release (see application.yaml) so error events are tagged with a
# version. Left empty rather than a placeholder like "unknown" when not passed at build time, so
# unrelated builds are not lumped together under one fake release.
ARG APPLICATION_VERSION=
ENV APPLICATION_VERSION=${APPLICATION_VERSION}
EXPOSE 8080
CMD ./bin/aam-backend-service
