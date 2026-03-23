package com.jeronea.neafutbol.listeners;

import com.jeronea.neafutbol.NeaFutbol;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class GameListener implements Listener {

    private final NeaFutbol plugin;

    public GameListener(NeaFutbol plugin) {
        this.plugin = plugin;
    }

    /**
     * Maneja el golpe directo al balón (ArmorStand).
     */
    @EventHandler
    public void onEntityHit(EntityDamageByEntityEvent event) {
        if (
            plugin.getGameManager().getBallManager().isBall(event.getEntity())
        ) {
            event.setCancelled(true); // Prevenir daño al armor stand

            if (event.getDamager() instanceof Player) {
                Player player = (Player) event.getDamager();
                if (plugin.getGameManager().isPlayerInGame(player)) {
                    // Actualizar último toque
                    plugin.getGameManager().setLastTouch(player);

                    // Lógica invertida:
                    // Shift (Sneaking) -> Tiro Elevado
                    // Normal -> Pase Raso
                    boolean isSneaking = player.isSneaking();
                    plugin
                        .getGameManager()
                        .getBallManager()
                        .applyPhysics(player, isSneaking);
                }
            }
        }
    }

    /**
     * Maneja clics al aire para la "hitbox grande" y el uso del WAND.
     */
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Action action = event.getAction();

        // Lógica del WAND (Selección de área)
        ItemStack item = event.getItem();
        if (
            player.hasPermission("neafutbol.admin") &&
            item != null &&
            item.getType() == Material.STICK
        ) {
            if (
                item.hasItemMeta() &&
                item.getItemMeta().hasDisplayName() &&
                item.getItemMeta().getDisplayName().contains("NeaFutbol Wand")
            ) {
                if (action == Action.LEFT_CLICK_BLOCK) {
                    event.setCancelled(true); // Evitar romper bloque
                    plugin
                        .getGoalSelectionManager()
                        .setPos1(player, event.getClickedBlock().getLocation());
                    return;
                } else if (action == Action.RIGHT_CLICK_BLOCK) {
                    event.setCancelled(true); // Evitar interactuar
                    plugin
                        .getGoalSelectionManager()
                        .setPos2(player, event.getClickedBlock().getLocation());
                    return;
                }
            }
        }

        // Lógica de juego: Hitbox grande
        if (
            action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK
        ) {
            if (plugin.getGameManager().isPlayerInGame(player)) {
                // Actualizar último toque si está razonablemente cerca
                if (plugin.getGameManager().getBallManager().isBallActive()) {
                    double dist = player
                        .getLocation()
                        .distance(
                            plugin
                                .getGameManager()
                                .getBallManager()
                                .getBall()
                                .getLocation()
                        );
                    if (dist < 5.0) {
                        plugin.getGameManager().setLastTouch(player);
                    }
                }

                // Intenta patear el balón si está cerca (hitbox extendida)
                plugin.getGameManager().getBallManager().tryKick(player);
            }
        }
    }

    /**
     * Bloquear movimiento durante pausas o saques de banda.
     */
    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (plugin.getGameManager().isPlayerInGame(player)) {
            // Verificar si hubo movimiento real (ignorar rotación)
            Location from = event.getFrom();
            Location to = event.getTo();
            if (to == null) return;

            if (
                from.getX() == to.getX() &&
                from.getY() == to.getY() &&
                from.getZ() == to.getZ()
            ) {
                return;
            }

            if (!plugin.getGameManager().canMove(player)) {
                event.setTo(from); // Cancelar movimiento manteniendo rotación
            }
        }
    }

    /**
     * Si un jugador se desconecta, sacarlo del juego.
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (plugin.getGameManager().isPlayerInGame(event.getPlayer())) {
            plugin.getGameManager().leavePlayer(event.getPlayer());
        }
    }

    /**
     * Prevenir daño entre jugadores o por caida durante el juego.
     */
    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player p = (Player) event.getEntity();
            if (plugin.getGameManager().isPlayerInGame(p)) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Prevenir pérdida de hambre.
     */
    @EventHandler
    public void onFood(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player) {
            Player p = (Player) event.getEntity();
            if (plugin.getGameManager().isPlayerInGame(p)) {
                event.setCancelled(true);
                p.setFoodLevel(20);
            }
        }
    }

    /**
     * Prevenir romper bloques en la arena (salvo admins si quisieran, opcional).
     */
    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (plugin.getGameManager().isPlayerInGame(event.getPlayer())) {
            if (!event.getPlayer().hasPermission("neafutbol.admin")) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * Prevenir colocar bloques en la arena.
     */
    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.getGameManager().isPlayerInGame(event.getPlayer())) {
            if (!event.getPlayer().hasPermission("neafutbol.admin")) {
                event.setCancelled(true);
            }
        }
    }
}
