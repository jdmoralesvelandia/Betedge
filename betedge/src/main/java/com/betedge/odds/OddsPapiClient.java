package com.betedge.odds;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class OddsPapiClient {

    private final RestClient restClient;
    private final String apiKey;

    public OddsPapiClient(
            RestClient.Builder restClientBuilder,
            @Value("${oddspapi.base-url}") String baseUrl,
            @Value("${oddspapi.key}") String apiKey) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
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
