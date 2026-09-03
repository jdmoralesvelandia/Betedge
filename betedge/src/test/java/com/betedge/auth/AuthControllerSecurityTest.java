package com.betedge.auth;

import static org.assertj.core.api.Assertions.assertThat;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Full-stack (real HTTP, real Spring Security filter chain, real embedded Postgres) verification
 * that the retired email+password register flow is actually gone at the HTTP layer, not just
 * unused by the frontend - the whole point of removing it was that it must never be reachable
 * "just in case", so this proves that against a real running app, not by reading the code.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class AuthControllerSecurityTest {

    private static EmbeddedPostgres embeddedPostgres;

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) throws IOException {
        embeddedPostgres = EmbeddedPostgres.builder().start();
        registry.add("spring.datasource.url", () -> embeddedPostgres.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @AfterAll
    static void stopEmbeddedPostgres() throws IOException {
        if (embeddedPostgres != null) {
            embeddedPostgres.close();
        }
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserRepository userRepository;

    @Test
    void registerEndpointNoLongerExistsAndNeverCreatesAnAccount() {
        long usersBefore = userRepository.count();
        Map<String, String> body = Map.of("email", "shouldnotexist@example.com", "password", "password1234");

        ResponseEntity<String> response = restTemplate.postForEntity("/auth/register", body, String.class);

        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("a request that used to create an account (201) must never succeed again")
                .isFalse();
        assertThat(userRepository.count()).isEqualTo(usersBefore);
        assertThat(userRepository.findByEmail("shouldnotexist@example.com")).isEmpty();
    }
}
