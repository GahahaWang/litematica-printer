package me.aleksilassila.litematica.printer.command;

import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;

import me.aleksilassila.litematica.printer.schematic.LitematicaSchematicManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.arguments.blocks.BlockInput;
import net.minecraft.commands.arguments.blocks.BlockPredicateArgument;
import net.minecraft.commands.arguments.blocks.BlockStateArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.WorldCoordinates;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.pattern.BlockInWorld;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

/**
 * /litematicaprinter setblock and /litematicaprinter fill - build an in-memory
 * schematic (see {@link me.aleksilassila.litematica.printer.schematic}) the
 * printer can then read as a placement target, without needing a real
 * .litematic file.
 * <p>
 * Reuses vanilla's own SetBlockCommand/FillCommand argument types
 * ({@link BlockPosArgument}, {@link BlockStateArgument}, {@link BlockPredicateArgument})
 * instead of hand-parsing, so ~ relative coordinates, block state properties,
 * block entity NBT, and #tag/predicate filters all parse exactly like the real
 * commands. Modes mirror vanilla exactly too - there's no "keep" mode because
 * vanilla no longer has one either (SetBlockCommand.Mode/FillCommand.Mode only
 * have REPLACE/DESTROY and REPLACE/OUTLINE/HOLLOW/DESTROY).
 * <p>
 * ^ local coordinates parse fine (vanilla's grammar allows them) but are
 * rejected at execution time: resolving them needs a real CommandSourceStack,
 * which client commands don't have (FabricClientCommandSource only extends
 * SharedSuggestionProvider, not CommandSourceStack).
 */
public class PrinterCommand {
    private static final SimpleCommandExceptionType LOCAL_COORDINATES_NOT_SUPPORTED =
            new SimpleCommandExceptionType(Component.literal("^ local coordinates are not supported here"));

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) ->
                dispatcher.register(literal("litematicaprinter")
                        .then(literal("setblock").then(setBlockArgs(buildContext)))
                        .then(literal("fill").then(fillArgs(buildContext)))
                ));
    }

    private static ArgumentBuilder<FabricClientCommandSource, ?> setBlockArgs(CommandBuildContext buildContext) {
        return argument("pos", BlockPosArgument.blockPos())
                .then(argument("block", BlockStateArgument.block(buildContext))
                        .executes(PrinterCommand::runSetBlock)
                        .then(literal("destroy").executes(PrinterCommand::runSetBlock))
                        .then(literal("replace").executes(PrinterCommand::runSetBlock)));
    }

    private static ArgumentBuilder<FabricClientCommandSource, ?> fillArgs(CommandBuildContext buildContext) {
        return argument("from", BlockPosArgument.blockPos())
                .then(argument("to", BlockPosArgument.blockPos())
                        .then(argument("block", BlockStateArgument.block(buildContext))
                                .executes(c -> runFill(c, "replace", null))
                                .then(literal("destroy").executes(c -> runFill(c, "destroy", null)))
                                .then(literal("hollow").executes(c -> runFill(c, "hollow", null)))
                                .then(literal("outline").executes(c -> runFill(c, "outline", null)))
                                .then(literal("replace")
                                        .executes(c -> runFill(c, "replace", null))
                                        .then(argument("filter", BlockPredicateArgument.blockPredicate(buildContext))
                                                .executes(c -> runFill(c, "replace", "filter"))))));
    }

    private static int runSetBlock(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        BlockPos pos = resolveBlockPos(ctx, "pos");
        BlockState state = ctx.getArgument("block", BlockInput.class).getState();

        LitematicaSchematicManager.setBlock(pos, state);
        ctx.getSource().sendFeedback(Component.literal("Set the blueprint block at " + pos.toShortString()));
        return 1;
    }

    /** @param filterArgName name of the "filter" argument node if this branch has one, else null */
    private static int runFill(CommandContext<FabricClientCommandSource> ctx, String mode, String filterArgName) throws CommandSyntaxException {
        Level world = ctx.getSource().getPlayer().level();
        BlockPos from = resolveBlockPos(ctx, "from");
        BlockPos to = resolveBlockPos(ctx, "to");
        BlockState state = ctx.getArgument("block", BlockInput.class).getState();

        LitematicaSchematicManager.FillMode fillMode = switch (mode) {
            case "hollow" -> LitematicaSchematicManager.FillMode.HOLLOW;
            case "outline" -> LitematicaSchematicManager.FillMode.OUTLINE;
            case "destroy" -> LitematicaSchematicManager.FillMode.DESTROY;
            default -> LitematicaSchematicManager.FillMode.REPLACE;
        };

        Predicate<BlockPos> filter = null;
        if (filterArgName != null) {
            Predicate<BlockInWorld> predicate = ctx.getArgument(filterArgName, BlockPredicateArgument.Result.class);
            filter = testPos -> predicate.test(new BlockInWorld(world, testPos, true));
        }

        int count = LitematicaSchematicManager.fill(from, to, state, fillMode, filter);

        ctx.getSource().sendFeedback(Component.literal("Set " + count + " block(s) in the blueprint"));
        return count;
    }

    private static BlockPos resolveBlockPos(CommandContext<FabricClientCommandSource> ctx, String name) throws CommandSyntaxException {
        Coordinates coordinates = ctx.getArgument(name, Coordinates.class);
        if (!(coordinates instanceof WorldCoordinates worldCoordinates)) {
            throw LOCAL_COORDINATES_NOT_SUPPORTED.create();
        }

        Vec3 base = ctx.getSource().getPosition();
        double x = worldCoordinates.x().get(base.x);
        double y = worldCoordinates.y().get(base.y);
        double z = worldCoordinates.z().get(base.z);
        return BlockPos.containing(x, y, z);
    }
}
