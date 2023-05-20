package club.rogueram.smartplay.commands;

import club.rogueram.smartplay.SmartPlay;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class AskQuestionCommand implements CommandExecutor {

    private final SmartPlay plugin;

    public AskQuestionCommand(SmartPlay plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("askquestion")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;
                plugin.questionCountdown(player);
                return true;
            } else {
                sender.sendMessage("This command can only be ran by a player.");
                return false;
            }
        }

        return false;
    }
}
