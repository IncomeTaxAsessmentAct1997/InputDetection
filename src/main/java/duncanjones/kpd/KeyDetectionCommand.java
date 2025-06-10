package duncanjones.kpd;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.List;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

public class KeyDetectionCommand {

	private static final SuggestionProvider<ServerCommandSource> FUNCTION_SUGGESTIONS = (context, builder) -> {
		Iterable<Identifier> functions = context.getSource().getServer().getCommandFunctionManager().getAllFunctions();
		for (Identifier function : functions) {
			builder.suggest(function.toString());
		}
		return builder.buildFuture();
	};

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerKeyDetectionCommand(dispatcher));
	}

	private static void registerKeyDetectionCommand(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(literal("keydetection")
				.requires(source -> source.hasPermissionLevel(2))
				.then(argument("players", EntityArgumentType.entities())
						.then(literal("add")
								.then(literal("press")
										.then(argument("key", IntegerArgumentType.integer(0, 511))
												.then(literal("run")
														.then(argument("function", IdentifierArgumentType.identifier())
																.suggests(FUNCTION_SUGGESTIONS)
																.executes(context -> executeAdd(context, "press"))))))
								.then(literal("release")
										.then(argument("key", IntegerArgumentType.integer(0, 511))
												.then(literal("run")
														.then(argument("function", IdentifierArgumentType.identifier())
																.suggests(FUNCTION_SUGGESTIONS)
																.executes(context -> executeAdd(context, "release"))))))
								.then(literal("hold")
										.then(argument("key", IntegerArgumentType.integer(0, 511))
												.then(literal("run")
														.then(argument("function", IdentifierArgumentType.identifier())
																.suggests(FUNCTION_SUGGESTIONS)
																.executes(context -> executeAdd(context, "hold")))))))
						.then(literal("remove")
								.then(literal("press")
										.then(argument("key", IntegerArgumentType.integer(0, 511))
												.executes(context -> executeRemove(context, "press"))))
								.then(literal("release")
										.then(argument("key", IntegerArgumentType.integer(0, 511))
												.executes(context -> executeRemove(context, "release"))))
								.then(literal("hold")
										.then(argument("key", IntegerArgumentType.integer(0, 511))
												.executes(context -> executeRemove(context, "hold"))))
								.executes(KeyDetectionCommand::executeRemoveAll))
						.then(literal("list")
								.executes(KeyDetectionCommand::executeList))));
	}

	private static int executeAdd(CommandContext<ServerCommandSource> context, String keyType) {
		try {
			Collection<? extends net.minecraft.entity.Entity> entities = EntityArgumentType.getEntities(context, "players");
			int keyCode = IntegerArgumentType.getInteger(context, "key");
			Identifier functionId = IdentifierArgumentType.getIdentifier(context, "function");

			int addedCount = 0;
			for (net.minecraft.entity.Entity entity : entities) {
				if (entity instanceof ServerPlayerEntity player) {
					KeyBinding binding = new KeyBinding(player, keyCode, keyType, functionId);
					KeyDetectionManager.addBinding(binding);
					addedCount++;
				}
			}

			if (addedCount > 0) {
				final int finalCount = addedCount;
				context.getSource().sendFeedback(() ->
						Text.literal("Added key detection for " + finalCount + " player(s) | Key: " + keyCode +
								" | Type: " + keyType + " | Function: " + functionId), false);
			} else {
				context.getSource().sendError(Text.literal("No players found in selection"));
			}

			return addedCount;
		} catch (CommandSyntaxException e) {
			context.getSource().sendError(Text.literal("Error: " + e.getMessage()));
			return 0;
		}
	}

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

	private static int executeRemoveAll(CommandContext<ServerCommandSource> context) {
		try {
			Collection<? extends net.minecraft.entity.Entity> entities = EntityArgumentType.getEntities(context, "players");

			int totalRemoved = 0;
			for (net.minecraft.entity.Entity entity : entities) {
				if (entity instanceof ServerPlayerEntity player) {
					List<KeyBinding> bindings = KeyDetectionManager.getBindings(player);
					totalRemoved += bindings.size();
					KeyDetectionManager.removeAllBindings(player);
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

						for (KeyBinding binding : bindings) {
							context.getSource().sendFeedback(() ->
									Text.literal("- Key: " + binding.keyCode() +
											" | Type: " + binding.keyType() +
											" | Function: " + binding.functionId()), false);
						}
					}
				}
			}

			return 1;
		} catch (CommandSyntaxException e) {
			context.getSource().sendError(Text.literal("Error: " + e.getMessage()));
			return 0;
		}
	}
}