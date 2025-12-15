package com.savage.globalchat.redis;

import com.google.gson.Gson;
import com.savage.globalchat.SavsGlobalChat;
import com.savage.globalchat.config.ChatConfig;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.pubsub.api.async.RedisPubSubAsyncCommands;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;

import java.time.Duration;

public class RedisManager {
    private static RedisClient redisClient;
    private static StatefulRedisConnection<String, String> connection;
    private static StatefulRedisPubSubConnection<String, String> pubSubConnection;
    private static final Gson gson = new Gson();
    private static MinecraftServer server;

    public static void init() {
        String host = ChatConfig.instance.redis.host;
        int port = ChatConfig.instance.redis.port;
        String password = ChatConfig.instance.redis.password;

        RedisURI.Builder builder = RedisURI.builder()
                .withHost(host)
                .withPort(port)
                .withTimeout(Duration.ofSeconds(0)); // Infinite timeout to match previous logic

        if (password != null && !password.isEmpty()) {
            builder.withPassword(password);
        }

        RedisURI uri = builder.build();
        redisClient = RedisClient.create(uri);
        redisClient.setOptions(io.lettuce.core.ClientOptions.builder()
                .protocolVersion(io.lettuce.core.protocol.ProtocolVersion.RESP2)
                .build());

        // Retry Logic variables
        int maxRetries = 5;
        int retryDelay = 1000; // Start with 1s

        for (int i = 0; i < maxRetries; i++) {
            try {
                // Establish shared connection for publishing
                connection = redisClient.connect();
                
                // Establish dedicated connection for subscribing
                pubSubConnection = redisClient.connectPubSub();

                SavsGlobalChat.LOGGER.info("Redis initialized on " + host + ":" + port);

                // Add Listener
                pubSubConnection.addListener(new RedisPubSubAdapter<String, String>() {
                    @Override
                    public void message(String channel, String message) {
                        // Determine type based on channel
                        String type = "GLOBAL";
                        if (channel.endsWith("-staff")) {
                            type = "STAFF";
                        }
                        handleChatMessage(message, type);
                    }
                });

                // Subscribe asynchronously
                RedisPubSubAsyncCommands<String, String> async = pubSubConnection.async();
                async.subscribe(ChatConfig.instance.channels.globalChat, ChatConfig.instance.channels.globalChat + "-staff");
                
                SavsGlobalChat.LOGGER.info("Subscribed to channels: " + ChatConfig.instance.channels.globalChat + ", " + ChatConfig.instance.channels.globalChat + "-staff");
                
                // If successful, break the loop
                return;

            } catch (Exception e) {
                if (i < maxRetries - 1) {
                    SavsGlobalChat.LOGGER.warn("Failed to connect to Redis (Attempt " + (i + 1) + "/" + maxRetries + "). Retrying in " + (retryDelay / 1000) + "s...");
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ignored) {}
                    retryDelay += 1000; // Linear Backoff: 1s, 2s, 3s...
                } else {
                    SavsGlobalChat.LOGGER.error("Failed to initialize Redis connection after " + maxRetries + " attempts.", e);
                }
            }
        }
    }

    public static void setServer(MinecraftServer mcServer) {
        server = mcServer;
    }

    public static void publishChat(String playerName, String message, String type) {
        if (connection == null || !connection.isOpen()) {
            SavsGlobalChat.LOGGER.warn("Attempted to publish chat but Redis connection is closed.");
            return;
        }

        try {
            ChatMessage chat = new ChatMessage(playerName, message);
            String json = gson.toJson(chat);
            
            String channel = ChatConfig.instance.channels.globalChat;
            if ("STAFF".equals(type)) {
                channel = ChatConfig.instance.channels.globalChat + "-staff";
            }
            
            // Async publish
            connection.async().publish(channel, json);
        } catch (Exception e) {
            SavsGlobalChat.LOGGER.error("Failed to publish chat", e);
        }
    }

    // No longer needed as Lettuce handles subscriptions via the long-lived connection and listener
    // private static void subscribeToChannel(String channelName, String type) { ... }

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
    
    public static void shutdown() {
        if (connection != null) connection.close();
        if (pubSubConnection != null) pubSubConnection.close();
        if (redisClient != null) redisClient.shutdown();
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
