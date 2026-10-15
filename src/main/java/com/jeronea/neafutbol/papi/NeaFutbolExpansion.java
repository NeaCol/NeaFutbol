package com.jeronea.neafutbol.papi;

import com.jeronea.neafutbol.NeaFutbol;
import com.jeronea.neafutbol.game.GameManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NeaFutbolExpansion extends PlaceholderExpansion {

    private final NeaFutbol plugin;

    public NeaFutbolExpansion(NeaFutbol plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "neafutbol";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Nea#9654";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        GameManager gm = plugin.getGameManager();
        if (gm == null) return null;

        String lower = params.toLowerCase();

        switch (lower) {
            // Marcadores
            case "score_blue":
                return String.valueOf(gm.getScoreBlue());
            case "score_red":
                return String.valueOf(gm.getScoreRed());
            case "scores":
                return gm.getScoreBlue() + " - " + gm.getScoreRed();

            // Tiempo y Estado
            case "time":
                return gm.getFormattedTime();
            case "time_seconds":
                return String.valueOf(Math.max(0, gm.getTimer()));
            case "status":
                return gm.getStateFormatted();
            case "status_raw":
                return gm.getState().name();

            // Jugadores en la partida
            case "players":
            case "players_count":
                return String.valueOf(gm.getPlayerCount());
            case "max_players":
                return String.valueOf(gm.getMaxPlayers());
        }

        // Placeholders específicos de jugador
        if (offlinePlayer != null) {
            switch (lower) {
                // Estadísticas globales acumuladas
                case "goals":
                    return String.valueOf(plugin.getStatsManager().getGoals(offlinePlayer.getUniqueId()));
                case "wins":
                    return String.valueOf(plugin.getStatsManager().getWins(offlinePlayer.getUniqueId()));
                case "matches":
                    return String.valueOf(plugin.getStatsManager().getMatches(offlinePlayer.getUniqueId()));

                // Estado actual del jugador en la partida
                case "team":
                    if (offlinePlayer.isOnline()) {
                        Player onlinePlayer = offlinePlayer.getPlayer();
                        if (onlinePlayer != null && gm.isPlayerInGame(onlinePlayer)) {
                            return gm.getPlayerTeamName(onlinePlayer);
                        }
                    }
                    return "Ninguno";

                case "in_game":
                    if (offlinePlayer.isOnline()) {
                        Player onlinePlayer = offlinePlayer.getPlayer();
                        return (onlinePlayer != null && gm.isPlayerInGame(onlinePlayer)) ? "true" : "false";
                    }
                    return "false";
            }
        }

        return null;
    }
}
