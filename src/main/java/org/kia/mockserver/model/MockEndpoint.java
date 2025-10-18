package org.kia.mockserver.model;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class MockEndpoint {
    private String path;
    private String method;
    private MockResponse response;
    private List<ConditionalResponse> conditionalResponses;
    private Integer delay;
    private Boolean randomDelay;
    private Integer minDelay;
    private Integer maxDelay;

    @Data
    public static class MockResponse {
        private Integer status;
        private Object body;
        private Map<String, String> headers;
    }

    @Data
    public static class ConditionalResponse {
        private Map<String, Object> condition;
        private MockResponse response;
    }
}