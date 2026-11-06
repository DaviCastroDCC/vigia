package com.vigia.service;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MonitoredServiceRepository extends JpaRepository<MonitoredService, Long> {

    List<MonitoredService> findByOwnerIdOrderByNameAsc(Long ownerId);

    Optional<MonitoredService> findByIdAndOwnerId(Long id, Long ownerId);

    long countByOwnerId(Long ownerId);

    List<MonitoredService> findByActiveTrue();

    List<MonitoredService> findByPublicPageTrueOrderByNameAsc();

    List<MonitoredService> findByOwnerIdAndPublicPageTrueOrderByNameAsc(Long ownerId);
}
