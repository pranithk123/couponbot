package com.couponbot.couponbot.service;

import com.couponbot.couponbot.db.entity.Coupon;
import com.couponbot.couponbot.db.repo.CouponRepo;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class CouponService {

    private final CouponRepo couponRepo;

    public CouponService(CouponRepo couponRepo) {
        this.couponRepo = couponRepo;
    }

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

    public List<String> getAvailablePlatforms() {
        return couponRepo.findDistinctPlatformsWithAvailableCoupons();
    }

    // ✅ Added helper for the bot to check limits
    public long getClaimCount(Long userId, Instant threshold) {
        return couponRepo.countByClaimedByAndClaimedAtAfter(userId, threshold);
    }

    // ✅ Updated claim logic to return the full Coupon object
    public Optional<Coupon> claim(Long couponId, Long userId) {
        Optional<Coupon> opt = couponRepo.findByIdAndStatusAndClaimedByIsNull(couponId, Coupon.Status.AVAILABLE);
        if (opt.isEmpty()) return Optional.empty();

        Coupon c = opt.get();
        c.setClaimedBy(userId);
        c.setClaimedAt(Instant.now());
        c.setStatus(Coupon.Status.CLAIMED);
        return Optional.of(couponRepo.save(c));
    }

    public List<Coupon> listAvailableByPlatform(String platform, int limit) {
        return couponRepo.findByStatusAndClaimedByIsNullAndPlatformIgnoreCaseOrderBySubmittedAtDesc(
                Coupon.Status.AVAILABLE,
                platform,
                PageRequest.of(0, limit)
        );
    }
}