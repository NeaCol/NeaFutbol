package com.jeronea.neafutbol.stats;

import com.jeronea.neafutbol.NeaFutbol;
import java.io.File;
import java.io.IOException;
import java.util.UUID;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class StatsManager {

    private final NeaFutbol plugin;
    private File statsFile;
    private FileConfiguration statsConfig;

    public StatsManager(NeaFutbol plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        if (statsFile == null) {
            statsFile = new File(plugin.getDataFolder(), "stats.yml");
        }
        if (!statsFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                statsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("No se pudo crear stats.yml: " + e.getMessage());
            }
        }
        statsConfig = YamlConfiguration.loadConfiguration(statsFile);
    }

    public void save() {
        if (statsConfig == null || statsFile == null) return;
        try {
            statsConfig.save(statsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar stats.yml: " + e.getMessage());
        }
    }

    public int getGoals(UUID uuid) {
        if (uuid == null) return 0;
        return statsConfig.getInt("players." + uuid + ".goals", 0);
    }

    public int getWins(UUID uuid) {
        if (uuid == null) return 0;
        return statsConfig.getInt("players." + uuid + ".wins", 0);
    }

    public int getMatches(UUID uuid) {
        if (uuid == null) return 0;
        return statsConfig.getInt("players." + uuid + ".matches", 0);
    }

    public void addGoal(UUID uuid, String name) {
        if (uuid == null) return;
        String path = "players." + uuid;
        int current = statsConfig.getInt(path + ".goals", 0);
        statsConfig.set(path + ".goals", current + 1);
        if (name != null) {
            statsConfig.set(path + ".name", name);
        }
        save();
    }

    public void addWin(UUID uuid, String name) {
        if (uuid == null) return;
        String path = "players." + uuid;
        int current = statsConfig.getInt(path + ".wins", 0);
        statsConfig.set(path + ".wins", current + 1);
        if (name != null) {
            statsConfig.set(path + ".name", name);
        }
        save();
    }

    public void addMatch(UUID uuid, String name) {
        if (uuid == null) return;
        String path = "players." + uuid;
        int current = statsConfig.getInt(path + ".matches", 0);
        statsConfig.set(path + ".matches", current + 1);
        if (name != null) {
            statsConfig.set(path + ".name", name);
        }
        save();
    }
}
