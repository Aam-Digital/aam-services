package com.aamdigital.aambackendservice.container

/**
 * Container images used by the e2e test stack.
 *
 * These tags are plain Kotlin strings, so no built-in Renovate manager can discover them.
 * Collecting them here lets a single `custom.regex` manager (configured in renovate.json)
 * keep them updated, instead of regexes chasing the builder chains in [TestContainers].
 *
 * Keep one `// renovate:` annotation directly above each constant — the annotation is what
 * tells Renovate which image the following version string belongs to.
 */
object TestImages {
    // renovate: datasource=docker depName=couchdb
    const val COUCHDB = "3.5.2"

    // Upstream replaced the plain numeric tags with full-/slim- prefixed ones and has moved
    // on to 5.x, so updating this is a behaviour change for the export module rather than a
    // version bump. renovate.json groups it with the dev stack's carbone service and forces
    // a draft PR, so the two never drift and any move stays a deliberate decision.
    // renovate: datasource=docker depName=carbone/carbone-ee
    const val CARBONE = "4.23.4"

    // The tag is the keycloak-config-cli version followed by the Keycloak major it was built for.
    // Only the tool's behaviour matters here, so any Keycloak major that this tag supports is fine.
    // renovate: datasource=docker depName=adorsys/keycloak-config-cli
    const val KEYCLOAK_CONFIG_CLI = "6.5.1-26"

    // Built from our own repository and always tracked at head; nothing to pin.
    const val SQS = "latest"
}
