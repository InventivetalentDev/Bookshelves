package org.inventivetalent.bookshelves;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.managers.RegionManager;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.inventivetalent.bookshelves.Bookshelves;

import java.util.Optional;

public class WorldGuardUtils {

    private static final StateFlag stateFlag = new StateFlag( "bookshelf-access", false);

    public static void registerBookshelfAccessFlag() {
        try
        {
            WorldGuard.getInstance().getFlagRegistry().register( stateFlag );
        }
        catch (FlagConflictException ex)
        {
            Bookshelves.instance.getLogger().warning("The flag '" + stateFlag.getName() + "' is already registered by another plugin. Conflicts might occur!");
        }
    }

    public static boolean isAllowedToAccess(Player player, Block block) {
        if (player.hasPermission("worldguard.region.bypass." + block.getWorld().getName())) { return true; }

        WorldGuard worldGuard = WorldGuard.getInstance();
        Optional<RegionManager> regionManager = Optional.ofNullable( worldGuard.getPlatform().getRegionContainer().get(BukkitAdapter.adapt(block.getWorld())) );

        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
        BlockVector3 location = BukkitAdapter.adapt(block.getLocation()).toVector().toBlockPoint();

        boolean allowChestAccess = !regionManager.isPresent() || regionManager.get().getApplicableRegions(location).testState(localPlayer, stateFlag);
        boolean isRegionMember = !regionManager.isPresent() || regionManager.get().getApplicableRegions(location).isMemberOfAll(localPlayer);

        return allowChestAccess || isRegionMember;
    }

}
