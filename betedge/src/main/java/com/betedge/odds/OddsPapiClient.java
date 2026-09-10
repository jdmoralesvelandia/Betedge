package com.betedge.odds;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OddsPapiClient {

    /**
     * Neither timeout was ever set before this (2026-09-02) - the RestClient.Builder default
     * carries none, so a single slow OddsPapi response could block a whole ingestion run
     * indefinitely with no way out. Values aren't a measured minimum (unlike CALL_DELAY_MS in
     * IngestionService, which IS one) - just a generous ceiling: connect fails fast (5s) since a
     * TCP handshake to a reachable host normally completes in well under 1s, while read allows a
     * genuinely slow-but-alive response (25s) before giving up. A timeout here surfaces as a
     * RestClientException (SocketTimeoutException wrapped in ResourceAccessException, still a
     * RestClientException) - already caught per-call by IngestionService.fetchFixturesSafely
     * without aborting the rest of the run, so this fits the existing failure-handling design
     * without changing it.
     */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(25);

    private final RestClient restClient;
    private final String apiKey;

    public OddsPapiClient(
            RestClient.Builder restClientBuilder,
            @Value("${oddspapi.base-url}") String baseUrl,
            @Value("${oddspapi.key}") String apiKey) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .requestFactory(ClientHttpRequestFactoryBuilder.detect()
                        .build(HttpClientSettings.defaults()
                                .withConnectTimeout(CONNECT_TIMEOUT)
                                .withReadTimeout(READ_TIMEOUT)))
                .build();
        this.apiKey = apiKey;
    }

    /**
     * OddsPapi requires exactly one 'bookmaker' per call to /odds-by-tournaments (confirmed
     * against the real API: omitting it returns 400 INVALID_PARAMETER, "Please provide exactly
     * one bookmaker using the 'bookmaker' query parameter") - there is no "all bookmakers"
     * option. Callers must fetch once per bookmaker and merge the results.
     *
     * Also confirmed against the real API: at most 5 tournamentIds per call - a 6th returns 400
     * INVALID_PARAMETER, "Please provide a maximum of 5 tournament IDs". Callers with more than
     * 5 tracked tournaments must chunk and fetch once per (bookmaker, chunk) pair - see
     * IngestionService.fetchFixturesSafely.
     */
    public List<OddsPapiFixtureDto> fetchOddsByTournaments(List<String> tournamentIds, String bookmaker) {
        String tournamentIdsParam = String.join(",", tournamentIds);

        List<OddsPapiFixtureDto> fixtures = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/odds-by-tournaments")
                        .queryParam("tournamentIds", tournamentIdsParam)
                        .queryParam("bookmaker", bookmaker)
                        .queryParam("apiKey", apiKey)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<OddsPapiFixtureDto>>() {
                });

        return fixtures != null ? fixtures : List.of();
    }

    /**
     * GET /account - confirmed unmetered against OddsPapi's own docs
     * (oddspapi.io/en/docs/requests-and-quota, 2026-09-09): listed explicitly under "Unmetered
     * endpoints (do NOT count, and are never blocked)" and "always accessible, even after your
     * quota is exhausted" - safe to call before every scheduled ingestion run without spending
     * any of the budget it exists to protect. See IngestionService.runIngestion's quota guard.
     */
    public OddsPapiAccountDto fetchAccountInfo() {
        OddsPapiAccountDto account = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/account")
                        .queryParam("apiKey", apiKey)
                        .build())
                .retrieve()
                .body(OddsPapiAccountDto.class);

        return account != null ? account : new OddsPapiAccountDto(null, List.of());
    }

    /**
     * Confirmed against a real response: OddsPapi ignores any participant-id
     * filter and always returns the full participant directory for the given
     * sport (~19k entries for soccer), keyed by participant id as a string.
     * So there is no point asking for a subset - this always fetches (and
     * callers should cache) the whole thing.
     */
    public Map<String, String> fetchParticipantNames(int sportId) {
        Map<String, String> names = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/participants")
                        .queryParam("sportId", sportId)
                        .queryParam("apiKey", apiKey)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, String>>() {
                });

        return names != null ? names : Map.of();
    }
}
