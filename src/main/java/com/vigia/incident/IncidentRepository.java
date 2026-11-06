package com.vigia.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    Optional<Incident> findFirstByServiceIdAndResolvedAtIsNull(Long serviceId);

    List<Incident> findTop5ByServiceIdAndStartedAtAfterOrderByStartedAtDesc(Long serviceId, Instant since);
}
