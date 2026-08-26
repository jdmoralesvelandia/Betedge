package com.betedge.matches;

public record CompetitionResponse(Long id, String name) {

    static CompetitionResponse from(Competition competition) {
        return new CompetitionResponse(competition.getId(), competition.getName());
    }
}
