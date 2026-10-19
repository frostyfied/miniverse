package dev.frost.miniverse.minigame.impl.microparty.rule.impl;

import dev.frost.miniverse.minigame.impl.microparty.MicroPartyArenaHelper;
import dev.frost.miniverse.minigame.impl.microparty.MicroPartyMinigame;
import dev.frost.miniverse.minigame.impl.microparty.rule.MicroRule;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.*;

public class MortarStrikeRule implements MicroRule {
    public record MortarReticle(
        double x,
        double y,
        double z,
        double radius,
        int spawnRemainingTicks,
        int detonateTick
    ) {}

    private static final DustParticleEffect ORANGE_DUST = new DustParticleEffect(new Vector3f(1.0f, 0.72f, 0.1f), 1.1f);
    private static final DustParticleEffect CRIMSON_DUST = new DustParticleEffect(new Vector3f(1.0f, 0.08f, 0.08f), 1.35f);

    private final Set<UUID> hitPlayers = new HashSet<>();
    private final List<MortarReticle> activeReticles = new ArrayList<>();
    private final Random random = new Random();
    private boolean wave2Spawned = false;
    private boolean wave3Spawned = false;

    @Override
    public String id() {
        return "mortar_strike";
    }

    @Override
    public String name() {
        return "Mortar Strike";
    }

    @Override
    public String description() {
        return "Dodge the artillery blast reticles before shells strike the arena!";
    }

    @Override
    public Text title() {
        return Text.literal("MORTAR STRIKE!").formatted(Formatting.RED, Formatting.BOLD);
    }

    @Override
    public Text instruction() {
        return Text.literal("Dodge the artillery blast zones!").formatted(Formatting.YELLOW);
    }

    @Override
    public int baseDurationSeconds() {
        return 7;
    }

    @Override
    public double minDurationSeconds() {
        return 4.0;
    }

    /**
     * Spawns a wave of mortar reticles with strict non-overlapping spacing.
     * Guaranteed minimum distance of (r1 + r2 + 1.8 blocks) ensures clear corridors between zones.
     */
    private void spawnReticleWave(MicroPartyMinigame game, int detonateInTicks, int currentRemainingTicks) {
        int floorY = MicroPartyArenaHelper.getFloorY(game.getMapConfig());
        MicroPartyArenaHelper.ArenaBounds2D bounds = MicroPartyArenaHelper.getBounds2D(game.getMapConfig());
        int margin = Math.max(2, bounds.width() / 8);

        double[] possibleRadii = { 2.6, 3.2, 3.8 };
        int desiredCount = Math.max(4, Math.min(6, (bounds.width() * bounds.depth()) / 100));
        int spawned = 0;

        for (int attempt = 0; attempt < 100 && spawned < desiredCount; attempt++) {
            double zoneRadius = possibleRadii[this.random.nextInt(possibleRadii.length)];
            double minX = bounds.minX() + margin + zoneRadius;
            double maxX = bounds.maxX() - margin - zoneRadius;
            double minZ = bounds.minZ() + margin + zoneRadius;
            double maxZ = bounds.maxZ() - margin - zoneRadius;

            if (minX >= maxX || minZ >= maxZ) {
                minX = bounds.minX() + zoneRadius;
                maxX = bounds.maxX() - zoneRadius;
                minZ = bounds.minZ() + zoneRadius;
                maxZ = bounds.maxZ() - zoneRadius;
            }

            double rx = minX + this.random.nextDouble() * Math.max(1.0, maxX - minX);
            double rz = minZ + this.random.nextDouble() * Math.max(1.0, maxZ - minZ);

            // Strict Non-Overlap Check: must have at least 1.8 blocks of safe corridor between zones
            boolean overlaps = false;
            for (MortarReticle existing : this.activeReticles) {
                double dist = Math.hypot(rx - existing.x(), rz - existing.z());
                if (dist < (zoneRadius + existing.radius() + 1.8)) {
                    overlaps = true;
                    break;
                }
            }

            if (!overlaps) {
                this.activeReticles.add(new MortarReticle(
                    rx,
                    floorY + 0.05,
                    rz,
                    zoneRadius,
                    currentRemainingTicks,
                    detonateInTicks
                ));
                spawned++;
            }
        }
    }

    @Override
    public void onStart(MicroPartyMinigame game, MinecraftServer server) {
        this.hitPlayers.clear();
        this.activeReticles.clear();
        this.wave2Spawned = false;
        this.wave3Spawned = false;

        int totalTicks = getDurationTicks(game);
        int wave1Detonate = Math.max(25, (int)(totalTicks * 0.52));
        spawnReticleWave(game, wave1Detonate, totalTicks);
    }

    @Override
    public void onTick(MicroPartyMinigame game, MinecraftServer server, int remainingTicks) {
        ServerWorld world = game.getWorld();
        if (world == null) return;

        int totalTicks = getDurationTicks(game);

        // Wave 2 barrage
        if (!this.wave2Spawned && remainingTicks <= (int)(totalTicks * 0.65) && remainingTicks > 28) {
            this.wave2Spawned = true;
            spawnReticleWave(game, Math.max(8, (int)(totalTicks * 0.20)), remainingTicks);
        }

        // Wave 3 barrage if duration permits
        if (!this.wave3Spawned && remainingTicks <= (int)(totalTicks * 0.35) && remainingTicks > 18) {
            this.wave3Spawned = true;
            spawnReticleWave(game, Math.max(2, remainingTicks - 18), remainingTicks);
        }

        MicroPartyArenaHelper.ArenaBounds2D bounds = MicroPartyArenaHelper.getBounds2D(game.getMapConfig());
        int floorY = MicroPartyArenaHelper.getFloorY(game.getMapConfig());

        // Global ambient countdown sound
        if (!this.activeReticles.isEmpty() && remainingTicks % 8 == 0) {
            world.playSound(null, bounds.centerX(), floorY, bounds.centerZ(), SoundEvents.BLOCK_NOTE_BLOCK_HAT.value(), SoundCategory.BLOCKS, 0.7f, 1.8f);
        }

        Iterator<MortarReticle> it = this.activeReticles.iterator();
        while (it.hasNext()) {
            MortarReticle reticle = it.next();
            int totalWarningTicks = Math.max(1, reticle.spawnRemainingTicks() - reticle.detonateTick());
            int ticksLeft = remainingTicks - reticle.detonateTick();
            float progress = 1.0f - Math.clamp((float) ticksLeft / (float) totalWarningTicks, 0.0f, 1.0f);
            boolean isUrgent = progress >= 0.60f || ticksLeft <= 20;

            DustParticleEffect borderEffect = isUrgent ? CRIMSON_DUST : ORANGE_DUST;
            double r = reticle.radius();

            // 1. Draw outer boundary ring with clear angular resolution
            int degStep = r > 3.2 ? 18 : 24;
            for (int deg = 0; deg < 360; deg += degStep) {
                double rad = Math.toRadians(deg);
                double px = reticle.x() + Math.cos(rad) * r;
                double pz = reticle.z() + Math.sin(rad) * r;
                world.spawnParticles(borderEffect, px, reticle.y(), pz, 1, 0, 0, 0, 0);
            }

            // 2. Contracting countdown ring: smoothly shrinks from radius down to 0 at detonation
            double innerR = r * (1.0 - progress);
            if (innerR > 0.4) {
                int innerStep = innerR > 2.0 ? 30 : 45;
                for (int deg = 0; deg < 360; deg += innerStep) {
                    double rad = Math.toRadians(deg);
                    double px = reticle.x() + Math.cos(rad) * innerR;
                    double pz = reticle.z() + Math.sin(rad) * innerR;
                    world.spawnParticles(ParticleTypes.SMALL_FLAME, px, reticle.y() + 0.02, pz, 1, 0, 0, 0, 0);
                }
            }

            // 3. Center target crosshair (+)
            world.spawnParticles(ParticleTypes.SMOKE, reticle.x(), reticle.y() + 0.1, reticle.z(), 1, 0.05, 0.1, 0.05, 0.01);
            world.spawnParticles(borderEffect, reticle.x() + 0.5, reticle.y(), reticle.z(), 1, 0, 0, 0, 0);
            world.spawnParticles(borderEffect, reticle.x() - 0.5, reticle.y(), reticle.z(), 1, 0, 0, 0, 0);
            world.spawnParticles(borderEffect, reticle.x(), reticle.y(), reticle.z() + 0.5, 1, 0, 0, 0, 0);
            world.spawnParticles(borderEffect, reticle.x(), reticle.y(), reticle.z() - 0.5, 1, 0, 0, 0, 0);

            // 4. Descending artillery shell trail from the sky (final 22 ticks / ~1.1s)
            if (ticksLeft <= 22 && ticksLeft > 0) {
                double shellHeight = ((double) ticksLeft / 22.0) * 20.0;
                world.spawnParticles(ParticleTypes.FLAME, reticle.x(), reticle.y() + shellHeight, reticle.z(), 3, 0.08, 0.2, 0.08, 0.02);
                world.spawnParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, reticle.x(), reticle.y() + shellHeight + 0.5, reticle.z(), 1, 0.02, 0.1, 0.02, 0.01);

                if (ticksLeft == 22) {
                    world.playSound(null, reticle.x(), reticle.y() + 14.0, reticle.z(), SoundEvents.ENTITY_FIREWORK_ROCKET_LAUNCH, SoundCategory.BLOCKS, 0.75f, 0.55f);
                }
            }

            // 5. Detonation check
            if (remainingTicks == reticle.detonateTick()) {
                world.spawnParticles(ParticleTypes.EXPLOSION, reticle.x(), reticle.y() + 0.5, reticle.z(), 3, 0.25, 0.25, 0.25, 0.05);
                world.spawnParticles(ParticleTypes.FLAME, reticle.x(), reticle.y() + 0.2, reticle.z(), 16, 0.6, 0.25, 0.6, 0.08);
                world.spawnParticles(ParticleTypes.LAVA, reticle.x(), reticle.y() + 0.3, reticle.z(), 6, 0.4, 0.2, 0.4, 0.02);
                world.playSound(null, reticle.x(), reticle.y(), reticle.z(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.BLOCKS, 1.0f, 1.25f);

                for (ServerPlayerEntity p : game.getLivingPlayers()) {
                    double distSq = (p.getX() - reticle.x()) * (p.getX() - reticle.x()) + (p.getZ() - reticle.z()) * (p.getZ() - reticle.z());
                    if (distSq <= r * r && Math.abs(p.getY() - reticle.y()) <= 2.5) {
                        this.hitPlayers.add(p.getUuid());
                        game.getTracker().recordHazardHit(p.getUuid());
                        p.sendMessage(Text.literal("§c§l💥 HIT BY MORTAR!"), true);
                        p.playSoundToPlayer(SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.0f, 1.0f);
                        p.setVelocity(new Vec3d(0, 0.55, 0));
                        p.velocityModified = true;
                        p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
                    }
                }
                it.remove();
            }
        }

        // 6. Real-Time Actionbar HUD for each living player
        for (ServerPlayerEntity p : game.getLivingPlayers()) {
            if (this.hitPlayers.contains(p.getUuid())) {
                p.sendMessage(Text.literal("§c§l💥 HIT BY MORTAR!"), true);
                continue;
            }

            MortarReticle nearestDanger = null;
            double maxEscapeDistance = -1.0;

            for (MortarReticle reticle : this.activeReticles) {
                double dist = Math.hypot(p.getX() - reticle.x(), p.getZ() - reticle.z());
                if (dist <= reticle.radius()) {
                    double escapeNeeded = reticle.radius() - dist + 0.15;
                    if (escapeNeeded > maxEscapeDistance) {
                        maxEscapeDistance = escapeNeeded;
                        nearestDanger = reticle;
                    }
                }
            }

            if (nearestDanger != null) {
                p.sendMessage(Text.literal(String.format(
                    Locale.ROOT,
                    "§c§l⚠ IN BLAST ZONE! §eRun %.1fm to escape! §c⚠",
                    maxEscapeDistance
                )), true);
                if (remainingTicks % 4 == 0) {
                    p.playSoundToPlayer(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), SoundCategory.PLAYERS, 0.45f, 1.7f);
                }
            } else {
                p.sendMessage(Text.literal("§a§l✔ IN THE CLEAR §7• Watch for falling shells!"), true);
            }
        }
    }

    @Override
    public boolean hasPassed(ServerPlayerEntity player, MicroPartyMinigame game) {
        return !this.hitPlayers.contains(player.getUuid());
    }

    @Override
    public void onEnd(MicroPartyMinigame game, MinecraftServer server) {
        this.activeReticles.clear();
        this.hitPlayers.clear();
    }
}
