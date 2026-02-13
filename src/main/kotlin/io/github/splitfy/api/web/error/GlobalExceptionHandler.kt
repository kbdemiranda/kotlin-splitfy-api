package io.github.splitfy.api.web.error

import io.github.splitfy.api.exception.ApiException
import jakarta.persistence.EntityNotFoundException
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(ApiException::class)
    fun handleApiException(ex: ApiException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        return buildResponse(
            status = ex.status,
            code = ex.code,
            message = ex.message ?: "Request failed",
            request = request,
            ex = ex
        )
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {
        val validationMessage = ex.bindingResult.fieldErrors
            .joinToString("; ") { "${it.field}: ${it.defaultMessage ?: "invalid value"}" }
            .ifBlank { "Validation error" }

        return buildResponse(
            status = HttpStatus.BAD_REQUEST,
            code = "BAD_REQUEST",
            message = validationMessage,
            request = request,
            ex = ex
        )
    }

    @ExceptionHandler(
        IllegalArgumentException::class,
        HttpMessageNotReadableException::class
    )
    fun handleBadRequest(ex: Exception, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        return buildResponse(
            status = HttpStatus.BAD_REQUEST,
            code = "BAD_REQUEST",
            message = ex.message ?: "Bad request",
            request = request,
            ex = ex
        )
    }

    @ExceptionHandler(EntityNotFoundException::class)
    fun handleNotFound(ex: EntityNotFoundException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        return buildResponse(
            status = HttpStatus.NOT_FOUND,
            code = "NOT_FOUND",
            message = ex.message ?: "Resource not found",
            request = request,
            ex = ex
        )
    }

    @ExceptionHandler(DataAccessException::class)
    fun handleDatabaseException(ex: DataAccessException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        return buildResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            code = "DB_ERROR",
            message = ex.mostSpecificCause?.message ?: ex.message ?: "Database error",
            request = request,
            ex = ex
        )
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleConflict(ex: DataIntegrityViolationException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        return buildResponse(
            status = HttpStatus.CONFLICT,
            code = "CONFLICT",
            message = ex.mostSpecificCause?.message ?: ex.message ?: "Conflict",
            request = request,
            ex = ex,
        )
    }

    @ExceptionHandler(BadCredentialsException::class, AuthenticationCredentialsNotFoundException::class)
    fun handleUnauthorized(ex: Exception, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        return buildResponse(
            status = HttpStatus.UNAUTHORIZED,
            code = "UNAUTHORIZED",
            message = ex.message ?: "Unauthorized",
            request = request,
            ex = ex,
        )
    }

    @ExceptionHandler(AccessDeniedException::class)
    fun handleForbidden(ex: AccessDeniedException, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        return buildResponse(
            status = HttpStatus.FORBIDDEN,
            code = "FORBIDDEN",
            message = ex.message ?: "Forbidden",
            request = request,
            ex = ex,
        )
    }

    @ExceptionHandler(ResponseStatusException::class)
    fun handleResponseStatusException(
        ex: ResponseStatusException,
        request: HttpServletRequest
    ): ResponseEntity<ApiErrorResponse> {
        val reason = ex.reason ?: ex.message ?: "Request failed"
        return buildResponse(
            status = HttpStatus.valueOf(ex.statusCode.value()),
            code = ex.statusCode.toString(),
            message = reason,
            request = request,
            ex = ex
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleUnexpectedException(ex: Exception, request: HttpServletRequest): ResponseEntity<ApiErrorResponse> {
        return buildResponse(
            status = HttpStatus.INTERNAL_SERVER_ERROR,
            code = "INTERNAL_SERVER_ERROR",
            message = ex.message ?: "Unexpected internal error",
            request = request,
            ex = ex
        )
    }

    private fun buildResponse(
        status: HttpStatus,
        code: String,
        message: String,
        request: HttpServletRequest,
        ex: Exception
    ): ResponseEntity<ApiErrorResponse> {
        val body = ApiErrorResponse(
            status = status.value(),
            code = code,
            message = message,
            path = request.requestURI
        )

        logException(status = status, code = code, message = message, path = request.requestURI, ex = ex)

        return ResponseEntity.status(status).body(body)
    }

    private fun logException(
        status: HttpStatus,
        code: String,
        message: String,
        path: String,
        ex: Exception
    ) {
        val logMessage = "API exception: status=${status.value()} code=$code message='$message' path=$path"
        if (status.is5xxServerError) {
            log.error(logMessage, ex)
            return
        }
        log.warn(logMessage, ex)
    }
}
