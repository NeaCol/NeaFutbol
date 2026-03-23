package com.jeronea.neafutbol.utils;

import com.jeronea.neafutbol.NeaFutbol;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class ConfigManager {

    private final NeaFutbol plugin;
    private FileConfiguration langConfig;
    private File langFile;
    // Pattern to match &#RRGGBB
    private static final Pattern HEX_PATTERN = Pattern.compile(
        "&#([A-Fa-f0-9]{6})"
    );

    public ConfigManager(NeaFutbol plugin) {
        this.plugin = plugin;
        saveDefaultLang();
        loadLang();
    }

    public void reload() {
        plugin.reloadConfig();
        loadLang();
    }

    private void saveDefaultLang() {
        if (langFile == null) {
            langFile = new File(plugin.getDataFolder(), "lang.yml");
        }
        if (!langFile.exists()) {
            plugin.saveResource("lang.yml", false);
        }
    }

    public void loadLang() {
        if (langFile == null) {
            langFile = new File(plugin.getDataFolder(), "lang.yml");
        }
        langConfig = YamlConfiguration.loadConfiguration(langFile);

        InputStream defConfigStream = plugin.getResource("lang.yml");
        if (defConfigStream != null) {
            langConfig.setDefaults(
                YamlConfiguration.loadConfiguration(
                    new InputStreamReader(
                        defConfigStream,
                        StandardCharsets.UTF_8
                    )
                )
            );
        }
    }

    public String getMessage(String key) {
        if (langConfig == null) loadLang();

        String prefix = langConfig.getString("prefix", "&8[&bNeaFutbol&8] &7");
        String msg = langConfig.getString(key);

        if (msg == null) {
            return colorize("&cMissing message key: " + key);
        }

        return colorize(prefix + msg);
    }

    public String getMessageNoPrefix(String key) {
        if (langConfig == null) loadLang();
        String msg = langConfig.getString(key);
        if (msg == null) return "Missing key: " + key;
        return colorize(msg);
    }

    public String getPureMessage(String key) {
        return getMessageNoPrefix(key);
    }

    public String colorize(String message) {
        if (message == null) return "";

        // Hex parsing: &#RRGGBB
        Matcher matcher = HEX_PATTERN.matcher(message);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find()) {
            try {
                String hex = "#" + matcher.group(1);
                // Use Spigot/Bungee API for Hex colors
                matcher.appendReplacement(
                    buffer,
                    net.md_5.bungee.api.ChatColor.of(hex).toString()
                );
            } catch (Exception e) {
                // Ignore if invalid
            }
        }
        matcher.appendTail(buffer);

        // Standard codes
        return ChatColor.translateAlternateColorCodes('&', buffer.toString());
    }
}
