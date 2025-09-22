// Add these new configuration variables to Config.java

// Auto Elytra Repair settings
public static int autoRepairThreshold = 50;
public static int autoRepairLandingRadius = 64;  
public static boolean autoRepairNotifications = true;
public static int autoRepairTimeout = 300;

// Add these to the load() method:
autoRepairThreshold = getIntProperty("autoRepairThreshold", 50);
autoRepairLandingRadius = getIntProperty("autoRepairLandingRadius", 64);
autoRepairNotifications = getBoolProperty("autoRepairNotifications", true);
autoRepairTimeout = getIntProperty("autoRepairTimeout", 300);

// Add these to the save() method:
properties.setProperty("autoRepairThreshold", String.valueOf(autoRepairThreshold));
properties.setProperty("autoRepairLandingRadius", String.valueOf(autoRepairLandingRadius));
properties.setProperty("autoRepairNotifications", String.valueOf(autoRepairNotifications));
properties.setProperty("autoRepairTimeout", String.valueOf(autoRepairTimeout));

// Add validation in the validate() method:
if (autoRepairThreshold < 1) {
    autoRepairThreshold = 1;
    changed = true;
} else if (autoRepairThreshold > 100) {
    autoRepairThreshold = 100;
    changed = true;
}

if (autoRepairLandingRadius < 16) {
    autoRepairLandingRadius = 16;
    changed = true;
} else if (autoRepairLandingRadius > 128) {
    autoRepairLandingRadius = 128;
    changed = true;
}

if (autoRepairTimeout < 60) {
    autoRepairTimeout = 60;
    changed = true;
} else if (autoRepairTimeout > 600) {
    autoRepairTimeout = 600;
    changed = true;
}