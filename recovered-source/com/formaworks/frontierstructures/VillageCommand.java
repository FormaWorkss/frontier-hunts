package com.formaworks.frontierstructures;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * [villages] /frontierstructures villages place &lt;outpost|lakeside|crowsnest|junction&gt;: plans that village layout
 * around you (the spot itself or the nearest suitable one within ~48 blocks) and builds it - for testing and for
 * builders. World generation picks the layout from the land; vanilla
 * {@code /place structure frontierstructures:village_<layout>} only builds where the land would have chosen that layout.
 */
public final class VillageCommand {
    private VillageCommand() {}

    public static void register(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("frontierstructures").requires(s -> s.hasPermission(2))
            .then(Commands.literal("villages").then(Commands.literal("place")
                .then(Commands.argument("layout", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(VillagePlan.TYPES, b))
                    .executes(c -> place(c.getSource(), StringArgumentType.getString(c, "layout")))))));
    }

    static int place(CommandSourceStack src, String type) {
        if (!java.util.Arrays.asList(VillagePlan.TYPES).contains(type)) {
            src.sendFailure(Component.literal("Unknown village layout " + type + " (outpost, lakeside, crowsnest, junction)."));
            return 0;
        }
        ServerLevel level = src.getLevel();
        var holder = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(ResourceKey.create(Registries.STRUCTURE, FrontierStructures.id("village_" + type))).orElse(null);
        if (holder == null) {src.sendFailure(Component.literal("The village structures are missing from this world's data packs.")); return 0;}
        var gen = level.getChunkSource().getGenerator();
        var h = new Heights(gen, level, level.getChunkSource().randomState());
        var tm = level.getStructureManager();
        BlockPos at = BlockPos.containing(src.getPosition());
        long seed = level.getSeed() ^ at.asLong() * 31L;
        VillagePlan plan = null;
        // the spot itself, then rings outward
        outer:
        for (int r = 0; r <= 48; r += 12) {
            int n = r == 0 ? 1 : 8;
            for (int i = 0; i < n; i++) {
                double a = i * Math.PI * 2 / n;
                int x = at.getX() + (int)Math.round(Math.cos(a) * r), z = at.getZ() + (int)Math.round(Math.sin(a) * r);
                plan = VillagePlan.plan(type, VillagePlan.templates(tm), h, seed + r * 8L + i, x, z, gen.getSeaLevel());
                if (plan != null) break outer;
            }
        }
        if (plan == null) {
            src.sendFailure(Component.literal("No ground for a " + type + " village here (it needs " + switch (type) {
                case "lakeside" -> "dry, gentle ground 30-60 blocks from a lake or river";
                case "crowsnest" -> "a hillside with a rise on one side";
                case "junction" -> "a wide, fairly level flat (about 130 x 80 blocks)";
                default -> "dry, rolling ground about 120 blocks across";
            } + "). Try /locate structure frontierstructures:village_" + type + "."));
            return 0;
        }
        var b = new StructurePiecesBuilder();
        Village.addPieces(b, plan, tm);
        var start = new StructureStart(holder.value(), new ChunkPos(new BlockPos(plan.cx, 0, plan.cz)), 0, b.build());
        BoundingBox bb = start.getBoundingBox();
        ChunkPos a = new ChunkPos(SectionPos.blockToSectionCoord(bb.minX()), SectionPos.blockToSectionCoord(bb.minZ()));
        ChunkPos z = new ChunkPos(SectionPos.blockToSectionCoord(bb.maxX()), SectionPos.blockToSectionCoord(bb.maxZ()));
        ChunkPos.rangeClosed(a, z).forEach(cp -> {
            level.getChunk(cp.x, cp.z); // generated and loaded
            start.placeInChunk(level, level.structureManager(), gen, level.getRandom(),
                new BoundingBox(cp.getMinBlockX(), level.getMinBuildHeight(), cp.getMinBlockZ(), cp.getMaxBlockX(), level.getMaxBuildHeight(), cp.getMaxBlockZ()), cp);
        });
        final VillagePlan p = plan;
        src.sendSuccess(() -> Component.literal("Built a " + type + " village (" + p.lots.size() + " buildings) around ")
            .append(Component.literal("[" + p.cx + ", " + p.greenY + ", " + p.cz + "]").withStyle(s -> s.withColor(net.minecraft.ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tp @s " + p.cx + " " + (p.greenY + 2) + " " + p.cz)))), true);
        return p.lots.size();
    }
}
