package com.vigia.service;

import com.vigia.service.ServiceDtos.StatusCard;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Endpoint aberto, sem login, usado pela página pública de status. */
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
}
