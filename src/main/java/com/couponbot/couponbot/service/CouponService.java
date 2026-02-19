package com.couponbot.couponbot.service;

import com.couponbot.couponbot.db.entity.Coupon;
import com.couponbot.couponbot.db.repo.CouponRepo;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit; // Added for time math
import java.util.List;
import java.util.Optional;

@Service
public class CouponService {

    private final CouponRepo couponRepo;

    public CouponService(CouponRepo couponRepo) {
        this.couponRepo = couponRepo;
    }

    // Existing saveCoupon method...
    public Coupon saveCoupon(Long submittedBy, String code, String platform, String details) {
        Coupon c = new Coupon();
        c.setSubmittedBy(submittedBy);
        c.setCode(code.trim());
        c.setPlatform(platform.trim());
        c.setDetails(details == null ? null : details.trim());
        c.setSubmittedAt(Instant.now());
        c.setStatus(Coupon.Status.AVAILABLE);
        return couponRepo.save(c);
    }

    // ✅ New method to get active platforms for the menu
    public List<String> getAvailablePlatforms() {
        return couponRepo.findDistinctPlatformsWithAvailableCoupons();
    }

    // ✅ Updated claim logic returning String codes/errors
    public String claim(Long couponId, Long userId) {
        // 1. Check 24-hour limit
        Instant oneDayAgo = Instant.now().minus(1, ChronoUnit.DAYS);
        if (couponRepo.countByClaimedByAndClaimedAtAfter(userId, oneDayAgo) >= 1) {
            return "LIMIT_REACHED";
        }

        // 2. Fetch available coupon
        Optional<Coupon> opt = couponRepo.findByIdAndStatusAndClaimedByIsNull(couponId, Coupon.Status.AVAILABLE);
        if (opt.isEmpty()) return "NOT_AVAILABLE";

        Coupon c = opt.get();
        c.setClaimedBy(userId);
        c.setClaimedAt(Instant.now());
        c.setStatus(Coupon.Status.CLAIMED);
        couponRepo.save(c);

        return c.getCode();
    }

    // Existing list methods...
    public List<Coupon> listAvailableByPlatform(String platform, int limit) {
        return couponRepo.findByStatusAndClaimedByIsNullAndPlatformIgnoreCaseOrderBySubmittedAtDesc(
                Coupon.Status.AVAILABLE,
                platform,
                PageRequest.of(0, limit)
        );
    }
}