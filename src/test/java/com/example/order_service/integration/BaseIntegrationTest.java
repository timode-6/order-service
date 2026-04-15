package com.example.order_service.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Testcontainers
public abstract class BaseIntegrationTest {

    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:12-alpine")
                    .withDatabaseName("testdb")
                    .withUsername("test")
                    .withPassword("test");

    static final WireMockServer wireMock =
            new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());

    static {
        postgres.start();
        wireMock.start(); 
        
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username",  postgres::getUsername);
        registry.add("spring.datasource.password",  postgres::getPassword);
        registry.add("spring.datasource.hikari.maximum-pool-size",           () -> 5);
        registry.add("spring.datasource.hikari.connection-timeout",          () -> 20000);
        registry.add("spring.datasource.hikari.initialization-fail-timeout", () -> 60000);

        registry.add("services.user-service.base-url",
                () -> "http://localhost:" + wireMock.port());
    }

    @AfterEach
    void resetWireMock() {
        wireMock.resetAll();
     
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected TestRestTemplate restTemplate;

    protected String baseUrl(String path) {
        return "http://localhost:" + port + path;
    }
}