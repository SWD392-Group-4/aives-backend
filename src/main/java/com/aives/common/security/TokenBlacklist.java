package com.aives.common.security;

import java.time.Instant;

/**
 * Danh sach token da logout. Hien tai luu trong RAM, sau nay co the doi sang Redis.
 */
public interface TokenBlacklist {

    void blacklist(String tokenId, Instant expiresAt);

    boolean isBlacklisted(String tokenId);
}
