package duncanjones.kpd;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import java.util.Collection;
import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class KeyDetectionCommand {

	private static final SuggestionProvider<CommandSourceStack> FUNCTION_SUGGESTIONS = (context, builder) -> {
		Iterable<Identifier> functions = context.getSource().getServer().getFunctions().getFunctionNames();
		for (Identifier function : functions) {
			builder.suggest(function.toString());
		}
		return builder.buildFuture();
	};

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> registerKeyDetectionCommand(dispatcher));
	}

	private static void registerKeyDetectionCommand(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("detection")
			.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(argument("players", EntityArgument.entities())
				.then(literal("add")
						.then(literal("press")
								.then(argument("key", IntegerArgumentType.integer(0, 511))
										.then(literal("run")
												.then(argument("function", IdentifierArgument.id())
														.suggests(FUNCTION_SUGGESTIONS)
														.executes(context -> executeAdd(context, "press"))
												)
										)
								)
						)
						.then(literal("release")
								.then(argument("key", IntegerArgumentType.integer(0, 511))
										.then(literal("run")
												.then(argument("function", IdentifierArgument.id())
														.suggests(FUNCTION_SUGGESTIONS)
														.executes(context -> executeAdd(context, "release"))
												)
										)
								)
						)
				)
				.then(literal("remove")
						.then(literal("press")
								.then(argument("key", IntegerArgumentType.integer(0, 511))
										.executes(context -> executeRemove(context, "press"))
								)
						)
						.then(literal("release")
								.then(argument("key", IntegerArgumentType.integer(0, 511))
										.executes(context -> executeRemove(context, "release"))
								)
						)
						.executes(KeyDetectionCommand::executeRemoveAll)
				)
				.then(literal("list")
						.executes(KeyDetectionCommand::executeList)
				)
			)
		);
	}

	private static int executeAdd(CommandContext<CommandSourceStack> context, String keyType) {
		try {
			Collection<? extends net.minecraft.world.entity.Entity> entities = EntityArgument.getEntities(context, "players");
			int keyCode = IntegerArgumentType.getInteger(context, "key");
			Identifier functionId = IdentifierArgument.getId(context, "function");

			int addedCount = 0;
			for (net.minecraft.world.entity.Entity entity : entities) {
				if (entity instanceof ServerPlayer player) {
					KeyBinding binding = new KeyBinding(player, keyCode, keyType, functionId);
					KeyDetectionManager.addBinding(binding);
					addedCount++;
				}
			}

			if (addedCount > 0) {
				final int finalCount = addedCount;
				final int finalKeyCode = keyCode;
				final String finalKeyType = keyType;
				final String finalFunctionId = functionId.toString(); // 转换为字符串
				context.getSource().sendSuccess(() ->
						Component.translatable("commands.press_detection.add.success",
								finalCount, finalKeyCode, finalKeyType, finalFunctionId), false);
			} else {
				context.getSource().sendFailure(Component.translatable("commands.press_detection.add.no_players"));
			}

			return addedCount;
		} catch (CommandSyntaxException e) {
			context.getSource().sendFailure(Component.translatable("commands.press_detection.error.generic", e.getMessage()));
			return 0;
		}
	}

	private static int executeRemove(CommandContext<CommandSourceStack> context, String keyType) {
		try {
			Collection<? extends net.minecraft.world.entity.Entity> entities = EntityArgument.getEntities(context, "players");
			int keyCode = IntegerArgumentType.getInteger(context, "key");

			int removedCount = 0;
			for (net.minecraft.world.entity.Entity entity : entities) {
				if (entity instanceof ServerPlayer player) {
					if (KeyDetectionManager.removeBinding(player, keyCode, keyType)) {
						removedCount++;
					}
				}
			}

			if (removedCount > 0) {
				final int finalCount = removedCount;
				final int finalKeyCode = keyCode;
				final String finalKeyType = keyType;
				context.getSource().sendSuccess(() ->
						Component.translatable("commands.press_detection.remove.success",
								finalCount, finalKeyCode, finalKeyType), false);
			} else {
				context.getSource().sendFailure(Component.translatable("commands.press_detection.remove.not_found"));
			}

			return removedCount;
		} catch (CommandSyntaxException e) {
			context.getSource().sendFailure(Component.translatable("commands.press_detection.error.generic", e.getMessage()));
			return 0;
		}
	}

	private static int executeRemoveAll(CommandContext<CommandSourceStack> context) {
		try {
			Collection<? extends net.minecraft.world.entity.Entity> entities = EntityArgument.getEntities(context, "players");

			int totalRemoved = 0;
			for (net.minecraft.world.entity.Entity entity : entities) {
				if (entity instanceof ServerPlayer player) {
					List<KeyBinding> bindings = KeyDetectionManager.getBindings(player);
					totalRemoved += bindings.size();
					KeyDetectionManager.removeAllBindings(player);
				}
			}

			final int finalCount = totalRemoved;
			context.getSource().sendSuccess(() ->
					Component.translatable("commands.press_detection.remove_all.success", finalCount), false);

			return totalRemoved;
		} catch (CommandSyntaxException e) {
			context.getSource().sendFailure(Component.translatable("commands.press_detection.error.generic", e.getMessage()));
			return 0;
		}
	}

	private static int executeList(CommandContext<CommandSourceStack> context) {
		try {
			Collection<? extends net.minecraft.world.entity.Entity> entities = EntityArgument.getEntities(context, "players");

			for (net.minecraft.world.entity.Entity entity : entities) {
				if (entity instanceof ServerPlayer player) {
					List<KeyBinding> bindings = KeyDetectionManager.getBindings(player);

					if (bindings.isEmpty()) {
						context.getSource().sendSuccess(() ->
								Component.translatable("commands.press_detection.list.no_bindings",
										player.getName().getString()), false);
					} else {
						context.getSource().sendSuccess(() ->
								Component.translatable("commands.press_detection.list.header",
										player.getName().getString()), false);

						for (KeyBinding binding : bindings) {
							final int finalKeyCode = binding.keyCode();
							final String finalKeyType = binding.keyType();
							final String finalFunctionId = binding.functionId().toString(); // 转换为字符串
							context.getSource().sendSuccess(() ->
									Component.translatable("commands.press_detection.list.entry",
											finalKeyCode, finalKeyType, finalFunctionId), false);
						}
					}
				}
			}

			return 1;
		} catch (CommandSyntaxException e) {
			context.getSource().sendFailure(Component.translatable("commands.press_detection.error.generic", e.getMessage()));
			return 0;
		}
	}
}
