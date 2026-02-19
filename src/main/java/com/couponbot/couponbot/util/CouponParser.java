package com.couponbot.couponbot.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CouponParser {

    // accept lowercase too
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\b[a-zA-Z0-9]{5,30}\\b");

    public record Parsed(String code, String platform, String details) {}

    public static Parsed parseFromText(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (t.isEmpty()) return null;

        String platform = guessPlatform(t);
        String details = t;

        // 1) Prefer URL if present
        Matcher url = URL_PATTERN.matcher(t);
        if (url.find()) {
            return new Parsed(url.group(), platform, details);
        }

        // 2) Collect best token as coupon code
        Matcher m = TOKEN_PATTERN.matcher(t);

        String best = null;
        int bestScore = -1;

        while (m.find()) {
            String token = m.group();

            // ignore platform words and common command words
            String low = token.toLowerCase(Locale.ROOT);
            if (low.equals("canva") || low.equals("adobe") || low.equals("linkedin") ||
                    low.equals("amazon") || low.equals("netflix") || low.equals("spotify") ||
                    low.equals("start") || low.equals("save") || low.equals("claim")) {
                continue;
            }

            int score = scoreToken(token);
            if (score > bestScore) {
                bestScore = score;
                best = token;
            }
        }

        if (best == null) return null;

        // store codes uppercase for consistency (optional)
        return new Parsed(best.toUpperCase(Locale.ROOT), platform, details);
    }

    // Prefer tokens that look like real coupons: has digits, mixed letters+digits, etc.
    private static int scoreToken(String token) {
        boolean hasDigit = token.chars().anyMatch(Character::isDigit);
        boolean hasLetter = token.chars().anyMatch(Character::isLetter);
        boolean allLetters = hasLetter && !hasDigit;
        boolean allDigits = !hasLetter && hasDigit;

        int len = token.length();
        int score = 0;

        if (hasDigit) score += 50;                 // big boost if digits exist
        if (hasDigit && hasLetter) score += 30;    // mixed letters+digits common for coupons
        if (allLetters) score -= 30;               // "CANVA" should not win
        if (allDigits) score -= 10;                // sometimes codes are digits, but less common
        score += Math.min(len, 20);                // slightly prefer longer (up to 20)

        return score;
    }

    private static String guessPlatform(String t) {
        String s = t.toLowerCase(Locale.ROOT);
        if (s.contains("linkedin")) return "LinkedIn";
        if (s.contains("canva")) return "Canva";
        if (s.contains("adobe")) return "Adobe";
        if (s.contains("netflix")) return "Netflix";
        if (s.contains("spotify")) return "Spotify";
        if (s.contains("amazon")) return "Amazon";
        return "General";
    }
}
