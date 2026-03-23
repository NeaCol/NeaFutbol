package com.jeronea.neafutbol.game.hologram;

import com.jeronea.neafutbol.NeaFutbol;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;

public class HologramManager {

    private final NeaFutbol plugin;
    private final List<ArmorStand> hologramLines;
    private Location hologramLoc;

    public HologramManager(NeaFutbol plugin) {
        this.plugin = plugin;
        this.hologramLines = new ArrayList<>();
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

    public void setLocation(Location loc) {
        this.hologramLoc = loc;
        plugin.getConfig().set("locations.hologram", loc);
        plugin.saveConfig();
        spawnHologram(0, 0);
    }

    public void removeHologram() {
        for (ArmorStand stand : hologramLines) {
            if (stand != null && !stand.isDead()) {
                stand.remove();
            }
        }
        hologramLines.clear();
        cleanOrphanedHolograms();
    }

    private void cleanOrphanedHolograms() {
        if (hologramLoc == null || hologramLoc.getWorld() == null) return;

        // Buscar ArmorStands con nuestro tag cerca de la ubicación para eliminarlos
        hologramLoc
            .getWorld()
            .getNearbyEntities(hologramLoc, 5, 5, 5)
            .forEach(entity -> {
                if (
                    entity instanceof ArmorStand &&
                    entity.getScoreboardTags().contains("NeaHolo")
                ) {
                    entity.remove();
                }
            });
    }

    public void spawnHologram(int scoreBlue, int scoreRed) {
        removeHologram(); // Limpiar anterior para evitar duplicados

        if (hologramLoc == null) return;
        if (hologramLoc.getWorld() == null) return;

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
        ArmorStand as = (ArmorStand) loc
            .getWorld()
            .spawnEntity(loc, EntityType.ARMOR_STAND);
        as.setVisible(false);
        as.setGravity(false);
        as.setMarker(true);
        as.setCustomNameVisible(true);
        as.setCustomName(text);
        as.setSmall(true);
        as.addScoreboardTag("NeaHolo"); // Tag para identificar y limpiar
        hologramLines.add(as);
    }

    public void updateScore(int blue, int red) {
        if (hologramLines.size() < 4) {
            // Si no están todas las líneas, mejor respawnear
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
        if (hologramLines.size() < 4) return;

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
