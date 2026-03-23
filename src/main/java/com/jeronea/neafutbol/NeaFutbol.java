package com.jeronea.neafutbol;

import com.jeronea.neafutbol.commands.MainCommand;
import com.jeronea.neafutbol.game.GameManager;
import com.jeronea.neafutbol.listeners.GameListener;
import com.jeronea.neafutbol.utils.ConfigManager;
import com.jeronea.neafutbol.utils.selection.GoalSelectionManager;
import org.bukkit.plugin.java.JavaPlugin;

public class NeaFutbol extends JavaPlugin {

    private static NeaFutbol instance;
    private ConfigManager configManager;
    private GameManager gameManager;
    private GoalSelectionManager goalSelectionManager;

    @Override
    public void onEnable() {
        instance = this;

        // Cargar configuración por defecto
        saveDefaultConfig();

        // Inicializar gestor de configuración y lenguaje
        this.configManager = new ConfigManager(this);
        this.configManager.loadLang();

        // Inicializar gestor de selección (Wand)
        this.goalSelectionManager = new GoalSelectionManager(this);

        // Inicializar el gestor del juego
        this.gameManager = new GameManager(this);

        // Registrar comandos
        if (getCommand("neafutbol") != null) {
            getCommand("neafutbol").setExecutor(new MainCommand(this));
        }

        // Registrar eventos
        getServer()
            .getPluginManager()
            .registerEvents(new GameListener(this), this);

        getLogger().info(
            "NeaFutbol v" +
                getDescription().getVersion() +
                " ha sido habilitado. Autor: Nea#9654"
        );
    }

    @Override
    public void onDisable() {
        // Limpiar el juego si está activo al apagar
        if (gameManager != null) {
            gameManager.stopGameForced();
        }
        getLogger().info("NeaFutbol ha sido deshabilitado.");
    }

    public static NeaFutbol getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public GameManager getGameManager() {
        return gameManager;
    }

    public GoalSelectionManager getGoalSelectionManager() {
        return goalSelectionManager;
    }
}
