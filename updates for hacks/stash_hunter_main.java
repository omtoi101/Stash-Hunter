// Updates needed for StashHunter.java main class

// Add these imports:
import com.stashhunter.stashhunter.modules.AutoElytraRepair;

// In the onInitializeClient() method, add the new modules:
@Override
public void onInitializeClient() {
    LOG.info("Initializing Stash-Hunter");
    
    // Load configuration
    Config.load();
    Config.validate();
    
    // Add modules
    Modules.get().add(new StashHunterModule());
    Modules.get().add(new StuckDetector());
    Modules.get().add(new AltitudeLossDetector());
    Modules.get().add(new AutoElytraRepair()); // Add this line
    
    // Add commands
    Commands.get().add(new StashHunterCommand());
    
    LOG.info("Stash-Hunter initialized successfully");
}