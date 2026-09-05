package com.artmendez.urlshortener.v2.shortlink.bulk.web;

import com.artmendez.urlshortener.v2.config.SecurityConfig;
import com.artmendez.urlshortener.v2.shortlink.bulk.domain.BulkJob;
import com.artmendez.urlshortener.v2.shortlink.bulk.domain.BulkJobItem;
import com.artmendez.urlshortener.v2.shortlink.bulk.domain.BulkJobItemStatus;
import com.artmendez.urlshortener.v2.shortlink.bulk.service.BulkJobNotFoundException;
import com.artmendez.urlshortener.v2.shortlink.bulk.service.BulkJobService;
import com.artmendez.urlshortener.v2.shortlink.bulk.service.BulkSubmissionTooLargeException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link BulkJobController}, same style as {@code ShortLinkControllerTest}:
 * real {@link SecurityConfig}, {@link BulkJobService} mocked.
 */
@WebMvcTest(controllers = BulkJobController.class)
@Import(SecurityConfig.class)
class BulkJobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private BulkJobService service;

    private BulkJob newJob(String ownerUserId, int totalItems, Long id) {
        BulkJob job = new BulkJob(ownerUserId, totalItems);
        ReflectionTestUtils.setField(job, "id", id);
        return job;
    }

    @BeforeEach
    void setUp() {
        when(service.createJob(any(), anyString())).thenReturn(newJob("user-123", 2, 42L));
    }

    @Test
    void submit_withoutAuthenticationIsRejected() throws Exception {
        mockMvc.perform(post("/api/v2/urls/bulk")
                        .contentType("application/json")
                        .content("{\"urls\":[{\"longUrl\":\"https://example.com\"}]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void submit_withAValidAuthenticatedRequestReturns202() throws Exception {
        mockMvc.perform(post("/api/v2/urls/bulk")
                        .with(jwt())
                        .contentType("application/json")
                        .content("{\"urls\":[{\"longUrl\":\"https://example.com/1\"},"
                                + "{\"longUrl\":\"https://example.com/2\"}]}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value(42))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    void submit_withAnEmptyUrlsListReturns400() throws Exception {
        mockMvc.perform(post("/api/v2/urls/bulk")
                        .with(jwt())
                        .contentType("application/json")
                        .content("{\"urls\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submit_whenTheServiceRejectsAnOversizedSubmissionReturns400() throws Exception {
        when(service.createJob(any(), anyString())).thenThrow(new BulkSubmissionTooLargeException(600, 500));

        mockMvc.perform(post("/api/v2/urls/bulk")
                        .with(jwt())
                        .contentType("application/json")
                        .content("{\"urls\":[{\"longUrl\":\"https://example.com\"}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void status_withoutAuthenticationIsRejected() throws Exception {
        mockMvc.perform(get("/api/v2/urls/bulk/42")).andExpect(status().isUnauthorized());
    }

    @Test
    void status_returnsTheJobAndItsItems() throws Exception {
        BulkJob job = newJob("user-123", 1, 42L);
        // BulkJobItem is read/create-only on this service's side (see its Javadoc): only
        // bulk-processor's own write-side entity has markCompleted. Set the fields directly,
        // same idiom as newJob() above for the generated id.
        BulkJobItem item = new BulkJobItem(42L, 0, "https://example.com", null);
        ReflectionTestUtils.setField(item, "status", BulkJobItemStatus.COMPLETED);
        ReflectionTestUtils.setField(item, "shortCode", "abc1234");
        when(service.getJob(eq(42L), anyString())).thenReturn(job);
        when(service.getItems(42L)).thenReturn(List.of(item));

        mockMvc.perform(get("/api/v2/urls/bulk/42").with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(42))
                .andExpect(jsonPath("$.items[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].shortCode").value("abc1234"));
    }

    @Test
    void status_whenTheJobDoesNotExistOrIsNotOwnedReturns404() throws Exception {
        when(service.getJob(eq(99L), anyString())).thenThrow(new BulkJobNotFoundException(99L));

        mockMvc.perform(get("/api/v2/urls/bulk/99").with(jwt())).andExpect(status().isNotFound());
    }
}
