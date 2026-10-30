package com.stashhunter.stashhunter.modules;

import com.stashhunter.stashhunter.StashHunter;
import com.stashhunter.stashhunter.baritone.BaritoneBridge;
import com.stashhunter.stashhunter.utils.ElytraController;
import com.stashhunter.stashhunter.utils.FireworkBoost;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.phys.Vec3;

public class AutoFirework extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Double> minSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("min-speed")
        .description("Fire a rocket when the forward speed (along your look direction) drops below this, in blocks per second.")
        .defaultValue(20)
        .min(0)
        .sliderMax(40)
        .build()
    );

    private final Setting<Integer> useDelay = sgGeneral.add(new IntSetting.Builder()
        .name("use-delay")
        .description("Minimum ticks between two rockets, so a rocket that is still burning isn't stacked.")
        .defaultValue(40)
        .min(1)
        .sliderMax(200)
        .build()
    );

    private final Setting<Integer> checkCooldown = sgGeneral.add(new IntSetting.Builder()
        .name("check-cooldown")
        .description("Ticks between two speed checks.")
        .defaultValue(5)
        .min(0)
        .sliderMax(40)
        .build()
    );

    // Rockets only boost while gliding anyway, so "flying" here narrows it to the bot's own flight.
    private final Setting<Boolean> onlyWhenFlying = sgGeneral.add(new BoolSetting.Builder()
        .name("only-when-flying")
        .description("Only fire while the Stash-Hunter scan flight is running. Off: during any elytra flight.")
        .defaultValue(false)
        .build()
    );

    private int ticksSinceUse = Integer.MAX_VALUE;
    private int ticksSinceCheck = 0;
    private boolean warnedNoRockets = false;

    public AutoFirework() {
        super(StashHunter.CATEGORY, "auto-firework",
            "AUTOMATION: uses firework rockets from your hotbar on its own while you fly with an elytra "
                + "and your forward speed drops below min-speed. Briefly switches the hotbar slot. "
                + "Consumes rockets; servers with anti-cheat may flag automated item use. "
                + "Only uses rockets without firework stars. Pauses while Baritone's elytra flight is active.");
    }

    @Override
    public void onActivate() {
        ticksSinceUse = Integer.MAX_VALUE;
        ticksSinceCheck = 0;
        warnedNoRockets = false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        if (ticksSinceUse != Integer.MAX_VALUE) ticksSinceUse++;
        ticksSinceCheck++;
        if (!FireworkBoost.shouldCheck(ticksSinceCheck, checkCooldown.get())) return;
        ticksSinceCheck = 0;

        // An open screen (inventory, chat) would make a slot switch or item use act on the wrong UI.
        if (mc.gui.screen() != null) return;
        if (onlyWhenFlying.get() && !ElytraController.isActive()) return;
        // A deliberate post-scan landing must not be undone by a boost.
        if (ElytraController.isLandingAfterCompletion()) return;
        // Baritone's elytra process times its own rockets and slot swaps; ours would stack on top.
        if (BaritoneBridge.isElytraActive()) return;

        Vec3 v = mc.player.getDeltaMovement();
        Vec3 look = mc.player.getLookAngle();
        double speed = FireworkBoost.forwardSpeed(v.x, v.y, v.z, look.x, look.y, look.z);
        if (!FireworkBoost.shouldBoost(mc.player.isFallFlying(), speed, minSpeed.get(), ticksSinceUse, useDelay.get())) {
            return;
        }

        FindItemResult rocket = InvUtils.findInHotbar(AutoFirework::isSafeRocket);
        if (!rocket.found()) {
            // Once per shortage, not every check.
            if (!warnedNoRockets) {
                warning("No firework rockets without stars in hotbar or offhand.");
                warnedNoRockets = true;
            }
            return;
        }
        warnedNoRockets = false;

        if (rocket.isOffhand() || rocket.isMainHand()) {
            mc.gameMode.useItem(mc.player, rocket.getHand());
        } else {
            if (!InvUtils.swap(rocket.slot(), true)) return;
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            InvUtils.swapBack();
        }
        ticksSinceUse = 0;
    }

    private static boolean isSafeRocket(ItemStack stack) {
        if (!stack.is(Items.FIREWORK_ROCKET)) return false;
        Fireworks fireworks = stack.get(DataComponents.FIREWORKS);
        return FireworkBoost.isSafeRocket(fireworks == null ? 0 : fireworks.explosions().size());
    }
}
