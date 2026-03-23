package com.jeronea.neafutbol.game;

import com.jeronea.neafutbol.NeaFutbol;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
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

    public BallManager(NeaFutbol plugin) {
        this.plugin = plugin;
        setupTeams();
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
        // Spawnear un poco más alto para evitar que se bugee en el suelo al aparecer
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
        physicsEntity.addScoreboardTag("NeaBall");

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
        visualEntity.addScoreboardTag("NeaBall");

        // Configurar Cabeza en la entidad Visual
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            String skinName = plugin
                .getConfig()
                .getString("ball.skin", "MHF_CoconutB");
            try {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(skinName));
            } catch (Exception e) {
                // Fallback
            }
            head.setItemMeta(meta);
        }
        visualEntity.getEquipment().setHelmet(head);

        isBallActive = true;
        currentRotationX = 0;

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

                // --- 1. Efecto Hielo (Deslizamiento) ---
                if (physicsEntity.isOnGround()) {
                    Vector vel = physicsEntity.getVelocity();
                    if (vel.getX() != 0 || vel.getZ() != 0) {
                        // Aumentamos a 1.45 para contrarrestar la fricción y simular hielo puro
                        vel.multiply(1.45);
                        physicsEntity.setVelocity(vel);
                    }
                }

                // --- 2. Sincronización de Posición y Ajuste de Altura ---
                Location baseLoc = physicsEntity.getLocation();

                // Offset base (-0.7 aprox para small AS) + config modifier
                double configMod = plugin
                    .getConfig()
                    .getDouble("ball.y-modifier", -0.5);

                // Si está en el suelo, subimos un pelín la visual
                if (physicsEntity.isOnGround()) {
                    configMod += 0.15;
                }

                Location visualLoc = baseLoc.clone().add(0, configMod, 0);

                // --- 3. Lógica de Rotación ---
                Vector velocity = physicsEntity.getVelocity();
                double speed = new Vector(
                    velocity.getX(),
                    0,
                    velocity.getZ()
                ).length();

                // Solo rotar si se mueve significativamente
                if (speed > 0.08) {
                    visualLoc.setDirection(velocity);
                    double angleFactor = 1.5;
                    currentRotationX += speed * angleFactor;
                    visualEntity.setHeadPose(
                        new EulerAngle(currentRotationX, 0, 0)
                    );
                } else {
                    currentRotationX = 0;
                    visualEntity.setHeadPose(new EulerAngle(0, 0, 0));
                }

                visualEntity.teleport(visualLoc);
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
            // Limpiar equipo
            if (teamBlue.hasEntry(visualEntity.getUniqueId().toString())) {
                teamBlue.removeEntry(visualEntity.getUniqueId().toString());
            }
            if (teamRed.hasEntry(visualEntity.getUniqueId().toString())) {
                teamRed.removeEntry(visualEntity.getUniqueId().toString());
            }
            visualEntity.remove();
        }
        physicsEntity = null;
        visualEntity = null;
        isBallActive = false;
        cleanOrphanedBallEntities();
    }

    private void cleanOrphanedBallEntities() {
        Bukkit.getWorlds().forEach(world -> {
            world
                .getEntitiesByClass(ArmorStand.class)
                .forEach(as -> {
                    if (as.getScoreboardTags().contains("NeaBall")) {
                        as.remove();
                    }
                });
        });
    }

    public boolean isBall(Entity entity) {
        if (entity == null) return false;
        if (!isBallActive) return false;

        // Verificar si es la entidad física o la visual
        return (
            entity.getUniqueId().equals(physicsEntity.getUniqueId()) ||
            (visualEntity != null &&
                entity.getUniqueId().equals(visualEntity.getUniqueId()))
        );
    }

    public Entity getBall() {
        return physicsEntity;
    }

    public boolean isBallActive() {
        return isBallActive && physicsEntity != null && !physicsEntity.isDead();
    }

    public void tryKick(Player player) {
        if (!isBallActive()) return;
        if (player.getWorld() != physicsEntity.getWorld()) return;

        double reach = plugin.getConfig().getDouble("ball.hitbox-range", 3.5);
        double distance = player
            .getLocation()
            .distance(physicsEntity.getLocation());

        if (distance > reach) return;

        Vector toBall = physicsEntity
            .getLocation()
            .toVector()
            .subtract(player.getEyeLocation().toVector());
        Vector direction = player.getEyeLocation().getDirection();

        double angle = toBall.normalize().angle(direction);

        if (angle < 0.7 || distance < 2.0) {
            boolean isSneaking = player.isSneaking();
            applyPhysics(player, isSneaking);
        }
    }

    public void applyPhysics(Player player, boolean isSneaking) {
        if (!isBallActive()) return;

        // Lógica de Glowing (Color de equipo)
        updateGlowing(player);

        Vector dir = player.getLocation().getDirection();

        // Pass power defaults upped to 1.2 as requested
        double kickPower = plugin.getConfig().getDouble("ball.kick-power", 1.2);
        double kickHeight = plugin
            .getConfig()
            .getDouble("ball.kick-height", 0.6);
        double passPower = plugin.getConfig().getDouble("ball.pass-power", 1.2);

        Vector velocity;

        // LOGICA INVERTIDA:
        // Agachado (Shift) -> Tiro (Shoot)
        // Normal -> Pase (Pass)

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
        player
            .getWorld()
            .playSound(
                physicsEntity.getLocation(),
                Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR,
                0.5f,
                2.0f
            );
        player
            .getWorld()
            .playSound(
                physicsEntity.getLocation(),
                Sound.ENTITY_ARMOR_STAND_HIT,
                0.8f,
                1.0f
            );
    }

    private void updateGlowing(Player player) {
        if (visualEntity == null || visualEntity.isDead()) return;

        // Determinar equipo del jugador a través del GameManager
        String team = plugin.getGameManager().getPlayerTeamName(player);

        if ("Azul".equalsIgnoreCase(team)) {
            if (!teamBlue.hasEntry(visualEntity.getUniqueId().toString())) {
                teamBlue.addEntry(visualEntity.getUniqueId().toString());
            }
            visualEntity.setGlowing(true);
        } else if ("Rojo".equalsIgnoreCase(team)) {
            if (!teamRed.hasEntry(visualEntity.getUniqueId().toString())) {
                teamRed.addEntry(visualEntity.getUniqueId().toString());
            }
            visualEntity.setGlowing(true);
        }
    }
}
