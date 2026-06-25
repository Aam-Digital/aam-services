package com.aamdigital.aambackendservice.e2e.contract

import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.type.filter.AnnotationTypeFilter
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Discovers the REST endpoints a module actually exposes by scanning its
 * `@RestController`s, so the inventory gate can flag endpoints missing from the spec.
 *
 * Uses Spring's merged-annotation support, so `@GetMapping` etc. resolve to a
 * `@RequestMapping` with the concrete HTTP method(s) and path(s) — matching how
 * Spring MVC itself maps requests, without booting the application context.
 */
object ControllerEndpointScanner {
    /** Canonical operation keys for all `<modulePrefix>` endpoints, e.g. `GET /report/{}`. */
    fun endpointKeys(
        basePackage: String,
        modulePrefix: String
    ): Set<String> {
        val provider = ClassPathScanningCandidateComponentProvider(false)
        provider.addIncludeFilter(AnnotationTypeFilter(RestController::class.java))

        val keys = mutableSetOf<String>()
        for (candidate in provider.findCandidateComponents(basePackage)) {
            val className = candidate.beanClassName ?: continue
            val clazz = Class.forName(className, false, Thread.currentThread().contextClassLoader)
            val classMapping = AnnotatedElementUtils.findMergedAnnotation(clazz, RequestMapping::class.java)
            val basePaths = mappingPaths(classMapping?.value, classMapping?.path)

            for (method in clazz.declaredMethods) {
                val mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping::class.java) ?: continue
                val httpMethods = mapping.method.map { it.name }
                if (httpMethods.isEmpty()) continue
                val methodPaths = mappingPaths(mapping.value, mapping.path)

                for (basePath in basePaths) {
                    for (methodPath in methodPaths) {
                        val fullPath = joinPaths(basePath, methodPath)
                        if (!fullPath.startsWith(modulePrefix)) continue
                        val stripped = fullPath.removePrefix(modulePrefix).ifEmpty { "/" }
                        httpMethods.forEach { keys.add(operationKey(it, stripped)) }
                    }
                }
            }
        }
        return keys
    }

    private fun mappingPaths(
        value: Array<String>?,
        path: Array<String>?
    ): List<String> {
        val paths = (value?.toList().orEmpty() + path?.toList().orEmpty()).filter { it.isNotEmpty() }
        return paths.distinct().ifEmpty { listOf("") }
    }

    private fun joinPaths(
        base: String,
        path: String
    ): String = "/" + (base.trim('/') + "/" + path.trim('/')).trim('/')
}
