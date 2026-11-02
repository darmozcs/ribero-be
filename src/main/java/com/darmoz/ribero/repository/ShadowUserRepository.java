package com.darmoz.ribero.repository;

import com.darmoz.ribero.entity.ShadowUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShadowUserRepository extends JpaRepository<ShadowUser, UUID> {

    List<ShadowUser> findAllByOrderByEmailAsc();

    /**
     * Upsert atomico via ON CONFLICT: evita el race read-then-write cuando el
     * mismo usuario dispara varios requests concurrentes (cada uno con su
     * propio filtro de autenticacion intentando "actualizar" el shadow user).
     *
     * El conflicto se resuelve por email, no por user_id: email es la
     * identidad estable para esta app, mientras que user_id es un UUID de
     * darmoz-auth que puede reemitirse (si esa cuenta se borra y se vuelve a
     * registrar con el mismo email, login/refresh dan un user_id nuevo). Si
     * eso pasa, esta fila adopta el user_id nuevo en vez de chocar contra el
     * UNIQUE de email. Las tablas de ribero referencian al usuario por email,
     * asi que el cambio de user_id no las afecta.
     */
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO rb_users (user_id, email, roles, last_seen_at, created_at)
            VALUES (:userId, :email, :roles, now(), now())
            ON CONFLICT (email) DO UPDATE
                SET user_id = EXCLUDED.user_id,
                    roles = EXCLUDED.roles,
                    last_seen_at = now()
            """, nativeQuery = true)
    void upsert(UUID userId, String email, String roles);
}
