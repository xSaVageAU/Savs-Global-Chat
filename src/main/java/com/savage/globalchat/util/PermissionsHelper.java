package com.savage.globalchat.util;

import com.savage.globalchat.SavsGlobalChat;
import net.minecraft.server.command.ServerCommandSource;

public class PermissionsHelper {

    public static boolean check(ServerCommandSource source, String node, int level) {
        // 1. Check OP Level (Universal)
        if (source.hasPermissionLevel(level)) {
            return true;
        }

        // 2. Check Vanilla Tags (Universal - equivalent to permission nodes)
        // e.g. /tag <player> add globalchat.channel.staff
        try {
            if (source.getEntity() != null && source.getEntity().getCommandTags().contains(node)) {
                return true;
            }
        } catch (Throwable t) {
            // Ignore (Command block, etc.)
        }

        return false;
    }

    public static boolean check(net.minecraft.server.network.ServerPlayerEntity player, String node, int level) {
        return check(player.getCommandSource(), node, level);
    }
}
