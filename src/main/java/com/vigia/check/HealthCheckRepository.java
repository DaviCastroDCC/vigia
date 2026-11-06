package com.vigia.check;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface HealthCheckRepository extends JpaRepository<HealthCheck, Long> {

    Page<HealthCheck> findByServiceIdOrderByCheckedAtDesc(Long serviceId, Pageable pageable);

    List<HealthCheck> findTop60ByServiceIdOrderByCheckedAtDesc(Long serviceId);

    @Query("""
            select new com.vigia.check.UptimeStats(
                count(c),
                sum(case when c.up = true then 1L else 0L end),
                avg(c.responseTimeMs))
            from HealthCheck c
            where c.service.id = :serviceId and c.checkedAt >= :since
            """)
    UptimeStats uptimeStats(@Param("serviceId") Long serviceId, @Param("since") Instant since);
}
