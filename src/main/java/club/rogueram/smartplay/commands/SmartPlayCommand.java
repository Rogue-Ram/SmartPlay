package club.rogueram.smartplay.commands;

import club.rogueram.smartplay.SmartPlay;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import java.util.*;

public final class SmartPlayCommand implements TabExecutor {
    private final SmartPlay plugin;
    public SmartPlayCommand(SmartPlay plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String action = command.getName().equalsIgnoreCase("smreload") ? "reload"
                : command.getName().equalsIgnoreCase("askquestion") ? "ask"
                : args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        switch (action) {
            case "ask":
            case "stats":
                if (!sender.hasPermission("smartplay.play")) { plugin.message(sender, "no-permission"); return true; }
                if (!(sender instanceof Player)) { plugin.message(sender, "player-only"); return true; }
                if (action.equals("ask")) plugin.questionCountdown((Player) sender);
                else plugin.showStats((Player) sender);
                return true;
            case "reload":
            case "start":
            case "stop":
            case "status":
                String permission = action.equals("reload") ? "smartplay.reload" : "smartplay.admin";
                if (!sender.hasPermission(permission)) { plugin.message(sender, "no-permission"); return true; }
                if (action.equals("reload")) plugin.message(sender, plugin.reloadSettings() ? "reloaded" : "reload-failed");
                if (action.equals("start")) { plugin.startAutomatic(); plugin.message(sender, "started"); }
                if (action.equals("stop")) { plugin.stopAutomatic(); plugin.cancelAll(); plugin.message(sender, "stopped"); }
                if (action.equals("status")) plugin.message(sender, "status", "{questions}", String.valueOf(plugin.getQuestionCount()),
                        "{sessions}", String.valueOf(plugin.getSessionCount()), "{automatic}", plugin.isAutomaticRunning() ? "ON" : "OFF");
                return true;
            default:
                plugin.message(sender, "help");
                if (sender.hasPermission("smartplay.admin") || sender.hasPermission("smartplay.reload")) plugin.message(sender, "help-admin");
                return true;
        }
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("smartplay") || args.length != 1) return Collections.emptyList();
        List<String> options = new ArrayList<>(Collections.singletonList("help"));
        if (sender.hasPermission("smartplay.play")) options.addAll(Arrays.asList("ask", "stats"));
        if (sender.hasPermission("smartplay.reload")) options.add("reload");
        if (sender.hasPermission("smartplay.admin")) options.addAll(Arrays.asList("start", "stop", "status"));
        options.removeIf(option -> !option.startsWith(args[0].toLowerCase(Locale.ROOT)));
        return options;
    }
}
