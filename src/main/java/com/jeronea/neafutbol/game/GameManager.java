package com.jeronea.neafutbol.game;

import com.jeronea.neafutbol.NeaFutbol;
import com.jeronea.neafutbol.game.hologram.HologramManager;
import java.util.ArrayList;
import java.util.List;
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
    private final List<UUID> players;
    private GameState state;

    private BukkitTask gameLoopTask;
    private BukkitTask throwInTask;
    private int timer;
    private int totalGameTime;
    private int ticks;

    private int scoreBlue;
    private int scoreRed;

    // Team Tracking
    private final List<UUID> teamBluePlayers = new ArrayList<>();
    private final List<UUID> teamRedPlayers = new ArrayList<>();
    private UUID lastTouchPlayer;
    private UUID throwInPlayer;
    // Bounds check cooldown to prevent loops
    private long boundsCooldown = 0;

    public GameManager(NeaFutbol plugin) {
        this.plugin = plugin;
        this.ballManager = new BallManager(plugin);
        this.hologramManager = new HologramManager(plugin);
        this.players = new ArrayList<>();
        this.state = GameState.WAITING;

        this.hologramManager.load();
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
        return players.contains(player.getUniqueId());
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
        if (teamBluePlayers.contains(player.getUniqueId())) return "Azul";
        if (teamRedPlayers.contains(player.getUniqueId())) return "Rojo";
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

        state = GameState.PLAYING;
        scoreBlue = 0;
        scoreRed = 0;
        hologramManager.spawnHologram(0, 0);
        broadcast("game-started");

        // Distribute teams
        teamBluePlayers.clear();
        teamRedPlayers.clear();
        for (int i = 0; i < players.size(); i++) {
            Player p = Bukkit.getPlayer(players.get(i));
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

        // Use precise entity location
        Location ballLoc = ballManager.getBall().getLocation();
        BoundingBox goal1 = getBox("locations.goal1");
        BoundingBox goal2 = getBox("locations.goal2");

        // Check intersection
        if (goal1 != null && isInside(ballLoc, goal1)) {
            handleGoal("Rojo");
        } else if (goal2 != null && isInside(ballLoc, goal2)) {
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

        hologramManager.updateScore(scoreBlue, scoreRed);
        ballManager.removeBall();

        // 1 second delay then reset positions
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

        BoundingBox field = getBox("locations.field");
        if (field == null) return;

        // Check if ball is OUTSIDE field (and above min Y)
        Location ballLoc = ballManager.getBall().getLocation();

        // Strict containment check for X and Z
        boolean inX =
            ballLoc.getX() >= field.getMinX() &&
            ballLoc.getX() <= field.getMaxX();
        boolean inZ =
            ballLoc.getZ() >= field.getMinZ() &&
            ballLoc.getZ() <= field.getMaxZ();

        // If outside
        if (!inX || !inZ) {
            handleThrowIn(ballLoc);
        }
    }

    private void handleThrowIn(Location ballLoc) {
        state = GameState.THROW_IN;
        ballManager.removeBall(); // Stop ball

        ballManager.spawnBall(ballLoc);

        // Determine team to throw
        boolean blueLastTouch =
            lastTouchPlayer != null &&
            teamBluePlayers.contains(lastTouchPlayer);

        // If blue touched last, Red throws.
        List<UUID> throwingTeam = blueLastTouch
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
                double d = p.getLocation().distance(ballLoc);
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
        Location throwLoc = ballLoc.clone();
        throwLoc.setDirection(
            ballLoc
                .getWorld()
                .getSpawnLocation()
                .toVector()
                .subtract(ballLoc.toVector())
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

        // Add 2 seconds cooldown before checking bounds again
        boundsCooldown = System.currentTimeMillis() + 2000;

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

        hologramManager.spawnHologram(0, 0);

        Location lobby = plugin.getConfig().getLocation("locations.lobby");
        if (lobby == null && !players.isEmpty()) {
            Player first = Bukkit.getPlayer(players.get(0));
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
        // We use infinite Y for goals logic, and full box for field
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
        // Tolerant X/Z check
        double tolerance = 0.5;
        boolean inX =
            loc.getX() >= (box.getMinX() - tolerance) &&
            loc.getX() <= (box.getMaxX() + tolerance);
        boolean inZ =
            loc.getZ() >= (box.getMinZ() - tolerance) &&
            loc.getZ() <= (box.getMaxZ() + tolerance);
        return inX && inZ;
    }
}
