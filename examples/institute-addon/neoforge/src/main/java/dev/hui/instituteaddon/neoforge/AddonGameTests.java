package dev.hui.instituteaddon.neoforge;

import com.mojang.authlib.GameProfile;
import dev.hui.instituteaddon.AddonGameTestChecks;
import dev.hui.instituteaddon.AddonRegistration;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AddonRegistration.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AddonGameTests {
    @GameTest(template = "empty_test", timeoutTicks = 40)
    public static void placeRegisteredRoom(GameTestHelper helper) { AddonGameTestChecks.placeRegisteredRoom(helper); }

    @GameTest(template = "empty_test", timeoutTicks = 40)
    public static void toggleDevice(GameTestHelper helper) {
        AddonGameTestChecks.toggleDevice(helper, FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "AddonTest")));
    }
}
