package com.betedge.odds;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookmakerRepository extends JpaRepository<Bookmaker, Long> {

    Optional<Bookmaker> findByExternalKey(String externalKey);
}
