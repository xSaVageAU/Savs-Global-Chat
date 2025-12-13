package com.savage.globalchat.manager;

import com.savage.globalchat.redis.RedisManager;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class ChatInterceptor {
    public static void init() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            ChannelManager.ChannelType channel = ChannelManager.getPlayerChannel(sender.getUuid());

            if (channel == ChannelManager.ChannelType.GLOBAL) {
                // Publish to Global Redis
                String content = message.getContent().getString();
                String player = sender.getName().getString();
                
                RedisManager.publishChat(player, content, "GLOBAL");

                // Cancel vanilla broadcast
                return false;
            }

            if (channel == ChannelManager.ChannelType.STAFF) {
                // Publish to Staff Redis
                String content = message.getContent().getString();
                String player = sender.getName().getString();
                
                RedisManager.publishChat(player, content, "STAFF");

                // Cancel vanilla broadcast
                return false;
            }

            // Local chat proceeds as normal
            return true;
        });
    }
}
