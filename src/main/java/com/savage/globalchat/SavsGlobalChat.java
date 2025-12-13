package com.savage.globalchat;

import com.savage.globalchat.command.GlobalChatCommand;
import com.savage.globalchat.config.ChatConfig;
import com.savage.globalchat.redis.RedisManager;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SavsGlobalChat implements ModInitializer {
    public static final String MOD_ID = "savsglobalchat";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Savs Global Chat Initializing...");

        // Load Config
        ChatConfig.load();

        // Initialize Redis
        RedisManager.init();

        // Register Command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            GlobalChatCommand.register(dispatcher);
        });

        // Register Server Lifecycle
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            RedisManager.setServer(server);
        });
    }
}