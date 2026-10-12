package com.aives.modules.auth.repository;

import com.aives.modules.auth.entity.OtpVerification;
import com.aives.modules.auth.enums.OtpType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OtpVerificationRepository extends JpaRepository<OtpVerification, String> {

    Optional<OtpVerification> findTopByEmailAndTypeOrderByCreatedAtDesc(String email, OtpType type);

    Optional<OtpVerification> findTopByEmailAndTypeAndVerifiedFalseOrderByCreatedAtDesc(String email, OtpType type);
}
