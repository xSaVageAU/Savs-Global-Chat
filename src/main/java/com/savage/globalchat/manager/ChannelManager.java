package com.savage.globalchat.manager;

import com.savage.globalchat.config.ChatConfig;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChannelManager {
    public enum ChannelType {
        LOCAL,
        GLOBAL,
        STAFF
    }

    private static final Map<UUID, ChannelType> playerChannels = new ConcurrentHashMap<>();

    public static void init() {
        // Set default channel on join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            String defaultChan = ChatConfig.instance.defaultChannel.toUpperCase();
            try {
                ChannelType type = ChannelType.valueOf(defaultChan);
                setPlayerChannel(handler.getPlayer().getUuid(), type);
            } catch (IllegalArgumentException e) {
                setPlayerChannel(handler.getPlayer().getUuid(), ChannelType.LOCAL);
            }
        });

        // Clean up on disconnect
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            playerChannels.remove(handler.getPlayer().getUuid());
        });
    }

    public static void setPlayerChannel(UUID uuid, ChannelType channel) {
        playerChannels.put(uuid, channel);
    }

    public static ChannelType getPlayerChannel(UUID uuid) {
        return playerChannels.getOrDefault(uuid, ChannelType.LOCAL);
    }
}
