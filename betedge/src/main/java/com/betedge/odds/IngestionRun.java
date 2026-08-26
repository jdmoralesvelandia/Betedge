package com.betedge.odds;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only history of ingestion cycles, scheduled or manual - never updated after insert. */
@Entity
@Table(name = "ingestion_run")
@Getter
@Setter
@NoArgsConstructor
public class IngestionRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Which ingestion pipeline produced this run - reuses Odds.dataSource's enum since the set of providers is the same. */
    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false)
    private DataSource provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "triggered_by", nullable = false)
    private TriggeredBy triggeredBy;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at", nullable = false)
    private Instant finishedAt;

    @Column(name = "total_events_received", nullable = false)
    private int totalEventsReceived;

    @Column(name = "total_new_matches", nullable = false)
    private int totalNewMatches;

    @Column(name = "total_new_odds", nullable = false)
    private int totalNewOdds;

    @Column(name = "value_bets_detected", nullable = false)
    private int valueBetsDetected;

    @Column(name = "surebets_detected", nullable = false)
    private int surebetsDetected;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "competition_breakdown", nullable = false, columnDefinition = "jsonb")
    private List<CompetitionBreakdownEntry> competitionBreakdown;
}
