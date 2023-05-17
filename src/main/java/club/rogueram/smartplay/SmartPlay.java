package club.rogueram.smartplay;

import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public final class SmartPlay extends JavaPlugin implements Listener {
    private final Map<String, String> questionAnswerMap = new HashMap<>();
    private final List<String> wrongQuestions = new ArrayList<>();
    private final Set<String> activePlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Random random = new Random();

    @Override
    public void onEnable() {
        // Plugin startup logic
        setupQuestions(); // assuming a method to populate questionAnswerMap
        Bukkit.getServer().getPluginManager().registerEvents(this, this);

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    askQuestion(player);
                }
            }
        }.runTaskTimer(this, 0L, 20L * 60L ); // 20 ticks * 60 seconds * number of minutes random.nextInt((7 - 3) + 1) + 3
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        activePlayers.remove(event.getPlayer().getName());
    }

    private void setupQuestions() {
        questionAnswerMap.put("What is the capital of France?", "Paris");
        questionAnswerMap.put("What is 2+2?", "4");
        questionAnswerMap.put("Who wrote 'Romeo and Juliet'?", "Shakespeare");
        // ... add more questions and answers here
    }

    private void askQuestion(Player player) {
        if (activePlayers.contains(player.getName())) {
            return; // player is already answering a question, skip them
        }

        List<String> questions = new ArrayList<>(questionAnswerMap.keySet());
        questions.addAll(wrongQuestions);

        String question = questions.get(random.nextInt(questions.size()));
        String answer = questionAnswerMap.get(question);

        activePlayers.add(player.getName());

        new AnvilGUI.Builder()
                .onClose(stateSnapshot -> { // Handle when the inventory closes
                    stateSnapshot.getPlayer().sendMessage(ChatColor.RED + "You closed the inventory!");
                    activePlayers.remove(stateSnapshot.getPlayer().getName());
                })
                .onClickAsync((slot, stateSnapshot) -> CompletableFuture.supplyAsync(() -> { // Handle when the player submits the text they wrote
                    if (slot != AnvilGUI.Slot.OUTPUT) {
                        return Collections.emptyList();
                    }

                    if (stateSnapshot.getText().equalsIgnoreCase(answer)) {
                        stateSnapshot.getPlayer().sendMessage(ChatColor.GREEN + "Correct!");
                        wrongQuestions.remove(question);
                        activePlayers.remove(stateSnapshot.getPlayer().getName());
                        return Arrays.asList(AnvilGUI.ResponseAction.close());
                    } else {
                        stateSnapshot.getPlayer().sendMessage(ChatColor.RED + "Incorrect! The correct answer was: " + answer);
                        if (!wrongQuestions.contains(question)) {
                            wrongQuestions.add(question);
                        }
                        player.setHealth(0);
                        activePlayers.remove(stateSnapshot.getPlayer().getName());
                        return Arrays.asList(AnvilGUI.ResponseAction.replaceInputText(question));
                    }
                }))
                .preventClose() // prevents the inventory from being closed
                .text(question) // Sets the text that is to be displayed to the player
                .title("Answer the question!") // The title that is to be displayed on the inventory
                .plugin(this) // The plugin instance
                .open(player); // Opens the GUI for the player provided
    }
}