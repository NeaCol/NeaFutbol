package com.jeronea.neafutbol.game;

import com.jeronea.neafutbol.NeaFutbol;
import com.jeronea.neafutbol.game.hologram.HologramManager;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;

public class GameManager {

    public enum GameState {
        WAITING,
        STARTING,
        PLAYING,
        GOAL_PAUSE,
        THROW_IN,
    }

    private final NeaFutbol plugin;
    private final BallManager ballManager;
    private final HologramManager hologramManager;
    private final Set<UUID> players;
    private GameState state;

    private BukkitTask gameLoopTask;
    private BukkitTask throwInTask;
    private int timer;
    private int totalGameTime;
    private int ticks;

    private int scoreBlue;
    private int scoreRed;

    // Team Tracking con Set para búsquedas O(1) en PlayerMoveEvent
    private final Set<UUID> teamBluePlayers = new LinkedHashSet<>();
    private final Set<UUID> teamRedPlayers = new LinkedHashSet<>();
    private UUID lastTouchPlayer;
    private UUID throwInPlayer;
    // Bounds check cooldown to prevent loops
    private long boundsCooldown = 0;

    // BoundingBoxes en caché para no hacer cientos de lecturas YAML por segundo
    private BoundingBox cachedGoal1;
    private BoundingBox cachedGoal2;
    private BoundingBox cachedField;

    public GameManager(NeaFutbol plugin) {
        this.plugin = plugin;
        this.ballManager = new BallManager(plugin);
        this.hologramManager = new HologramManager(plugin);
        this.players = new LinkedHashSet<>();
        this.state = GameState.WAITING;

        loadCachedBoxes();
        this.hologramManager.load();
    }

    public void reload() {
        loadCachedBoxes();
        if (ballManager != null) {
            ballManager.reloadConfigValues();
        }
        if (hologramManager != null) {
            hologramManager.reload();
        }
    }

    public void loadCachedBoxes() {
        this.cachedGoal1 = getBox("locations.goal1");
        this.cachedGoal2 = getBox("locations.goal2");
        this.cachedField = getBox("locations.field");
    }

    public BallManager getBallManager() {
        return ballManager;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public int getScoreBlue() {
        return scoreBlue;
    }

    public int getScoreRed() {
        return scoreRed;
    }

    public GameState getState() {
        return state;
    }

    public boolean isPlayerInGame(Player player) {
        return player != null && players.contains(player.getUniqueId());
    }

    public int getTimer() {
        return timer;
    }

    public int getTotalGameTime() {
        return totalGameTime;
    }

    public int getPlayerCount() {
        return players.size();
    }

    public int getMaxPlayers() {
        return plugin.getConfig().getInt("game.max-players", 4);
    }

    public String getFormattedTime() {
        if (state != GameState.PLAYING && state != GameState.GOAL_PAUSE && state != GameState.THROW_IN) {
            return "--:--";
        }
        int minutes = timer / 60;
        int seconds = timer % 60;
        return String.format("%02d:%02d", Math.max(0, minutes), Math.max(0, seconds));
    }

    public String getStateFormatted() {
        switch (state) {
            case WAITING:
                return "Esperando";
            case STARTING:
                return "Iniciando";
            case PLAYING:
                return "En juego";
            case GOAL_PAUSE:
                return "Gol";
            case THROW_IN:
                return "Saque de banda";
            default:
                return state.name();
        }
    }

    public List<UUID> getPlayers() {
        return Collections.unmodifiableList(new ArrayList<>(players));
    }

    public List<UUID> getTeamBluePlayers() {
        return Collections.unmodifiableList(new ArrayList<>(teamBluePlayers));
    }

    public List<UUID> getTeamRedPlayers() {
        return Collections.unmodifiableList(new ArrayList<>(teamRedPlayers));
    }

    // Helper for GameListener to block movement
    public boolean canMove(Player player) {
        if (state == GameState.GOAL_PAUSE) return false;
        if (state == GameState.THROW_IN) {
            return player.getUniqueId().equals(throwInPlayer);
        }
        return true;
    }

    public void setLastTouch(Player player) {
        this.lastTouchPlayer = player.getUniqueId();
        // If we are in THROW_IN and the thrower kicked it, resume game
        if (
            state == GameState.THROW_IN &&
            player.getUniqueId().equals(throwInPlayer)
        ) {
            resumeFromThrowIn();
        }
    }

    public String getPlayerTeamName(Player player) {
        if (player == null) return "None";
        UUID uuid = player.getUniqueId();
        if (teamBluePlayers.contains(uuid)) return "Azul";
        if (teamRedPlayers.contains(uuid)) return "Rojo";
        return "None";
    }

    public void joinPlayer(Player player) {
        if (players.contains(player.getUniqueId())) {
            player.sendMessage(
                plugin.getConfigManager().getMessage("already-in-game")
            );
            return;
        }

        int maxPlayers = plugin.getConfig().getInt("game.max-players", 4);
        if (players.size() >= maxPlayers) {
            player.sendMessage(
                plugin.getConfigManager().getMessage("game-full")
            );
            return;
        }

        if (state != GameState.WAITING && state != GameState.STARTING) {
            player.sendMessage(
                plugin.getConfigManager().getMessage("game-full")
            );
            return;
        }

        players.add(player.getUniqueId());

        player.getInventory().clear();
        player.setGameMode(GameMode.ADVENTURE);
        player.setHealth(20);
        player.setFoodLevel(20);

        Location lobby = plugin.getConfig().getLocation("locations.lobby");
        if (lobby != null) {
            player.teleport(lobby);
        }

        broadcast("player-joined", "%player%", player.getName());
        player.sendMessage(
            plugin
                .getConfigManager()
                .getMessage("joined-game")
                .replace("%current%", String.valueOf(players.size()))
                .replace("%max%", String.valueOf(maxPlayers))
        );

        checkStartCondition();
    }

    public void leavePlayer(Player player) {
        if (!players.contains(player.getUniqueId())) {
            player.sendMessage(
                plugin.getConfigManager().getMessage("not-in-game")
            );
            return;
        }

        players.remove(player.getUniqueId());
        teamBluePlayers.remove(player.getUniqueId());
        teamRedPlayers.remove(player.getUniqueId());

        player.getInventory().clear();
        player.setGameMode(GameMode.SURVIVAL);

        Location spawn = player.getWorld().getSpawnLocation();
        player.teleport(spawn);

        broadcast("player-left", "%player%", player.getName());
        player.sendMessage(plugin.getConfigManager().getMessage("left-game"));

        int minPlayers = plugin.getConfig().getInt("game.min-players", 2);

        if (state == GameState.STARTING && players.size() < minPlayers) {
            cancelStart();
        } else if (state != GameState.WAITING && players.size() < 2) {
            stopGame("Not enough players.");
        }
    }

    private void checkStartCondition() {
        if (state == GameState.WAITING) {
            int minPlayers = plugin.getConfig().getInt("game.min-players", 2);
            if (players.size() >= minPlayers) {
                startCountdown();
            }
        }
    }

    private void startCountdown() {
        state = GameState.STARTING;
        timer = plugin.getConfig().getInt("game.countdown-seconds", 60);
        broadcast("min-players-reached");

        gameLoopTask = new BukkitRunnable() {
            @Override
            public void run() {
                int minPlayers = plugin
                    .getConfig()
                    .getInt("game.min-players", 2);
                if (players.size() < minPlayers) {
                    cancelStart();
                    return;
                }

                if (timer <= 0) {
                    startGame();
                    return;
                }

                if (timer <= 5 || timer == 10 || timer == 30) {
                    broadcast("countdown", "%time%", String.valueOf(timer));
                    for (UUID uuid : players) {
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null) p.playSound(
                            p.getLocation(),
                            Sound.BLOCK_NOTE_BLOCK_PLING,
                            1,
                            2
                        );
                    }
                }

                timer--;
            }
        }
            .runTaskTimer(plugin, 0L, 20L);
    }

    private void cancelStart() {
        if (gameLoopTask != null) gameLoopTask.cancel();
        state = GameState.WAITING;
        broadcast("waiting-for-players");
    }

    public void startGame() {
        if (gameLoopTask != null) gameLoopTask.cancel();

        Location t1 = plugin.getConfig().getLocation("locations.team1");
        Location t2 = plugin.getConfig().getLocation("locations.team2");
        Location ballLoc = plugin.getConfig().getLocation("locations.ball");

        if (t1 == null || t2 == null || ballLoc == null) {
            broadcastRaw(
                "&cError: Locations not set! Admins must use /neafutbol setspawn/setball"
            );
            stopGame("Setup missing");
            return;
        }

        loadCachedBoxes();

        state = GameState.PLAYING;
        scoreBlue = 0;
        scoreRed = 0;
        hologramManager.spawnHologram(0, 0);
        broadcast("game-started");

        // Distribute teams
        teamBluePlayers.clear();
        teamRedPlayers.clear();
        int i = 0;
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null) continue;

            p.getInventory().clear();
            p.setHealth(20);
            p.setFoodLevel(20);

            if (i % 2 == 0) {
                teamBluePlayers.add(p.getUniqueId());
                p.teleport(t1);
                p.sendMessage(ChatColor.BLUE + "¡Eres Equipo Azul!");
                if (plugin.getConfig().getBoolean("game.team-armor", true)) {
                    applyTeamArmor(p, Color.BLUE);
                }
            } else {
                teamRedPlayers.add(p.getUniqueId());
                p.teleport(t2);
                p.sendMessage(ChatColor.RED + "¡Eres Equipo Rojo!");
                if (plugin.getConfig().getBoolean("game.team-armor", true)) {
                    applyTeamArmor(p, Color.RED);
                }
            }
            i++;
        }

        ballManager.spawnBall(ballLoc);
        totalGameTime = plugin.getConfig().getInt("game.duration-seconds", 300);
        timer = totalGameTime;
        ticks = 0;

        gameLoopTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (state == GameState.PLAYING) {
                    checkGoals();
                    checkBounds();
                }

                ticks++;
                if (ticks >= 20) {
                    ticks = 0;

                    if (timer <= 0) {
                        stopGame("Time Limit");
                        return;
                    }

                    hologramManager.updateTime(timer, totalGameTime);
                    timer--;
                }
            }
        }
            .runTaskTimer(plugin, 0L, 1L);
    }

    private void checkGoals() {
        if (!ballManager.isBallActive()) return;

        Location ballLoc = ballManager.getBall().getLocation();

        // Comprobación directa contra cachés en memoria
        if (cachedGoal1 != null && isInside(ballLoc, cachedGoal1)) {
            handleGoal("Rojo");
        } else if (cachedGoal2 != null && isInside(ballLoc, cachedGoal2)) {
            handleGoal("Azul");
        }
    }

    private void handleGoal(String scoringTeam) {
        state = GameState.GOAL_PAUSE;
        broadcast("goal-scored-team", "%team%", scoringTeam);
        broadcast("game-frozen");

        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.playSound(
                p.getLocation(),
                Sound.ENTITY_ENDER_DRAGON_GROWL,
                1.0f,
                1.0f
            );
        }

        if (scoringTeam.equalsIgnoreCase("Azul")) scoreBlue++;
        else scoreRed++;

        if (lastTouchPlayer != null) {
            Player scorer = Bukkit.getPlayer(lastTouchPlayer);
            String scorerTeam = scorer != null ? getPlayerTeamName(scorer) : null;
            if (scorerTeam != null && scorerTeam.equalsIgnoreCase(scoringTeam)) {
                plugin.getStatsManager().addGoal(lastTouchPlayer, scorer != null ? scorer.getName() : null);
                if (scorer != null) {
                    broadcast("goal-scored", "%player%", scorer.getName());
                }
            }
        }

        hologramManager.updateScore(scoreBlue, scoreRed);
        ballManager.removeBall();

        // 2 segundos delay y luego reanudar
        new BukkitRunnable() {
            @Override
            public void run() {
                resetPositions();
                Location ballLoc = plugin
                    .getConfig()
                    .getLocation("locations.ball");
                if (ballLoc != null) ballManager.spawnBall(ballLoc);
                state = GameState.PLAYING;
                broadcast("game-resumed");
            }
        }
            .runTaskLater(plugin, 40L); // 2 seconds
    }

    private void resetPositions() {
        Location t1 = plugin.getConfig().getLocation("locations.team1");
        Location t2 = plugin.getConfig().getLocation("locations.team2");

        for (UUID uuid : teamBluePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && t1 != null) {
                p.teleport(t1);
                p.setRotation(t1.getYaw(), t1.getPitch());
            }
        }
        for (UUID uuid : teamRedPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && t2 != null) {
                p.teleport(t2);
                p.setRotation(t2.getYaw(), t2.getPitch());
            }
        }
    }

    private void checkBounds() {
        if (!ballManager.isBallActive()) return;

        // Cooldown check
        if (System.currentTimeMillis() < boundsCooldown) return;

        if (cachedField == null) return;

        Location ballLoc = ballManager.getBall().getLocation();

        // Strict containment check for X and Z
        boolean inX =
            ballLoc.getX() >= cachedField.getMinX() &&
            ballLoc.getX() <= cachedField.getMaxX();
        boolean inZ =
            ballLoc.getZ() >= cachedField.getMinZ() &&
            ballLoc.getZ() <= cachedField.getMaxZ();

        // If outside
        if (!inX || !inZ) {
            handleThrowIn(ballLoc);
        }
    }

    private Location clampInsideField(Location loc, BoundingBox field) {
        Location clamped = loc.clone();
        double margin = 1.0;
        double minX = Math.min(field.getMinX(), field.getMaxX()) + margin;
        double maxX = Math.max(field.getMinX(), field.getMaxX()) - margin;
        double minZ = Math.min(field.getMinZ(), field.getMaxZ()) + margin;
        double maxZ = Math.max(field.getMinZ(), field.getMaxZ()) - margin;

        clamped.setX(Math.max(minX, Math.min(maxX, loc.getX())));
        clamped.setZ(Math.max(minZ, Math.min(maxZ, loc.getZ())));
        return clamped;
    }

    private void handleThrowIn(Location ballLoc) {
        state = GameState.THROW_IN;
        // Evita re-trigger inmediato con un cooldown preventivo de 4 segundos
        boundsCooldown = System.currentTimeMillis() + 4000;

        ballManager.removeBall(); // Stop ball

        // Clampear la pelota dentro de la cancha en la línea de banda para evitar bucles fuera de límites
        Location throwInBallLoc = (cachedField != null) ? clampInsideField(ballLoc, cachedField) : ballLoc;
        ballManager.spawnBall(throwInBallLoc);

        // Determine team to throw
        boolean blueLastTouch =
            lastTouchPlayer != null &&
            teamBluePlayers.contains(lastTouchPlayer);

        // If blue touched last, Red throws.
        Set<UUID> throwingTeam = blueLastTouch
            ? teamRedPlayers
            : teamBluePlayers;
        String throwingTeamName = blueLastTouch ? "ROJO" : "AZUL";

        broadcast("ball-out", "%team%", throwingTeamName);

        // Pick nearest player from throwing team
        Player nearest = null;
        double minDist = Double.MAX_VALUE;

        for (UUID uuid : throwingTeam) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                double d = p.getLocation().distance(throwInBallLoc);
                if (d < minDist) {
                    minDist = d;
                    nearest = p;
                }
            }
        }

        if (nearest == null) {
            // No players? Just resume
            resumeFromThrowIn();
            return;
        }

        throwInPlayer = nearest.getUniqueId();

        // Teleport thrower to ball
        Location throwLoc = throwInBallLoc.clone();
        throwLoc.setDirection(
            throwInBallLoc
                .getWorld()
                .getSpawnLocation()
                .toVector()
                .subtract(throwInBallLoc.toVector())
        );
        nearest.teleport(throwLoc);
        nearest.sendMessage(
            plugin
                .getConfigManager()
                .getMessage("throw-in-player")
                .replace("%time%", "10")
        );

        // Timer
        if (throwInTask != null) throwInTask.cancel();

        throwInTask = new BukkitRunnable() {
            int left = 10;

            @Override
            public void run() {
                if (state != GameState.THROW_IN) {
                    this.cancel();
                    return;
                }
                if (left <= 0) {
                    broadcast("throw-in-expired");
                    resumeFromThrowIn();
                    this.cancel();
                }
                left--;
            }
        }
            .runTaskTimer(plugin, 0L, 20L);
    }

    private void resumeFromThrowIn() {
        if (throwInTask != null) throwInTask.cancel();
        state = GameState.PLAYING;
        throwInPlayer = null;

        // Cooldown de 2.5 segundos para evitar re-detecciones accidentales
        boundsCooldown = System.currentTimeMillis() + 2500;

        broadcast("game-resumed");
    }

    public void stopGame(String reason) {
        if (gameLoopTask != null) gameLoopTask.cancel();
        if (throwInTask != null) throwInTask.cancel();
        ballManager.removeBall();
        state = GameState.WAITING;

        broadcast("game-ended");

        if (scoreBlue > scoreRed) {
            broadcast(
                "game-winner",
                "%team%",
                "AZUL",
                "%score%",
                String.valueOf(scoreBlue)
            );
        } else if (scoreRed > scoreBlue) {
            broadcast(
                "game-winner",
                "%team%",
                "ROJO",
                "%score%",
                String.valueOf(scoreRed)
            );
        } else {
            broadcast("game-draw", "%score%", String.valueOf(scoreBlue));
        }

        if (!"Forced".equalsIgnoreCase(reason) && !"Setup missing".equalsIgnoreCase(reason)) {
            if (scoreBlue > scoreRed) {
                for (UUID uuid : teamBluePlayers) {
                    Player p = Bukkit.getPlayer(uuid);
                    plugin.getStatsManager().addWin(uuid, p != null ? p.getName() : null);
                }
            } else if (scoreRed > scoreBlue) {
                for (UUID uuid : teamRedPlayers) {
                    Player p = Bukkit.getPlayer(uuid);
                    plugin.getStatsManager().addWin(uuid, p != null ? p.getName() : null);
                }
            }
            for (UUID uuid : players) {
                Player p = Bukkit.getPlayer(uuid);
                plugin.getStatsManager().addMatch(uuid, p != null ? p.getName() : null);
            }
        }

        if (!"Forced".equalsIgnoreCase(reason)) {
            hologramManager.spawnHologram(0, 0);
        }

        Location lobby = plugin.getConfig().getLocation("locations.lobby");
        if (lobby == null && !players.isEmpty()) {
            Player first = Bukkit.getPlayer(players.iterator().next());
            if (first != null) lobby = first.getWorld().getSpawnLocation();
        }

        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                p.getInventory().clear();
                p.setGameMode(GameMode.SURVIVAL);
                if (lobby != null) p.teleport(lobby);
            }
        }
        players.clear();
        teamBluePlayers.clear();
        teamRedPlayers.clear();
    }

    public void stopGameForced() {
        stopGame("Forced");
        hologramManager.removeHologram();
    }

    private void broadcast(String key, String... placeholders) {
        String msg = plugin.getConfigManager().getMessage(key);
        for (int i = 0; i < placeholders.length; i += 2) {
            msg = msg.replace(placeholders[i], placeholders[i + 1]);
        }
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(msg);
        }
    }

    private void broadcastRaw(String msg) {
        String colorMsg = ChatColor.translateAlternateColorCodes('&', msg);
        for (UUID uuid : players) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(colorMsg);
        }
    }

    private BoundingBox getBox(String path) {
        if (!plugin.getConfig().contains(path + ".world")) return null;
        double minX = plugin.getConfig().getDouble(path + ".minX");
        double minZ = plugin.getConfig().getDouble(path + ".minZ");
        double maxX = plugin.getConfig().getDouble(path + ".maxX");
        double maxZ = plugin.getConfig().getDouble(path + ".maxZ");
        return new BoundingBox(minX, 0, minZ, maxX, 0, maxZ);
    }

    private void applyTeamArmor(Player player, Color color) {
        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
        ItemStack legs = new ItemStack(Material.LEATHER_LEGGINGS);
        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);

        LeatherArmorMeta metaC = (LeatherArmorMeta) chest.getItemMeta();
        LeatherArmorMeta metaL = (LeatherArmorMeta) legs.getItemMeta();
        LeatherArmorMeta metaB = (LeatherArmorMeta) boots.getItemMeta();

        if (metaC != null) {
            metaC.setColor(color);
            chest.setItemMeta(metaC);
        }
        if (metaL != null) {
            metaL.setColor(color);
            legs.setItemMeta(metaL);
        }
        if (metaB != null) {
            metaB.setColor(color);
            boots.setItemMeta(metaB);
        }

        player.getInventory().setChestplate(chest);
        player.getInventory().setLeggings(legs);
        player.getInventory().setBoots(boots);
    }

    private boolean isInside(Location loc, BoundingBox box) {
        double tolerance = 0.5;
        double minX = Math.min(box.getMinX(), box.getMaxX());
        double maxX = Math.max(box.getMinX(), box.getMaxX());
        double minZ = Math.min(box.getMinZ(), box.getMaxZ());
        double maxZ = Math.max(box.getMinZ(), box.getMaxZ());

        boolean inX = loc.getX() >= (minX - tolerance) && loc.getX() <= (maxX + tolerance);
        boolean inZ = loc.getZ() >= (minZ - tolerance) && loc.getZ() <= (maxZ + tolerance);
        return inX && inZ;
    }
}
