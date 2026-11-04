package dev.hui.enderinfection.combat;

/** One source of truth for server timing. Animation hit keys use hitTick / 20 seconds. */
public enum AttackSpec {
    SWEEP(1, "claw_sweep", 12, 26, 10F, 3.4, 0.85, 40),
    SLAM(2, "heavy_slam", 18, 40, 16F, 2.8, 1.25, 70),
    PULSE(3, "crystal_burst", 28, 50, 12F, 6.0, 1.45, 160);

    public final int id, hitTick, duration, cooldown;
    public final String animation;
    public final float damage;
    public final double radius, knockback;
    AttackSpec(int id, String animation, int hitTick, int duration, float damage, double radius, double knockback, int cooldown) {
        this.id=id; this.animation=animation; this.hitTick=hitTick; this.duration=duration;
        this.damage=damage; this.radius=radius; this.knockback=knockback; this.cooldown=cooldown;
    }
    public static AttackSpec byId(int id) {
        for (AttackSpec attack : values()) if (attack.id == id) return attack;
        return null;
    }
    public boolean resolvesAt(int tick) {
        int elapsed = tick - hitTick;
        return this == PULSE ? elapsed >= 0 && elapsed <= 10 && elapsed % 2 == 0 : elapsed == 0;
    }
    public double activeRadius(int tick) {
        return this == PULSE ? radius * Math.max(0, Math.min(1, (tick - hitTick + 1) / 11.0)) : radius;
    }
    public static boolean inSector(double dx, double dz, double forwardX, double forwardZ, double reach) {
        double length = Math.sqrt(dx*dx + dz*dz);
        if (length > reach) return false;
        return length < 0.001 || (dx*forwardX + dz*forwardZ)/length >= Math.cos(Math.toRadians(70));
    }
}
