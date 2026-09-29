package com.glancesync.common.exception;

import com.glancesync.common.dto.ErrorResponse;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Catch-all for exceptions with no more specific handler, so a stack trace never
 * reaches the client. Micronaut resolves handlers by exception type, so anything
 * unmatched lands here. The detail is logged server-side; the client gets a plain 500.
 */
@Produces
@Singleton
public class UnhandledExceptionHandler implements ExceptionHandler<Throwable, HttpResponse<?>> {

    private static final Logger LOG = LoggerFactory.getLogger(UnhandledExceptionHandler.class);

    @Override
    public HttpResponse<?> handle(HttpRequest request, Throwable exception) {
        LOG.error("Unhandled error on {} {}", request.getMethod(), request.getPath(), exception);
        return HttpResponse
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("Something went wrong. Please try again."));
    }
}
