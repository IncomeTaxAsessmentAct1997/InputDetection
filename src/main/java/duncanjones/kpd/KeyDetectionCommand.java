package duncanjones.kpd;  // Package declaration

import com.mojang.brigadier.CommandDispatcher;  // Command framework dispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType;  // Integer command argument
import com.mojang.brigadier.context.CommandContext;  // Command execution context
import com.mojang.brigadier.exceptions.CommandSyntaxException;  // Command parsing exceptions
import com.mojang.brigadier.suggestion.SuggestionProvider;  // Tab completion provider
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;  // Fabric command registration
import net.minecraft.command.argument.EntityArgumentType;  // Entity selector argument
import net.minecraft.command.argument.IdentifierArgumentType;  // Identifier argument (for functions)
import net.minecraft.server.command.ServerCommandSource;  // Command execution source
import net.minecraft.server.network.ServerPlayerEntity;  // Server-side player object
import net.minecraft.text.Text;  // Minecraft text component
import net.minecraft.util.Identifier;  // Resource identifier

import java.util.Collection;
import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;  // Create command arguments
import static net.minecraft.server.command.CommandManager.literal;  // Create literal command parts

public class KeyDetectionCommand {
    
    // Suggestion provider for function names (tab completion)
    private static final SuggestionProvider<ServerCommandSource> FUNCTION_SUGGESTIONS = (context, builder) -> {
        // Get all available functions from the server
        Iterable<Identifier> functions = context.getSource().getServer().getCommandFunctionManager().getAllFunctions();
        for (Identifier function : functions) {
            builder.suggest(function.toString());  // Add each function to suggestions
        }
        return builder.buildFuture();  // Return suggestions
    };

    // Register command with Fabric
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> 
            registerKeyDetectionCommand(dispatcher));
    }

    // Build the command tree
    private static void registerKeyDetectionCommand(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("keydetection")  // Root command
                .requires(source -> source.hasPermissionLevel(2))  // Requires OP level 2
                .then(argument("players", EntityArgumentType.entities())  // Player selector
                        .then(literal("add")  // Add subcommand
                                .then(literal("press")  // Press event type
                                        .then(argument("key", IntegerArgumentType.integer(0, 511))  // Key code (0-511)
                                                .then(literal("run")  // Function prefix
                                                        .then(argument("function", IdentifierArgumentType.identifier())  // Function name
                                                                .suggests(FUNCTION_SUGGESTIONS)  // Enable tab completion
                                                                .executes(context -> executeAdd(context, "press"))))))  // Execute add for press
                                .then(literal("release")  // Release event type (same structure as press)
                                        .then(argument("key", IntegerArgumentType.integer(0, 511))
                                                .then(literal("run")
                                                        .then(argument("function", IdentifierArgumentType.identifier())
                                                                .suggests(FUNCTION_SUGGESTIONS)
                                                                .executes(context -> executeAdd(context, "release"))))))
                                .then(literal("sustained")  // Sustained event type (new)
                                        .then(argument("key", IntegerArgumentType.integer(0, 511))
                                                .then(literal("run")
                                                        .then(argument("function", IdentifierArgumentType.identifier())
                                                                .suggests(FUNCTION_SUGGESTIONS)
                                                                .executes(context -> executeAdd(context, "sustained")))))))
                        .then(literal("remove")  // Remove subcommand
                                .then(literal("press")  // Remove press binding
                                        .then(argument("key", IntegerArgumentType.integer(0, 511))
                                                .executes(context -> executeRemove(context, "press"))))
                                .then(literal("release")  // Remove release binding
                                        .then(argument("key", IntegerArgumentType.integer(0, 511))
                                                .executes(context -> executeRemove(context, "release"))))
                                .then(literal("sustained")  // Remove sustained binding
                                        .then(argument("key", IntegerArgumentType.integer(0, 511))
                                                .executes(context -> executeRemove(context, "sustained"))))
                                .executes(KeyDetectionCommand::executeRemoveAll))  // Remove all bindings
                        .then(literal("list")  // List subcommand
                                .executes(KeyDetectionCommand::executeList))));  // Execute list
    }

    // Execute command to add a key binding
    private static int executeAdd(CommandContext<ServerCommandSource> context, String keyType) {
        try {
            // Get selected players from command argument
            Collection<? extends net.minecraft.entity.Entity> entities = EntityArgumentType.getEntities(context, "players");
            int keyCode = IntegerArgumentType.getInteger(context, "key");  // Get key code
            Identifier functionId = IdentifierArgumentType.getIdentifier(context, "function");  // Get function name

            int addedCount = 0;
            // Process each selected entity
            for (net.minecraft.entity.Entity entity : entities) {
                if (entity instanceof ServerPlayerEntity player) {
                    // Create new binding record
                    KeyBinding binding = new KeyBinding(player, keyCode, keyType, functionId);
                    KeyDetectionManager.addBinding(binding);  // Add to manager
                    addedCount++;
                }
            }

            // Send feedback to command source
            if (addedCount > 0) {
                final int finalCount = addedCount;
                context.getSource().sendFeedback(() ->
                        Text.literal("Added key detection for " + finalCount + " player(s) | Key: " + keyCode +
                                " | Type: " + keyType + " | Function: " + functionId), false);
            } else {
                context.getSource().sendError(Text.literal("No players found in selection"));
            }

            return addedCount;  // Return number of bindings added
        } catch (CommandSyntaxException e) {
            context.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // Execute command to remove a specific key binding
    private static int executeRemove(CommandContext<ServerCommandSource> context, String keyType) {
        try {
            Collection<? extends net.minecraft.entity.Entity> entities = EntityArgumentType.getEntities(context, "players");
            int keyCode = IntegerArgumentType.getInteger(context, "key");

            int removedCount = 0;
            for (net.minecraft.entity.Entity entity : entities) {
                if (entity instanceof ServerPlayerEntity player) {
                    if (KeyDetectionManager.removeBinding(player, keyCode, keyType)) {
                        removedCount++;
                    }
                }
            }

            if (removedCount > 0) {
                final int finalCount = removedCount;
                context.getSource().sendFeedback(() ->
                        Text.literal("Removed key detection for " + finalCount + " player(s) | Key: " + keyCode +
                                " | Type: " + keyType), false);
            } else {
                context.getSource().sendError(Text.literal("No bindings found for that key and type"));
            }

            return removedCount;
        } catch (CommandSyntaxException e) {
            context.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // Execute command to remove all bindings for selected players
    private static int executeRemoveAll(CommandContext<ServerCommandSource> context) {
        try {
            Collection<? extends net.minecraft.entity.Entity> entities = EntityArgumentType.getEntities(context, "players");

            int totalRemoved = 0;
            for (net.minecraft.entity.Entity entity : entities) {
                if (entity instanceof ServerPlayerEntity player) {
                    List<KeyBinding> bindings = KeyDetectionManager.getBindings(player);
                    totalRemoved += bindings.size();  // Count bindings
                    KeyDetectionManager.removeAllBindings(player);  // Remove all
                }
            }

            final int finalCount = totalRemoved;
            context.getSource().sendFeedback(() ->
                    Text.literal("Cleared " + finalCount + " key detection(s) for selected player(s)"), false);

            return totalRemoved;
        } catch (CommandSyntaxException e) {
            context.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // Execute command to list all bindings for selected players
    private static int executeList(CommandContext<ServerCommandSource> context) {
        try {
            Collection<? extends net.minecraft.entity.Entity> entities = EntityArgumentType.getEntities(context, "players");

            for (net.minecraft.entity.Entity entity : entities) {
                if (entity instanceof ServerPlayerEntity player) {
                    List<KeyBinding> bindings = KeyDetectionManager.getBindings(player);

                    if (bindings.isEmpty()) {
                        context.getSource().sendFeedback(() ->
                                Text.literal("No key detections found for " + player.getName().getString()), false);
                    } else {
                        context.getSource().sendFeedback(() ->
                                Text.literal("Key detections for " + player.getName().getString() + ":"), false);

                        // List each binding
                        for (KeyBinding binding : bindings) {
                            context.getSource().sendFeedback(() ->
                                    Text.literal("- Key: " + binding.keyCode() +
                                            " | Type: " + binding.keyType() +
                                            " | Function: " + binding.functionId()), false);
                        }
                    }
                }
            }

            return 1;  // Success
        } catch (CommandSyntaxException e) {
            context.getSource().sendError(Text.literal("Error: " + e.getMessage()));
            return 0;
        }
    }
}
