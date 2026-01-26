package net.saturn.spearLimits;

import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class SpearLimits extends JavaPlugin implements Listener {

    private final Map<String, Integer> spearLimits = new HashMap<>();
    private final Map<UUID, Map<String, Integer>> playerCraftedSpears = new HashMap<>();
    private final Map<Material, String> spearMaterials = new HashMap<>();

    @Override
    public void onEnable() {
        // Map spear materials to their type names
        spearMaterials.put(Material.WOODEN_SPEAR, "wood");
        spearMaterials.put(Material.STONE_SPEAR, "stone");
        spearMaterials.put(Material.GOLDEN_SPEAR, "gold");
        spearMaterials.put(Material.IRON_SPEAR, "iron");
        spearMaterials.put(Material.DIAMOND_SPEAR, "diamond");
        spearMaterials.put(Material.NETHERITE_SPEAR, "netherite");

        // Load config
        loadLimitsFromConfig();

        getServer().getPluginManager().registerEvents(this, this);

        getLogger().info("SpearLimits has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("SpearLimits has been disabled!");
    }

    private void loadLimitsFromConfig() {
        FileConfiguration config = getConfig();

        // Set defaults if config is empty
        config.addDefault("limits.wood", 5);
        config.addDefault("limits.stone", 5);
        config.addDefault("limits.gold", 5);
        config.addDefault("limits.iron", 5);
        config.addDefault("limits.diamond", 5);
        config.addDefault("limits.netherite", 5);
        config.options().copyDefaults(true);
        saveConfig();

        spearLimits.put("wood", config.getInt("limits.wood", 5));
        spearLimits.put("stone", config.getInt("limits.stone", 5));
        spearLimits.put("gold", config.getInt("limits.gold", 5));
        spearLimits.put("iron", config.getInt("limits.iron", 5));
        spearLimits.put("diamond", config.getInt("limits.diamond", 5));
        spearLimits.put("netherite", config.getInt("limits.netherite", 5));
    }

    private void saveLimitsToConfig() {
        FileConfiguration config = getConfig();

        for (Map.Entry<String, Integer> entry : spearLimits.entrySet()) {
            config.set("limits." + entry.getKey(), entry.getValue());
        }

        saveConfig();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("spearlimit")) {
            return false;
        }

        if (!sender.hasPermission("spearlimits.admin")) {
            sender.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage("§cUsage: /spearlimit <spear_type> <limit>");
            sender.sendMessage("§cSpear types: wood, stone, gold, iron, diamond, netherite");
            return true;
        }

        String spearType = args[0].toLowerCase();

        if (!spearLimits.containsKey(spearType)) {
            sender.sendMessage("§cInvalid spear type! Valid types: wood, stone, gold, iron, diamond, netherite");
            return true;
        }

        int limit;
        try {
            limit = Integer.parseInt(args[1]);
            if (limit < 0) {
                sender.sendMessage("§cLimit must be a positive number!");
                return true;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid number!");
            return true;
        }

        spearLimits.put(spearType, limit);
        saveLimitsToConfig();

        sender.sendMessage("§aSpear limit for §e" + spearType + "§a set to §e" + limit);

        return true;
    }

    @EventHandler
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getRecipe() != null ? event.getRecipe().getResult() : null;

        if (result == null) {
            return;
        }

        // Check if this is a spear
        String spearType = getSpearType(result.getType());

        if (spearType == null) {
            return; // Not a spear
        }

        if (!(event.getView().getPlayer() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getView().getPlayer();

        // Check if player has reached the limit
        int currentCount = getPlayerSpearCount(player.getUniqueId(), spearType);
        int limit = spearLimits.getOrDefault(spearType, 5);

        if (currentCount >= limit) {
            event.getInventory().setResult(new ItemStack(Material.AIR));
        }
    }

    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        ItemStack result = event.getRecipe().getResult();

        // Check if this is a spear
        String spearType = getSpearType(result.getType());

        if (spearType == null) {
            return; // Not a spear
        }

        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        int currentCount = getPlayerSpearCount(player.getUniqueId(), spearType);
        int limit = spearLimits.getOrDefault(spearType, 5);

        if (currentCount >= limit) {
            event.setCancelled(true);
            player.sendMessage("§cYou have reached the maximum limit of §e" + limit + " " + spearType + "§c spears!");
            return;
        }

        // Increment the count
        incrementPlayerSpearCount(player.getUniqueId(), spearType);

        int newCount = currentCount + 1;
        player.sendMessage("§aYou have crafted a §e" + spearType + "§a spear! (§e" + newCount + "/" + limit + "§a)");
    }

    private String getSpearType(Material material) {
        return spearMaterials.get(material);
    }

    private int getPlayerSpearCount(UUID playerUuid, String spearType) {
        return playerCraftedSpears
                .getOrDefault(playerUuid, new HashMap<>())
                .getOrDefault(spearType, 0);
    }

    private void incrementPlayerSpearCount(UUID playerUuid, String spearType) {
        playerCraftedSpears.putIfAbsent(playerUuid, new HashMap<>());
        Map<String, Integer> spearCounts = playerCraftedSpears.get(playerUuid);
        spearCounts.put(spearType, spearCounts.getOrDefault(spearType, 0) + 1);
    }
}