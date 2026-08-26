package com.betedge.odds;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Isolated client for The Odds API - the complementary source (see README-notas.md), used only
 * for a handful of the tracked leagues, never wired into IngestionService/MatchReconciliationService
 * yet. That integration is a later step; this class only fetches and parses.
 */
@Component
public class TheOddsApiClient {

    private static final Logger log = LoggerFactory.getLogger(TheOddsApiClient.class);
    private static final String H2H_MARKET_KEY = "h2h";

    private final RestClient restClient;
    private final String apiKey;

    /**
     * The x-requests-remaining value from the most recent real call (by any caller - normal
     * ingestion or a previous hot refresh) - empty until the first call ever completes. The Odds
     * API has no separate "check my quota" endpoint that doesn't itself cost a credit, so this
     * lagging, last-observed value is the only pre-flight signal HotMatchRefreshService can use
     * before deciding to spend another one.
     */
    private final AtomicReference<Integer> lastKnownRequestsRemaining = new AtomicReference<>();

    public TheOddsApiClient(
            RestClient.Builder restClientBuilder,
            @Value("${theoddsapi.base-url}") String baseUrl,
            @Value("${theoddsapi.key}") String apiKey) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
    }

    /**
     * GET /v4/sports/{sportKey}/odds?regions=eu&markets=h2h&oddsFormat=decimal. Some bookmakers
     * (e.g. Betfair exchange, key "betfair_ex_eu") return a second market, "h2h_lay" (the price
     * for betting AGAINST an outcome), alongside "h2h" (the back price we actually want) - even
     * though we already ask for markets=h2h. Confirmed against a real response, saved at
     * exploracion-odds-api/odds_sample.json. Every bookmaker's markets list is filtered down to
     * "h2h" only before this method returns, so no downstream code can ever read a lay price as
     * if it were a normal price.
     */
    public List<TheOddsApiEventDto> fetchOdds(String sportKey) {
        ResponseEntity<List<TheOddsApiEventDto>> response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/sports/{sportKey}/odds")
                        .queryParam("apiKey", apiKey)
                        .queryParam("regions", "eu")
                        .queryParam("markets", H2H_MARKET_KEY)
                        .queryParam("oddsFormat", "decimal")
                        .build(sportKey))
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<TheOddsApiEventDto>>() {
                });

        String remaining = response.getHeaders().getFirst("x-requests-remaining");
        log.info("The Odds API requests remaining: {}", remaining);
        updateLastKnownRequestsRemaining(remaining);

        List<TheOddsApiEventDto> events = response.getBody();
        return events == null ? List.of() : events.stream().map(TheOddsApiClient::keepOnlyH2hMarkets).toList();
    }

    /** Empty if no call has completed yet (e.g. right after a fresh deploy) - see {@link #lastKnownRequestsRemaining}. */
    public Optional<Integer> lastKnownRequestsRemaining() {
        return Optional.ofNullable(lastKnownRequestsRemaining.get());
    }

    private void updateLastKnownRequestsRemaining(String headerValue) {
        if (headerValue == null) {
            return;
        }
        try {
            lastKnownRequestsRemaining.set(Integer.valueOf(headerValue));
        } catch (NumberFormatException e) {
            log.warn("Couldn't parse x-requests-remaining header value '{}'", headerValue);
        }
    }

    private static TheOddsApiEventDto keepOnlyH2hMarkets(TheOddsApiEventDto event) {
        List<TheOddsApiBookmakerDto> filteredBookmakers = event.bookmakers() == null
                ? List.of()
                : event.bookmakers().stream().map(TheOddsApiClient::keepOnlyH2hMarkets).toList();
        return new TheOddsApiEventDto(
                event.id(), event.sportKey(), event.sportTitle(), event.commenceTime(),
                event.homeTeam(), event.awayTeam(), filteredBookmakers);
    }

    private static TheOddsApiBookmakerDto keepOnlyH2hMarkets(TheOddsApiBookmakerDto bookmaker) {
        List<TheOddsApiMarketDto> h2hOnly = bookmaker.markets() == null
                ? List.of()
                : bookmaker.markets().stream().filter(m -> H2H_MARKET_KEY.equals(m.key())).toList();
        return new TheOddsApiBookmakerDto(bookmaker.key(), bookmaker.title(), bookmaker.lastUpdate(), h2hOnly);
    }
}
