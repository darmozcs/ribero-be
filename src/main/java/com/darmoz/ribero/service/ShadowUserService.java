package com.darmoz.ribero.service;

import com.darmoz.ribero.entity.ShadowUser;
import com.darmoz.ribero.repository.ShadowUserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ShadowUserService {

    private final ShadowUserRepository shadowUserRepository;

    public ShadowUserService(ShadowUserRepository shadowUserRepository) {
        this.shadowUserRepository = shadowUserRepository;
    }

    public void upsert(UUID userId, String email, Set<String> roles) {
        shadowUserRepository.upsert(userId, email, String.join(",", roles));
    }

    public List<ShadowUser> listAll() {
        return shadowUserRepository.findAllByOrderByEmailAsc();
    }
}
