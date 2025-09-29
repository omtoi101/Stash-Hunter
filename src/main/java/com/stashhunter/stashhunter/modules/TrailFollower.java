package com.stashhunter.stashhunter.modules;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalXZ;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.stashhunter.stashhunter.StashHunter;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.common.DisconnectS2CPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import xaeroplus.XaeroPlus;
import xaeroplus.event.ChunkDataEvent;
import xaeroplus.module.ModuleManager;
import xaeroplus.module.impl.OldChunks;

import java.time.Duration;
import java.util.ArrayDeque;

public class TrailFollower extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<Integer> maxTrailLength = sgGeneral.add(new IntSetting.Builder()
        .name("Max Trail Length")
        .description("The number of trail points to keep for the average.")
        .defaultValue(20)
        .sliderRange(1, 100)
        .build()
    );

    public final Setting<Integer> chunksBeforeStarting = sgGeneral.add(new IntSetting.Builder()
        .name("Chunks Before Starting")
        .description("The amount of chunks before it gets detected as a trail.")
        .defaultValue(10)
        .sliderRange(1, 50)
        .build()
    );

    public final Setting<Integer> chunkConsiderationWindow = sgGeneral.add(new IntSetting.Builder()
        .name("Chunk Timeframe")
        .description("The amount of time in seconds that the chunks must be found in before starting.")
        .defaultValue(5)
        .sliderRange(1, 20)
        .build()
    );

    public final Setting<TrailEndBehavior> trailEndBehavior = sgGeneral.add(new EnumSetting.Builder<TrailEndBehavior>()
        .name("Trail End Behavior")
        .description("What to do when the trail ends.")
        .defaultValue(TrailEndBehavior.DISABLE)
        .build()
    );

    public final Setting<Double> trailEndYaw = sgGeneral.add(new DoubleSetting.Builder()
        .name("Trail End Yaw")
        .description("The direction to go after the trail is abandoned.")
        .defaultValue(0.0)
        .sliderRange(0.0, 359.9)
        .visible(() -> trailEndBehavior.get() == TrailEndBehavior.FLY_TOWARDS_YAW)
        .build()
    );

    public enum OverworldFlightMode {
        VANILLA,
        PITCH40,
        OTHER
    }

    public enum NetherPathMode {
        AVERAGE,
        OTHER
    }

    public final Setting<OverworldFlightMode> overworldFlightMode = sgGeneral.add(new EnumSetting.Builder<OverworldFlightMode>()
        .name("Overworld Flight Mode")
        .description("Choose how TrailFollower flies in Overworld.")
        .defaultValue(OverworldFlightMode.PITCH40)
        .build()
    );

    public final Setting<NetherPathMode> netherPathMode = sgGeneral.add(new EnumSetting.Builder<NetherPathMode>()
        .name("Nether Path Mode")
        .description("Choose how TrailFollower does baritone pathing in Nether.")
        .defaultValue(NetherPathMode.AVERAGE)
        .build()
    );

    public final Setting<Double> rotateScaling = sgGeneral.add(new DoubleSetting.Builder()
        .name("Rotate Scaling")
        .description("Scaling of how fast the yaw changes. 1 = instant, 0 = doesn't change")
        .defaultValue(0.1)
        .sliderRange(0.0, 1.0)
        .build()
    );

    public final Setting<Boolean> oppositeDimension = sgGeneral.add(new BoolSetting.Builder()
        .name("Opposite Dimension")
        .description("Follows trails from the opposite dimension.")
        .defaultValue(false)
        .build()
    );

    public final Setting<Boolean> autoElytra = sgGeneral.add(new BoolSetting.Builder()
        .name("[Baritone] Auto Start Baritone Elytra")
        .description("Starts baritone elytra for you.")
        .defaultValue(false)
        .build()
    );

    private final SettingGroup sgAdvanced = settings.createGroup("Advanced", false);

    public final Setting<Double> pathDistance = sgAdvanced.add(new DoubleSetting.Builder()
        .name("Path Distance")
        .description("The distance to path in the direction of the trail.")
        .defaultValue(500)
        .sliderRange(100, 2000)
        .onChanged(value -> pathDistanceActual = value)
        .build()
    );

    public final Setting<FollowMode> flightMethod = sgAdvanced.add(new EnumSetting.Builder<FollowMode>()
        .name("Flight Method")
        .description("How the goals will be used.")
        .defaultValue(FollowMode.AUTO)
        .build()
    );

    public final Setting<Double> startDirectionWeighting = sgAdvanced.add(new DoubleSetting.Builder()
        .name("Start Direction Weight")
        .description("The weighting of the direction the player is facing when starting.")
        .defaultValue(0.5)
        .min(0)
        .sliderMax(1)
        .build()
    );

    public final Setting<DirectionWeighting> directionWeighting = sgAdvanced.add(new EnumSetting.Builder<DirectionWeighting>()
        .name("Direction Weighting")
        .description("How the chunks found should be weighted.")
        .defaultValue(DirectionWeighting.NONE)
        .build()
    );

    public final Setting<Integer> directionWeightingMultiplier = sgAdvanced.add(new IntSetting.Builder()
        .name("Direction Weighting Multiplier")
        .description("The multiplier for how much weight should be given to chunks.")
        .defaultValue(2)
        .min(2)
        .sliderMax(10)
        .visible(() -> directionWeighting.get() != DirectionWeighting.NONE)
        .build()
    );

    public final Setting<Double> chunkFoundTimeout = sgAdvanced.add(new DoubleSetting.Builder()
        .name("Chunk Found Timeout")
        .description("The amount of time in ms without a chunk found to trigger circling.")
        .defaultValue(1000 * 5)
        .min(1000)
        .sliderMax(1000 * 10)
        .build()
    );

    public final Setting<Double> circlingDegPerTick = sgAdvanced.add(new DoubleSetting.Builder()
        .name("Circling Degrees Per Tick")
        .description("The amount of degrees to change per tick while circling.")
        .defaultValue(2.0)
        .min(1.0)
        .sliderMax(20.0)
        .build()
    );

    public final Setting<Double> trailTimeout = sgAdvanced.add(new DoubleSetting.Builder()
        .name("Trail Timeout")
        .description("The amount of time in ms without a chunk found to stop following.")
        .defaultValue(1000 * 30)
        .min(1000 * 10)
        .sliderMax(1000 * 60)
        .build()
    );

    public final Setting<Double> maxTrailDeviation = sgAdvanced.add(new DoubleSetting.Builder()
        .name("Max Trail Deviation")
        .description("Maximum allowed angle from the original trail direction.")
        .defaultValue(180.0)
        .min(1.0)
        .sliderMax(270.0)
        .build()
    );

    public final Setting<Integer> chunkCacheLength = sgAdvanced.add(new IntSetting.Builder()
        .name("Chunk Cache Length")
        .description("The amount of chunks to keep in the cache.")
        .defaultValue(100_000)
        .sliderRange(0, 10_000_000)
        .build()
    );

    public final Setting<Integer> baritoneUpdateTicks = sgAdvanced.add(new IntSetting.Builder()
        .name("[Baritone] Baritone Path Update Ticks")
        .description("The amount of ticks between updates to the baritone goal.")
        .defaultValue(5 * 20) // 5 seconds
        .sliderRange(20, 30 * 20)
        .build()
    );

    public final Setting<Boolean> debug = sgAdvanced.add(new BoolSetting.Builder()
        .name("Debug")
        .description("Debug mode.")
        .defaultValue(false)
        .build()
    );

    private FollowMode followMode;
    private boolean followingTrail = false;
    private ArrayDeque<Vec3d> trail = new ArrayDeque<>();
    private ArrayDeque<Vec3d> possibleTrail = new ArrayDeque<>();
    private long lastFoundTrailTime;
    private long lastFoundPossibleTrailTime;
    private double pathDistanceActual = pathDistance.get();
    private Cache<Long, Byte> seenChunksCache = Caffeine.newBuilder()
        .maximumSize(chunkCacheLength.get())
        .expireAfterWrite(Duration.ofMinutes(5))
        .build();

    public TrailFollower() {
        super(StashHunter.CATEGORY, "TrailFollower", "Automatically follows trails in all dimensions.");
    }

    void resetTrail() {
        baritoneSetGoalTicks = 0;
        followingTrail = false;
        trail = new ArrayDeque<>();
        possibleTrail = new ArrayDeque<>();
    }

    @Override
    public void onActivate() {
        resetTrail();
        XaeroPlus.EVENT_BUS.register(this);
        if (mc.player != null && mc.world != null) {
            RegistryKey<World> currentDimension = mc.world.getRegistryKey();
            if (oppositeDimension.get()) {
                if (currentDimension.equals(World.END) || currentDimension.equals(World.NETHER)) {
                    info("Opposite dimension is not supported for End or Nether. Disabling.");
                    this.toggle();
                    return;
                }
            }

            if (flightMethod.get() == FollowMode.AUTO) {
                followMode = currentDimension.equals(World.NETHER) ? FollowMode.BARITONE : FollowMode.YAWLOCK;
            } else {
                followMode = flightMethod.get();
            }

            if (followMode == FollowMode.BARITONE) {
                try {
                    Class.forName("baritone.api.BaritoneAPI");
                    info("Baritone mode will be used.");
                } catch (ClassNotFoundException e) {
                    info("Baritone is required for this mode. Disabling.");
                    this.toggle();
                    return;
                }
            } else {
                info("Yaw lock mode will be used.");
            }

            Vec3d offset = new Vec3d(Math.sin(-mc.player.getYaw() * Math.PI / 180), 0, Math.cos(-mc.player.getYaw() * Math.PI / 180)).normalize().multiply(pathDistance.get());
            Vec3d targetPos = mc.player.getPos().add(offset);
            for (int i = 0; i < (maxTrailLength.get() * startDirectionWeighting.get()); i++) {
                trail.add(targetPos);
            }
            targetYaw = getActualYaw(mc.player.getYaw());
        } else {
            this.toggle();
        }
    }

    @Override
    public void onDeactivate() {
        seenChunksCache = Caffeine.newBuilder()
            .maximumSize(chunkCacheLength.get())
            .expireAfterWrite(Duration.ofMinutes(5))
            .build();
        XaeroPlus.EVENT_BUS.unregister(this);
        trail.clear();
        if (followMode == FollowMode.BARITONE) {
            BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("cancel");
        }
    }

    private double targetYaw;
    private int baritoneSetGoalTicks = 0;

    private void circle() {
        if (followMode == FollowMode.BARITONE) return;
        mc.player.setYaw(getActualYaw((float) (mc.player.getYaw() + circlingDegPerTick.get())));
        if (mc.player.age % 100 == 0) {
            info("Circling to look for new chunks...");
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        if (followingTrail && System.currentTimeMillis() - lastFoundTrailTime > trailTimeout.get()) {
            resetTrail();
            info("Trail timed out, stopping.");
            handleTrailEnd();
        }
        if (followingTrail && System.currentTimeMillis() - lastFoundTrailTime > chunkFoundTimeout.get()) {
            circle();
            return;
        }

        switch (followMode) {
            case BARITONE:
                handleBaritone();
                break;
            case YAWLOCK:
                mc.player.setYaw(smoothRotation(getActualYaw(mc.player.getYaw()), targetYaw, rotateScaling.get()));
                break;
        }
    }

    private void handleBaritone() {
        if (baritoneSetGoalTicks > 0) {
            baritoneSetGoalTicks--;
        } else {
            baritoneSetGoalTicks = baritoneUpdateTicks.get();
            if (!trail.isEmpty()) {
                Vec3d baritoneTarget;
                if (netherPathMode.get() == NetherPathMode.AVERAGE) {
                    Vec3d averagePos = calculateAveragePosition(trail);
                    Vec3d directionVec = averagePos.subtract(mc.player.getPos()).normalize();
                    Vec3d predictedPos = mc.player.getPos().add(directionVec.multiply(10));
                    targetYaw = Rotations.getYaw(predictedPos);
                    baritoneTarget = positionInDirection(mc.player.getPos(), targetYaw, pathDistanceActual);
                } else {
                    baritoneTarget = trail.getLast();
                }
                BaritoneAPI.getProvider().getPrimaryBaritone().getCustomGoalProcess().setGoalAndPath(new GoalXZ((int) baritoneTarget.x, (int) baritoneTarget.z));
            }

            if (autoElytra.get() && !BaritoneAPI.getProvider().getPrimaryBaritone().getElytraProcess().isElytraFlying()) {
                BaritoneAPI.getSettings().elytraTermsAccepted.value = true;
                BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager().execute("elytra");
            }
        }
    }

    private void handleTrailEnd() {
        switch (trailEndBehavior.get()) {
            case DISABLE:
                this.toggle();
                break;
            case FLY_TOWARDS_YAW:
                targetYaw = trailEndYaw.get();
                break;
            case DISCONNECT:
                mc.player.networkHandler.onDisconnect(new DisconnectS2CPacket(Text.literal("[TrailFollower] Trail timed out.")));
                break;
        }
    }

    Vec3d posDebug;

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!debug.get()) return;
        Vec3d targetPos = positionInDirection(mc.player.getPos(), targetYaw, 10);
        event.renderer.line(mc.player.getX(), mc.player.getY(), mc.player.getZ(), targetPos.x, targetPos.y, targetPos.z, new Color(255, 0, 0));
        if (posDebug != null)
            event.renderer.line(mc.player.getX(), mc.player.getY(), mc.player.getZ(), posDebug.x, posDebug.y, posDebug.z, new Color(0, 0, 255));
    }

    @net.lenni0451.lambdaevents.EventHandler(priority = -1)
    public void onChunkData(ChunkDataEvent event) {
        if (event.seenChunk()) return;
        RegistryKey<World> currentDimension = mc.world.getRegistryKey();
        ChunkPos chunkPos = event.chunk().getPos();
        long chunkLong = chunkPos.toLong();

        if (seenChunksCache.getIfPresent(chunkLong) != null) return;

        if (oppositeDimension.get()) {
            if (currentDimension.equals(World.OVERWORLD)) {
                chunkPos = new ChunkPos(mc.player.getChunkPos().x / 8 + (chunkPos.x - mc.player.getChunkPos().x), mc.player.getChunkPos().z / 8 + (chunkPos.z - mc.player.getChunkPos().z));
                currentDimension = World.NETHER;
            } else if (currentDimension.equals(World.NETHER)) {
                chunkPos = new ChunkPos(mc.player.getChunkPos().x * 8 + (chunkPos.x - mc.player.getChunkPos().x), mc.player.getChunkPos().z * 8 + (chunkPos.z - mc.player.getChunkPos().z));
                currentDimension = World.OVERWORLD;
            }
        }

        if (!isValidChunk(chunkPos, currentDimension)) return;

        seenChunksCache.put(chunkLong, Byte.MAX_VALUE);

        Vec3d pos = event.chunk().getPos().getCenterAtY(mc.player.getY());
        posDebug = pos;

        if (!followingTrail) {
            if (System.currentTimeMillis() - lastFoundPossibleTrailTime > chunkConsiderationWindow.get() * 1000) {
                possibleTrail.clear();
            }
            possibleTrail.add(pos);
            lastFoundPossibleTrailTime = System.currentTimeMillis();
            if (possibleTrail.size() > chunksBeforeStarting.get()) {
                info("Trail found, starting to follow.");
                followingTrail = true;
                lastFoundTrailTime = System.currentTimeMillis();
                trail.addAll(possibleTrail);
                possibleTrail.clear();
            }
            return;
        }

        double chunkAngle = Rotations.getYaw(pos);
        double angleDiff = angleDifference(targetYaw, chunkAngle);
        if (Math.abs(angleDiff) > maxTrailDeviation.get()) {
            return;
        }

        lastFoundTrailTime = System.currentTimeMillis();
        while (trail.size() >= maxTrailLength.get()) {
            trail.pollFirst();
        }

        int weight = 1;
        if (directionWeighting.get() == DirectionWeighting.LEFT && angleDiff > 0 && angleDiff < 90) {
            weight = directionWeightingMultiplier.get();
        } else if (directionWeighting.get() == DirectionWeighting.RIGHT && angleDiff < 0 && angleDiff > -90) {
            weight = directionWeightingMultiplier.get();
        }

        for (int i = 0; i < weight; i++) {
            if (trail.size() >= maxTrailLength.get()) {
                trail.pollFirst();
            }
            trail.add(pos);
        }

        if (!trail.isEmpty()) {
            if (followMode == FollowMode.YAWLOCK) {
                Vec3d averagePos = calculateAveragePosition(trail);
                Vec3d positionVec = averagePos.subtract(mc.player.getPos()).normalize();
                Vec3d targetPos = mc.player.getPos().add(positionVec.multiply(10));
                targetYaw = Rotations.getYaw(targetPos);
            } else {
                targetYaw = Rotations.getYaw(trail.getLast());
            }
        }
    }

    private boolean isValidChunk(ChunkPos chunkPos, RegistryKey<World> currentDimension) {
        return ModuleManager.getModule(OldChunks.class).isOldChunk(chunkPos.x, chunkPos.z, currentDimension);
    }

    private Vec3d calculateAveragePosition(ArrayDeque<Vec3d> positions) {
        double sumX = 0, sumZ = 0;
        for (Vec3d pos : positions) {
            sumX += pos.x;
            sumZ += pos.z;
        }
        return new Vec3d(sumX / positions.size(), 0, sumZ / positions.size());
    }

    private float getActualYaw(float yaw) {
        return (yaw % 360 + 360) % 360;
    }

    private Vec3d positionInDirection(Vec3d start, double yaw, double distance) {
        double yawRad = Math.toRadians(yaw);
        Vec3d direction = new Vec3d(-Math.sin(yawRad), 0, Math.cos(yawRad));
        return start.add(direction.multiply(distance));
    }

    private double angleDifference(double a, double b) {
        return ((((a - b) % 360) + 540) % 360) - 180;
    }

    private float smoothRotation(float current, double target, double scale) {
        double diff = angleDifference(target, current);
        return (float) (current + diff * scale);
    }

    public enum FollowMode { AUTO, BARITONE, YAWLOCK }
    public enum DirectionWeighting { LEFT, NONE, RIGHT }
    public enum TrailEndBehavior { DISABLE, FLY_TOWARDS_YAW, DISCONNECT }
}