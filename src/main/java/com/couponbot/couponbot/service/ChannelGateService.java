package com.couponbot.couponbot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.bots.AbsSender;

@Service
public class ChannelGateService {

    private final String requiredChannel;

    public ChannelGateService(@Value("${app.requiredChannel}") String requiredChannel) {
        this.requiredChannel = requiredChannel;
    }

    public String getRequiredChannel() {
        return requiredChannel;
    }

    public boolean isJoined(AbsSender sender, Long userId) {
        try {
            ChatMember member = sender.execute(new GetChatMember(requiredChannel, userId));
            String status = member.getStatus();
            return "creator".equals(status) || "administrator".equals(status) || "member".equals(status) || "restricted".equals(status);
        } catch (Exception e) {
            return false;
        }
    }
}
