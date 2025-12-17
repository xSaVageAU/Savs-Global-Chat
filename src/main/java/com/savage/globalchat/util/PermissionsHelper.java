package com.savage.globalchat.util;

import com.savage.globalchat.SavsGlobalChat;
import net.minecraft.server.command.ServerCommandSource;

public class PermissionsHelper {

    private static boolean permissionsApiAvailable;

    static {
        try {
            Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            permissionsApiAvailable = true;
            SavsGlobalChat.LOGGER.info("Fabric Permissions API found. Using granular permissions.");
        } catch (ClassNotFoundException e) {
            permissionsApiAvailable = false;
            SavsGlobalChat.LOGGER.warn("Fabric Permissions API NOT found. Falling back to vanilla OP levels.");
        }
    }

    public static boolean check(ServerCommandSource source, String node, int level) {
        if (permissionsApiAvailable) {
            try {
                return me.lucko.fabric.api.permissions.v0.Permissions.check(
                        source,
                        node,
                        level
                );
            } catch (Throwable t) {
                SavsGlobalChat.LOGGER.error("Error checking permissions for node " + node + ". Falling back to vanilla check.", t);
                return checkVanilla(source, level);
            }
        }
        return checkVanilla(source, level);
    }

    public static boolean check(net.minecraft.server.network.ServerPlayerEntity player, String node, int level) {
        return check(player.getCommandSource(), node, level);
    }

    private static boolean checkVanilla(ServerCommandSource source, int level) {
        return source.hasPermissionLevel(level);
    }
}
