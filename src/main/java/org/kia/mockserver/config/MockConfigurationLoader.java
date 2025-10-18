package org.kia.mockserver.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.kia.mockserver.model.MockEndpoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Configuration
public class MockConfigurationLoader {

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final CopyOnWriteArrayList<MockEndpoint> endpoints = new CopyOnWriteArrayList<>();

    @Value("${mock.config.file:mock-config.json}")
    private String configFile;

    @Value("${mock.config.watch:true}")
    private boolean watchConfig;

    private WatchService watchService;
    private Thread watchThread;

    public MockConfigurationLoader(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void init() {
        loadConfiguration();
    }

    @Bean
    public List<MockEndpoint> mockEndpoints() {
        return endpoints;
    }

    private void loadConfiguration() {
        try {
            File file = new File(configFile);

            if (!file.exists()) {
                // Try to load from classpath
                Resource resource = resourceLoader.getResource("classpath:" + configFile);
                if (resource.exists()) {
                    loadFromResource(resource);
                } else {
                    log.warn("Configuration file not found: {}. Creating default configuration.", configFile);
                    createDefaultConfiguration(file);
                    loadFromFile(file);
                }
            } else {
                loadFromFile(file);
            }

        } catch (Exception e) {
            log.error("Error loading configuration", e);
            loadDefaultEndpoints();
        }
    }

    private void loadFromFile(File file) throws IOException {
        log.info("Loading mock configuration from: {}", file.getAbsolutePath());

        Map<String, Object> config = objectMapper.readValue(file, new TypeReference<Map<String, Object>>() {});

        if (config.containsKey("endpoints")) {
            List<MockEndpoint> newEndpoints = objectMapper.convertValue(
                config.get("endpoints"),
                new TypeReference<List<MockEndpoint>>() {}
            );

            endpoints.clear();
            endpoints.addAll(newEndpoints);

            log.info("Loaded {} mock endpoints from {}", endpoints.size(), file.getName());
            endpoints.forEach(endpoint ->
                log.info("  {} {}", endpoint.getMethod() != null ? endpoint.getMethod() : "ANY", endpoint.getPath())
            );
        }
    }

    private void loadFromResource(Resource resource) throws IOException {
        log.info("Loading mock configuration from classpath: {}", resource.getFilename());

        Map<String, Object> config = objectMapper.readValue(
            resource.getInputStream(),
            new TypeReference<Map<String, Object>>() {}
        );

        if (config.containsKey("endpoints")) {
            List<MockEndpoint> newEndpoints = objectMapper.convertValue(
                config.get("endpoints"),
                new TypeReference<List<MockEndpoint>>() {}
            );

            endpoints.clear();
            endpoints.addAll(newEndpoints);

            log.info("Loaded {} mock endpoints", endpoints.size());
        }
    }

    private void createDefaultConfiguration(File file) throws IOException {
        Map<String, Object> defaultConfig = Map.of(
            "endpoints", List.of(
                Map.of(
                    "path", "/api/health",
                    "method", "GET",
                    "response", Map.of(
                        "status", 200,
                        "body", Map.of(
                            "status", "UP",
                            "timestamp", System.currentTimeMillis()
                        )
                    )
                ),
                Map.of(
                    "path", "/api/users",
                    "method", "GET",
                    "response", Map.of(
                        "status", 200,
                        "body", List.of(
                            Map.of("id", 1, "name", "John Doe", "email", "john@example.com"),
                            Map.of("id", 2, "name", "Jane Smith", "email", "jane@example.com")
                        )
                    )
                )
            )
        );

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, defaultConfig);
        log.info("Created default configuration file: {}", file.getAbsolutePath());
    }

    private void loadDefaultEndpoints() {
        MockEndpoint healthEndpoint = new MockEndpoint();
        healthEndpoint.setPath("/api/health");
        healthEndpoint.setMethod("GET");

        MockEndpoint.MockResponse healthResponse = new MockEndpoint.MockResponse();
        healthResponse.setStatus(200);
        healthResponse.setBody(Map.of("status", "UP"));
        healthEndpoint.setResponse(healthResponse);

        endpoints.clear();
        endpoints.add(healthEndpoint);

        log.info("Loaded default endpoints");
    }

}

