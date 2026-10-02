package com.aives.common.security;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryTokenBlacklist implements TokenBlacklist {

    // tokenId (jti) -> thoi diem token het han
    private final Map<String, Instant> blacklistedTokens = new ConcurrentHashMap<>();

    @Override
    public void blacklist(String tokenId, Instant expiresAt) {
        removeExpired();
        if (tokenId != null && expiresAt != null) {
            blacklistedTokens.put(tokenId, expiresAt);
        }
    }

    @Override
    public boolean isBlacklisted(String tokenId) {
        return tokenId != null && blacklistedTokens.containsKey(tokenId);
    }

    private void removeExpired() {
        Instant now = Instant.now();
        blacklistedTokens.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }
}
