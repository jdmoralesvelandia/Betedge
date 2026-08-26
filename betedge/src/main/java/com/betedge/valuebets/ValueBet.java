package com.betedge.valuebets;

import com.betedge.matches.Match;
import com.betedge.odds.Bookmaker;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "value_bet")
@Getter
@Setter
@NoArgsConstructor
public class ValueBet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bookmaker_id", nullable = false)
    private Bookmaker bookmaker;

    @Column(name = "selection", nullable = false)
    private String selection;

    @Column(name = "implied_probability", nullable = false, precision = 10, scale = 4)
    private BigDecimal impliedProbability;

    @Column(name = "estimated_true_probability", nullable = false, precision = 10, scale = 4)
    private BigDecimal estimatedTrueProbability;

    @Column(name = "edge_percentage", nullable = false, precision = 10, scale = 4)
    private BigDecimal edgePercentage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "bookmaker_probabilities", columnDefinition = "jsonb")
    private Map<String, BigDecimal> bookmakerProbabilities;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt = Instant.now();
}
