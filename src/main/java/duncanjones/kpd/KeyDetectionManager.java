package duncanjones.kpd;  // Package declaration

import com.mojang.brigadier.ParseResults;  // Command parsing results
import com.mojang.brigadier.exceptions.CommandSyntaxException;  // Command exceptions
import net.minecraft.server.command.ServerCommandSource;  // Command execution source
import net.minecraft.server.network.ServerPlayerEntity;  // Server-side player object

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;  // Thread-safe map
import java.util.concurrent.Executors;  // Executor service creation
import java.util.concurrent.ScheduledExecutorService;  // Scheduled task service
import java.util.concurrent.ScheduledFuture;  // Future for scheduled tasks
import java.util.concurrent.TimeUnit;  // Time units for scheduling

public class KeyDetectionManager {
    // Store key bindings: key format = "playerUUID:keyCode:eventType"
    private static final Map<String, List<KeyBinding>> bindings = new ConcurrentHashMap<>();

    // Store scheduled tasks for sustained key events
    private static final Map<String, ScheduledFuture<?>> sustainedTasks = new ConcurrentHashMap<>();
    // Thread pool for executing sustained events (20 times/second)
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    // Add a new key binding to the manager
    public static void addBinding(KeyBinding binding) {
        String key = createKey(binding.player(), binding.keyCode(), binding.keyType());
        bindings.computeIfAbsent(key, k -> new ArrayList<>()).add(binding);
    }

    // Remove a specific key binding
    public static boolean removeBinding(ServerPlayerEntity player, int keyCode, String keyType) {
        String key = createKey(player, keyCode, keyType);
        boolean removed = bindings.remove(key) != null;

        // If removing sustained binding, also stop the scheduled task
        if ("sustained".equals(keyType)) {
            stopSustainedTask(player, keyCode);
        }

        return removed;
    }

    // Remove all bindings for a player
    public static void removeAllBindings(ServerPlayerEntity player) {
        // Remove all bindings that start with player's UUID
        bindings.entrySet().removeIf(entry -> entry.getKey().startsWith(player.getUuid().toString()));

        // Stop all sustained tasks for this player
        stopAllSustainedTasks(player);
    }

    // Get all bindings for a specific player
    public static List<KeyBinding> getBindings(ServerPlayerEntity player) {
        List<KeyBinding> playerBindings = new ArrayList<>();
        String playerUuid = player.getUuid().toString();

        // Search through all bindings for this player
        for (List<KeyBinding> bindingList : bindings.values()) {
            for (KeyBinding binding : bindingList) {
                if (binding.player().getUuid().toString().equals(playerUuid)) {
                    playerBindings.add(binding);
                }
            }
        }

        return playerBindings;
    }

    // Execute all bindings for a key event
    public static void executeBindings(ServerPlayerEntity player, int keyCode, String keyType) {
        String key = createKey(player, keyCode, keyType);
        List<KeyBinding> playerBindings = bindings.get(key);

        if (playerBindings != null && !playerBindings.isEmpty()) {
            // Create command source with appropriate permissions
            ServerCommandSource source = player.getCommandSource()
                    .withSilent()  // Don't show command feedback to player
                    .withLevel(2);  // OP level 2

            if ("press".equals(keyType)) {
                // Execute press event functions
                for (KeyBinding binding : playerBindings) {
                    String command = "function " + binding.functionId().toString();
                    executeCommand(source, command);
                }

                // Check if there's a sustained binding for this key
                String sustainedKey = createKey(player, keyCode, "sustained");
                List<KeyBinding> sustainedBindings = bindings.get(sustainedKey);
                if (sustainedBindings != null && !sustainedBindings.isEmpty()) {
                    // Start sustained task if sustained binding exists
                    startSustainedTask(player, keyCode, sustainedBindings, source);
                }
            }
            else if ("release".equals(keyType)) {
                // Execute release event functions
                for (KeyBinding binding : playerBindings) {
                    String command = "function " + binding.functionId().toString();
                    executeCommand(source, command);
                }

                // Stop sustained task on key release
                stopSustainedTask(player, keyCode);
            }
            else {
                // Handle other event types (hold, sustained, etc.)
                for (KeyBinding binding : playerBindings) {
                    String command = "function " + binding.functionId().toString();
                    executeCommand(source, command);
                }
            }
        }
    }

    // Execute a Minecraft command/function
    private static void executeCommand(ServerCommandSource source, String command) {
        ParseResults<ServerCommandSource> parseResults = source.getServer().getCommandManager()
                .getDispatcher().parse(command, source);
        try {
            source.getServer().getCommandManager().execute(parseResults, command);
        } catch (CommandSyntaxException e) {
            KeyPressDetector.LOGGER.error("Failed to execute command: " + command, e);
        }
    }

    // Start a scheduled task for sustained key events
    private static void startSustainedTask(ServerPlayerEntity player, int keyCode,
                                           List<KeyBinding> sustainedBindings, ServerCommandSource source) {
        String taskKey = player.getUuid() + ":" + keyCode;

        // Stop existing task if any
        stopSustainedTask(player, keyCode);

        // Create new scheduled task
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> {
            // Check if player is still online and alive
            if (player.isDisconnected() || !player.isAlive()) {
                stopSustainedTask(player, keyCode);
                return;
            }

            try {
                // Execute all sustained bindings
                for (KeyBinding binding : sustainedBindings) {
                    String command = "function " + binding.functionId().toString();
                    executeCommand(source, command);
                }
            } catch (Exception e) {
                KeyPressDetector.LOGGER.error("Error executing sustained function", e);
                stopSustainedTask(player, keyCode);
            }
        }, 0, 50, TimeUnit.MILLISECONDS); // Execute every 50ms (20 times per second)

        sustainedTasks.put(taskKey, task);
    }

    // Stop a specific sustained task
    private static void stopSustainedTask(ServerPlayerEntity player, int keyCode) {
        String taskKey = player.getUuid() + ":" + keyCode;
        ScheduledFuture<?> task = sustainedTasks.remove(taskKey);
        if (task != null) {
            task.cancel(false);  // Cancel without interrupting if running
        }
    }

    // Stop all sustained tasks for a player
    private static void stopAllSustainedTasks(ServerPlayerEntity player) {
        String playerPrefix = player.getUuid() + ":";
        sustainedTasks.entrySet().removeIf(entry -> {
            if (entry.getKey().startsWith(playerPrefix)) {
                entry.getValue().cancel(false);
                return true;
            }
            return false;
        });
    }

    // Create a unique key for storing bindings
    private static String createKey(ServerPlayerEntity player, int keyCode, String keyType) {
        return player.getUuid().toString() + ":" + keyCode + ":" + keyType;
    }

    // Cleanup method called when server shuts down
    public static void shutdown() {
        scheduler.shutdown();  // Initiate shutdown
        try {
            // Wait for tasks to complete
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();  // Force shutdown if timeout
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();  // Force shutdown if interrupted
        }
    }
}
