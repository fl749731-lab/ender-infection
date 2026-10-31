import dev.hui.enderinfection.combat.AttackSpec;

/** Run with assertions enabled; independent of Minecraft so geometry/timing can be checked offline. */
public final class CombatChecks {
    public static void main(String[] args) {
        check(AttackSpec.inSector(0, 3, 0, 1, 3.4), "front target must be hit");
        check(!AttackSpec.inSector(0, -2, 0, 1, 3.4), "target behind must be excluded");
        check(!AttackSpec.inSector(0, 3.5, 0, 1, 3.4), "outside reach must be excluded");
        check(!AttackSpec.inSector(3, 0, 0, 1, 3.4), "side target outside 140-degree arc");
        check(AttackSpec.inSector(1, 2, 0, 1, 3.4), "diagonal front target");
        check(AttackSpec.inSector(0, 0, 0, 1, 3.4), "coincident position cannot divide by zero");
        for (AttackSpec spec : AttackSpec.values()) {
            check(AttackSpec.byId(spec.id) == spec, "synced ID mapping");
            int hits = 0;
            for (int tick = 1; tick <= spec.duration; tick++) if (tick == spec.hitTick) hits++;
            check(hits == 1, "exactly one damage event per attack");
            check(spec.hitTick > 0 && spec.hitTick < spec.duration, "wind-up and recovery must exist");
        }
        check(AttackSpec.byId(0) == null, "idle has no damage");
        check(!AttackSpec.SLAM.resolvesAt(17) && AttackSpec.SLAM.resolvesAt(18) && !AttackSpec.SLAM.resolvesAt(19), "slam hits exactly on frame 18");
        check(!AttackSpec.PULSE.resolvesAt(27) && AttackSpec.PULSE.resolvesAt(28), "pulse starts on frame 28");
        check(!AttackSpec.PULSE.resolvesAt(29) && AttackSpec.PULSE.resolvesAt(30), "pulse damage follows visible ring steps");
        check(AttackSpec.PULSE.resolvesAt(38) && !AttackSpec.PULSE.resolvesAt(40), "pulse ends after full radius");
        check(Math.abs(AttackSpec.PULSE.activeRadius(28) - 6.0/11) < 1e-6, "first wave is small");
        check(Math.abs(AttackSpec.PULSE.activeRadius(38) - 6.0) < 1e-6, "last wave reaches six blocks");
        System.out.println("PASS: sector boundaries, zero-distance case, ID mapping, hit timings and expanding pulse window.");
    }
    private static void check(boolean valid, String message) {
        if (!valid) throw new AssertionError(message);
    }
}
