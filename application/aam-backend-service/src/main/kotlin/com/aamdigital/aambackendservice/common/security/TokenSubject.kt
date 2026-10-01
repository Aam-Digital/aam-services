package com.aamdigital.aambackendservice.common.security

import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import java.security.Principal

/**
 * The id of the calling user: the `sub` claim of its access token, or null if the token has none.
 *
 * Identify the caller by this rather than by [Principal.getName]. For a token without `sub`,
 * [JwtAuthenticationToken.getName] returns "" since Spring Security 7, where it used to return
 * null, so every such caller would pass for one and the same user "".
 *
 * A principal that is not a [JwtAuthenticationToken], which only tests pass, is identified by its name.
 */
val Principal.tokenSubject: String?
    get() = if (this is JwtAuthenticationToken) token.subject else name
