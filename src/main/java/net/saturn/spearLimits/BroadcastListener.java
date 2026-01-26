package net.saturn.spearLimits;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

public class BroadcastListener implements Listener {
    private final SpearLimits plugin;

    public BroadcastListener(SpearLimits plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        
        ItemStack result = event.getRecipe().getResult();
        String spearType = plugin.getSpearType(result.getType());
        
        if (spearType != null && plugin.isBroadcastingEnabled() && plugin.shouldBroadcast(spearType)) {
            Component message = Component.text("[SpearLimits] ", NamedTextColor.GOLD)
                    .append(Component.text(player.getName(), NamedTextColor.YELLOW))
                    .append(Component.text(" crafted a ", NamedTextColor.GREEN))
                    .append(Component.text(spearType, NamedTextColor.YELLOW))
                    .append(Component.text(" spear!", NamedTextColor.GREEN));
            
            plugin.getServer().broadcast(message);
        }
    }
}
