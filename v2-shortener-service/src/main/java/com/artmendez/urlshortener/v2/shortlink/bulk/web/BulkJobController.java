package com.artmendez.urlshortener.v2.shortlink.bulk.web;

import com.artmendez.urlshortener.v2.ratelimit.RateLimitExceededException;
import com.artmendez.urlshortener.v2.ratelimit.RateLimiter;
import com.artmendez.urlshortener.v2.shortlink.bulk.domain.BulkJob;
import com.artmendez.urlshortener.v2.shortlink.bulk.domain.BulkJobItem;
import com.artmendez.urlshortener.v2.shortlink.bulk.service.BulkJobNotFoundException;
import com.artmendez.urlshortener.v2.shortlink.bulk.service.BulkJobService;
import com.artmendez.urlshortener.v2.shortlink.bulk.service.BulkSubmissionTooLargeException;
import com.artmendez.urlshortener.v2.shortlink.web.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.amqp.AmqpException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * The two bulk endpoints from the OpenAPI contract (ARCHITECTURE.md, section 4.1; Day 3, Task
 * #10). Both require a valid JWT, unlike {@code GET /{shortCode}} in {@code ShortLinkController}.
 */
@RestController
@RequestMapping("/api/v2/urls/bulk")
public class BulkJobController {

    private final BulkJobService service;
    private final RateLimiter rateLimiter;
    private final int rateLimitPerWindow;
    private final Duration rateLimitWindow;

    public BulkJobController(
            BulkJobService service,
            RateLimiter rateLimiter,
            @Value("${app.ratelimit.bulk-create.limit}") int rateLimitPerWindow,
            @Value("${app.ratelimit.bulk-create.window-seconds}") long rateLimitWindowSeconds) {
        this.service = service;
        this.rateLimiter = rateLimiter;
        this.rateLimitPerWindow = rateLimitPerWindow;
        this.rateLimitWindow = Duration.ofSeconds(rateLimitWindowSeconds);
    }

    @PostMapping
    public ResponseEntity<CreateBulkUrlResponse> submit(
            @Valid @RequestBody CreateBulkUrlRequest request, @AuthenticationPrincipal Jwt jwt) {
        rateLimiter.checkLimit("ratelimit:bulk-create:" + jwt.getSubject(), rateLimitPerWindow, rateLimitWindow);
        BulkJob job = service.createJob(request.urls(), jwt.getSubject());
        // 202 Accepted, not 201 Created: the resources this request is ABOUT (the short links)
        // do not exist yet, only the job that will create them does.
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new CreateBulkUrlResponse(job.getId(), job.getStatus().name(), job.getTotalItems()));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<BulkJobStatusResponse> status(
            @PathVariable("jobId") Long jobId, @AuthenticationPrincipal Jwt jwt) {
        BulkJob job = service.getJob(jobId, jwt.getSubject());
        List<BulkJobItem> items = service.getItems(jobId);
        List<BulkJobItemResponse> itemResponses = items.stream()
                .map(item -> new BulkJobItemResponse(
                        item.getLineIndex(),
                        item.getLongUrl(),
                        item.getStatus().name(),
                        item.getShortCode(),
                        item.getErrorMessage()))
                .toList();
        BulkJobStatusResponse body = new BulkJobStatusResponse(
                job.getId(),
                job.getStatus().name(),
                job.getTotalItems(),
                job.getProcessedItems(),
                job.getFailedItems(),
                itemResponses);
        return ResponseEntity.ok(body);
    }

    @ExceptionHandler(BulkJobNotFoundException.class)
    public ResponseEntity<Void> handleNotFound() {
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimitExceeded(
            RateLimitExceededException e, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                OffsetDateTime.now(),
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                e.getMessage(),
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(e.getRetryAfterSeconds()))
                .body(body);
    }

    @ExceptionHandler(BulkSubmissionTooLargeException.class)
    public ResponseEntity<ErrorResponse> handleTooLarge(
            BulkSubmissionTooLargeException e, HttpServletRequest request) {
        return badRequest(e.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        return badRequest(e.getMessage(), request);
    }

    @ExceptionHandler(AmqpException.class)
    public ResponseEntity<ErrorResponse> handleMessagingFailure(AmqpException e, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                OffsetDateTime.now(),
                HttpStatus.SERVICE_UNAVAILABLE.value(),
                HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                "Could not enqueue the bulk job, please retry",
                request.getRequestURI());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    private ResponseEntity<ErrorResponse> badRequest(String message, HttpServletRequest request) {
        ErrorResponse body = new ErrorResponse(
                OffsetDateTime.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                message,
                request.getRequestURI());
        return ResponseEntity.badRequest().body(body);
    }
}
