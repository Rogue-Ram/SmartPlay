package club.rogueram.smartplay;

import club.rogueram.smartplay.commands.SmartPlayCommand;
import club.rogueram.smartplay.events.PlayerEvents;
import club.rogueram.smartplay.questions.Question;
import club.rogueram.smartplay.questions.QuestionBank;
import club.rogueram.smartplay.session.MobFreezeManager;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.logging.Level;

/** All session and entity state belongs to the main server thread. */
public final class SmartPlay extends JavaPlugin {
    private List<Question> questions = Collections.emptyList();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final MobFreezeManager frozenMobs = new MobFreezeManager();
    private final Map<UUID, Progress> progress = new HashMap<>();
    private final Random random = new Random();
    private BukkitTask automaticTask;

    private enum Phase { COUNTDOWN, ANSWERING, RECOVERY }
    private static final class Session {
        private Phase phase = Phase.COUNTDOWN;
        private BukkitTask task;
        private AnvilGUI gui;
        private Question question;
        private final UUID freezeOwner = UUID.randomUUID();
    }
    private static final class Progress {
        private final Set<String> mistakes = new HashSet<>();
        private String previous;
        private int correct;
        private int incorrect;
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        if (!getDataFolder().toPath().resolve("questions.json").toFile().exists()) saveResource("questions.json", false);
        reloadQuestions();
        SmartPlayCommand executor = new SmartPlayCommand(this);
        for (String name : Arrays.asList("smartplay", "askquestion", "smreload")) {
            Objects.requireNonNull(getCommand(name), name).setExecutor(executor);
            getCommand(name).setTabCompleter(executor);
        }
        getServer().getPluginManager().registerEvents(new PlayerEvents(this), this);
        if (getConfig().getBoolean("automatic.enabled", true)) startAutomatic();
        getLogger().info("SmartPlay active : " + questions.size() + " questions.");
    }

    @Override public void onDisable() {
        stopAutomatic();
        cancelAll();
        getServer().getScheduler().cancelTasks(this);
        frozenMobs.releaseAll();
        progress.clear();
    }

    public boolean reloadQuestions() {
        try (Reader reader = Files.newBufferedReader(getDataFolder().toPath().resolve("questions.json"), StandardCharsets.UTF_8)) {
            List<Question> loaded = QuestionBank.load(reader);
            questions = loaded;
            Set<String> texts = new HashSet<>();
            for (Question question : questions) texts.add(question.getText());
            for (Progress value : progress.values()) value.mistakes.retainAll(texts);
            return true;
        } catch (IOException | RuntimeException exception) {
            getLogger().warning("Chargement refuse, anciennes questions conservees : " + exception.getMessage());
            return false;
        }
    }

    public boolean reloadSettings() {
        // Validate YAML before committing a new bank or restarting the automatic cycle.
        try {
            YamlConfiguration candidate = new YamlConfiguration();
            candidate.load(getDataFolder().toPath().resolve("config.yml").toFile());
            for (String key : Arrays.asList("automatic.enabled", "ignore-accents", "kill-on-wrong-answer")) {
                if (candidate.contains(key) && !candidate.isBoolean(key))
                    throw new IllegalArgumentException(key + " doit etre true ou false.");
            }
            for (String key : Arrays.asList("automatic.interval-seconds", "countdown-seconds", "answer-timeout-seconds", "recovery-seconds", "freeze-radius")) {
                if (candidate.contains(key) && !candidate.isInt(key))
                    throw new IllegalArgumentException(key + " doit etre un entier.");
            }
        } catch (IOException | InvalidConfigurationException | IllegalArgumentException exception) {
            getLogger().warning("Configuration refusee, anciennes donnees conservees : " + exception.getMessage());
            return false;
        }
        if (!reloadQuestions()) return false;
        reloadConfig();
        stopAutomatic();
        if (getConfig().getBoolean("automatic.enabled", true)) startAutomatic();
        return true;
    }

    public void startAutomatic() {
        if (automaticTask != null) return;
        long interval = setting("automatic.interval-seconds", 300, 10, 86400) * 20L;
        automaticTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (eligible(player) && !sessions.containsKey(player.getUniqueId()) && !questions.isEmpty()) questionCountdown(player);
            }
        }, interval, interval);
    }

    public void stopAutomatic() {
        if (automaticTask != null) automaticTask.cancel();
        automaticTask = null;
    }

    public boolean isAutomaticRunning() { return automaticTask != null; }
    public int getQuestionCount() { return questions.size(); }
    public int getSessionCount() { return sessions.size(); }

    private boolean eligible(Player player) {
        return player.isOnline() && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR
                && player.hasPermission("smartplay.play") && !player.hasPermission("smartplay.bypass");
    }

    public boolean isProtected(Player player) {
        Session session = sessions.get(player.getUniqueId());
        return session != null && session.phase != Phase.COUNTDOWN;
    }

    public void questionCountdown(Player player) {
        if (!eligible(player)) { message(player, "ineligible"); return; }
        if (questions.isEmpty()) { message(player, "no-questions"); return; }
        if (sessions.containsKey(player.getUniqueId())) { message(player, "already-active"); return; }
        Session session = new Session();
        sessions.put(player.getUniqueId(), session);
        session.task = new BukkitRunnable() {
            private int remaining = setting("countdown-seconds", 10, 0, 300);
            @Override public void run() {
                if (!eligible(player)) { cancelSession(player); return; }
                if (remaining <= 0) {
                    cancel();
                    session.task = null;
                    openQuestion(player, session);
                    return;
                }
                actionBar(player, "countdown", "{seconds}", String.valueOf(remaining--));
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private Question selectQuestion(Player player) {
        Progress value = progress.computeIfAbsent(player.getUniqueId(), id -> new Progress());
        List<Question> pool = new ArrayList<>();
        for (Question question : questions) {
            if (questions.size() == 1 || !question.getText().equals(value.previous)) pool.add(question);
        }
        List<Question> review = new ArrayList<>();
        for (Question question : pool) if (value.mistakes.contains(question.getText())) review.add(question);
        if (!review.isEmpty() && random.nextBoolean()) pool = review;
        Question selected = pool.get(random.nextInt(pool.size()));
        value.previous = selected.getText();
        return selected;
    }

    private void openQuestion(Player player, Session session) {
        session.question = selectQuestion(player);
        session.phase = Phase.ANSWERING;
        try {
            freeze(player, session);
            message(player, "question", "{question}", session.question.getText());
            ItemStack item = new ItemStack(Material.PAPER);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(color(getConfig().getString("messages.answer-label", "Votre reponse")));
                meta.setLore(wrap(session.question.getText(), 45));
                item.setItemMeta(meta);
            }
            session.gui = new AnvilGUI.Builder().plugin(this).itemLeft(item).text(" ")
                    .title(color(getConfig().getString("messages.gui-title", "SmartPlay")))
                    .onClick((slot, state) -> {
                        if (slot != AnvilGUI.Slot.OUTPUT || session.phase != Phase.ANSWERING
                                || sessions.get(player.getUniqueId()) != session) return Collections.emptyList();
                        if (state.getText().trim().isEmpty()) {
                            message(player, "empty-answer");
                            return Collections.emptyList();
                        }
                        boolean correct = session.question.accepts(state.getText(), getConfig().getBoolean("ignore-accents", true));
                        finishAnswer(player, session, correct, false);
                        return Collections.singletonList(AnvilGUI.ResponseAction.close());
                    })
                    .onClose(state -> {
                        // Inventory mutations must not occur inside the inventory close event.
                        if (isEnabled() && sessions.get(player.getUniqueId()) == session && session.phase == Phase.ANSWERING) {
                            Bukkit.getScheduler().runTask(this, () -> {
                                if (sessions.get(player.getUniqueId()) == session && session.phase == Phase.ANSWERING)
                                    finishAnswer(player, session, false, false);
                            });
                        }
                    }).open(player);
            int timeout = setting("answer-timeout-seconds", 60, 0, 3600);
            if (timeout > 0) session.task = Bukkit.getScheduler().runTaskLater(this, () -> {
                if (sessions.get(player.getUniqueId()) == session && session.phase == Phase.ANSWERING) {
                    finishAnswer(player, session, false, true);
                    if (session.gui != null) session.gui.closeInventory();
                }
            }, timeout * 20L);
        } catch (RuntimeException exception) {
            cancelSession(player);
            message(player, "gui-error");
            getLogger().log(Level.SEVERE, "Impossible d'ouvrir le questionnaire pour " + player.getName(), exception);
        }
    }

    private void finishAnswer(Player player, Session session, boolean correct, boolean timeout) {
        session.phase = Phase.RECOVERY;
        if (session.task != null) session.task.cancel();
        session.task = null;
        Progress value = progress.computeIfAbsent(player.getUniqueId(), id -> new Progress());
        if (correct) {
            value.correct++;
            value.mistakes.remove(session.question.getText());
            message(player, "correct", "{answer}", session.question.getAnswer());
        } else {
            value.incorrect++;
            value.mistakes.add(session.question.getText());
            message(player, timeout ? "timeout" : "incorrect", "{answer}", session.question.getAnswer());
            if (getConfig().getBoolean("kill-on-wrong-answer", true)) {
                sessions.remove(player.getUniqueId());
                release(session);
                player.setHealth(0);
                return;
            }
        }
        session.task = new BukkitRunnable() {
            private int remaining = setting("recovery-seconds", 3, 0, 30);
            @Override public void run() {
                if (!player.isOnline() || player.isDead() || remaining <= 0) {
                    releaseSession(player);
                    return;
                }
                actionBar(player, "recovery", "{seconds}", String.valueOf(remaining--));
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void freeze(Player player, Session session) {
        int radius = setting("freeze-radius", 10, 0, 64);
        if (radius == 0) return;
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (!(entity instanceof Mob)) continue;
            frozenMobs.acquire(session.freezeOwner, (Mob) entity);
        }
    }

    private void release(Session session) {
        if (session.task != null) session.task.cancel();
        frozenMobs.release(session.freezeOwner);
    }

    private void releaseSession(Player player) {
        Session session = sessions.remove(player.getUniqueId());
        if (session != null) release(session);
    }

    public void cancelSession(Player player) {
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        release(session);
        if (session.gui != null) session.gui.closeInventory();
    }

    public void forgetPlayer(Player player) {
        cancelSession(player);
        progress.remove(player.getUniqueId());
    }

    public void cancelAll() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) cancelSession(player);
            else release(sessions.remove(id));
        }
    }

    public void showStats(Player player) {
        Progress value = progress.computeIfAbsent(player.getUniqueId(), id -> new Progress());
        message(player, "stats", "{correct}", String.valueOf(value.correct), "{incorrect}", String.valueOf(value.incorrect),
                "{review}", String.valueOf(value.mistakes.size()));
    }

    public String text(String key, String... replacements) {
        String value = getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < replacements.length; i += 2) value = value.replace(replacements[i], replacements[i + 1]);
        return color(value);
    }

    public void message(org.bukkit.command.CommandSender sender, String key, String... replacements) {
        sender.sendMessage(text("prefix") + text(key, replacements));
    }

    private void actionBar(Player player, String key, String... replacements) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, new TextComponent(text(key, replacements)));
    }

    private int setting(String key, int fallback, int min, int max) {
        return Math.max(min, Math.min(max, getConfig().getInt(key, fallback)));
    }

    private static String color(String value) { return ChatColor.translateAlternateColorCodes('&', value); }

    private static List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\\R", -1)) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split("\\s+")) {
                if (line.length() > 0 && line.length() + word.length() + 1 > width) {
                    lines.add(ChatColor.GREEN + line.toString());
                    line.setLength(0);
                }
                if (line.length() > 0) line.append(' ');
                line.append(word);
            }
            lines.add(ChatColor.GREEN + line.toString());
        }
        return lines;
    }
}
