package club.rogueram.smartplay.session;

import org.bukkit.entity.Mob;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MobFreezeManagerTest {
    private static final class FakeMob {
        private final UUID id = UUID.randomUUID();
        private boolean ai;
        private boolean valid = true;
        private int writes;
        private final Mob mob;
        private FakeMob(boolean initialAI) {
            ai = initialAI;
            mob = (Mob) Proxy.newProxyInstance(Mob.class.getClassLoader(), new Class<?>[]{Mob.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "getUniqueId": return id;
                    case "hasAI": return ai;
                    case "setAI": ai = (boolean) args[0]; writes++; return null;
                    case "isValid": return valid;
                    case "toString": return "FakeMob";
                    default: throw new UnsupportedOperationException(method.getName());
                }
            });
        }
    }

    @Test void waitsForAllPlayersBeforeRestoringAI() {
        MobFreezeManager manager = new MobFreezeManager();
        FakeMob mob = new FakeMob(true);
        UUID alice = UUID.randomUUID(), bob = UUID.randomUUID();
        manager.acquire(alice, mob.mob);
        manager.acquire(bob, mob.mob);
        manager.release(alice);
        assertFalse(mob.ai);
        manager.release(bob);
        assertTrue(mob.ai);
    }

    @Test void preservesOriginallyDisabledAI() {
        MobFreezeManager manager = new MobFreezeManager();
        FakeMob mob = new FakeMob(false);
        UUID player = UUID.randomUUID();
        manager.acquire(player, mob.mob);
        manager.release(player);
        assertFalse(mob.ai);
    }

    @Test void repeatedAcquireAndReleaseAreIdempotent() {
        MobFreezeManager manager = new MobFreezeManager();
        FakeMob mob = new FakeMob(true);
        UUID player = UUID.randomUUID();
        manager.acquire(player, mob.mob);
        manager.acquire(player, mob.mob);
        manager.release(player);
        assertTrue(mob.ai);
        int writes = mob.writes;
        manager.release(player);
        assertEquals(writes, mob.writes);
    }

    @Test void shutdownRestoresAllMobsAndClearsOwners() {
        MobFreezeManager manager = new MobFreezeManager();
        FakeMob first = new FakeMob(true), second = new FakeMob(false);
        UUID player = UUID.randomUUID();
        manager.acquire(player, first.mob);
        manager.acquire(player, second.mob);
        manager.releaseAll();
        assertTrue(first.ai);
        assertFalse(second.ai);
        int writes = first.writes;
        manager.releaseAll();
        assertEquals(writes, first.writes);
    }

    @Test void doesNotTouchRemovedEntities() {
        MobFreezeManager manager = new MobFreezeManager();
        FakeMob mob = new FakeMob(true);
        UUID player = UUID.randomUUID();
        manager.acquire(player, mob.mob);
        mob.valid = false;
        manager.release(player);
        assertEquals(1, mob.writes);
    }
}
