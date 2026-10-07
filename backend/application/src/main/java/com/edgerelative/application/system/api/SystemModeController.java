package com.edgerelative.application.system.api;

import com.edgerelative.application.system.SystemModeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Broker-neutral system posture API. {@code /mode} is authoritative read-only state: the declared
 * trading mode and the persisted safety controls. It creates no orders and grants no authority.
 */
@RestController
@RequestMapping("/api/v1/system")
public class SystemModeController {

    private final SystemModeService service;

    public SystemModeController(SystemModeService service) {
        this.service = service;
    }

    @GetMapping("/mode")
    public SystemModeResponse mode() {
        return service.current();
    }
}
