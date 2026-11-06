package com.vigia.service;

import com.vigia.security.AuthUser;
import com.vigia.service.ServiceDtos.CheckResponse;
import com.vigia.service.ServiceDtos.ServiceRequest;
import com.vigia.service.ServiceDtos.ServiceResponse;
import com.vigia.service.ServiceDtos.StatusCard;
import com.vigia.service.ServiceDtos.UptimeResponse;
import jakarta.validation.Valid;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/services")
public class MonitoredServiceController {

    private final MonitoredServiceManager manager;

    public MonitoredServiceController(MonitoredServiceManager manager) {
        this.manager = manager;
    }

    @GetMapping
    public List<ServiceResponse> list(@AuthenticationPrincipal AuthUser user) {
        return manager.list(user.id());
    }

    /** Dados do painel: todos os seus serviços com uptime e últimas verificações. */
    @GetMapping("/dashboard")
    public List<StatusCard> dashboard(@AuthenticationPrincipal AuthUser user) {
        return manager.dashboard(user.id());
    }

    @GetMapping("/{id}")
    public ServiceResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return manager.get(user.id(), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceResponse create(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody ServiceRequest request) {
        return manager.create(user.id(), request);
    }

    @PutMapping("/{id}")
    public ServiceResponse update(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                  @Valid @RequestBody ServiceRequest request) {
        return manager.update(user.id(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        manager.delete(user.id(), id);
    }

    @GetMapping("/{id}/checks")
    public PagedModel<CheckResponse> history(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "50") int size) {
        return new PagedModel<>(manager.history(user.id(), id, page, size));
    }

    @GetMapping("/{id}/uptime")
    public UptimeResponse uptime(@AuthenticationPrincipal AuthUser user, @PathVariable Long id,
                                 @RequestParam(defaultValue = "24") int hours) {
        return manager.uptime(user.id(), id, hours);
    }

    @PostMapping("/{id}/check")
    public CheckResponse checkNow(@AuthenticationPrincipal AuthUser user, @PathVariable Long id) {
        return manager.checkNow(user.id(), id);
    }
}
