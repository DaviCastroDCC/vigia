package com.vigia.service;

import com.vigia.service.ServiceDtos.StatusCard;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;

/** Endpoints abertos, sem login: página pública de status e badges. */
@RestController
@RequestMapping("/api/public")
@SecurityRequirements
public class PublicStatusController {

    private final MonitoredServiceManager manager;

    public PublicStatusController(MonitoredServiceManager manager) {
        this.manager = manager;
    }

    @GetMapping("/status")
    public List<StatusCard> status() {
        return manager.publicStatus();
    }

    /**
     * Badge SVG de uptime para colar em README: {@code ![uptime](https://.../api/public/services/1/badge.svg)}.
     * Só funciona para serviços marcados como públicos.
     */
    @GetMapping(value = "/services/{id}/badge.svg", produces = "image/svg+xml")
    public ResponseEntity<String> badge(@PathVariable Long id, @RequestParam(defaultValue = "30") int days) {
        var uptime = manager.publicUptime(id, days);
        String label = "uptime " + (uptime.periodHours() / 24) + "d";
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(UptimeBadge.render(label, uptime.uptimePercent()));
    }
}
