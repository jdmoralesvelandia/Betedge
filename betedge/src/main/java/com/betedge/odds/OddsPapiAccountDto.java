package com.betedge.odds;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * GET /account response - confirmed against OddsPapi's own docs (oddspapi.io/en/docs/get-account,
 * 2026-09-09), not assumed. Unlike /odds-by-tournaments and /participants (whose real JSON keys
 * are already camelCase, matching OddsPapiFixtureDto's field names with zero annotations), this
 * endpoint's real JSON uses snake_case (request_count, request_limit, etc.) - there's no global
 * Jackson naming-strategy bean anywhere in this project (confirmed via search), so every field
 * here needs an explicit @JsonProperty or it silently deserializes to null.
 *
 * request_count/request_limit live INSIDE each entry of the subscriptions array, not at the top
 * level - current_subscription_id says which entry is the active one to read (an account can
 * carry more than one subscription record, e.g. an expired past plan alongside the current one),
 * so remainingRequests() below matches by id rather than assuming index 0.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OddsPapiAccountDto(
        @JsonProperty("current_subscription_id") String currentSubscriptionId,
        List<Subscription> subscriptions) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Subscription(
            @JsonProperty("subscription_id") String subscriptionId,
            @JsonProperty("request_limit") Integer requestLimit,
            @JsonProperty("request_count") Integer requestCount) {

        Integer remaining() {
            return requestLimit != null && requestCount != null ? requestLimit - requestCount : null;
        }
    }

    /**
     * Remaining calls under the account's CURRENT subscription, or {@code null} if the response
     * shape didn't match what's documented (no subscriptions, no match for
     * currentSubscriptionId, or either count missing) - null means "couldn't confirm", never a
     * guessed number.
     */
    Integer remainingRequests() {
        if (subscriptions == null || currentSubscriptionId == null) {
            return null;
        }
        return subscriptions.stream()
                .filter(s -> currentSubscriptionId.equals(s.subscriptionId()))
                .findFirst()
                .map(Subscription::remaining)
                .orElse(null);
    }
}
