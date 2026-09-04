package com.artmendez.urlshortener.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Temporary stub for {@code /api/v2/**}: the V2 service (microservices) does not exist yet
 * (it starts in Task #4, Greenfield scenario, see ARCHITECTURE.md section 6). Instead of
 * letting the Gateway return a generic, context-free 404 for these routes, this stub
 * responds with a 501 and an explicit message. It is removed once the Gateway actually
 * routes to the real V2 microservices.
 */
@RestController
public class V2StubController {

    @RequestMapping("/api/v2/**")
    public ResponseEntity<Map<String, String>> notImplementedYet() {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(Map.of(
                        "message",
                        "V2 not implemented yet. See ARCHITECTURE.md, section 6 (Scenario A) "
                                + "and section 7 (day-by-day plan)."
                ));
    }
}
