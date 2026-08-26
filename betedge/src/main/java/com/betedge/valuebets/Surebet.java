package com.betedge.valuebets;

import com.betedge.matches.Match;
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
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "surebet")
@Getter
@Setter
@NoArgsConstructor
public class Surebet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "legs", nullable = false, columnDefinition = "jsonb")
    private List<SurebetLeg> legs;

    @Column(name = "total_implied_probability", nullable = false, precision = 10, scale = 4)
    private BigDecimal totalImpliedProbability;

    @Column(name = "profit_percentage", nullable = false, precision = 10, scale = 4)
    private BigDecimal profitPercentage;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt = Instant.now();
}
