package com.jeronea.neafutbol.utils.selection;

import com.jeronea.neafutbol.NeaFutbol;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.BoundingBox;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GoalSelectionManager {

    private final NeaFutbol plugin;
    private final Map<UUID, Location> pos1Map = new HashMap<>();
    private final Map<UUID, Location> pos2Map = new HashMap<>();

    public GoalSelectionManager(NeaFutbol plugin) {
        this.plugin = plugin;
    }

    public void setPos1(Player player, Location loc) {
        pos1Map.put(player.getUniqueId(), loc);
        player.sendMessage(plugin.getConfigManager().getMessage("wand-pos1")
                .replace("%loc%", formatLoc(loc)));
        showParticles(player);
    }

    public void setPos2(Player player, Location loc) {
        pos2Map.put(player.getUniqueId(), loc);
        player.sendMessage(plugin.getConfigManager().getMessage("wand-pos2")
                .replace("%loc%", formatLoc(loc)));
        showParticles(player);
    }

    public boolean hasSelection(Player player) {
        return pos1Map.containsKey(player.getUniqueId()) && pos2Map.containsKey(player.getUniqueId());
    }

    public BoundingBox getSelection(Player player) {
        if (!hasSelection(player)) return null;
        Location p1 = pos1Map.get(player.getUniqueId());
        Location p2 = pos2Map.get(player.getUniqueId());

        if (p1 == null || p2 == null || !p1.getWorld().equals(p2.getWorld())) return null;

        return BoundingBox.of(p1, p2);
    }

    private String formatLoc(Location loc) {
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }

    public void showParticles(Player player) {
        BoundingBox box = getSelection(player);
        if (box == null) return;

        World world = pos1Map.get(player.getUniqueId()).getWorld();
        if (world == null) return;

        double minX = box.getMinX();
        double minY = box.getMinY();
        double minZ = box.getMinZ();
        double maxX = box.getMaxX();
        double maxY = box.getMaxY();
        double maxZ = box.getMaxZ();

        // Crear marco de partículas
        for (double x = minX; x <= maxX; x += 0.5) {
            spawnParticle(world, x, minY, minZ);
            spawnParticle(world, x, maxY, minZ);
            spawnParticle(world, x, minY, maxZ);
            spawnParticle(world, x, maxY, maxZ);
        }
        for (double y = minY; y <= maxY; y += 0.5) {
            spawnParticle(world, minX, y, minZ);
            spawnParticle(world, maxX, y, minZ);
            spawnParticle(world, minX, y, maxZ);
            spawnParticle(world, maxX, y, maxZ);
        }
        for (double z = minZ; z <= maxZ; z += 0.5) {
            spawnParticle(world, minX, minY, z);
            spawnParticle(world, maxX, minY, z);
            spawnParticle(world, minX, maxY, z);
            spawnParticle(world, maxX, maxY, z);
        }
    }

    private void spawnParticle(World world, double x, double y, double z) {
        // Usamos COMPOSTER (verde) o VILLAGER_HAPPY
        world.spawnParticle(Particle.COMPOSTER, x, y, z, 1, 0, 0, 0, 0);
    }
}
