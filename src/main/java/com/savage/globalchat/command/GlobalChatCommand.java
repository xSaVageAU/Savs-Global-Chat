package com.savage.globalchat.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.savage.globalchat.redis.RedisManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

public class GlobalChatCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("g")
            .then(CommandManager.argument("message", StringArgumentType.greedyString())
                .executes(context -> {
                    String message = StringArgumentType.getString(context, "message");
                    ServerCommandSource source = context.getSource();
                    
                    if (source.isExecutedByPlayer()) {
                        String player = source.getPlayer().getName().getString();
                        boolean sent = RedisManager.publishChat(player, message, "GLOBAL");
                        if (!sent) {
                            source.sendFeedback(() -> Text.literal("§c[Error] Communication channels not responding."), false);
                        }
                    } else {
                        RedisManager.publishChat("Console", message, "GLOBAL");
                    }
                    
                    return 1;
                })));
    }
}
