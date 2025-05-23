package dev.muon.dynamic_difficulty.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.muon.dynamic_difficulty.DynamicDifficulty;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(modid = DynamicDifficulty.MODID)
public class ModCommands {
  @SubscribeEvent
  public static void onRegisterCommands(RegisterCommandsEvent event) {
    LiteralArgumentBuilder<CommandSourceStack> addGlobalLevelCommand =
        Commands.literal("dynamic_difficulty")
            .then(
                Commands.literal("level")
                    .then(
                        Commands.literal("add")
                            .then(
                                Commands.argument("value", IntegerArgumentType.integer())
                                    .executes(ModCommands::executeAddLevelCommand))))
            .requires(ModCommands::hasPermission);
    event.getDispatcher().register(addGlobalLevelCommand);
    LiteralArgumentBuilder<CommandSourceStack> getGlobalLevelCommand =
        Commands.literal("dynamic_difficulty")
            .then(
                Commands.literal("level")
                    .then(
                        Commands.literal("get")
                            .executes(ModCommands::executeGetLevelCommand)))
            .requires(ModCommands::hasPermission);
    event.getDispatcher().register(getGlobalLevelCommand);
  }

  private static int executeAddLevelCommand(CommandContext<CommandSourceStack> ctx) {
    MinecraftServer server = ctx.getSource().getServer();
    // todo: this should target entities
    return 1;
  }

  private static int executeGetLevelCommand(CommandContext<CommandSourceStack> ctx) {
    MinecraftServer server = ctx.getSource().getServer();


    return 1;
  }

  private static boolean hasPermission(CommandSourceStack commandSourceStack) {
    return commandSourceStack.hasPermission(2);
  }
}
