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

        // Start Subscriber
        new Thread(RedisManager::subscribe).start();
    }

    public static void setServer(MinecraftServer mcServer) {
        server = mcServer;
    }

    public static void publishChat(String playerName, String message) {
        try (Jedis jedis = jedisPool.getResource()) {
            ChatMessage chat = new ChatMessage(playerName, message);
            String json = gson.toJson(chat);
            jedis.publish(ChatConfig.instance.channels.globalChat, json);
        } catch (Exception e) {
            SavsGlobalChat.LOGGER.error("Failed to publish chat", e);
        }
    }

    private static void subscribe() {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.subscribe(new JedisPubSub() {
                @Override
                public void onMessage(String channel, String message) {
                    if (channel.equals(ChatConfig.instance.channels.globalChat)) {
                        handleChatMessage(message);
                    }
                }
            }, ChatConfig.instance.channels.globalChat);
        } catch (Exception e) {
            SavsGlobalChat.LOGGER.error("Redis Subscriber failed", e);
        }
    }

    private static void handleChatMessage(String json) {
        if (server == null) return;

        try {
            ChatMessage chat = gson.fromJson(json, ChatMessage.class);
            // Broadcast to all players
            // Format: [Global] <Player>: Message
            String formatted = String.format("§b[Global] §f%s: %s", chat.player, chat.message);
            
            server.execute(() -> {
                server.getPlayerManager().broadcast(Text.of(formatted), false);
            });
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
