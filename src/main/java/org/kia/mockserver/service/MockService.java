package org.kia.mockserver.service;


import lombok.extern.slf4j.Slf4j;
import org.kia.mockserver.config.MockConfigurationLoader;
import org.kia.mockserver.model.MockEndpoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class MockService {

    @Autowired
    private List<MockEndpoint> mockEndpoints;

    @Autowired
    private MockConfigurationLoader configurationLoader;

    public MockEndpoint findEndpoint(String path, String method) {
        return mockEndpoints.stream()
            .filter(endpoint -> methodMatches(endpoint.getMethod(), method))
            .filter(endpoint -> pathMatches(endpoint.getPath(), path))
            .findFirst()
            .orElse(null);
    }

    public MockEndpoint.MockResponse getResponse(
        MockEndpoint endpoint,
        Map<String, Object> body,
        Map<String, String> params,
        Map<String, String> headers) {

        // Check conditional responses first
        if (endpoint.getConditionalResponses() != null) {
            for (MockEndpoint.ConditionalResponse conditional : endpoint.getConditionalResponses()) {
                if (conditionMatches(conditional.getCondition(), body, params, headers)) {
                    log.info("Matched conditional response with condition: {}", conditional.getCondition());
                    return conditional.getResponse();
                }
            }
        }

        // Return default response
        return endpoint.getResponse();
    }

    public List<MockEndpoint> getAllEndpoints() {
        return mockEndpoints;
    }

    public void reloadConfiguration() {
        configurationLoader.init();
    }

    private boolean methodMatches(String endpointMethod, String requestMethod) {
        if (endpointMethod == null || endpointMethod.equals("*") || endpointMethod.isEmpty()) {
            return true;
        }
        return endpointMethod.equalsIgnoreCase(requestMethod);
    }

    private boolean pathMatches(String endpointPath, String requestPath) {
        // Exact match
        if (endpointPath.equals(requestPath)) {
            return true;
        }

        // Pattern matching for path parameters and wildcards
        String regexPattern = endpointPath
            .replaceAll("\\{[^}]+\\}", "([^/]+)")   // Replace {param} with capture group
            .replaceAll("\\*\\*", ".*")              // Replace ** with match all
            .replaceAll("\\*", "[^/]+");             // Replace * with match segment

        Pattern pattern = Pattern.compile("^" + regexPattern + "$");
        Matcher matcher = pattern.matcher(requestPath);

        return matcher.matches();
    }

    private boolean conditionMatches(
        Map<String, Object> condition,
        Map<String, Object> body,
        Map<String, String> params,
        Map<String, String> headers) {

        if (condition == null) {
            return false;
        }

        // Check body conditions
        if (condition.containsKey("body")) {
            Object bodyCondition = condition.get("body");
            if (bodyCondition instanceof Map) {
                if (!matchesCondition((Map<String, Object>) bodyCondition, body)) {
                    return false;
                }
            }
        }

        // Check params conditions
        if (condition.containsKey("params")) {
            Object paramsCondition = condition.get("params");
            if (paramsCondition instanceof Map) {
                if (!matchesCondition((Map<String, Object>) paramsCondition, params)) {
                    return false;
                }
            }
        }

        // Check headers conditions
        if (condition.containsKey("headers")) {
            Object headersCondition = condition.get("headers");
            if (headersCondition instanceof Map) {
                Map<String, Object> headerConditionMap = (Map<String, Object>) headersCondition;
                // Case-insensitive header matching
                for (Map.Entry<String, Object> entry : headerConditionMap.entrySet()) {
                    String headerValue = getCaseInsensitiveHeader(headers, entry.getKey());
                    if (!objectsEqual(entry.getValue(), headerValue)) {
                        return false;
                    }
                }
            }
        }

        return true;
    }

    private String getCaseInsensitiveHeader(Map<String, String> headers, String key) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean matchesCondition(Map<String, Object> condition, Map<?, ?> data) {
        if (data == null) {
            return condition.isEmpty();
        }

        for (Map.Entry<String, Object> entry : condition.entrySet()) {
            Object dataValue = data.get(entry.getKey());
            Object conditionValue = entry.getValue();

            if (conditionValue instanceof Map && dataValue instanceof Map) {
                // Recursive check for nested objects
                if (!matchesCondition((Map<String, Object>) conditionValue, (Map<?, ?>) dataValue)) {
                    return false;
                }
            } else {
                if (!objectsEqual(conditionValue, dataValue)) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean objectsEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;

        // Convert to string for comparison to handle type differences
        return a.toString().equals(b.toString());
    }
}
