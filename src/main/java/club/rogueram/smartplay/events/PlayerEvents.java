package club.rogueram.smartplay.events;

import club.rogueram.smartplay.SmartPlay;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitScheduler;

public class PlayerEvents implements Listener {

    private final SmartPlay plugin;

    public PlayerEvents(SmartPlay plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.activePlayers.remove(event.getPlayer().getName());
        plugin.unfreezeEntities(event.getPlayer());
        BukkitScheduler scheduler = Bukkit.getServer().getScheduler();
        scheduler.scheduleSyncDelayedTask(plugin, new Runnable() {
            @Override
            public void run() {
                if (Bukkit.getOnlinePlayers().size() == 0) {
                    plugin.task.cancel();
                    plugin.started = false;
                }
            }
        }, 20L);
    }

    @EventHandler
    public void OnPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if(plugin.questionsProvided == false) {
            player.sendMessage(ChatColor.RED + "Questions have not been provided yet! see https://rogueram.club/smartplay for more information");
        }else{
          if(plugin.started == false){
              plugin.started = true;
              plugin.start();
          }
        }
    }
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if(plugin.activePlayers.contains(player.getName())) {
            Location from = event.getFrom();
            Location to = event.getTo();
            if(from.getX() != to.getX() || from.getZ() != to.getZ()) {
                player.teleport(from);
        }
        }
    }
    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player) {
            Player player = (Player) event.getEntity();
            if (plugin.activePlayers.contains(player.getName())) {
                event.setCancelled(true);
            }
        }
    }
}
