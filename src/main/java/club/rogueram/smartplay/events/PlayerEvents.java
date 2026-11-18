package club.rogueram.smartplay.events;

import club.rogueram.smartplay.SmartPlay;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;

public final class PlayerEvents implements Listener {
    private final SmartPlay plugin;
    public PlayerEvents(SmartPlay plugin) { this.plugin = plugin; }

    @EventHandler public void onQuit(PlayerQuitEvent event) { plugin.forgetPlayer(event.getPlayer()); }
    @EventHandler public void onDeath(PlayerDeathEvent event) { plugin.cancelSession(event.getEntity()); }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) { plugin.cancelSession(event.getPlayer()); }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onGameMode(PlayerGameModeChangeEvent event) { plugin.cancelSession(event.getPlayer()); }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) { plugin.cancelSession(event.getPlayer()); }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event instanceof PlayerTeleportEvent || !plugin.isProtected(event.getPlayer())) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to != null && (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ())) {
            Location fixed = from.clone();
            fixed.setYaw(to.getYaw());
            fixed.setPitch(to.getPitch());
            event.setTo(fixed);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && plugin.isProtected((Player) event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && plugin.isProtected((Player) event.getDamager())) event.setCancelled(true);
        if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();
            if (projectile.getShooter() instanceof Player && plugin.isProtected((Player) projectile.getShooter())) event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (plugin.isProtected(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.isProtected(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (plugin.isProtected(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.isProtected(event.getPlayer())) event.setCancelled(true);
    }
}
