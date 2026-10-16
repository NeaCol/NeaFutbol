package com.jeronea.neafutbol.game.hologram;

import com.jeronea.neafutbol.NeaFutbol;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataType;

public class HologramManager {

    private final NeaFutbol plugin;
    private final List<ArmorStand> hologramLines;
    private final NamespacedKey holoKey;
    private Location hologramLoc;

    public HologramManager(NeaFutbol plugin) {
        this.plugin = plugin;
        this.hologramLines = new ArrayList<>();
        this.holoKey = new NamespacedKey(plugin, "hologram_line");
    }

    public void load() {
        // Cargar ubicación desde config
        this.hologramLoc = plugin.getConfig().getLocation("locations.hologram");
        if (hologramLoc != null) {
            // Limpiar posibles hologramas huérfanos antes de spawnear el nuevo
            cleanOrphanedHolograms();
            spawnHologram(0, 0);
        }
    }

    public void reload() {
        this.hologramLoc = plugin.getConfig().getLocation("locations.hologram");
        cleanOrphanedHolograms();
        if (hologramLoc != null) {
            int blue = plugin.getGameManager() != null ? plugin.getGameManager().getScoreBlue() : 0;
            int red = plugin.getGameManager() != null ? plugin.getGameManager().getScoreRed() : 0;
            spawnHologram(blue, red);
        } else {
            removeHologram();
        }
    }

    public void setLocation(Location loc) {
        // Limpiar en ubicación previa antes de cambiar la coordenada
        removeHologram();
        this.hologramLoc = loc;
        plugin.getConfig().set("locations.hologram", loc);
        plugin.saveConfig();
        spawnHologram(0, 0);
    }

    public void setLocationNull() {
        this.hologramLoc = null;
    }

    public void removeHologram() {
        for (ArmorStand stand : hologramLines) {
            if (stand != null && !stand.isDead()) {
                stand.remove();
            }
        }
        hologramLines.clear();
    }

    public int cleanOrphanedHolograms() {
        int removed = 0;

        // 1. Limpieza global en todos los mundos cargados (evita fugas en cualquier coordenada)
        for (World world : Bukkit.getWorlds()) {
            for (ArmorStand as : world.getEntitiesByClass(ArmorStand.class)) {
                if (isHologramEntity(as)) {
                    as.remove();
                    removed++;
                }
            }
        }

        // 2. Si la ubicación configurada está cargada, comprobar también por proximidad
        if (hologramLoc != null && hologramLoc.getWorld() != null) {
            World w = hologramLoc.getWorld();
            Chunk chunk = hologramLoc.getChunk();
            if (chunk.isLoaded()) {
                for (Entity entity : w.getNearbyEntities(hologramLoc, 10, 10, 10)) {
                    if (entity instanceof ArmorStand) {
                        ArmorStand as = (ArmorStand) entity;
                        if (isHologramEntity(as)) {
                            as.remove();
                            removed++;
                        }
                    }
                }
            }
        }

        return removed;
    }

    public boolean isHologramEntity(ArmorStand as) {
        if (as == null) return false;
        if (as.getScoreboardTags().contains("NeaHolo")) return true;
        if (as.getPersistentDataContainer().has(holoKey, PersistentDataType.BYTE)) return true;

        // Seguridad extra para limpiar hologramas residuales de versiones anteriores que hayan perdido tags
        String name = as.getCustomName();
        if (name != null && as.isMarker() && as.isSmall()) {
            String stripped = ChatColor.stripColor(name);
            if (stripped.contains("NeaFutbol") ||
                (stripped.contains("AZUL") && stripped.contains("ROJO")) ||
                stripped.startsWith("Tiempo:") ||
                stripped.contains("█████")) {
                return true;
            }
        }
        return false;
    }

    public boolean areLinesValid() {
        if (hologramLines.size() != 4) return false;
        for (ArmorStand as : hologramLines) {
            if (as == null || as.isDead() || !as.isValid()) {
                return false;
            }
        }
        return true;
    }

    public void spawnHologram(int scoreBlue, int scoreRed) {
        removeHologram(); // Limpiar anterior para evitar duplicados

        if (hologramLoc == null) return;
        if (hologramLoc.getWorld() == null) return;

        Chunk chunk = hologramLoc.getChunk();
        if (!chunk.isLoaded()) {
            chunk.load();
        }

        Location currentLoc = hologramLoc.clone();
        double lineSpacing = 0.3;

        // Linea 0: Título (Más arriba)
        String title = plugin
            .getConfigManager()
            .getMessageNoPrefix("hologram-title");
        spawnLine(currentLoc.clone().add(0, lineSpacing * 3, 0), title);

        // Linea 1: Score
        String scoreFormat = plugin
            .getConfigManager()
            .getMessageNoPrefix("hologram-score");
        String scoreText = scoreFormat
            .replace("%blue%", String.valueOf(scoreBlue))
            .replace("%red%", String.valueOf(scoreRed));
        spawnLine(currentLoc.clone().add(0, lineSpacing * 2, 0), scoreText);

        // Linea 2: Tiempo (Inicialmente --:--)
        String timeText = plugin
            .getConfigManager()
            .getMessageNoPrefix("hologram-time")
            .replace("%time%", "--:--");
        spawnLine(currentLoc.clone().add(0, lineSpacing * 1, 0), timeText);

        // Linea 3: Barra (Inicialmente llena)
        String barText = createProgressBar(100, 100);
        spawnLine(currentLoc.clone(), barText);
    }

    private void spawnLine(Location loc, String text) {
        if (loc == null || loc.getWorld() == null) return;
        Chunk chunk = loc.getChunk();
        if (!chunk.isLoaded()) {
            chunk.load();
        }

        ArmorStand as = (ArmorStand) loc
            .getWorld()
            .spawnEntity(loc, EntityType.ARMOR_STAND);
        as.setVisible(false);
        as.setGravity(false);
        as.setMarker(true);
        as.setCustomNameVisible(true);
        as.setCustomName(text);
        as.setSmall(true);
        // CRÍTICO: No persistente para que Minecraft NUNCA guarde estos armor stands en disco al descargar chunks
        as.setPersistent(false);
        as.addScoreboardTag("NeaHolo"); // Tag para identificar y limpiar
        as.getPersistentDataContainer().set(holoKey, PersistentDataType.BYTE, (byte) 1);
        hologramLines.add(as);
    }

    public void updateScore(int blue, int red) {
        if (!areLinesValid()) {
            // Si no están todas las líneas válidas (ej. descarga de chunks o muerte de entidades), respawnear
            spawnHologram(blue, red);
            return;
        }

        ArmorStand scoreLine = hologramLines.get(1); // Índice 1 es Score
        if (scoreLine != null && !scoreLine.isDead()) {
            String scoreFormat = plugin
                .getConfigManager()
                .getMessageNoPrefix("hologram-score");
            String scoreText = scoreFormat
                .replace("%blue%", String.valueOf(blue))
                .replace("%red%", String.valueOf(red));
            scoreLine.setCustomName(scoreText);
        }
    }

    public void updateTime(int secondsLeft, int totalSeconds) {
        if (!areLinesValid()) return;

        // Formato MM:SS
        int minutes = secondsLeft / 60;
        int seconds = secondsLeft % 60;
        String timeString = String.format("%02d:%02d", minutes, seconds);

        // Actualizar línea de tiempo (Índice 2)
        ArmorStand timeLine = hologramLines.get(2);
        if (timeLine != null && !timeLine.isDead()) {
            String msg = plugin
                .getConfigManager()
                .getMessageNoPrefix("hologram-time")
                .replace("%time%", timeString);
            timeLine.setCustomName(msg);
        }

        // Actualizar barra (Índice 3)
        ArmorStand barLine = hologramLines.get(3);
        if (barLine != null && !barLine.isDead()) {
            barLine.setCustomName(createProgressBar(secondsLeft, totalSeconds));
        }
    }

    private String createProgressBar(int current, int max) {
        if (max == 0) max = 1;
        double percentage = (double) current / max;
        int totalBars = 20;
        int filledBars = (int) (percentage * totalBars);

        String symbol = plugin
            .getConfigManager()
            .getPureMessage("hologram-bar-symbol");
        String color;

        if (percentage > 0.5) {
            color = plugin
                .getConfigManager()
                .getPureMessage("hologram-bar-high");
        } else if (percentage > 0.2) {
            color = plugin
                .getConfigManager()
                .getPureMessage("hologram-bar-mid");
        } else {
            color = plugin
                .getConfigManager()
                .getPureMessage("hologram-bar-low");
        }

        StringBuilder bar = new StringBuilder(color);
        for (int i = 0; i < totalBars; i++) {
            if (i < filledBars) {
                bar.append(symbol);
            } else {
                bar.append(ChatColor.GRAY).append(symbol);
            }
        }

        return bar.toString();
    }
}
