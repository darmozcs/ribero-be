package com.darmoz.ribero.controller;

import com.darmoz.ribero.dto.request.SyncRequest;
import com.darmoz.ribero.dto.response.SyncResponse;
import com.darmoz.ribero.security.AuthUser;
import com.darmoz.ribero.service.SyncService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rb/sync")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping
    public SyncResponse sync(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody SyncRequest request) {
        return syncService.sync(user, request);
    }
}
