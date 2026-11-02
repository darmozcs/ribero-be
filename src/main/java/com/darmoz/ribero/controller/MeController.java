package com.darmoz.ribero.controller;

import com.darmoz.ribero.dto.response.MeResponse;
import com.darmoz.ribero.security.AuthUser;
import com.darmoz.ribero.service.AccessService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rb/me")
public class MeController {

    private final AccessService accessService;

    public MeController(AccessService accessService) {
        this.accessService = accessService;
    }

    @GetMapping
    public MeResponse me(@AuthenticationPrincipal AuthUser user) {
        return new MeResponse(user.email(), user.roles(), user.isAdmin(), accessService.localesPermitidos(user));
    }
}
