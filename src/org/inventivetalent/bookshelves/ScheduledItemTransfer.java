package org.inventivetalent.bookshelves;

import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class ScheduledItemTransfer implements Runnable {

    private final Inventory source;

    private final Inventory destination;

    private final ItemStack itemStack;

    public ScheduledItemTransfer(Inventory source, Inventory destination, ItemStack itemStack, int seconds) {
        this.source = source;
        this.destination = destination;
        this.itemStack = itemStack;

        Bukkit.getScheduler().runTaskLater(Bookshelves.instance, this, 20 * seconds);
    }

    @Override
    public void run() {
        if (source.contains(itemStack)) {
            if (destination.firstEmpty() > -1) {
                if (itemStack.getAmount() <= 2) {
                    source.remove(itemStack);
                    destination.addItem(itemStack);
                } else {
                    ItemStack partialStack = itemStack.clone();
                    partialStack.setAmount(2);
                    itemStack.setAmount(itemStack.getAmount() - 2);
                    destination.addItem(partialStack);
                }
            }
        }
    }

}
