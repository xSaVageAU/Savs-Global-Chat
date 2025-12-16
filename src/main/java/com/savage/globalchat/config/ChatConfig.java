package com.savage.globalchat.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.savage.globalchat.SavsGlobalChat;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ChatConfig {
    private static final File CONFIG_FILE = new File("config/savsglobalchat.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static ConfigData instance = new ConfigData();

    public static class ConfigData {
        public RedisConfig redis = new RedisConfig();
        public ChannelsConfig channels = new ChannelsConfig();
        public String defaultChannel = "LOCAL";
    }

    public static class RedisConfig {
        // Connection details managed by Savs-Redis-Lib
    }

    public static class ChannelsConfig {
        public String globalChat = "global:chat";
    }

    public static void load() {
        if (!CONFIG_FILE.getParentFile().exists()) {
            CONFIG_FILE.getParentFile().mkdirs();
        }

        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                instance = GSON.fromJson(reader, ConfigData.class);
            } catch (IOException e) {
                SavsGlobalChat.LOGGER.error("Failed to load config", e);
            }
        } else {
            save();
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(instance, writer);
        } catch (IOException e) {
            SavsGlobalChat.LOGGER.error("Failed to save config", e);
        }
    }
}
