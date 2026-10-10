// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 The steakOS Authors

package steakos.api

import grails.converters.JSON
import groovy.util.logging.Slf4j
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.MultipartException

/**
 * Multipart failures happen before ContentApiController — return JSON for /api/*
 * instead of the Grails HTML error page (SPA shows "HTML instead of JSON").
 */
@Slf4j
@ControllerAdvice
class ApiMultipartExceptionHandler {

    @ExceptionHandler([MaxUploadSizeExceededException, MultipartException])
    ResponseEntity<String> handleMultipart(Exception ex, HttpServletRequest request) {
        String path = request?.requestURI ?: ''
        log.warn('Multipart error on {}: {}', path, ex.message)
        String body = ([
                code : 'UPLOAD_TOO_LARGE',
                error: ex.message ?: 'Upload rejected (file too large or multipart error)',
        ] as JSON).toString()
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .header('Content-Type', 'application/json;charset=UTF-8')
                .body(body)
    }
}
