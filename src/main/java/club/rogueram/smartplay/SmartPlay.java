package club.rogueram.smartplay;

import club.rogueram.smartplay.commands.AskQuestionCommand;
import club.rogueram.smartplay.events.PlayerEvents;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.spigotmc.*;
import java.io.*;
import java.util.concurrent.TimeUnit;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class SmartPlay extends JavaPlugin implements Listener {
    private final Map<String, String> questionAnswerMap = new HashMap<>();
    private final List<String> wrongQuestions = new ArrayList<>();
    public final Set<String> activePlayers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Random random = new Random();
    public Boolean questionsProvided = false;
    @Override
    public void onEnable() {
        setupQuestions();
        getLogger().info("SmartPlay has been enabled!");
        Bukkit.getServer().getPluginManager().registerEvents(this, this);
        this.getCommand("askquestion").setExecutor(new AskQuestionCommand(this));
        Bukkit.getServer().getPluginManager().registerEvents(new PlayerEvents(this), this);
        try {
            TimeUnit.SECONDS.sleep(3);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        getLogger().info("Starting questions...");
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    questionCountdown(player);
                }
            }
        }.runTaskTimer(this, 0L, 20L * 60L * random.nextInt((5 - 3) + 1)+3);
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        getLogger().info("SmartPlay has been disabled!");
    }


    public void setupQuestions() {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdir();
        }


        File jsonFile = new File(dataFolder, "questions.json");
        if (jsonFile.exists()) {
            try {
                Gson gson = new Gson();
                Type collectionType = new TypeToken<Collection<Map<String, String>>>(){}.getType();
                Collection<Map<String, String>> json = gson.fromJson(new FileReader(jsonFile), collectionType);
                for (Map<String, String> map : json) {
                    questionAnswerMap.put(map.get("question"), map.get("answer"));
                }
                questionsProvided = true;
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            questionsProvided = false;
            setupDefaultQuestions(jsonFile);
        }
    }

    private void setupDefaultQuestions(File jsonFile) {
        try {
            List<Map<String, String>> defaultQuestions = Arrays.asList(
                    new HashMap<String, String>() {{ put("question", "Questions not yet setup!"); put("answer", "ok"); }}
            );
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(defaultQuestions);
            FileWriter writer = new FileWriter(jsonFile);
            writer.write(json);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private final Map<String, Set<LivingEntity>> playerFrozenEntities = new ConcurrentHashMap<>();

    private void freezeEntities(Player player) {
        Set<LivingEntity> frozenEntities = ConcurrentHashMap.newKeySet();
        for (Entity entity : player.getNearbyEntities(10, 10, 10)) {
            if (entity instanceof Mob) {
                Mob mob = (Mob) entity;
                mob.setAI(false);
                frozenEntities.add(mob);
            }
        }
        playerFrozenEntities.put(player.getName(), frozenEntities);
    }

    public void unfreezeEntities(Player player) {
        Set<LivingEntity> frozenEntities = playerFrozenEntities.get(player.getName());
        if (frozenEntities != null) {
            for (LivingEntity entity : frozenEntities) {
                entity.setAI(true);
            }
            frozenEntities.clear();
        }
        playerFrozenEntities.remove(player.getName());
    }

    public void questionCountdown(Player player){
        if (questionsProvided == false) {
            player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.RED + "Could not load questions!"));
            player.sendMessage(ChatColor.RED + "Could not load questions! See https://rogueram.com/smartplay for more info.");
            return;
        }
        new BukkitRunnable() {
            int counter = 10;
            @Override
            public void run() {
                if (counter > 0) {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(ChatColor.GREEN + "New question in: " + counter));
                    counter--;
                } else {
                    askQuestion(player);
                    this.cancel();
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    public void askQuestion(Player player) {
        if (activePlayers.contains(player.getName()) || questionsProvided == false) {
            return;
        }

        List<String> questions = new ArrayList<>(questionAnswerMap.keySet());
        questions.addAll(wrongQuestions);

        String question = questions.get(random.nextInt(questions.size()));
        String answer = questionAnswerMap.get(question);

        activePlayers.add(player.getName());
        List<String> loreLines = new ArrayList<>(Arrays.asList(question.split("\n")));
        freezeEntities(player);
        ItemStack questionItem = new ItemStack(Material.DIAMOND_BLOCK);
        ItemMeta meta = questionItem.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("Answer here");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GREEN + question);
            meta.setLore(loreLines);
            questionItem.setItemMeta(meta);
        }

        new AnvilGUI.Builder()
                .onClose(playerExecutor -> {
                    // Player attempted to close the inventory...
                })
                .onClickAsync((slot, playerExecutor) -> CompletableFuture.supplyAsync(() -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) {
                        return Collections.emptyList();
                    }
                    Set<String> correctWords = new HashSet<>(Arrays.asList(answer.toLowerCase().split("\\s+")));
                    boolean isCorrect = Arrays.stream(playerExecutor.getText().toLowerCase().split("\\s+"))
                            .anyMatch(word -> correctWords.contains(word));

                    if (isCorrect) {
                        playerExecutor.getPlayer().sendMessage(ChatColor.GREEN + "Correct!\n" + question + answer);
                        wrongQuestions.remove(question);
                        new BukkitRunnable() {
                            int counter = 3;
                            @Override
                            public void run() {
                                if (counter > 0) {
                                    playerExecutor.getPlayer().sendTitle(ChatColor.GREEN + "Resuming in " + counter, "", 10, 20, 10);
                                    counter--;
                                } else {
                                    activePlayers.remove(playerExecutor.getPlayer().getName());
                                    unfreezeEntities(playerExecutor.getPlayer());
                                    this.cancel();
                                }
                            }
                        }.runTaskTimer(this, 0L, 20L);
                        return Arrays.asList(AnvilGUI.ResponseAction.close());
                    } else {
                        Bukkit.getScheduler().runTask(this, () -> {
                            unfreezeEntities(playerExecutor.getPlayer());
                            playerExecutor.getPlayer().sendMessage(ChatColor.RED + "Incorrect! The correct answer was: " + answer);
                            if (!wrongQuestions.contains(question)) {
                                wrongQuestions.add(question);
                            }
                            playerExecutor.getPlayer().setHealth(0);
                            activePlayers.remove(playerExecutor.getPlayer().getName());
                        });
                        return Arrays.asList(AnvilGUI.ResponseAction.close());
                    }
                }))
                .preventClose()
                .itemLeft(questionItem)
                .title("Answer the question!")
                .plugin(this)
                .open(player);
    }
}