package com.aamdigital.aambackendservice.rest

import com.aamdigital.aambackendservice.error.AamException
import com.aamdigital.aambackendservice.error.ExternalSystemException
import com.aamdigital.aambackendservice.error.ForbiddenAccessException
import com.aamdigital.aambackendservice.error.IOException
import com.aamdigital.aambackendservice.error.InternalServerException
import com.aamdigital.aambackendservice.error.InvalidArgumentException
import com.aamdigital.aambackendservice.error.NetworkException
import com.aamdigital.aambackendservice.error.NotFoundException
import com.aamdigital.aambackendservice.error.UnauthorizedAccessException
import org.springframework.boot.web.error.ErrorAttributeOptions
import org.springframework.boot.web.servlet.error.DefaultErrorAttributes
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.validation.FieldError
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.context.request.WebRequest
import org.springframework.web.servlet.resource.NoResourceFoundException

@Component
class AamErrorAttributes : DefaultErrorAttributes() {

    override fun getErrorAttributes(
        request: WebRequest,
        options: ErrorAttributeOptions
    ): MutableMap<String, Any> {
        val errorAttributes = super.getErrorAttributes(request, options)

        when (val error = getError(request)) {
            is AamException -> {
                errorAttributes["status"] = getStatus(error)
                errorAttributes["error"] = error.code
                errorAttributes["message"] = error.message
            }

            is WebExchangeBindException -> {
                errorAttributes["status"] = HttpStatus.BAD_REQUEST.value()
                errorAttributes["error"] = "BAD_REQUEST"
                errorAttributes["message"] = createErrorMessage(error)
            }

            is NoResourceFoundException -> {
                errorAttributes["status"] = HttpStatus.NOT_FOUND.value()
                errorAttributes["error"] = "NOT_FOUND"
                errorAttributes["message"] = "Not the place you're looking for."
            }
        }

        errorAttributes.remove("path")
        errorAttributes.remove("requestId")
        errorAttributes.remove("trace")

        return errorAttributes
    }

    private fun createErrorMessage(error: WebExchangeBindException): String {
        return if (error.hasFieldErrors()) {
            error.allErrors.joinToString(", ") {
                if (it is FieldError) {
                    "Error in field ${it.field}: ${it.defaultMessage}"
                } else {
                    it.defaultMessage.toString()
                }
            }
        } else {
            error.reason ?: ""
        }
    }

    private fun getStatus(error: AamException) = when (error) {
        is InternalServerException -> HttpStatus.INTERNAL_SERVER_ERROR.value()
        is IOException -> HttpStatus.INTERNAL_SERVER_ERROR.value()
        is ExternalSystemException -> HttpStatus.INTERNAL_SERVER_ERROR.value()
        is NetworkException -> HttpStatus.INTERNAL_SERVER_ERROR.value()
        is InvalidArgumentException -> HttpStatus.BAD_REQUEST.value()
        is UnauthorizedAccessException -> HttpStatus.UNAUTHORIZED.value()
        is ForbiddenAccessException -> HttpStatus.FORBIDDEN.value()
        is NotFoundException -> HttpStatus.NOT_FOUND.value()
    }
}
