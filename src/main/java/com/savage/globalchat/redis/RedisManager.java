package com.savage.globalchat.redis;

import com.google.gson.Gson;
import com.savage.globalchat.SavsGlobalChat;
import com.savage.globalchat.config.ChatConfig;
import com.savage.redislib.RedisService;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

public class RedisManager {
    private static final Gson gson = new Gson();
    private static MinecraftServer server;

    public static void init() {
        if (!RedisService.isReady()) {
            SavsGlobalChat.LOGGER.warn("Redis Lib not ready during initialization.");
        }

        // Subscribe to Global Chat
        String globalIdentifier = ChatConfig.instance.channels.globalChat;
        RedisService.get().subscribe(globalIdentifier, (channel, message) -> {
             if (channel.equals(globalIdentifier)) {
                 handleChatMessage(message, "GLOBAL");
             }
        });
        

        
        SavsGlobalChat.LOGGER.info("Global Chat Initialized using Savs-Redis-Lib");
    }

    public static void setServer(MinecraftServer mcServer) {
        server = mcServer;
    }

    public static boolean publishChat(String playerName, String message, String type) {
        if (!RedisService.isReady()) {
            SavsGlobalChat.LOGGER.warn("[GlobalChat] Cannot publish: Redis not connected.");
            return false;
        }

        try {
            ChatMessage chat = new ChatMessage(playerName, message);
            String json = gson.toJson(chat);
            
            String channel = ChatConfig.instance.channels.globalChat;

            
            RedisService.get().publish(channel, json);
            return true;
        } catch (Exception e) {
            SavsGlobalChat.LOGGER.error("[GlobalChat] Failed to publish chat", e);
            return false;
        }
    }

    private static void handleChatMessage(String json, String type) {
        if (server == null) return;

        try {
            ChatMessage chat = gson.fromJson(json, ChatMessage.class);
            
            String formatted;
                formatted = String.format("§b[Global] §f%s: %s", chat.player, chat.message);
                // Broadcast to all
                server.execute(() -> {
                    server.getPlayerManager().broadcast(Text.of(formatted), false);
                });

        } catch (Exception e) {
            SavsGlobalChat.LOGGER.error("Failed to parse chat message", e);
        }
    }
    
    public static void shutdown() {
        // Handled by library
    }

    private static class ChatMessage {
        String player;
        String message;

        public ChatMessage(String player, String message) {
            this.player = player;
            this.message = message;
        }
    }
}
