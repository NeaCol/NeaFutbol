package com.jeronea.neafutbol.game;

import com.jeronea.neafutbol.NeaFutbol;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

public class BallManager {

    private final NeaFutbol plugin;
    private ArmorStand physicsEntity; // La entidad con gravedad y hitbox
    private ArmorStand visualEntity; // La entidad que gira y tiene la textura
    private boolean isBallActive = false;
    private BukkitRunnable physicsTask;
    private double currentRotationX = 0;

    private Team teamBlue;
    private Team teamRed;

    private final NamespacedKey ballKey;

    // Cachés y optimizaciones anti-lag
    private ItemStack cachedBallHead;
    private double cachedYModifier = -0.5;
    private Location lastVisualBaseLoc = null;
    private String currentGlowingTeam = null;

    // Cooldowns anti-spam para 4+ jugadores
    private final Map<UUID, Long> playerKickCooldowns = new HashMap<>();
    private long lastBallHitTime = 0;

    public BallManager(NeaFutbol plugin) {
        this.plugin = plugin;
        this.ballKey = new NamespacedKey(plugin, "ball_entity");
        setupTeams();
        reloadConfigValues();
    }

    public void reloadConfigValues() {
        this.cachedYModifier = plugin.getConfig().getDouble("ball.y-modifier", -0.5);
        prepareBallHead();
    }

    private void prepareBallHead() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            String skinName = plugin.getConfig().getString("ball.skin", "MHF_CoconutB");
            try {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(skinName));
            } catch (Exception ignored) {
            }
            head.setItemMeta(meta);
        }
        this.cachedBallHead = head;
    }

    private void setupTeams() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();

        // Team Blue
        teamBlue = board.getTeam("NeaBlue");
        if (teamBlue == null) teamBlue = board.registerNewTeam("NeaBlue");
        teamBlue.setColor(ChatColor.BLUE);

        // Team Red
        teamRed = board.getTeam("NeaRed");
        if (teamRed == null) teamRed = board.registerNewTeam("NeaRed");
        teamRed.setColor(ChatColor.RED);
    }

    public void spawnBall(Location location) {
        if (location == null) return;
        removeBall();

        // 1. Physics Entity (Base Invisible)
        Location spawnLoc = location.clone().add(0, 1.0, 0);

        physicsEntity = (ArmorStand) spawnLoc
            .getWorld()
            .spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
        physicsEntity.setVisible(false);
        physicsEntity.setGravity(true);
        physicsEntity.setSmall(true);
        physicsEntity.setBasePlate(false);
        physicsEntity.setArms(false);
        physicsEntity.setCustomName("NeaBallPhysics");
        physicsEntity.setCustomNameVisible(false);
        physicsEntity.setInvulnerable(true);
        physicsEntity.setPersistent(false);
        // Deshabilitar colisiones físicas directas para evitar lag O(N^2) con 4+ jugadores apiñados
        physicsEntity.setCollidable(false);
        physicsEntity.addScoreboardTag("NeaBall");
        physicsEntity.getPersistentDataContainer().set(ballKey, PersistentDataType.BYTE, (byte) 1);

        // 2. Visual Entity (Cabeza Rotatoria)
        visualEntity = (ArmorStand) spawnLoc
            .getWorld()
            .spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
        visualEntity.setVisible(false);
        visualEntity.setGravity(false);
        visualEntity.setSmall(true);
        visualEntity.setBasePlate(false);
        visualEntity.setArms(false);
        visualEntity.setMarker(true); // No hitbox, solo visual
        visualEntity.setCustomName("NeaBallVisual");
        visualEntity.setCustomNameVisible(false);
        visualEntity.setInvulnerable(true);
        visualEntity.setPersistent(false);
        visualEntity.addScoreboardTag("NeaBall");
        visualEntity.getPersistentDataContainer().set(ballKey, PersistentDataType.BYTE, (byte) 1);

        // Configurar Cabeza en la entidad Visual usando el caché para no congelar el hilo principal
        if (cachedBallHead == null) {
            prepareBallHead();
        }
        visualEntity.getEquipment().setHelmet(cachedBallHead.clone());

        isBallActive = true;
        currentRotationX = 0;
        lastVisualBaseLoc = null;
        currentGlowingTeam = null;

        // 3. Iniciar tarea de sincronización y rotación
        startPhysicsTask();
    }

    private void startPhysicsTask() {
        physicsTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!isBallActive()) {
                    this.cancel();
                    return;
                }

                if (physicsEntity.isDead() || visualEntity.isDead()) {
                    removeBall();
                    return;
                }

                // --- 1. Efecto Deslizamiento suave y amortiguación controlada ---
                if (physicsEntity.isOnGround()) {
                    Vector vel = physicsEntity.getVelocity();
                    double hSpeed = Math.sqrt(vel.getX() * vel.getX() + vel.getZ() * vel.getZ());
                    if (hSpeed > 0.04) {
                        // Multiplicador suave acotado para simular césped/fútbol rápido sin explosión de velocidad
                        double newSpeed = Math.min(hSpeed * 1.08, 1.8);
                        vel.setX((vel.getX() / hSpeed) * newSpeed);
                        vel.setZ((vel.getZ() / hSpeed) * newSpeed);
                        physicsEntity.setVelocity(vel);
                    } else if (hSpeed > 0.0001) {
                        // Frenar completamente para que la entidad duerma y no consuma ciclos de CPU
                        vel.setX(0);
                        vel.setZ(0);
                        physicsEntity.setVelocity(vel);
                    }
                }

                // --- 2. Sincronización de Posición (Solo cuando se mueve para ahorrar paquetes) ---
                Location baseLoc = physicsEntity.getLocation();
                if (lastVisualBaseLoc == null || lastVisualBaseLoc.distanceSquared(baseLoc) > 0.0004) {
                    double configMod = cachedYModifier;
                    if (physicsEntity.isOnGround()) {
                        configMod += 0.15;
                    }
                    Location visualLoc = baseLoc.clone().add(0, configMod, 0);

                    // --- 3. Lógica de Rotación ---
                    Vector velocity = physicsEntity.getVelocity();
                    double speed = Math.sqrt(velocity.getX() * velocity.getX() + velocity.getZ() * velocity.getZ());

                    if (speed > 0.08) {
                        visualLoc.setDirection(velocity);
                        currentRotationX += speed * 1.5;
                        visualEntity.setHeadPose(new EulerAngle(currentRotationX, 0, 0));
                    } else {
                        currentRotationX = 0;
                        visualEntity.setHeadPose(new EulerAngle(0, 0, 0));
                    }

                    visualEntity.teleport(visualLoc);
                    lastVisualBaseLoc = baseLoc;
                }
            }
        };
        physicsTask.runTaskTimer(plugin, 0L, 1L); // Ejecutar cada tick (20 TPS)
    }

    public void removeBall() {
        if (physicsTask != null) {
            physicsTask.cancel();
            physicsTask = null;
        }
        if (physicsEntity != null && !physicsEntity.isDead()) {
            physicsEntity.remove();
        }
        if (visualEntity != null && !visualEntity.isDead()) {
            String uuidStr = visualEntity.getUniqueId().toString();
            if (teamBlue.hasEntry(uuidStr)) {
                teamBlue.removeEntry(uuidStr);
            }
            if (teamRed.hasEntry(uuidStr)) {
                teamRed.removeEntry(uuidStr);
            }
            visualEntity.remove();
        }
        physicsEntity = null;
        visualEntity = null;
        isBallActive = false;
        currentGlowingTeam = null;
        lastVisualBaseLoc = null;
        playerKickCooldowns.clear();
        // NOTA DE RENDIMIENTO: NO llamar a cleanOrphanedBallEntities() aquí.
        // La entidad ya se remueve limpiamente por referencia directa. El escaneo global solo se hace en purgas.
    }

    public int cleanOrphanedBallEntities() {
        int count = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (ArmorStand as : world.getEntitiesByClass(ArmorStand.class)) {
                if (as.getScoreboardTags().contains("NeaBall") ||
                    as.getPersistentDataContainer().has(ballKey, PersistentDataType.BYTE) ||
                    "NeaBallPhysics".equals(as.getCustomName()) ||
                    "NeaBallVisual".equals(as.getCustomName())) {
                    as.remove();
                    count++;
                }
            }
        }
        return count;
    }

    public boolean isBall(Entity entity) {
        if (entity == null) return false;
        if (!isBallActive) return false;

        return (
            (physicsEntity != null && entity.getUniqueId().equals(physicsEntity.getUniqueId())) ||
            (visualEntity != null && entity.getUniqueId().equals(visualEntity.getUniqueId()))
        );
    }

    public Entity getBall() {
        return physicsEntity;
    }

    public boolean isBallActive() {
        return isBallActive && physicsEntity != null && !physicsEntity.isDead();
    }

    /**
     * Comprueba si el jugador o la pelota están en cooldown para evitar spam-click y micro-lags.
     */
    public boolean canKick(Player player) {
        if (!isBallActive()) return false;
        long now = System.currentTimeMillis();
        // Cooldown global de la pelota (100ms) para evitar colisiones simultáneas
        if (now - lastBallHitTime < 100) {
            return false;
        }
        // Cooldown por jugador (250ms) para evitar auto-clickers o spam
        Long playerLast = playerKickCooldowns.get(player.getUniqueId());
        if (playerLast != null && (now - playerLast < 250)) {
            return false;
        }
        return true;
    }

    public void tryKick(Player player) {
        if (!canKick(player)) return;
        if (player.getWorld() != physicsEntity.getWorld()) return;

        double reach = plugin.getConfig().getDouble("ball.hitbox-range", 3.5);
        double distance = player.getLocation().distance(physicsEntity.getLocation());

        if (distance > reach) return;

        Vector toBall = physicsEntity.getLocation().toVector().subtract(player.getEyeLocation().toVector());
        Vector direction = player.getEyeLocation().getDirection();

        double angle = toBall.normalize().angle(direction);

        if (angle < 0.7 || distance < 2.0) {
            boolean isSneaking = player.isSneaking();
            applyPhysics(player, isSneaking);
        }
    }

    public void applyPhysics(Player player, boolean isSneaking) {
        if (!canKick(player)) return;

        long now = System.currentTimeMillis();
        playerKickCooldowns.put(player.getUniqueId(), now);
        lastBallHitTime = now;

        // Lógica de Glowing (Color de equipo optimizada)
        updateGlowing(player);

        Vector dir = player.getLocation().getDirection();

        double kickPower = plugin.getConfig().getDouble("ball.kick-power", 1.2);
        double kickHeight = plugin.getConfig().getDouble("ball.kick-height", 0.6);
        double passPower = plugin.getConfig().getDouble("ball.pass-power", 1.2);

        Vector velocity;

        if (isSneaking) {
            // TIRO (Vuela)
            velocity = dir.normalize().multiply(kickPower);
            velocity.setY(kickHeight);
        } else {
            // PASE (Raso y fuerte)
            velocity = dir.normalize().multiply(passPower);
            velocity.setY(0.1);
        }

        physicsEntity.setVelocity(velocity);

        // Efectos de sonido
        player.getWorld().playSound(
            physicsEntity.getLocation(),
            Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR,
            0.5f,
            2.0f
        );
        player.getWorld().playSound(
            physicsEntity.getLocation(),
            Sound.ENTITY_ARMOR_STAND_HIT,
            0.8f,
            1.0f
        );
    }

    private void updateGlowing(Player player) {
        if (visualEntity == null || visualEntity.isDead()) return;

        String team = plugin.getGameManager().getPlayerTeamName(player);
        if (team == null || team.equalsIgnoreCase(currentGlowingTeam)) return;

        String uuidStr = visualEntity.getUniqueId().toString();

        if ("Azul".equalsIgnoreCase(team)) {
            if (teamRed.hasEntry(uuidStr)) {
                teamRed.removeEntry(uuidStr);
            }
            if (!teamBlue.hasEntry(uuidStr)) {
                teamBlue.addEntry(uuidStr);
            }
            visualEntity.setGlowing(true);
            currentGlowingTeam = "Azul";
        } else if ("Rojo".equalsIgnoreCase(team)) {
            if (teamBlue.hasEntry(uuidStr)) {
                teamBlue.removeEntry(uuidStr);
            }
            if (!teamRed.hasEntry(uuidStr)) {
                teamRed.addEntry(uuidStr);
            }
            visualEntity.setGlowing(true);
            currentGlowingTeam = "Rojo";
        }
    }
}
