package com.couponbot.couponbot.db.repo;

import com.couponbot.couponbot.db.entity.Coupon;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query; // Added for @Query

import java.time.Instant; // Added for threshold
import java.util.List;
import java.util.Optional;

public interface CouponRepo extends JpaRepository<Coupon, Long> {

    List<Coupon> findByStatusOrderBySubmittedAtDesc(Coupon.Status status, Pageable pageable);

    List<Coupon> findByStatusAndPlatformIgnoreCaseOrderBySubmittedAtDesc(
            Coupon.Status status, String platform, Pageable pageable
    );

    Optional<Coupon> findByIdAndStatus(Long id, Coupon.Status status);

    List<Coupon> findBySubmittedByOrderBySubmittedAtDesc(Long submittedBy, Pageable pageable);

    Optional<Coupon> findByIdAndStatusAndClaimedByIsNull(Long id, Coupon.Status status);

    List<Coupon> findByStatusAndClaimedByIsNullOrderBySubmittedAtDesc(Coupon.Status status, Pageable pageable);

    List<Coupon> findByStatusAndClaimedByIsNullAndPlatformIgnoreCaseOrderBySubmittedAtDesc(
            Coupon.Status status, String platform, Pageable pageable
    );

    // ✅ STEP 1: Count claims for a user in the last 24 hours
    long countByClaimedByAndClaimedAtAfter(Long userId, Instant threshold);

    // ✅ STEP 2: Find unique platforms that currently have AVAILABLE coupons
    @Query("SELECT DISTINCT c.platform FROM Coupon c WHERE c.status = 'AVAILABLE'")
    List<String> findDistinctPlatformsWithAvailableCoupons();
}