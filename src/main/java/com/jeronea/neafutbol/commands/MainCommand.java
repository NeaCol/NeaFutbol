package com.jeronea.neafutbol.commands;

import com.jeronea.neafutbol.NeaFutbol;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.BoundingBox;

public class MainCommand implements CommandExecutor, TabCompleter {

    private final NeaFutbol plugin;

    public MainCommand(NeaFutbol plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(
        CommandSender sender,
        Command command,
        String label,
        String[] args
    ) {
        if (args.length == 0) {
            return handleHelp(sender);
        }

        String sub = args[0].toLowerCase();

        // Soporte para formato compuesto /neafutbol holo <set|remove|purge> o /neafutbol hologram <set|remove|purge>
        if (sub.equals("holo") || sub.equals("hologram")) {
            if (args.length > 1) {
                String action = args[1].toLowerCase();
                if (action.equals("set") || action.equals("create")) {
                    return handleSetHolo(sender);
                } else if (action.equals("remove") || action.equals("delete") || action.equals("del")) {
                    return handleRemoveHolo(sender);
                } else if (action.equals("purge") || action.equals("clean") || action.equals("clear")) {
                    return handlePurgeHolo(sender);
                }
            }
            sender.sendMessage(ChatColor.YELLOW + "Uso: /neafutbol setholo | /neafutbol removeholo | /neafutbol purgeholo");
            return true;
        }

        switch (sub) {
            case "join":
                return handleJoin(sender, args);
            case "leave":
                return handleLeave(sender);
            case "setspawn":
                return handleSetSpawn(sender, args);
            case "setball":
                return handleSetBall(sender);
            case "setporteria":
            case "setgoal":
                return handleSetGoal(sender, args);
            case "setarena":
                return handleSetArena(sender);
            case "setholo":
            case "sethologram":
            case "hologramset":
            case "createholo":
                return handleSetHolo(sender);
            case "removeholo":
            case "hologramremove":
            case "removehologram":
            case "delholo":
            case "deleteholo":
            case "hologramdelete":
                return handleRemoveHolo(sender);
            case "purgeholo":
            case "cleanholo":
            case "clearholo":
                return handlePurgeHolo(sender);
            case "forcestart":
                return handleForceStart(sender);
            case "reload":
                return handleReload(sender);
            case "wand":
                return handleWand(sender);
            case "help":
                return handleHelp(sender);
            default:
                sender.sendMessage(
                    plugin.getConfigManager().getMessage("unknown-command")
                );
                return true;
        }
    }

    private boolean handleHelp(CommandSender sender) {
        String version = plugin.getDescription().getVersion();
        String author = String.join(", ", plugin.getDescription().getAuthors());

        // Header (No Prefix)
        sender.sendMessage(
            plugin
                .getConfigManager()
                .getMessageNoPrefix("help-header")
                .replace("%version%", version)
        );

        sendHelpLine(sender, "join <jugador>", "Unirse a la partida");
        sendHelpLine(sender, "leave", "Salir de la partida");

        if (sender.hasPermission("neafutbol.admin")) {
            sendHelpLine(sender, "wand", "Herramienta de selección");
            sendHelpLine(sender, "setarena", "Definir límites de cancha");
            sendHelpLine(
                sender,
                "setspawn <team1|team2|lobby>",
                "Puntos de aparición"
            );
            sendHelpLine(sender, "setball", "Punto del balón");
            sendHelpLine(
                sender,
                "setporteria <team1|team2>",
                "Definir portería"
            );
            sendHelpLine(sender, "setholo", "Poner holograma");
            sendHelpLine(sender, "removeholo", "Quitar holograma");
            sendHelpLine(sender, "purgeholo", "Purgar hologramas y balones huérfanos");
            sendHelpLine(sender, "forcestart", "Forzar inicio");
            sendHelpLine(sender, "reload", "Recargar config");
        }

        sender.sendMessage(
            plugin
                .getConfigManager()
                .getMessageNoPrefix("help-footer")
                .replace("%author%", author)
        );
        return true;
    }

    private void sendHelpLine(CommandSender sender, String cmd, String desc) {
        sender.sendMessage(
            plugin
                .getConfigManager()
                .getMessageNoPrefix("help-entry")
                .replace("%cmd%", cmd)
                .replace("%desc%", desc)
        );
    }

    private boolean handleWand(CommandSender sender) {
        if (!sender.hasPermission("neafutbol.admin")) {
            sender.sendMessage(
                plugin.getConfigManager().getMessage("no-permission")
            );
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(
                plugin.getConfigManager().getMessage("only-player")
            );
            return true;
        }

        Player p = (Player) sender;
        ItemStack wand = new ItemStack(Material.STICK);
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.AQUA + "NeaFutbol Wand");
            meta.setLore(
                Arrays.asList(
                    ChatColor.GRAY + "Clic Izquierdo: Posición 1",
                    ChatColor.GRAY + "Clic Derecho: Posición 2"
                )
            );
            wand.setItemMeta(meta);
        }
        p.getInventory().addItem(wand);
        p.sendMessage(plugin.getConfigManager().getMessage("wand-given"));
        return true;
    }

    private boolean handleJoin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("neafutbol.join")) {
            sender.sendMessage(
                plugin.getConfigManager().getMessage("no-permission")
            );
            return true;
        }

        Player target;
        if (args.length > 1) {
            // Join specific player
            if (
                !sender.hasPermission("neafutbol.admin") &&
                sender instanceof Player
            ) {
                Player pSender = (Player) sender;
                if (
                    !pSender.getName().equalsIgnoreCase(args[1]) &&
                    !pSender.hasPermission("neafutbol.admin")
                ) {
                    sender.sendMessage(
                        plugin.getConfigManager().getMessage("no-permission")
                    );
                    return true;
                }
            }
            target = Bukkit.getPlayer(args[1]);
        } else {
            // Join self
            if (!(sender instanceof Player)) {
                sender.sendMessage(
                    plugin.getConfigManager().getMessage("usage-join")
                );
                return true;
            }
            target = (Player) sender;
        }

        if (target == null) {
            sender.sendMessage(
                plugin.getConfigManager().getMessage("player-not-found")
            );
            return true;
        }

        plugin.getGameManager().joinPlayer(target);
        return true;
    }

    private boolean handleLeave(CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(
                plugin.getConfigManager().getMessage("only-player")
            );
            return true;
        }
        plugin.getGameManager().leavePlayer((Player) sender);
        return true;
    }

    private boolean handleSetSpawn(CommandSender sender, String[] args) {
        if (!sender.hasPermission("neafutbol.admin")) {
            sender.sendMessage(
                plugin.getConfigManager().getMessage("no-permission")
            );
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(
                plugin.getConfigManager().getMessage("only-player")
            );
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(
                ChatColor.RED + "Uso: /neafutbol setspawn <team1|team2|lobby>"
            );
            return true;
        }

        String type = args[1].toLowerCase();
        Player player = (Player) sender;
        Location loc = player.getLocation();

        switch (type) {
            case "team1":
                plugin.getConfig().set("locations.team1", loc);
                break;
            case "team2":
                plugin.getConfig().set("locations.team2", loc);
                break;
            case "lobby":
                plugin.getConfig().set("locations.lobby", loc);
                break;
            default:
                sender.sendMessage(
                    ChatColor.RED + "Tipos válidos: team1, team2, lobby"
                );
                return true;
        }

        plugin.saveConfig();
        String msg = plugin.getConfigManager().getMessage("setup-set-spawn");
        sender.sendMessage(msg.replace("%team%", type));
        return true;
    }

    private boolean handleSetBall(CommandSender sender) {
        if (!sender.hasPermission("neafutbol.admin")) {
            sender.sendMessage(
                plugin.getConfigManager().getMessage("no-permission")
            );
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(
                plugin.getConfigManager().getMessage("only-player")
            );
            return true;
        }

        Player player = (Player) sender;
        plugin.getConfig().set("locations.ball", player.getLocation());
        plugin.saveConfig();

        sender.sendMessage(
            plugin.getConfigManager().getMessage("setup-set-ball")
        );
        return true;
    }

    private boolean handleSetGoal(CommandSender sender, String[] args) {
        if (!sender.hasPermission("neafutbol.admin")) {
            sender.sendMessage(
                plugin.getConfigManager().getMessage("no-permission")
            );
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(
                plugin.getConfigManager().getMessage("only-player")
            );
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(
                ChatColor.RED + "Uso: /neafutbol setporteria <team1|team2>"
            );
            return true;
        }

        Player player = (Player) sender;

        // Verificar selección
        BoundingBox box = plugin.getGoalSelectionManager().getSelection(player);
        if (box == null) {
            sender.sendMessage(
                plugin.getConfigManager().getMessage("selection-incomplete")
            );
            return true;
        }

        String type = args[1].toLowerCase();
        String pathPrefix;

        switch (type) {
            case "team1":
            case "1":
                pathPrefix = "locations.goal1";
                type = "team1";
                break;
            case "team2":
            case "2":
                pathPrefix = "locations.goal2";
                type = "team2";
                break;
            default:
                sender.sendMessage(
                    ChatColor.RED + "Equipos válidos: team1, team2"
                );
                return true;
        }

        // Guardar bounding box
        plugin
            .getConfig()
            .set(pathPrefix + ".world", player.getWorld().getName());
        plugin.getConfig().set(pathPrefix + ".minX", box.getMinX());
        plugin.getConfig().set(pathPrefix + ".minY", box.getMinY());
        plugin.getConfig().set(pathPrefix + ".minZ", box.getMinZ());
        plugin.getConfig().set(pathPrefix + ".maxX", box.getMaxX());
        plugin.getConfig().set(pathPrefix + ".maxY", box.getMaxY());
        plugin.getConfig().set(pathPrefix + ".maxZ", box.getMaxZ());

        plugin.saveConfig();
        if (plugin.getGameManager() != null) {
            plugin.getGameManager().loadCachedBoxes();
        }

        String msg = plugin.getConfigManager().getMessage("setup-set-goal");
        sender.sendMessage(msg.replace("%team%", type));
        return true;
    }

    private boolean handleSetArena(CommandSender sender) {
        if (!sender.hasPermission("neafutbol.admin")) {
            sender.sendMessage(
                plugin.getConfigManager().getMessage("no-permission")
            );
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(
                plugin.getConfigManager().getMessage("only-player")
            );
            return true;
        }

        Player player = (Player) sender;
        BoundingBox box = plugin.getGoalSelectionManager().getSelection(player);
        if (box == null) {
            sender.sendMessage(
                plugin.getConfigManager().getMessage("selection-incomplete")
            );
            return true;
        }

        String pathPrefix = "locations.field";
        plugin
            .getConfig()
            .set(pathPrefix + ".world", player.getWorld().getName());
        plugin.getConfig().set(pathPrefix + ".minX", box.getMinX());
        plugin.getConfig().set(pathPrefix + ".minY", box.getMinY());
        plugin.getConfig().set(pathPrefix + ".minZ", box.getMinZ());
        plugin.getConfig().set(pathPrefix + ".maxX", box.getMaxX());
        plugin.getConfig().set(pathPrefix + ".maxY", box.getMaxY());
        plugin.getConfig().set(pathPrefix + ".maxZ", box.getMaxZ());

        plugin.saveConfig();
        if (plugin.getGameManager() != null) {
            plugin.getGameManager().loadCachedBoxes();
        }

        sender.sendMessage(
            plugin.getConfigManager().getMessage("setup-set-arena")
        );
        return true;
    }

    private boolean handleSetHolo(CommandSender sender) {
        if (!sender.hasPermission("neafutbol.admin")) {
            sender.sendMessage(
                plugin.getConfigManager().getMessage("no-permission")
            );
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage(
                plugin.getConfigManager().getMessage("only-player")
            );
            return true;
        }

        Player player = (Player) sender;
        plugin
            .getGameManager()
            .getHologramManager()
            .setLocation(player.getLocation());

        sender.sendMessage(
            plugin.getConfigManager().getMessage("setup-set-holo")
        );
        return true;
    }

    private boolean handleRemoveHolo(CommandSender sender) {
        if (!sender.hasPermission("neafutbol.admin")) {
            sender.sendMessage(
                plugin.getConfigManager().getMessage("no-permission")
            );
            return true;
        }

        plugin.getGameManager().getHologramManager().removeHologram();
        plugin.getConfig().set("locations.hologram", null);
        plugin.saveConfig();
        plugin.getGameManager().getHologramManager().setLocationNull();

        sender.sendMessage(
            plugin.getConfigManager().getMessage("setup-remove-holo")
        );
        return true;
    }

    private boolean handlePurgeHolo(CommandSender sender) {
        if (!sender.hasPermission("neafutbol.admin")) {
            sender.sendMessage(
                plugin.getConfigManager().getMessage("no-permission")
            );
            return true;
        }

        int removedHolo = plugin.getGameManager().getHologramManager().cleanOrphanedHolograms();
        int removedBall = plugin.getGameManager().getBallManager().cleanOrphanedBallEntities();
        sender.sendMessage(
            ChatColor.GREEN + "Purga completada: " +
            ChatColor.YELLOW + removedHolo + ChatColor.GREEN + " hologramas y " +
            ChatColor.YELLOW + removedBall + ChatColor.GREEN + " entidades de balón huérfanas eliminadas."
        );
        return true;
    }

    private boolean handleForceStart(CommandSender sender) {
        if (!sender.hasPermission("neafutbol.admin")) {
            sender.sendMessage(
                plugin.getConfigManager().getMessage("no-permission")
            );
            return true;
        }

        plugin.getGameManager().startGame();
        sender.sendMessage(
            plugin.getConfigManager().getMessage("force-started")
        );
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!sender.hasPermission("neafutbol.admin")) {
            sender.sendMessage(
                plugin.getConfigManager().getMessage("no-permission")
            );
            return true;
        }

        plugin.reloadConfig();
        plugin.getConfigManager().reload();
        if (plugin.getStatsManager() != null) {
            plugin.getStatsManager().load();
        }

        // Recargar GameManager y sus subsistemas para aplicar cambios de configuración/idioma/cajas inmediatamente
        if (plugin.getGameManager() != null) {
            plugin.getGameManager().reload();
        }

        sender.sendMessage(plugin.getConfigManager().getMessage("reload"));
        return true;
    }

    @Override
    public List<String> onTabComplete(
        CommandSender sender,
        Command command,
        String alias,
        String[] args
    ) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("join");
            completions.add("leave");
            completions.add("help");
            if (sender.hasPermission("neafutbol.admin")) {
                completions.add("wand");
                completions.add("setspawn");
                completions.add("setball");
                completions.add("setporteria");
                completions.add("setarena");
                completions.add("setholo");
                completions.add("removeholo");
                completions.add("purgeholo");
                completions.add("holo");
                completions.add("forcestart");
                completions.add("reload");
            }
            return filter(completions, args[0]);
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("join")) {
                return null; // Player list
            }
            if (
                (args[0].equalsIgnoreCase("holo") || args[0].equalsIgnoreCase("hologram")) &&
                sender.hasPermission("neafutbol.admin")
            ) {
                return filter(Arrays.asList("set", "remove", "purge"), args[1]);
            }
            if (
                args[0].equalsIgnoreCase("setspawn") &&
                sender.hasPermission("neafutbol.admin")
            ) {
                return filter(
                    Arrays.asList("team1", "team2", "lobby"),
                    args[1]
                );
            }
            if (
                (args[0].equalsIgnoreCase("setporteria") ||
                    args[0].equalsIgnoreCase("setgoal")) &&
                sender.hasPermission("neafutbol.admin")
            ) {
                return filter(Arrays.asList("team1", "team2"), args[1]);
            }
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String input) {
        if (input == null || input.isEmpty()) return list;
        List<String> result = new ArrayList<>();
        for (String s : list) {
            if (s.toLowerCase().startsWith(input.toLowerCase())) {
                result.add(s);
            }
        }
        return result;
    }
}
