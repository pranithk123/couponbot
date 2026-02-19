package com.couponbot.couponbot.bot;

import com.couponbot.couponbot.db.entity.Coupon;
import com.couponbot.couponbot.service.CouponService;
import com.couponbot.couponbot.util.CouponParser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

import java.util.List;

@Component
public class CouponTelegramBot extends TelegramLongPollingBot {

    private final String username;
    private final CouponService couponService;

    public CouponTelegramBot(
            CouponService couponService,
            @Value("${BOT_TOKEN}") String token,
            @Value("${BOT_USERNAME}") String username
    ) {
        super(token);
        this.couponService = couponService;
        this.username = username;
        System.out.println("✅ Bot constructed, username=" + username);
    }

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (!update.hasMessage() || !update.getMessage().hasText()) return;

            String text = update.getMessage().getText().trim();
            long chatId = update.getMessage().getChatId();
            Long userId = update.getMessage().getFrom().getId();

            System.out.println("🔥 MSG: " + text + " | userId=" + userId + " chatId=" + chatId);

            // Commands
            if (text.startsWith("/start")) {
                reply(chatId,
                        "✅ Coupon Saver Bot is running!\n\n" +
                                "Commands:\n" +
                                "/save <text>  - save coupon/link\n" +
                                "/list [platform] - list available coupons\n" +
                                "/my - your submitted coupons\n" +
                                "/claim <id> - claim a coupon\n\n" +
                                "Tip: You can also just send a coupon message/link normally, I’ll auto-save it."
                );
                return;
            }

            if (text.startsWith("/save")) {
                String body = text.replaceFirst("^/save\\s*", "").trim();
                if (body.isEmpty()) {
                    reply(chatId, "Send like: /save Canva SAVE20 (or paste the redeem link)");
                    return;
                }

                CouponParser.Parsed p = CouponParser.parseFromText(body);
                if (p == null) {
                    reply(chatId, "I couldn't detect a coupon code or link. Try including a code like SAVE20 or a URL.");
                    return;
                }

                Coupon c = couponService.saveCoupon(userId, p.code(), p.platform(), p.details());
                reply(chatId, "✅ Saved!\nID: " + c.getId() + "\nPlatform: " + c.getPlatform() + "\nCode/Link: " + c.getCode());
                return;
            }

            if (text.startsWith("/list")) {
                String arg = text.replaceFirst("^/list\\s*", "").trim();
                List<Coupon> list = arg.isEmpty()
                        ? couponService.listAvailable(10)
                        : couponService.listAvailableByPlatform(arg, 10);

                if (list.isEmpty()) {
                    reply(chatId, "No available coupons" + (arg.isEmpty() ? "" : (" for " + arg)) + " right now.");
                    return;
                }

                StringBuilder sb = new StringBuilder();

                sb.append("📌 Available coupons")
                        .append(arg.isEmpty() ? "" : (" for " + arg))
                        .append(":\n\n");

                for (Coupon c : list) {
                    sb.append("ID: ")
                            .append(c.getId())
                            .append(" | ")
                            .append(c.getPlatform())
                            .append("\n")
                            .append("To claim: /claim ")
                            .append(c.getId())
                            .append("\n\n");
                }

                reply(chatId, sb.toString());
                return;

            }

            if (text.startsWith("/my")) {
                List<Coupon> mine = couponService.listMine(userId, 10);
                if (mine.isEmpty()) {
                    reply(chatId, "You haven’t submitted any coupons yet.");
                    return;
                }

                StringBuilder sb = new StringBuilder("🧾 Your last 10 submissions:\n\n");
                for (Coupon c : mine) {
                    sb.append("ID: ").append(c.getId())
                            .append(" | ").append(c.getPlatform())
                            .append(" | ").append(c.getStatus())
                            .append("\nCode/Link: ").append(c.getCode())
                            .append("\n\n");
                }
                reply(chatId, sb.toString());
                return;
            }

            if (text.startsWith("/claim")) {
                String arg = text.replaceFirst("^/claim\\s*", "").trim();
                if (arg.isEmpty()) {
                    reply(chatId, "Use: /claim <id>");
                    return;
                }

                long id;
                try {
                    id = Long.parseLong(arg);
                } catch (Exception e) {
                    reply(chatId, "Invalid id. Use: /claim 12");
                    return;
                }

                var claimed = couponService.claim(id, userId);
                if (claimed.isEmpty()) {
                    reply(chatId, "❌ That coupon is not available (maybe already claimed or removed).");
                } else {
                    Coupon c = claimed.get();
                    reply(chatId, "✅ Claimed!\nID: " + c.getId() + "\nPlatform: " + c.getPlatform() + "\nCode/Link: " + c.getCode());
                }
                return;
            }

            // Auto-save any normal message that looks like a coupon/link
            CouponParser.Parsed parsed = CouponParser.parseFromText(text);
            if (parsed != null) {
                Coupon c = couponService.saveCoupon(userId, parsed.code(), parsed.platform(), parsed.details());
                reply(chatId, "✅ Auto-saved coupon!\nID: " + c.getId() + " | " + c.getPlatform());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void reply(long chatId, String text) throws Exception {
        SendMessage msg = new SendMessage(String.valueOf(chatId), text);
        execute(msg);
    }
}


