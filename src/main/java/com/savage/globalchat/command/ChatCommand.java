package com.savage.globalchat.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.savage.globalchat.manager.ChannelManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class ChatCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("chat")
            .then(CommandManager.literal("local")
                .executes(context -> setChannel(context.getSource(), ChannelManager.ChannelType.LOCAL)))
            .then(CommandManager.literal("global")
                .executes(context -> setChannel(context.getSource(), ChannelManager.ChannelType.GLOBAL)))
            .then(CommandManager.literal("staff")
                .executes(context -> setChannel(context.getSource(), ChannelManager.ChannelType.STAFF)))
        );
    }

    private static int setChannel(ServerCommandSource source, ChannelManager.ChannelType channel) {
        // Console Handling
        if (!source.isExecutedByPlayer()) {
            // Console is always Staff/Admin
            if (channel == ChannelManager.ChannelType.STAFF) {
                 // No-op for console tracking as it doesn't have a UUID in ChannelManager strictly speaking, 
                 // but we can allow it to 'switch' conceptually or just ignore.
                 // For now, let's just say "Console switched" but we can't store UUID.
                 source.sendFeedback(() -> Text.literal("Console switched to " + channel.name()), false);
                 return 1;
            }
             source.sendFeedback(() -> Text.literal("Console cannot join non-admin channels widely."), false);
             return 0;
        }

        // Player Handling
        if (channel == ChannelManager.ChannelType.STAFF) {
            if (!com.savage.globalchat.util.PermissionsHelper.check(source, "globalchat.channel.staff", 2)) {
                 source.sendFeedback(() -> Text.literal("§cYou do not have permission to join the Staff channel."), false);
                 return 0;
            }
        }

        ChannelManager.setPlayerChannel(source.getPlayer().getUuid(), channel);
        source.sendFeedback(() -> Text.literal("§eSwitched to §f" + channel.name() + " §echat."), false);
        return 1;
    }
}
