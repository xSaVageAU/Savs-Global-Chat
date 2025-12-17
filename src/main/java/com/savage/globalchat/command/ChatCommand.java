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
        );
    }

    private static int setChannel(ServerCommandSource source, ChannelManager.ChannelType channel) {
        if (!source.isExecutedByPlayer()) {
             source.sendFeedback(() -> Text.literal("Console cannot switch channels."), false);
             return 0;
        }

        ChannelManager.setPlayerChannel(source.getPlayer().getUuid(), channel);
        source.sendFeedback(() -> Text.literal("§eSwitched to §f" + channel.name() + " §echat."), false);
        return 1;
    }
}
