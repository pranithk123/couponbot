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

import java.time.Instant; // ✅ Fix: Added missing import
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CouponTelegramBot extends TelegramLongPollingBot {

    private final CouponService couponService;
    private final ChannelGateService channelGateService;
    private final String username;

    private final Map<Long, SubmissionState> userStates = new ConcurrentHashMap<>();

    private record SubmissionState(String platform, String code, Step step) {}
    enum Step { SELECT_PLATFORM, ENTER_PLATFORM_NAME, ENTER_CODE, ENTER_DETAILS }

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

            String text = update.getMessage().getText().trim();
            long chatId = update.getMessage().getChatId();
            long userId = update.getMessage().getFrom().getId();

            if (text.equals("/start")) {
                userStates.remove(userId);
                sendMenu(chatId, "Welcome to Coupon Saver! Select an option below to get started:");
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

            if (text.equals("ℹ️ About Us")) {
                reply(chatId, "🌟 **About Coupon Saver**\n\nThis bot is a community-driven platform where users voluntarily share coupons they won't use so others can benefit.\n\n✅ **Voluntary Submissions**\n✅ **Verified Claims**\n✅ **Fair Use Policy (2 claims/day)**\n\nMade with ❤️ for savers!");
                return;
            }

            if (userStates.containsKey(userId)) {
                handleSubmissionSteps(chatId, userId, text);
                return;
            }

        } catch (Exception e) { e.printStackTrace(); }
    }

    private void handleSubmissionSteps(long chatId, long userId, String text) throws Exception {
        SubmissionState state = userStates.get(userId);

        if (state.step == Step.ENTER_PLATFORM_NAME) {
            userStates.put(userId, new SubmissionState(text, null, Step.ENTER_CODE));
            reply(chatId, "Platform set to: " + text + ". Now please paste the Coupon Code or redeem link:");
        }
        else if (state.step == Step.ENTER_CODE) {
            userStates.put(userId, new SubmissionState(state.platform, text, Step.ENTER_DETAILS));
            reply(chatId, "Great! Now enter a one-line description (max 100 characters, no line breaks):");
        }
        else if (state.step == Step.ENTER_DETAILS) {
            if (text.length() > 100 || text.contains("\n")) {
                reply(chatId, "❌ **Description too long or multi-line.**\nPlease keep it to one short sentence (max 100 characters).");
                return;
            }

            couponService.saveCoupon(userId, state.code, state.platform, text);
            userStates.remove(userId);
            reply(chatId, "✅ **Success!** Your coupon for " + state.platform + " has been added.");
        }
    }

    private void sendPlatformSelection(long chatId, long userId) throws Exception {
        userStates.put(userId, new SubmissionState(null, null, Step.SELECT_PLATFORM));
        SendMessage msg = new SendMessage(String.valueOf(chatId), "Which platform is this coupon for?");
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
            if (platform.equals("Other")) {
                userStates.put(userId, new SubmissionState(null, null, Step.ENTER_PLATFORM_NAME));
                reply(chatId, "Please type the name of the platform:");
            } else {
                userStates.put(userId, new SubmissionState(platform, null, Step.ENTER_CODE));
                reply(chatId, "Selected: " + platform + ". Now please paste the Coupon Code or Link:");
            }
        }
        else if (data.startsWith("view_")) {
            sendCouponsForPlatform(chatId, data.substring(5));
        }
        else if (data.startsWith("claim_")) {
            processClaim(chatId, userId, Long.parseLong(data.substring(6)));
        }
        else if (data.startsWith("verify_")) {
            long couponId = Long.parseLong(data.substring(7));
            if (channelGateService.isJoined(this, userId)) {
                processClaim(chatId, userId, couponId); // ✅ Resume claim logic
            } else {
                reply(chatId, "❌ You still haven't joined the channel. Please join " + channelGateService.getRequiredChannel() + " and click verify again!");
            }
        }
    }

    private void sendAvailablePlatforms(long chatId) throws Exception {
        List<String> platforms = couponService.getAvailablePlatforms();
        if (platforms.isEmpty()) {
            reply(chatId, "No coupons are available at the moment.");
            return;
        }
        SendMessage msg = new SendMessage(String.valueOf(chatId), "📌 **Available Platforms**");
        msg.setParseMode("Markdown");
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
        SendMessage msg = new SendMessage(String.valueOf(chatId), "🎁 **" + platform + " Coupons**");
        msg.setParseMode("Markdown");
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Coupon c : coupons) {
            InlineKeyboardButton btn = new InlineKeyboardButton(c.getDetails());
            btn.setCallbackData("claim_" + c.getId());
            rows.add(List.of(btn));
        }
        msg.setReplyMarkup(new InlineKeyboardMarkup(rows));
        execute(msg);
    }

    private void processClaim(long chatId, long userId, long couponId) throws Exception {
        // ✅ Fix: Gate check first
        if (!channelGateService.isJoined(this, userId)) {
            String channel = channelGateService.getRequiredChannel();
            SendMessage msg = new SendMessage(String.valueOf(chatId), "🔒 **Join Required**");
            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            InlineKeyboardButton joinBtn = new InlineKeyboardButton("📢 Join Channel");
            joinBtn.setUrl("https://t.me/" + channel.replace("@", ""));
            InlineKeyboardButton verifyBtn = new InlineKeyboardButton("✅ I Joined");
            verifyBtn.setCallbackData("verify_" + couponId);
            rows.add(List.of(joinBtn));
            rows.add(List.of(verifyBtn));
            msg.setReplyMarkup(new InlineKeyboardMarkup(rows));
            execute(msg);
            return;
        }

        // ✅ Fix: Check daily limit manually since claim() returns Optional
        Instant oneDayAgo = Instant.now().minus(1, java.time.temporal.ChronoUnit.DAYS);
        if (couponService.getClaimCount(userId, oneDayAgo) >= 2) {
            reply(chatId, "❌ **Daily Limit Reached**\nYou can only claim up to two coupons every 24 hours!");
            return;
        }

        Optional<Coupon> result = couponService.claim(couponId, userId);
        if (result.isPresent()) {
            handleClaimResult(chatId, result.get());
        } else {
            reply(chatId, "❌ Sorry, this coupon was just claimed by another user.");
        }
    }

    private void handleClaimResult(long chatId, Coupon c) throws Exception {
        // ✅ Added the description (details) below the code as requested
        String response = "✅ **Coupon Claimed!**\n\n" +
                "Your code/link is:\n" +
                "`" + c.getCode() + "`\n\n" +
                "ℹ️ **Coupon Description:**\n" +
                "_" + c.getDetails() + "_\n\n" +
                "Use it quickly before it expires!";
        reply(chatId, response);
    }

    private void sendMenu(long chatId, String text) throws Exception {
        SendMessage msg = new SendMessage(String.valueOf(chatId), text);
        msg.setReplyMarkup(createMainMenu());
        execute(msg);
    }

    private void reply(long chatId, String text) throws Exception {
        SendMessage msg = new SendMessage(String.valueOf(chatId), text);
        msg.setParseMode("Markdown");
        execute(msg);
    }

    private ReplyKeyboardMarkup createMainMenu() {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setResizeKeyboard(true);
        List<KeyboardRow> rows = new ArrayList<>();
        KeyboardRow r1 = new KeyboardRow();
        r1.add("📤 Submit Coupon");
        r1.add("📜 Available Coupons");
        KeyboardRow r2 = new KeyboardRow();
        r2.add("ℹ️ About Us");
        rows.add(r1);
        rows.add(r2);
        markup.setKeyboard(rows);
        return markup;
    }
}
