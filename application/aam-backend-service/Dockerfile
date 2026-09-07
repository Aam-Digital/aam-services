FROM gradle:9.7.1-jdk21-alpine AS BUILD
WORKDIR /opt/app
COPY . .
# With SENTRY_UPLOAD_SOURCES=true the sources of this build are bundled and uploaded, so Sentry
# shows the surrounding source code for our own stack trace frames (see the build workflow). That
# has to happen in this Gradle run: the bundle id is generated once per build and baked into the
# jar, so an upload from any other build would not match the shipped jar, and `installDist` alone
# does not pull the upload task in.
# The flag is a build arg rather than just "upload if a token is mounted", because secrets are
# deliberately not part of the layer cache key: a layer built without a token would otherwise be
# reused for a build that has one, shipping a jar whose bundle id was never uploaded. For the same
# reason asking for an upload without a usable token is an error rather than a quiet fallback - a
# missing upload should not pass unnoticed. Builds that don't ask for one (pull requests, forks)
# run the plain build, exactly as before.
ARG SENTRY_UPLOAD_SOURCES=false
RUN --mount=type=secret,id=sentry_auth_token \
    if [ "$SENTRY_UPLOAD_SOURCES" = "true" ]; then \
      if [ ! -s /run/secrets/sentry_auth_token ]; then \
        echo "SENTRY_UPLOAD_SOURCES=true but no sentry_auth_token secret is mounted" >&2; \
        exit 1; \
      fi; \
      SENTRY_AUTH_TOKEN="$(cat /run/secrets/sentry_auth_token)" \
        gradle installDist sentryUploadSourceBundleJava; \
    else \
      gradle installDist; \
    fi

# Pinned to a full JDK patch version on purpose. The floating "21-alpine" tag never changes,
# so Renovate had nothing to update and a JDK security fix produced no signal at all — the
# published release image would keep the old runtime until someone happened to cut a release.
# The alpine base is left floating so OS patches still flow in on every build.
FROM amazoncorretto:25.0.4-alpine
WORKDIR /opt/app
COPY --from=BUILD /opt/app/build/install/aam-backend-service /opt/app
# Consumed at runtime as sentry.release (see application.yaml) so error events are tagged with a
# version. Left empty rather than a placeholder like "unknown" when not passed at build time, so
# unrelated builds are not lumped together under one fake release.
ARG APPLICATION_VERSION=
ENV APPLICATION_VERSION=${APPLICATION_VERSION}
EXPOSE 8080
CMD ./bin/aam-backend-service
