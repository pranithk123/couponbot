package com.couponbot.couponbot.bot;

import com.couponbot.couponbot.db.entity.Coupon;
import com.couponbot.couponbot.service.ChannelGateService;
import com.couponbot.couponbot.service.CouponService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CouponTelegramBot extends TelegramLongPollingBot {

    private final CouponService couponService;
    private final ChannelGateService channelGateService;
    private final String username;

    // State tracking for submissions
    private final Map<Long, SubmissionState> userStates = new ConcurrentHashMap<>();

    private record SubmissionState(String platform, String code, Step step) {}
    enum Step { SELECT_PLATFORM, ENTER_CODE, ENTER_DETAILS }

    public CouponTelegramBot(CouponService couponService, ChannelGateService channelGateService,
                             @Value("${BOT_TOKEN}") String token, @Value("${BOT_USERNAME}") String username) {
        super(token);
        this.couponService = couponService;
        this.channelGateService = channelGateService;
        this.username = username;
    }

    @Override
    public String getBotUsername() { return username; }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                handleCallbackQuery(update);
                return;
            }

            if (!update.hasMessage() || !update.getMessage().hasText()) return;

            String text = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();
            long userId = update.getMessage().getFrom().getId();

            // 1. Handle Main Menu Actions
            if (text.equals("/start")) {
                userStates.remove(userId);
                sendMenu(chatId, "Welcome! How can I help you today?");
                return;
            }

            if (text.equals("📤 Submit Coupon")) {
                sendPlatformSelection(chatId, userId);
                return;
            }

            if (text.equals("📜 Available Coupons")) {
                sendAvailablePlatforms(chatId);
                return;
            }

            // 2. Handle Submission Steps
            if (userStates.containsKey(userId)) {
                handleSubmissionSteps(chatId, userId, text);
                return;
            }

        } catch (Exception e) { e.printStackTrace(); }
    }

    private void handleSubmissionSteps(long chatId, long userId, String text) throws Exception {
        SubmissionState state = userStates.get(userId);

        if (state.step == Step.ENTER_CODE) {
            userStates.put(userId, new SubmissionState(state.platform, text, Step.ENTER_DETAILS));
            reply(chatId, "Great! Now enter a one-line detail (e.g., '100rs off for new users'):");
        }
        else if (state.step == Step.ENTER_DETAILS) {
            couponService.saveCoupon(userId, state.code, state.platform, text);
            userStates.remove(userId);
            reply(chatId, "✅ Thank you! Your coupon for " + state.platform + " has been submitted.");
        }
    }

    private void sendPlatformSelection(long chatId, long userId) throws Exception {
        userStates.put(userId, new SubmissionState(null, null, Step.SELECT_PLATFORM));
        SendMessage msg = new SendMessage(String.valueOf(chatId), "Select a platform:");

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<String> common = List.of("Canva", "LinkedIn", "BigBasket", "Amazon", "Other");
        for (String p : common) {
            InlineKeyboardButton btn = new InlineKeyboardButton(p);
            btn.setCallbackData("plt_" + p);
            rows.add(List.of(btn));
        }
        msg.setReplyMarkup(new InlineKeyboardMarkup(rows));
        execute(msg);
    }

    private void handleCallbackQuery(Update update) throws Exception {
        String data = update.getCallbackQuery().getData();
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        long userId = update.getCallbackQuery().getFrom().getId();

        if (data.startsWith("plt_")) {
            String platform = data.substring(4);
            userStates.put(userId, new SubmissionState(platform, null, Step.ENTER_CODE));
            reply(chatId, "Selected: " + platform + ". Now please paste the Coupon Code or Link:");
        }
        else if (data.startsWith("view_")) {
            sendCouponsForPlatform(chatId, data.substring(5));
        }
        else if (data.startsWith("claim_")) {
            processClaim(chatId, userId, Long.parseLong(data.substring(6)));
        }
    }

    private void sendAvailablePlatforms(long chatId) throws Exception {
        // You'll need to add findDistinctPlatformsWithAvailableCoupons to your Repo/Service
        List<String> platforms = couponService.getAvailablePlatforms();
        if (platforms.isEmpty()) {
            reply(chatId, "No coupons available right now.");
            return;
        }

        SendMessage msg = new SendMessage(String.valueOf(chatId), "Select a platform to view coupons:");
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (String p : platforms) {
            InlineKeyboardButton btn = new InlineKeyboardButton(p);
            btn.setCallbackData("view_" + p);
            rows.add(List.of(btn));
        }
        msg.setReplyMarkup(new InlineKeyboardMarkup(rows));
        execute(msg);
    }

    private void sendCouponsForPlatform(long chatId, String platform) throws Exception {
        List<Coupon> coupons = couponService.listAvailableByPlatform(platform, 10);
        SendMessage msg = new SendMessage(String.valueOf(chatId), "Available " + platform + " coupons:");
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        for (Coupon c : coupons) {
            InlineKeyboardButton btn = new InlineKeyboardButton("🎁 " + c.getDetails());
            btn.setCallbackData("claim_" + c.getId());
            rows.add(List.of(btn));
        }
        msg.setReplyMarkup(new InlineKeyboardMarkup(rows));
        execute(msg);
    }

    private void processClaim(long chatId, long userId, long couponId) throws Exception {
        if (!channelGateService.isJoined(this, userId)) {
            reply(chatId, "🔒 Please join " + channelGateService.getRequiredChannel() + " to claim!");
            return;
        }

        String result = couponService.claim(couponId, userId);
        if (result.equals("LIMIT_REACHED")) {
            reply(chatId, "❌ You can only claim one coupon per day!");
        } else if (result.equals("NOT_AVAILABLE")) {
            reply(chatId, "❌ Sorry, this coupon was just taken.");
        } else {
            reply(chatId, "✅ Success! Your code is: " + result);
        }
    }

    private void sendMenu(long chatId, String text) throws Exception {
        SendMessage msg = new SendMessage(String.valueOf(chatId), text);
        msg.setReplyMarkup(createMainMenu());
        execute(msg);
    }

    private void reply(long chatId, String text) throws Exception {
        execute(new SendMessage(String.valueOf(chatId), text));
    }

    private ReplyKeyboardMarkup createMainMenu() {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        List<KeyboardRow> rows = new ArrayList<>();
        KeyboardRow r1 = new KeyboardRow(); r1.add("📤 Submit Coupon"); r1.add("📜 Available Coupons");
        KeyboardRow r2 = new KeyboardRow(); r2.add("ℹ️ About Us");
        rows.add(r1); rows.add(r2);
        markup.setKeyboard(rows);
        return markup;
    }
}


