package com.savage.globalchat.command;

import com.mojang.brigadier.CommandDispatcher;
import com.savage.globalchat.redis.RedisManager;
import com.savage.globalchat.util.PermissionsHelper;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.concurrent.CompletableFuture;

public class AdminCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("chat")
            .then(CommandManager.literal("reconnect")
                .executes(context -> {
                    ServerCommandSource source = context.getSource();
                    
                    // Permission Check
                    if (source.isExecutedByPlayer() && !PermissionsHelper.check(source.getPlayer(), "globalchat.admin", 4)) {
                        source.sendError(Text.literal("You do not have permission to use this command."));
                        return 0;
                    }

                    source.sendFeedback(() -> Text.literal("§eForce reconnecting to Redis..."), true);
                    
                    // Async Execution
                    CompletableFuture.runAsync(() -> {
                        RedisManager.connect();
                    }).thenRun(() -> {
                         source.sendFeedback(() -> Text.literal("§aReconnection attempt initiated. Check console."), true);
                    });

                    return 1;
                })
            )
        );
    }
}
