package com.betedge.matches;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "competition")
@Getter
@Setter
@NoArgsConstructor
public class Competition {

    /** Key into {@link #externalKeys} for the OddsPapi tournamentId - the primary, in-production source. */
    public static final String ODDSPAPI_KEY = "oddspapi";

    /** Key into {@link #externalKeys} for The Odds API sport key - complementary source, absent for leagues it doesn't cover. */
    public static final String THEODDSAPI_KEY = "theoddsapi";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sport_id", nullable = false)
    private Sport sport;

    @Column(nullable = false)
    private String name;

    /**
     * Per-provider tournament/league identifier, e.g. {"oddspapi": "17", "theoddsapi": "soccer_epl"}.
     * A provider's key is simply absent from the map for competitions not tracked through it -
     * every row has "oddspapi" (the only source in production so far), "theoddsapi" is optional.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "external_keys", nullable = false, columnDefinition = "jsonb")
    private Map<String, String> externalKeys;
}
