package dev.hui.enderinfection.test;
import net.minecraft.gametest.framework.*;
import net.minecraftforge.gametest.*;
/** Opt-in isolation of intermittent combat checks, using the exact full-suite assertions. */
@GameTestHolder("ender_ai_checks")
@PrefixGameTestTemplate(false)
public final class FocusedAiGameTests {
 @GameTest(template="empty64",templateNamespace="ender_ai_checks",timeoutTicks=170,batch="isolated_wave")
 public static void bossWave(GameTestHelper h){InfectionGameTests.bossCrystalWaveHitsDistantEnemyOnceAndSparesFaction(h);}
 @GameTest(template="empty64",templateNamespace="ender_ai_checks",timeoutTicks=250,batch="isolated_archer")
 public static void archerFireLane(GameTestHelper h){InfectionGameTests.skeletonClearsAllyFireLaneAndBacksAwayFromCloseEnemies(h);}
}
