package dev.frost.miniverse.minigame.impl.dropper;

import net.minecraft.nbt.NbtCompound;
import org.junit.Assert;
import org.junit.Test;

import java.util.Properties;

public class DropperSettingsAndLogicTest {

    @Test
    public void testDropperSettingsDefaults() {
        DropperSettings defaults = DropperSettings.defaults();
        Assert.assertEquals("", defaults.mapId());
        Assert.assertEquals(0, defaults.levelsToPlay());
        Assert.assertEquals("ORDER", defaults.selectionMode());
        Assert.assertTrue(defaults.allowSkip());
        Assert.assertEquals(20, defaults.skipFailsThreshold());
        Assert.assertEquals(60, defaults.finalCountdownSeconds());
        Assert.assertEquals(600, defaults.timeLimitSeconds());
    }

    @Test
    public void testDropperSettingsPropertiesSerialization() {
        DropperSettings original = new DropperSettings(
            "test_map", 5, "SHUFFLE", "lvl1,lvl2", 45, 300, true, 15
        );
        Properties props = new Properties();
        original.writeTo(props);

        DropperSettings restored = DropperSettings.fromProperties(props);
        Assert.assertEquals("test_map", restored.mapId());
        Assert.assertEquals(5, restored.levelsToPlay());
        Assert.assertEquals("SHUFFLE", restored.selectionMode());
        Assert.assertEquals("lvl1,lvl2", restored.selectedLevelIds());
        Assert.assertEquals(45, restored.finalCountdownSeconds());
        Assert.assertEquals(300, restored.timeLimitSeconds());
        Assert.assertTrue(restored.allowSkip());
        Assert.assertEquals(15, restored.skipFailsThreshold());
    }

    @Test
    public void testDropperSettingsNbtSerialization() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("mapId", "dropper_world");
        nbt.putInt("levelsToPlay", 10);
        nbt.putString("selectionMode", "RANDOM_N");
        nbt.putBoolean("allowSkip", true);
        nbt.putInt("skipFailsThreshold", 20);

        DropperSettings parsed = DropperSettings.fromNbt(nbt);
        Assert.assertEquals("dropper_world", parsed.mapId());
        Assert.assertEquals(10, parsed.levelsToPlay());
        Assert.assertEquals("RANDOM_N", parsed.selectionMode());
        Assert.assertTrue(parsed.allowSkip());
        Assert.assertEquals(20, parsed.skipFailsThreshold());
    }
}
