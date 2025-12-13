package com.savage.globalchat.util;

import com.savage.globalchat.SavsGlobalChat;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;

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
                        PermissionLevel.fromLevel(level)
                );
            } catch (Throwable t) {
                SavsGlobalChat.LOGGER.error("Error checking permissions for node " + node + ". Falling back to vanilla check.", t);
                return checkVanilla(source, level);
            }
        }
        return checkVanilla(source, level);
    }

    private static boolean checkVanilla(ServerCommandSource source, int level) {
        // In 1.21.11+, source.hasPermissionLevel(int) might be deprecated or moved.
        // Using the robust check found in reference project:
        // return source.getPermissions().hasPermission(new Permission.Level(PermissionLevel.fromLevel(level)));
        
        // However, if getPermissions() is complex, let's try to see if hasPermissionLevel exists but requires casting?
        // Reference code used: source.getPermissions().hasPermission(...)
        // I will trust the reference code.
        
        return source.getPermissions().hasPermission(new Permission.Level(PermissionLevel.fromLevel(level)));
    }
}
