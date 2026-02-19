package com.couponbot.couponbot.bot;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Configuration
public class BotInitializer {

    @Bean
    public TelegramBotsApi telegramBotsApi(CouponTelegramBot bot) throws TelegramApiException {
        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        api.registerBot(bot);
        System.out.println("✅ Bot registered with TelegramBotsApi");
        return api;
    }

    // Optional but useful: make sure webhook is OFF (for long polling)
    @Bean
    public ApplicationRunner deleteWebhookOnStart(CouponTelegramBot bot) {
        return args -> {
            try {
                bot.execute(new org.telegram.telegrambots.meta.api.methods.updates.DeleteWebhook(true));
                System.out.println("✅ Webhook deleted (long polling enabled)");
            } catch (Exception e) {
                System.out.println("⚠️ Could not delete webhook: " + e.getMessage());
            }
        };
    }
}
