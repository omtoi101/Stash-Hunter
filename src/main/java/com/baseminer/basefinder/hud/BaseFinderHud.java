package com.baseminer.basefinder.hud;

import com.baseminer.basefinder.BaseFinder;
import com.baseminer.basefinder.utils.ElytraController;

import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.systems.hud.HudElement;
import meteordevelopment.meteorclient.systems.hud.HudElementInfo;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.util.math.Vec3d;

public class BaseFinderHud extends HudElement {
    public static final HudElementInfo<BaseFinderHud> INFO = new HudElementInfo<>(
        BaseFinder.HUD_GROUP,
        "base-finder-status",
        "Displays the status of the Base Finder bot.",
        BaseFinderHud::new
    );

    public BaseFinderHud() {
        super(INFO);
    }

    @Override
    public void render(HudRenderer renderer) {
        if (MeteorClient.mc.player == null) {
            setSize(0, 0);
            return;
        }

        String status = ElytraController.getStatus();
        Color statusColor = getStatusColor();

        // Calculate text dimensions
        double lineHeight = renderer.textHeight();
        double maxWidth = renderer.textWidth(status);
        int lines = 1;

        // Add additional info if active
        String targetInfo = "";
        String progressInfo = "";

        if (ElytraController.isActive()) {
            Vec3d target = ElytraController.getCurrentTarget();
            if (target != null) {
                double distance = MeteorClient.mc.player.getPos().distanceTo(target);
                targetInfo = String.format("Target: %d, %d (%.1fm)",
                    (int)target.x, (int)target.z, distance);
                maxWidth = Math.max(maxWidth, renderer.textWidth(targetInfo));
                lines++;
            }

            progressInfo = String.format("Progress: %d/%d waypoints",
                ElytraController.getCurrentWaypoint(), ElytraController.getTotalWaypoints());
            maxWidth = Math.max(maxWidth, renderer.textWidth(progressInfo));
            lines++;
        }

        // Set HUD element size
        setSize(maxWidth, lineHeight * lines);

        // Render status
        double currentY = y;
        renderer.text(status, x, currentY, statusColor, true);
        currentY += lineHeight;

        // Render additional info
        if (!targetInfo.isEmpty()) {
            renderer.text(targetInfo, x, currentY, Color.LIGHT_GRAY, true);
            currentY += lineHeight;
        }

        if (!progressInfo.isEmpty()) {
            renderer.text(progressInfo, x, currentY, Color.GRAY, true);
        }
    }

    private Color getStatusColor() {
        if (ElytraController.isActive()) {
            return Color.GREEN;
        } else if (ElytraController.getCurrentWaypoint() >= ElytraController.getTotalWaypoints() &&
                   ElytraController.getTotalWaypoints() > 0) {
            return Color.YELLOW;
        } else {
            return Color.WHITE;
        }
    }
}
