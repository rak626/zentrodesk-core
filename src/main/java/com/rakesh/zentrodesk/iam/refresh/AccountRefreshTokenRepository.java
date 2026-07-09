package com.rakesh.zentrodesk.iam.refresh;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountRefreshTokenRepository extends JpaRepository<AccountRefreshToken, UUID> {

    Optional<AccountRefreshToken> findByToken(String token);
}
