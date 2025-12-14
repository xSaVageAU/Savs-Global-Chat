package com.savage.globalchat.redis;

import com.google.gson.Gson;
import com.savage.globalchat.SavsGlobalChat;
import com.savage.globalchat.config.ChatConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.JedisPubSub;

public class RedisManager {
    private static JedisPool jedisPool;
    private static final Gson gson = new Gson();
    private static MinecraftServer server;

    public static void init() {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(10);

        String host = ChatConfig.instance.redis.host;
        int port = ChatConfig.instance.redis.port;
        String password = ChatConfig.instance.redis.password;

        if (password == null || password.isEmpty()) {
            jedisPool = new JedisPool(poolConfig, host, port, 2000);
        } else {
            jedisPool = new JedisPool(poolConfig, host, port, 2000, password);
        }

        SavsGlobalChat.LOGGER.info("Redis initialized on " + host + ":" + port);

        // Start Subscribers (Split to avoid multi-channel issues on MicroRESP)
        new Thread(() -> subscribeToChannel(ChatConfig.instance.channels.globalChat, "GLOBAL")).start();
        new Thread(() -> subscribeToChannel(ChatConfig.instance.channels.globalChat + "-staff", "STAFF")).start();
    }

    public static void setServer(MinecraftServer mcServer) {
        server = mcServer;
    }

    public static void publishChat(String playerName, String message, String type) {
        try (Jedis jedis = jedisPool.getResource()) {
            ChatMessage chat = new ChatMessage(playerName, message);
            String json = gson.toJson(chat);
            
            String channel = ChatConfig.instance.channels.globalChat;
            if ("STAFF".equals(type)) {
                channel = ChatConfig.instance.channels.globalChat + "-staff";
            }
            
            jedis.publish(channel, json);
        } catch (Exception e) {
            SavsGlobalChat.LOGGER.error("Failed to publish chat", e);
        }
    }

    private static void subscribeToChannel(String channelName, String type) {
        while (true) { // Reconnection Loop
            try (Jedis jedis = jedisPool.getResource()) {
                SavsGlobalChat.LOGGER.info("Subscribing to Redis channel: " + channelName);
                
                // Disable timeout for subscriber
                jedis.getClient().setSoTimeout(0);
                
                jedis.subscribe(new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        handleChatMessage(message, type);
                    }
                }, channelName);

            } catch (Exception e) {
                SavsGlobalChat.LOGGER.error("Redis Subscriber (" + type + ") connection lost. Reconnecting in 5s...", e);
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignored) {}
            }
        }
    }

    private static void handleChatMessage(String json, String type) {
        if (server == null) return;

        try {
            ChatMessage chat = gson.fromJson(json, ChatMessage.class);
            
            String formatted;
            if ("STAFF".equals(type)) {
                formatted = String.format("§c[Staff] §f%s: §e%s", chat.player, chat.message);
                
                // Broadcast only to staff
                server.execute(() -> {
                    server.getPlayerManager().getPlayerList().forEach(player -> {
                        boolean hasPerm = com.savage.globalchat.util.PermissionsHelper.check(player, "globalchat.channel.staff", 2);
                        if (hasPerm) {
                            player.sendMessage(Text.of(formatted));
                        }
                    });
                });
            } else {
                formatted = String.format("§b[Global] §f%s: %s", chat.player, chat.message);
                // Broadcast to all
                server.execute(() -> {
                    server.getPlayerManager().broadcast(Text.of(formatted), false);
                });
            }

        } catch (Exception e) {
            SavsGlobalChat.LOGGER.error("Failed to parse chat message", e);
        }
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
