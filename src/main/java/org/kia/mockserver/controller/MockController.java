package org.kia.mockserver.controller;


import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.kia.mockserver.model.MockEndpoint;
import org.kia.mockserver.service.MockService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
public class MockController {

    @Autowired
    private MockService mockService;

    @RequestMapping(value = "/**", method = {RequestMethod.GET, RequestMethod.POST,
        RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.PATCH})
    public ResponseEntity<?> handleRequest(
        HttpServletRequest request,
        @RequestBody(required = false) Map<String, Object> body,
        @RequestParam Map<String, String> params,
        @RequestHeader Map<String, String> headers) {

        String path = request.getRequestURI();
        String method = request.getMethod();

        log.info("Received {} request to {}", method, path);
        if (body != null && !body.isEmpty()) {
            log.debug("Request body: {}", body);
        }
        if (!params.isEmpty()) {
            log.debug("Request params: {}", params);
        }

        try {
            MockEndpoint endpoint = mockService.findEndpoint(path, method);

            if (endpoint == null) {
                log.warn("No mock endpoint found for {} {}", method, path);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                        "error", "Mock endpoint not found",
                        "path", path,
                        "method", method
                    ));
            }

            // Apply delay if configured
            applyDelay(endpoint);

            // Get appropriate response (default or conditional)
            MockEndpoint.MockResponse response = mockService.getResponse(endpoint, body, params, headers);

            if (response == null) {
                log.error("No response configured for endpoint {} {}", method, path);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "No response configured"));
            }

            // Build response
            int status = response.getStatus() != null ? response.getStatus() : 200;
            ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.status(status);

            if (response.getHeaders() != null) {
                response.getHeaders().forEach(responseBuilder::header);
            }

            log.info("Returning {} response for {} {}", status, method, path);
            return responseBuilder.body(response.getBody());

        } catch (Exception e) {
            log.error("Error processing mock request", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "error", "Internal mock server error",
                    "message", e.getMessage()
                ));
        }
    }

    @GetMapping("/mock-admin/endpoints")
    public ResponseEntity<?> getEndpoints() {
        return ResponseEntity.ok(mockService.getAllEndpoints());
    }

    @PostMapping("/mock-admin/reload")
    public ResponseEntity<?> reloadConfiguration() {
        try {
            mockService.reloadConfiguration();
            return ResponseEntity.ok(Map.of(
                "message", "Configuration reloaded successfully",
                "endpoints", mockService.getAllEndpoints().size()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to reload configuration"));
        }
    }

    private void applyDelay(MockEndpoint endpoint) throws InterruptedException {
        if (endpoint.getDelay() != null && endpoint.getDelay() > 0) {
            log.debug("Applying delay of {}ms", endpoint.getDelay());
            TimeUnit.MILLISECONDS.sleep(endpoint.getDelay());
        } else if (Boolean.TRUE.equals(endpoint.getRandomDelay())) {
            int min = endpoint.getMinDelay() != null ? endpoint.getMinDelay() : 100;
            int max = endpoint.getMaxDelay() != null ? endpoint.getMaxDelay() : 1000;
            int delay = min + (int)(Math.random() * (max - min));
            log.debug("Applying random delay of {}ms", delay);
            TimeUnit.MILLISECONDS.sleep(delay);
        }
    }
}
