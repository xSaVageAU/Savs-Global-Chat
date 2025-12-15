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
                .autoReconnect(false) // Disable default noisy reconnect
                .build());

        // Initial connection attempt
        connect();
    }

    private static int retryAttempt = 0;
    private static final int MAX_RETRY_DELAY = 10000; // 10s cap

    public static void connect() {
        // Reset connections if they exist but are broken
        closeConnections();

        try {
            // Establish shared connection for publishing
            connection = redisClient.connect();
            
            // Establish dedicated connection for subscribing
            pubSubConnection = redisClient.connectPubSub();

            // Add Listener logic...
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
            
            // Re-subscribe to channels
            RedisPubSubAsyncCommands<String, String> async = pubSubConnection.async();
            async.subscribe(ChatConfig.instance.channels.globalChat, ChatConfig.instance.channels.globalChat + "-staff");
            
            // Add Connection Watchdog
            connection.addListener(new io.lettuce.core.RedisConnectionStateListener() {
                public void onRedisDisconnected(io.lettuce.core.RedisChannelHandler<?, ?> connection) {
                     scheduleReconnect();
                }
                public void onRedisExceptionCaught(io.lettuce.core.RedisChannelHandler<?, ?> connection, Throwable cause) {}
            });
            
            SavsGlobalChat.LOGGER.info("Redis connected on " + ChatConfig.instance.redis.host);
            retryAttempt = 0; // Reset counter on success

        } catch (Exception e) {
            scheduleReconnect();
        }
    }

    public static void scheduleReconnect() {
        if (redisClient == null) return; // Shutdown
        
        long delay = Math.min(1000 * (1L << retryAttempt), MAX_RETRY_DELAY); // 1s, 2s, 4s, 8s, 10s...
        retryAttempt++;

        SavsGlobalChat.LOGGER.warn("[GlobalChat] Redis connection lost/failed. Reconnecting in " + (delay/1000) + "s... (Attempt " + retryAttempt + ")");
        
        // Use a scheduler (need to create one or use server scheduler if available, but better separate thread for IO)
        new Thread(() -> {
            try {
                Thread.sleep(delay);
                connect();
            } catch (InterruptedException e) {
                // Ignore
            }
        }, "GlobalChat-Reconnect-Thread").start();
    }

    private static void closeConnections() {
        try {
            if (connection != null) connection.close();
            if (pubSubConnection != null) pubSubConnection.close();
        } catch (Exception ignored) {}
    }

    public static void setServer(MinecraftServer mcServer) {
        server = mcServer;
    }

    public static boolean publishChat(String playerName, String message, String type) {
        if (connection == null || !connection.isOpen()) {
            SavsGlobalChat.LOGGER.warn("[GlobalChat] Attempted to publish chat but Redis connection is closed.");
            return false;
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
            return true;
        } catch (Exception e) {
            SavsGlobalChat.LOGGER.error("[GlobalChat] Failed to publish chat", e);
            return false;
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
