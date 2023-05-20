package club.rogueram.smartplay.commands;

import club.rogueram.smartplay.SmartPlay;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class reload implements CommandExecutor {

    private final SmartPlay plugin;

    public reload(SmartPlay plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("smreload")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                if(player.hasPermission("smartplay.reload")) {
                    player.sendMessage(ChatColor.YELLOW+"Reloading SmartPlay questions...");
                    plugin.setupQuestions();
                    player.sendMessage(ChatColor.GREEN+"Questions reloaded!");
                } else {
                    player.sendMessage(ChatColor.RED+"You do not have permission to use this command.");
                }
                return true;
            } else {
                sender.sendMessage("This command can only be ran by a player.");
                return false;
            }
        }

        return false;
    }
}
