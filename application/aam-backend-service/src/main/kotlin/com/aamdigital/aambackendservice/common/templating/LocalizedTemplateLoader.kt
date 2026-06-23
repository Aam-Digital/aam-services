package com.aamdigital.aambackendservice.common.templating

import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Loads locale-specific, deployment-overridable template resources (email bodies, branding text,
 * etc.) using a single, shared resolution order. Centralizing this here keeps the order and the
 * `{locale}` / mounted-override / bundled-classpath path conventions in one place so every template
 * (the email HTML body, the email branding properties, and any future template) resolves
 * identically.
 *
 * Templates live under a per-language folder (`{locale}/...`) so a translator can copy a whole
 * language folder (e.g. `en/` -> `de/`) and translate it. Operators can override the bundled
 * defaults by mounting their own files under [templatesBaseDir] (e.g. `/opt/app/templates`).
 *
 * @param templatesBaseDir directory holding mounted override templates.
 * @param defaultLocale base language used as the final fallback (`en`).
 * @param classpathRoot root folder of the bundled templates on the classpath (`templates`).
 */
class LocalizedTemplateLoader(
    private val templatesBaseDir: Path,
    private val defaultLocale: String = DEFAULT_LOCALE,
    private val classpathRoot: String = TEMPLATES_CLASSPATH_ROOT
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Resolves the resource at [relativePath] (e.g. `notification/email-branding.properties`) for
     * the given [locale], trying, in order, and returning the content of the first that exists (or
     * `null` if none do):
     *  1. mounted override, localized: `{baseDir}/{locale}/{relativePath}`
     *  2. mounted override, legacy unsuffixed (back-compat): `{baseDir}/{relativePath}`
     *  3. bundled classpath, localized: `/{classpathRoot}/{locale}/{relativePath}`
     *  4. bundled classpath, default language: `/{classpathRoot}/{defaultLocale}/{relativePath}`
     *
     * A region suffix in [locale] is ignored (`de-DE` -> `de`); a blank locale falls back to
     * [defaultLocale].
     */
    fun load(relativePath: String, locale: String): String? {
        val normalizedLocale = normalizeLocale(locale)

        val localizedOverride = templatesBaseDir.resolve(normalizedLocale).resolve(relativePath)
        if (Files.isRegularFile(localizedOverride)) {
            logger.info("Loading template resource from {}", localizedOverride)
            return Files.readString(localizedOverride, StandardCharsets.UTF_8)
        }

        val legacyOverride = templatesBaseDir.resolve(relativePath)
        if (Files.isRegularFile(legacyOverride)) {
            logger.info("Loading template resource from legacy override {}", legacyOverride)
            return Files.readString(legacyOverride, StandardCharsets.UTF_8)
        }

        readClasspathResource("/$classpathRoot/$normalizedLocale/$relativePath")?.let { return it }

        return readClasspathResource("/$classpathRoot/$defaultLocale/$relativePath")
    }

    /**
     * Like [load] but throws when the resource cannot be resolved anywhere — for templates that
     * must always exist (a bundled classpath default is expected).
     */
    fun loadRequired(relativePath: String, locale: String): String =
        load(relativePath, locale)
            ?: throw IllegalStateException(
                "Missing template resource: /$classpathRoot/$defaultLocale/$relativePath"
            )

    private fun readClasspathResource(classpathLocation: String): String? {
        javaClass.getResourceAsStream(classpathLocation)?.use { resource ->
            logger.info("Loading template resource from classpath {}", classpathLocation)
            return resource.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        }
        return null
    }

    /**
     * Reduces a (possibly region-qualified) locale to the base language code used for the template
     * folder name, e.g. `en-US` -> `en`, `de_DE` -> `de`. Falls back to [defaultLocale] for blank input.
     */
    private fun normalizeLocale(locale: String): String =
        locale.substringBefore('-').substringBefore('_').trim().lowercase().ifBlank { defaultLocale }

    companion object {
        const val DEFAULT_LOCALE = "en"
        const val TEMPLATES_CLASSPATH_ROOT = "templates"
    }
}
