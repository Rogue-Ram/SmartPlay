package club.rogueram.smartplay.session;

import org.bukkit.entity.Mob;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Reference-counted freezes preserve pre-existing NoAI and overlapping quizzes. */
public final class MobFreezeManager {
    private final Map<UUID, FrozenMob> mobs = new HashMap<>();

    private static final class FrozenMob {
        private final Mob mob;
        private final boolean originalAI;
        private final Set<UUID> owners = new HashSet<>();
        private FrozenMob(Mob mob) { this.mob = mob; this.originalAI = mob.hasAI(); }
    }

    public void acquire(UUID owner, Mob mob) {
        FrozenMob frozen = mobs.computeIfAbsent(mob.getUniqueId(), id -> new FrozenMob(mob));
        frozen.owners.add(owner);
        mob.setAI(false);
    }

    public void release(UUID owner) {
        mobs.entrySet().removeIf(entry -> {
            FrozenMob frozen = entry.getValue();
            frozen.owners.remove(owner);
            if (!frozen.owners.isEmpty()) return false;
            restore(frozen);
            return true;
        });
    }

    public void releaseAll() {
        for (FrozenMob frozen : mobs.values()) restore(frozen);
        mobs.clear();
    }

    private void restore(FrozenMob frozen) {
        if (frozen.mob.isValid()) frozen.mob.setAI(frozen.originalAI);
    }
}
