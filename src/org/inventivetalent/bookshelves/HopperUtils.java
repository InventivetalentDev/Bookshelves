package org.inventivetalent.bookshelves;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Hopper;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class HopperUtils {

    public static void pull(Block block) {
        Inventory shelf = Bookshelves.instance.getShelf(block);
        List<Hopper> hoppers = getHoppers(block, BlockFace.UP, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);
        int seconds = 1;

        if (shelf.firstEmpty() > -1) {
            for (Hopper hopper : hoppers) {
                for (ItemStack itemStack : hopper.getInventory().getContents()) {
                    new ScheduledItemTransfer(hopper.getInventory(), shelf, itemStack, seconds);
                    seconds++;
                }
            }
        }
        scheduleNextCheck(block, seconds + 1);
        push(block);
    }

    public static void push(Block block) {
        List<Hopper> hoppers = getHoppers(block, BlockFace.DOWN).stream().filter(hopper -> !hopper.isLocked() && hopper.getInventory().firstEmpty() > -1).collect(Collectors.toCollection(ArrayList::new));

        if (!hoppers.isEmpty()) {
            int seconds = 1;
            Hopper hopper = hoppers.get(0);
            Inventory shelf = Bookshelves.instance.getShelf(block);

            for (ItemStack itemStack : shelf) {
                new ScheduledItemTransfer(shelf, hopper.getInventory(), itemStack, seconds);
                seconds++;
            }
        }
    }

    public static List<Hopper> getHoppers(Block block, BlockFace... faces) {
        List<Hopper> hoppers = new ArrayList<>();
        for (BlockFace face : faces) {
            Block relative = block.getRelative(face);
            if (relative.getType() == Material.HOPPER) {
                if (face == BlockFace.DOWN || relative.getRelative(((org.bukkit.block.data.type.Hopper) relative.getBlockData()).getFacing()).equals(block)) {
                    hoppers.add((Hopper) relative.getState());
                }
            }
        }
        return hoppers;
    }

    public static void scheduleNextCheck(Block block, int seconds) {
        Bukkit.getScheduler().runTaskLater(Bookshelves.instance, () -> pull(block), 20 * seconds);
    }

    public static void initializeHopperSupport() {
        Bookshelves.instance.getLogger().info("Hopper support enabled!");
        Bookshelves.instance.shelves.forEach(shelfLocation -> pull(shelfLocation.getBlock()));
    }

}
