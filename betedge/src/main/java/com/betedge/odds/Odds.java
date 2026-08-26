package com.betedge.odds;

import com.betedge.matches.Match;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

/**
 * Append-only: rows are inserted, never updated. {@link Immutable} tells
 * Hibernate to never issue UPDATE/DELETE statements for this entity.
 */
@Entity
@Table(
        name = "odds",
        indexes = @Index(
                name = "idx_odds_match_bookmaker_timestamp",
                columnList = "match_id, bookmaker_id, timestamp"
        )
)
@Immutable
@Getter
@Setter
@NoArgsConstructor
public class Odds {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bookmaker_id", nullable = false)
    private Bookmaker bookmaker;

    @Column(name = "market_type", nullable = false)
    private String marketType;

    @Column(name = "selection", nullable = false)
    private String selection;

    @Column(name = "odd_value", nullable = false, precision = 10, scale = 4)
    private BigDecimal oddValue;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp = Instant.now();

    /** Which provider this snapshot came from - debugging/audit trail, same spirit as ValueBet.bookmakerProbabilities. */
    @Enumerated(EnumType.STRING)
    @Column(name = "data_source", nullable = false)
    private DataSource dataSource;
}
