package org.inventivetalent.bookshelves;

import com.google.gson.*;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.inventivetalent.itembuilder.ItemBuilder;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Bookshelves extends JavaPlugin {

	public static Bookshelves instance;

	int         INVENTORY_SIZE  = 18;
	String      INVENTORY_TITLE = "Bookshelf";
	Set<String> disabledWorlds  = new HashSet<>();
	boolean     onlyBooks       = true;
	boolean     worldGuardSupport = false;
	boolean 	checkRestrictions = false;
	boolean		hopperSupport = false;
	RestrictionManager restrictionManager = null;

	Set<Location> shelves   = new HashSet<>();
	File          shelfFile = new File(getDataFolder(), "shelves.json");

	@Override
	public void onLoad() {
		worldGuardSupport = Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
		if (worldGuardSupport) {
			getLogger().info("Found WorldGuard plugin");
			WorldGuardUtils.registerBookshelfAccessFlag();
		}
	}

	@Override
	public void onEnable() {
		instance = this;
		Bukkit.getPluginManager().registerEvents(new ShelfListener(this), this);

		// Save configuration & load values
		saveDefaultConfig();
		INVENTORY_SIZE = getConfig().getInt("inventory.size");
		if (INVENTORY_SIZE % 9 != 0) {
			getLogger().warning("Inventory size is not a multiple of 9");
			INVENTORY_SIZE = 18;
		}
		INVENTORY_TITLE = ChatColor.translateAlternateColorCodes('&', getConfig().getString("inventory.title")) +/* Unique title */"§B§S";
		if (getConfig().contains("disabledWorlds")) { disabledWorlds.addAll(getConfig().getStringList("disabledWorlds")); }
		onlyBooks = getConfig().getBoolean("onlyBooks", true);
		checkRestrictions = getConfig().getBoolean("restrictions.enabled");
		hopperSupport = getConfig().getBoolean("hoppers");

		// Initialize restrictions
		if (checkRestrictions) {
			restrictionManager = new RestrictionManager();
		}

		// GriefPrevention compatibility
		if (Bukkit.getPluginManager().isPluginEnabled("GriefPrevention")) {
			getLogger().info("Found GriefPrevention plugin");
			try {
				Class<?> PlayerEventHandler = Class.forName("me.ryanhamshire.GriefPrevention.PlayerEventHandler");
				Object playerEventHandlerInstance = null;

				//PlayerInteractEvent is also handled by PlayerEventHandler
				for (RegisteredListener registeredListener : PlayerInteractEvent.getHandlerList().getRegisteredListeners()) {
					if (PlayerEventHandler.isAssignableFrom(registeredListener.getListener().getClass())) {
						playerEventHandlerInstance = registeredListener.getListener();
						break;
					}
				}
				if (playerEventHandlerInstance == null) {
					getLogger().warning("Could not find PlayerEventHandler for GriefPrevention");
				} else {
					Field inventoryHolderCacheField = AccessUtil.setAccessible(PlayerEventHandler.getDeclaredField("inventoryHolderCache"));
					ConcurrentHashMap<Material, Boolean> inventoryHolderCache = (ConcurrentHashMap<Material, Boolean>) inventoryHolderCacheField.get(playerEventHandlerInstance);

					inventoryHolderCache.put(Material.BOOKSHELF, true);
					getLogger().info("Injected Bookshelf as container type into GriefPrevention");
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		// ShelfFile creation
		if (!shelfFile.exists()) {
			try {
				shelfFile.createNewFile();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		// Schedule bookshelf loading
		Bukkit.getScheduler().runTaskLater(this, new Runnable() {
			@Override
			public void run() {
				getLogger().info("Loading shelves...");
				try {
					JsonElement jsonElement = new JsonParser().parse(new FileReader(shelfFile));
					if (jsonElement.isJsonArray()) {
						JsonArray jsonArray = jsonElement.getAsJsonArray();
						for (Iterator<JsonElement> iterator = jsonArray.iterator(); iterator.hasNext(); ) {
							JsonElement next = iterator.next();
							if (next.isJsonObject()) {
								JsonObject jsonObject = next.getAsJsonObject();
								Location location = JSONToLocation(jsonObject.get("location").getAsJsonObject());
								if (location.getBlock().getType() != Material.BOOKSHELF) { continue; }
								Inventory inventory = initShelf(location.getBlock());
								if (inventory == null) { inventory = getShelf(location.getBlock()); }
								if (inventory == null) { continue; }

								if (jsonObject.has("books")) {
									JsonElement bookElement = jsonObject.get("books");
									if (bookElement.isJsonArray()) {// Old file
										JsonArray bookArray = bookElement.getAsJsonArray();
										for (Iterator<JsonElement> bookIterator = bookArray.iterator(); bookIterator.hasNext(); ) {
											JsonObject nextBook = bookIterator.next().getAsJsonObject();
											int slot = nextBook.get("slot").getAsInt();
											JsonObject jsonItem = nextBook.get("item").getAsJsonObject();

											ConfigurationSection yamlItem = JSONToYAML(jsonItem, new YamlConfiguration());
											ItemStack itemStack = new ItemBuilder(Material.STONE).fromConfig(yamlItem).build();

											inventory.setItem(slot, itemStack);
										}
									} else {
										ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(bookElement.getAsString()));
										BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
										ItemStack[] stacks = new ItemStack[dataInput.readInt()];

										for (int i = 0; i < stacks.length; i++) {
											stacks[i] = (ItemStack) dataInput.readObject();
										}
										dataInput.close();

										inventory.setContents(stacks);
									}
								}
							}
						}
					}

					if (hopperSupport) HopperUtils.initializeHopperSupport();
				} catch (Exception e) {
					e.printStackTrace();
				}
				getLogger().info("Loaded " + shelves.size() + " shelves.");
			}
		}, 40);

		new Metrics(this);
	}

	@Override
	public void onDisable() {
		getLogger().info("Saving shelves...");
		try {
			JsonArray shelfArray = new JsonArray();
			for (Location location : shelves) {
				Block block = location.getBlock();
				Inventory inventory = MetaHelper.getMetaValue(block, "BOOKSHELF_INVENTORY", Inventory.class);
				if (inventory == null) { continue; }
				JsonObject shelfObject = new JsonObject();
				shelfObject.add("location", LocationToJSON(location));
				ItemStack[] contents = inventory.getContents();

				{
					ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
					BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

					dataOutput.writeInt(contents.length);

					for (ItemStack stack : contents) {
						dataOutput.writeObject(stack);
					}
					dataOutput.close();

					shelfObject.addProperty("books", Base64Coder.encodeLines(outputStream.toByteArray()));
				}

				shelfArray.add(shelfObject);
			}

			try (Writer writer = new FileWriter(shelfFile)) {
				new Gson().toJson(shelfArray, writer);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	boolean isValidBook(ItemStack itemStack) {
		if (!onlyBooks) { return checkRestrictions ? restrictionManager.isRestricted(itemStack.getType()) : true; }

		if (itemStack == null) { return false; }
		if (itemStack.getType() == Material.BOOK) { return true; }
		if (itemStack.getType() == Material.WRITABLE_BOOK) { return true; }
		if (itemStack.getType() == Material.ENCHANTED_BOOK) { return true; }
		if (itemStack.getType() == Material.WRITTEN_BOOK) { return true; }
		return false;
	}

	public Inventory initShelf(Block block) {
		Inventory inventory;
		if (!block.hasMetadata("BOOKSHELF_INVENTORY")) {
			inventory = Bukkit.createInventory(null, INVENTORY_SIZE, INVENTORY_TITLE);
			MetaHelper.setMetaValue(block, "BOOKSHELF_INVENTORY", inventory);

			shelves.add(block.getLocation());
			if (hopperSupport) HopperUtils.pull(block);

			return inventory;
		} else {
			inventory = getShelf(block);
			if (inventory != null) {
				shelves.add(block.getLocation());
				return inventory;
			}
		}
		return null;
	}

	public Inventory getShelf(Block block) {
		if (block.hasMetadata("BOOKSHELF_INVENTORY")) {
			return MetaHelper.getMetaValue(block, "BOOKSHELF_INVENTORY", Inventory.class);
		}
		return null;
	}

	public static ConfigurationSection JSONToYAML(JsonObject json, ConfigurationSection section) {
		for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
			String key = entry.getKey();
			JsonElement value = entry.getValue();
			if (value.isJsonObject()) {
				ConfigurationSection var9 = section.getConfigurationSection(key);
				if (var9 == null) {
					var9 = section.createSection(key);
				}

				var9 = JSONToYAML((JsonObject) value, var9);
				section.set(key, var9);
			} else if (!value.isJsonArray()) {
				section.set(key, value.getAsString());
			} else {
				ArrayList list = new ArrayList();
				JsonArray array = (JsonArray) value;

				for (int i = 0; i < array.size(); ++i) {
					list.add(i, array.get(i));
				}

				section.set(key, list);
			}
		}

		return section;
	}

	public static JsonObject LocationToJSON(Location location) {
		JsonObject json = new JsonObject();
		json.addProperty("world", location.getWorld().getName());
		json.addProperty("x", location.getX());
		json.addProperty("y", location.getY());
		json.addProperty("z", location.getZ());
		return json;
	}

	public static Location JSONToLocation(JsonObject json) {
		String worldName = json.get("world").getAsString();
		double x = json.get("x").getAsDouble();
		double y = json.get("y").getAsDouble();
		double z = json.get("z").getAsDouble();
		return new Location(Bukkit.getWorld(worldName), x, y, z);
	}

}
