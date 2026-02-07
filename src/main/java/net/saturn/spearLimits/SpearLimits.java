package net.saturn.spearLimits;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.SmithingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public final class SpearLimits extends JavaPlugin implements Listener {

    private final Map<String, Integer> spearLimits = new HashMap<>();
    private final Map<String, Integer> globalCraftedSpears = new HashMap<>();
    private final Map<Material, String> spearMaterials = new HashMap<>();
    private final Set<String> broadcastSpears = new HashSet<>();
    private boolean broadcastingEnabled = true;
    private File dataFile;
    private FileConfiguration dataConfig;

    @Override
    public void onEnable() {
        // Initialize materials
        initializeMaterials();

        // Load config and data
        saveDefaultConfig();
        loadLimitsFromConfig();
        loadData();

        // Register events
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new BroadcastListener(this), this);

        // Register commands using Paper's LifecycleEventManager
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            event.registrar().register("spearlimit", List.of("spearlimits"), new SpearLimitCommand());
            event.registrar().register("spearbroadcast", List.of("spearbroadcasts"), new SpearBroadcastCommand());
        });

        getLogger().info("SpearLimits 1.21.11 has been enabled!");
    }

    private void initializeMaterials() {
        safeAddMaterial(Material.WOODEN_SPEAR, "wood");
        safeAddMaterial(Material.STONE_SPEAR, "stone");
        safeAddMaterial(Material.GOLDEN_SPEAR, "gold");
        safeAddMaterial(Material.IRON_SPEAR, "iron");
        safeAddMaterial(Material.DIAMOND_SPEAR, "diamond");
        safeAddMaterial(Material.NETHERITE_SPEAR, "netherite");
        safeAddMaterial(Material.COPPER_SPEAR, "copper");
    }

    private void safeAddMaterial(Material mat, String name) {
        if (mat != null) {
            spearMaterials.put(mat, name);
        }
    }

    @Override
    public void onDisable() {
        saveData();
        getLogger().info("SpearLimits has been disabled!");
    }

    private void loadLimitsFromConfig() {
        reloadConfig();
        
        // Load limits
        ConfigurationSection limitsSection = getConfig().getConfigurationSection("limits");
        if (limitsSection == null) {
            limitsSection = getConfig().createSection("limits");
            limitsSection.set("wood", 5);
            limitsSection.set("stone", 5);
            limitsSection.set("gold", 5);
            limitsSection.set("iron", 5);
            limitsSection.set("diamond", 5);
            limitsSection.set("netherite", 5);
            limitsSection.set("copper", 5);
        }

        for (String key : limitsSection.getKeys(false)) {
            spearLimits.put(key.toLowerCase(), limitsSection.getInt(key));
        }

        // Load broadcasting settings
        if (!getConfig().contains("broadcasting")) {
            ConfigurationSection broadcastSection = getConfig().createSection("broadcasting");
            broadcastSection.set("enabled", true);
            broadcastSection.set("spears", Arrays.asList("wood", "stone", "copper", "gold", "iron", "diamond", "netherite"));
        }
        
        broadcastingEnabled = getConfig().getBoolean("broadcasting.enabled", true);
        broadcastSpears.clear();
        List<String> spears = getConfig().getStringList("broadcasting.spears");
        for (String s : spears) {
            broadcastSpears.add(s.toLowerCase());
        }
        
        saveConfig();
    }

    public boolean isBroadcastingEnabled() {
        return broadcastingEnabled;
    }

    public void setBroadcastingEnabled(boolean enabled) {
        this.broadcastingEnabled = enabled;
        getConfig().set("broadcasting.enabled", enabled);
        saveConfig();
    }

    public boolean shouldBroadcast(String spearType) {
        return broadcastSpears.contains(spearType.toLowerCase());
    }

    public void setShouldBroadcast(String spearType, boolean should) {
        if (should) {
            broadcastSpears.add(spearType.toLowerCase());
        } else {
            broadcastSpears.remove(spearType.toLowerCase());
        }
        getConfig().set("broadcasting.spears", new ArrayList<>(broadcastSpears));
        saveConfig();
    }

    public String getSpearType(Material material) {
        return spearMaterials.get(material);
    }

    private void loadData() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        ConfigurationSection globalSection = dataConfig.getConfigurationSection("global_counts");
        if (globalSection != null) {
            for (String type : globalSection.getKeys(false)) {
                globalCraftedSpears.put(type.toLowerCase(), globalSection.getInt(type));
            }
        }
    }

    private void saveData() {
        if (dataConfig == null) return;
        
        dataConfig.set("global_counts", null);
        for (Map.Entry<String, Integer> entry : globalCraftedSpears.entrySet()) {
            dataConfig.set("global_counts." + entry.getKey(), entry.getValue());
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onCrafterCraft(CrafterCraftEvent event) {
        ItemStack result = event.getRecipe().getResult();
        if (spearMaterials.containsKey(result.getType())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPrepareSmithing(PrepareSmithingEvent event) {
        ItemStack result = event.getResult();
        if (result == null || result.getType() == Material.AIR) return;

        String spearType = spearMaterials.get(result.getType());
        if (spearType == null) return;

        int currentTotal = getGlobalCount(spearType);
        int limit = spearLimits.getOrDefault(spearType, 5);

        if (currentTotal >= limit) {
            event.setResult(new ItemStack(Material.AIR));
        }
    }

    @EventHandler
    public void onSmithItem(SmithItemEvent event) {
        ItemStack result = event.getInventory().getResult();
        if (result == null || result.getType() == Material.AIR) return;

        String spearType = spearMaterials.get(result.getType());
        if (spearType == null) return;

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        int currentTotal = getGlobalCount(spearType);
        int limit = spearLimits.getOrDefault(spearType, 5);

        if (currentTotal >= limit) {
            event.setCancelled(true);
            player.sendMessage(Component.text("The server-wide limit of ", NamedTextColor.RED)
                    .append(Component.text(limit + " " + spearType, NamedTextColor.YELLOW))
                    .append(Component.text(" spears has been reached!", NamedTextColor.RED)));
            event.getInventory().setResult(new ItemStack(Material.AIR));
            return;
        }

        // Success - increment the global count by 1 (smithing always produces 1)
        incrementGlobalCount(spearType, 1);
        
        int newTotal = currentTotal + 1;
        player.sendMessage(Component.text("Converted to ", NamedTextColor.GREEN)
                .append(Component.text("1 " + spearType, NamedTextColor.YELLOW))
                .append(Component.text(" spear! Server total: (", NamedTextColor.GREEN))
                .append(Component.text(newTotal + "/" + limit, NamedTextColor.YELLOW))
                .append(Component.text(")", NamedTextColor.GREEN)));
        saveData();
    }

    @EventHandler
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        ItemStack result = event.getRecipe() != null ? event.getRecipe().getResult() : null;
        if (result == null) return;

        // Also handle Crafter inventories if they trigger this event
        String spearType = spearMaterials.get(result.getType());
        if (spearType == null) return;

        int currentTotal = getGlobalCount(spearType);
        int limit = spearLimits.getOrDefault(spearType, 5);

        if (currentTotal >= limit || event.getInventory().getType().name().equals("CRAFTER")) {
            event.getInventory().setResult(new ItemStack(Material.AIR));
        }
    }

    @EventHandler
    public void onCraftItem(CraftItemEvent event) {
        ItemStack result = event.getRecipe().getResult();
        String spearType = spearMaterials.get(result.getType());
        if (spearType == null) return;

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        int currentTotal = getGlobalCount(spearType);
        int limit = spearLimits.getOrDefault(spearType, 5);

        // Calculate how many are being crafted
        int amountToCraft = 1;
        if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
            amountToCraft = calculateMaxCraftAmount(event.getInventory());
        }

        if (currentTotal >= limit) {
            event.setCancelled(true);
            player.sendMessage(Component.text("The server-wide limit of ", NamedTextColor.RED)
                    .append(Component.text(limit + " " + spearType, NamedTextColor.YELLOW))
                    .append(Component.text(" spears has been reached!", NamedTextColor.RED)));
            event.getInventory().setResult(new ItemStack(Material.AIR));
            return;
        }

        // If shift-clicking would exceed the limit, we cancel it to be safe.
        if (currentTotal + amountToCraft > limit) {
            event.setCancelled(true);
            player.sendMessage(Component.text("Crafting this many would exceed the server-wide limit of ", NamedTextColor.RED)
                    .append(Component.text(String.valueOf(limit), NamedTextColor.YELLOW))
                    .append(Component.text("!", NamedTextColor.RED)));
            player.sendMessage(Component.text("Current total: ", NamedTextColor.GRAY)
                    .append(Component.text(currentTotal + "/" + limit, NamedTextColor.YELLOW)));
            return;
        }

        // Success - increment the global count by the amount crafted
        incrementGlobalCount(spearType, amountToCraft);
        
        int newTotal = currentTotal + amountToCraft;
        player.sendMessage(Component.text("Crafted ", NamedTextColor.GREEN)
                .append(Component.text(amountToCraft + " " + spearType, NamedTextColor.YELLOW))
                .append(Component.text(" spear(s)! Server total: (", NamedTextColor.GREEN))
                .append(Component.text(newTotal + "/" + limit, NamedTextColor.YELLOW))
                .append(Component.text(")", NamedTextColor.GREEN)));
        saveData();
    }

    private int calculateMaxCraftAmount(CraftingInventory inv) {
        int max = Integer.MAX_VALUE;
        for (ItemStack item : inv.getMatrix()) {
            if (item != null && item.getType() != Material.AIR) {
                if (item.getAmount() < max) {
                    max = item.getAmount();
                }
            }
        }
        return (max == Integer.MAX_VALUE) ? 0 : max;
    }

    private int getGlobalCount(String type) {
        return globalCraftedSpears.getOrDefault(type.toLowerCase(), 0);
    }

    private void incrementGlobalCount(String type, int amount) {
        String key = type.toLowerCase();
        globalCraftedSpears.put(key, globalCraftedSpears.getOrDefault(key, 0) + amount);
    }

    private class SpearLimitCommand implements BasicCommand {
        @Override
        public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
            if (!stack.getSender().hasPermission("spearlimits.admin")) {
                stack.getSender().sendMessage(Component.text("You don't have permission to use this command!", NamedTextColor.RED));
                return;
            }

            if (args.length == 0) {
                stack.getSender().sendMessage(Component.text("---- Global Spear Limits ----", NamedTextColor.YELLOW));
                spearLimits.forEach((type, limit) -> {
                    String limitDisplay = (limit == Integer.MAX_VALUE) ? "infinite" : String.valueOf(limit);
                    int current = getGlobalCount(type);
                    stack.getSender().sendMessage(Component.text("- ", NamedTextColor.GRAY)
                            .append(Component.text(type + ": ", NamedTextColor.WHITE))
                            .append(Component.text(current + "/" + limitDisplay, NamedTextColor.GREEN)));
                });
                stack.getSender().sendMessage(Component.text("Usage: /spearlimit <type> <limit>", NamedTextColor.YELLOW));
                return;
            }

            if (args.length != 2) {
                stack.getSender().sendMessage(Component.text("Usage: /spearlimit <type> <limit>", NamedTextColor.RED));
                return;
            }

            String type = args[0].toLowerCase();
            int limit;

            if (args[1].equalsIgnoreCase("inf")) {
                limit = Integer.MAX_VALUE;
            } else {
                try {
                    limit = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    stack.getSender().sendMessage(Component.text("Invalid number: " + args[1], NamedTextColor.RED));
                    return;
                }
            }

            if (limit < 0) {
                stack.getSender().sendMessage(Component.text("Limit cannot be negative!", NamedTextColor.RED));
                return;
            }

            spearLimits.put(type, limit);
            getConfig().set("limits." + type, limit);
            saveConfig();

            String limitMsg = (limit == Integer.MAX_VALUE) ? "infinite" : String.valueOf(limit);
            stack.getSender().sendMessage(Component.text("Set global limit for ", NamedTextColor.GREEN)
                    .append(Component.text(type, NamedTextColor.YELLOW))
                    .append(Component.text(" spears to ", NamedTextColor.GREEN))
                    .append(Component.text(limitMsg, NamedTextColor.YELLOW))
                    .append(Component.text(".", NamedTextColor.GREEN)));
        }

        @Override
        public @NotNull Collection<String> suggest(@NotNull CommandSourceStack stack, @NotNull String[] args) {
            if (args.length <= 1) {
                String input = args.length == 0 ? "" : args[0].toLowerCase();
                return Arrays.asList("wood", "stone", "copper", "gold", "iron", "diamond", "netherite").stream()
                        .filter(type -> type.startsWith(input))
                        .collect(Collectors.toList());
            }
            if (args.length == 2) {
                return Arrays.asList("0", "1", "2", "3", "4", "5", "inf").stream()
                        .filter(val -> val.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            return Collections.emptyList();
        }
    }

    private class SpearBroadcastCommand implements BasicCommand {
        @Override
        public void execute(@NotNull CommandSourceStack stack, @NotNull String[] args) {
            if (!stack.getSender().hasPermission("spearlimits.admin")) {
                stack.getSender().sendMessage(Component.text("You don't have permission to use this command!", NamedTextColor.RED));
                return;
            }

            if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
                stack.getSender().sendMessage(Component.text("---- Spear Broadcast Settings ----", NamedTextColor.YELLOW));
                stack.getSender().sendMessage(Component.text("Global: ", NamedTextColor.GRAY)
                        .append(Component.text(broadcastingEnabled ? "Enabled" : "Disabled", broadcastingEnabled ? NamedTextColor.GREEN : NamedTextColor.RED)));
                stack.getSender().sendMessage(Component.text("Spears:", NamedTextColor.GRAY));
                spearMaterials.values().stream().distinct().sorted().forEach(type -> {
                    boolean should = shouldBroadcast(type);
                    stack.getSender().sendMessage(Component.text("- ", NamedTextColor.GRAY)
                            .append(Component.text(type + ": ", NamedTextColor.WHITE))
                            .append(Component.text(should ? "ON" : "OFF", should ? NamedTextColor.GREEN : NamedTextColor.RED)));
                });
                stack.getSender().sendMessage(Component.text("Usage:", NamedTextColor.YELLOW));
                stack.getSender().sendMessage(Component.text("/spearbroadcast toggle", NamedTextColor.GRAY));
                stack.getSender().sendMessage(Component.text("/spearbroadcast set <type> <on/off>", NamedTextColor.GRAY));
                return;
            }

            if (args[0].equalsIgnoreCase("toggle")) {
                setBroadcastingEnabled(!broadcastingEnabled);
                stack.getSender().sendMessage(Component.text("Global broadcasting is now ", NamedTextColor.GREEN)
                        .append(Component.text(broadcastingEnabled ? "enabled" : "disabled", broadcastingEnabled ? NamedTextColor.GREEN : NamedTextColor.RED))
                        .append(Component.text(".", NamedTextColor.GREEN)));
                return;
            }

            if (args[0].equalsIgnoreCase("set")) {
                if (args.length != 3) {
                    stack.getSender().sendMessage(Component.text("Usage: /spearbroadcast set <type> <on/off>", NamedTextColor.RED));
                    return;
                }

                String type = args[1].toLowerCase();
                if (!spearMaterials.containsValue(type)) {
                    stack.getSender().sendMessage(Component.text("Invalid spear type: " + type, NamedTextColor.RED));
                    return;
                }

                boolean should;
                if (args[2].equalsIgnoreCase("on") || args[2].equalsIgnoreCase("true")) {
                    should = true;
                } else if (args[2].equalsIgnoreCase("off") || args[2].equalsIgnoreCase("false")) {
                    should = false;
                } else {
                    stack.getSender().sendMessage(Component.text("Invalid value: " + args[2] + " (use on/off)", NamedTextColor.RED));
                    return;
                }

                setShouldBroadcast(type, should);
                stack.getSender().sendMessage(Component.text("Broadcasting for ", NamedTextColor.GREEN)
                        .append(Component.text(type, NamedTextColor.YELLOW))
                        .append(Component.text(" is now ", NamedTextColor.GREEN))
                        .append(Component.text(should ? "ON" : "OFF", should ? NamedTextColor.GREEN : NamedTextColor.RED))
                        .append(Component.text(".", NamedTextColor.GREEN)));
                return;
            }

            stack.getSender().sendMessage(Component.text("Unknown subcommand. Use status, toggle, or set.", NamedTextColor.RED));
        }

        @Override
        public @NotNull Collection<String> suggest(@NotNull CommandSourceStack stack, @NotNull String[] args) {
            if (args.length <= 1) {
                String input = args.length == 0 ? "" : args[0].toLowerCase();
                return Arrays.asList("status", "toggle", "set").stream()
                        .filter(s -> s.startsWith(input))
                        .collect(Collectors.toList());
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
                String input = args[1].toLowerCase();
                return spearMaterials.values().stream().distinct().sorted()
                        .filter(type -> type.startsWith(input))
                        .collect(Collectors.toList());
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
                String input = args[2].toLowerCase();
                return Arrays.asList("on", "off").stream()
                        .filter(s -> s.startsWith(input))
                        .collect(Collectors.toList());
            }
            return Collections.emptyList();
        }
    }
}
