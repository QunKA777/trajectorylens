package dev.soityy.trajectorylens;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.soityy.trajectorylens.network.TargetPayload;
import dev.soityy.trajectorylens.physics.ItemPhysicsSimulator;
import dev.soityy.trajectorylens.physics.TrajectoryPath;
import dev.soityy.trajectorylens.util.PathTools;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class TrajectoryLensMod implements ModInitializer {
    public static final String MOD_ID = "trajectorylens";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[TrajectoryLens] common init (safe on dedicated servers)");
        PayloadTypeRegistry.clientboundPlay().register(TargetPayload.TYPE, TargetPayload.CODEC);

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var root = Commands.literal("trajectorylens");

            // /trajectorylens simulate <x y z vx vy vz> — engine check, no client needed
            root.then(
                Commands.literal("simulate")
                    .then(Commands.argument("x", DoubleArgumentType.doubleArg())
                        .then(Commands.argument("y", DoubleArgumentType.doubleArg())
                            .then(Commands.argument("z", DoubleArgumentType.doubleArg())
                                .then(Commands.argument("vx", DoubleArgumentType.doubleArg())
                                    .then(Commands.argument("vy", DoubleArgumentType.doubleArg())
                                        .then(Commands.argument("vz", DoubleArgumentType.doubleArg())
                                            .executes(ctx -> runSimulate(ctx.getSource(),
                                                DoubleArgumentType.getDouble(ctx, "x"),
                                                DoubleArgumentType.getDouble(ctx, "y"),
                                                DoubleArgumentType.getDouble(ctx, "z"),
                                                DoubleArgumentType.getDouble(ctx, "vx"),
                                                DoubleArgumentType.getDouble(ctx, "vy"),
                                                DoubleArgumentType.getDouble(ctx, "vz"))))))))));

            // Server-side twin of the client commands: guaranteed to exist in the
            // command tree (tab-complete) and forwarded to the client via packet.
            root.then(
                Commands.literal("target")
                    .then(Commands.literal("clear")
                        .executes(ctx -> sendToPlayer(ctx.getSource(), new TargetPayload("clear"), "")))
                    .then(Commands.argument("item", StringArgumentType.string())
                        .suggests(TrajectoryLensMod::suggestItems)
                        .executes(ctx -> setTargetServer(ctx.getSource(), StringArgumentType.getString(ctx, "item")))));
            root.then(
                Commands.literal("color")
                    .then(Commands.argument("item", StringArgumentType.string())
                        .suggests(TrajectoryLensMod::suggestItems)
                        .then(Commands.argument("color", StringArgumentType.string())
                            .executes(ctx -> setColorServer(ctx.getSource(),
                                StringArgumentType.getString(ctx, "item"),
                                StringArgumentType.getString(ctx, "color"))))));
            root.then(
                Commands.literal("colors")
                    .executes(ctx -> sendToPlayer(ctx.getSource(), new TargetPayload("list"), "")));
            root.then(Commands.literal("lost")
                .executes(ctx -> sendToPlayer(ctx.getSource(), new TargetPayload("lost"), ""))
                .then(Commands.literal("clear")
                    .executes(ctx -> sendToPlayer(ctx.getSource(), new TargetPayload("lost:clear"), ""))));
            root.then(
                Commands.literal("census")
                    .executes(ctx -> sendToPlayer(ctx.getSource(), new TargetPayload("census"), "")));
            root.then(
                Commands.literal("gui")
                    .executes(ctx -> sendToPlayer(ctx.getSource(), new TargetPayload("gui"), "")));
            for (String sw : new String[]{"aim", "threat", "flowcount", "losttrack", "jam", "despawn", "falling"}) {
                var node = Commands.literal(sw);
                for (String op : new String[]{"on", "off", "toggle"}) {
                    node.then(Commands.literal(op)
                        .executes(ctx -> sendToPlayer(ctx.getSource(),
                            new TargetPayload((sw.equals("aim") ? "aim:" + op
                                : sw.equals("threat") ? "threat:" + op
                                : sw.equals("losttrack") ? "losttrack:" + op
                                : sw.equals("jam") ? "jam:" + op
                                : sw.equals("despawn") ? "despawn:" + op
                                : sw.equals("falling") ? "falling:" + op : "flowcount:" + op)), "")));
                }
                root.then(node);
            }
            root.then(Commands.literal("export")
                .executes(ctx -> sendToPlayer(ctx.getSource(), new TargetPayload("export"), "")));
            for (String sw : new String[]{"chaindepth", "chainhorizon"}) {
                root.then(Commands.literal(sw)
                    .executes(ctx -> sendToPlayer(ctx.getSource(), new TargetPayload(sw + ":"), ""))
                    .then(Commands.argument("value", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 60))
                        .executes(ctx -> sendToPlayer(ctx.getSource(),
                            new TargetPayload(sw + ":" + com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "value")), ""))));
            }
            for (String sw : new String[]{"losttime", "glowtime", "jamtime"}) {
                root.then(Commands.literal(sw)
                    .executes(ctx -> sendToPlayer(ctx.getSource(), new TargetPayload(sw + ":"), ""))
                    .then(Commands.argument("seconds", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1, 300))
                        .executes(ctx -> sendToPlayer(ctx.getSource(),
                            new TargetPayload(sw + ":" + com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "seconds")), ""))));
            }
            root.then(Commands.literal("counter")
                .then(Commands.literal("add")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> sendToPlayer(ctx.getSource(),
                            new TargetPayload("counter:add:" + StringArgumentType.getString(ctx, "name")), ""))))
                .then(Commands.literal("remove")
                    .then(Commands.argument("name", StringArgumentType.word())
                        .executes(ctx -> sendToPlayer(ctx.getSource(),
                            new TargetPayload("counter:remove:" + StringArgumentType.getString(ctx, "name")), ""))))
                .then(Commands.literal("clear")
                    .executes(ctx -> sendToPlayer(ctx.getSource(), new TargetPayload("counter:clear"), "")))
                .then(Commands.literal("list")
                    .executes(ctx -> sendToPlayer(ctx.getSource(), new TargetPayload("counter:list"), ""))));
            for (String sw : new String[]{"projectiles", "tnt", "chain"}) {
                var node = Commands.literal(sw);
                for (String op : new String[]{"on", "off", "toggle"}) {
                    node.then(Commands.literal(op)
                        .executes(ctx -> sendToPlayer(ctx.getSource(),
                            new TargetPayload(sw.equals("projectiles") ? "proj:" + op
                                : sw.equals("tnt") ? "tnt:" + op : "chain:" + op), "")));
                }
                root.then(node);
            }
            root.then(
                Commands.literal("range")
                    .then(Commands.literal("add")
                        .then(Commands.argument("type", StringArgumentType.string())
                            .suggests(TrajectoryLensMod::suggestEntityTypes)
                            .executes(ctx -> rangeTypeOp(ctx.getSource(), StringArgumentType.getString(ctx, "type"), "rangeadd:"))))
                    .then(Commands.literal("clear")
                        .executes(ctx -> sendToPlayer(ctx.getSource(), new TargetPayload("rangeclear"), "")))
                    .then(Commands.literal("list")
                        .executes(ctx -> sendToPlayer(ctx.getSource(), new TargetPayload("rangelist"), "")))
                    .then(Commands.literal("toggle")
                        .executes(ctx -> sendToPlayer(ctx.getSource(), new TargetPayload("rangetoggle"), "")))
                    .then(Commands.literal("time")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, 60))
                            .executes(ctx -> {
                                sendToPlayer(ctx.getSource(), new TargetPayload("rangetime:"
                                    + IntegerArgumentType.getInteger(ctx, "seconds")), "");
                                return 1;
                            })))
                    .then(Commands.literal("color")
                        .then(Commands.argument("type", StringArgumentType.string())
                            .suggests(TrajectoryLensMod::suggestEntityTypes)
                            .then(Commands.argument("color", StringArgumentType.string())
                                .executes(ctx -> rangeColorOp(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "type"),
                                    StringArgumentType.getString(ctx, "color")))))));
            root.then(
                Commands.literal("toggle")
                    .executes(ctx -> sendToPlayer(ctx.getSource(), new TargetPayload("toggle"), "")));

            dispatcher.register(root);
            // short alias: /tl ... redirects to the full command tree
            var alias = Commands.literal("tl");
            alias.redirect(dispatcher.getRoot().getChild("trajectorylens"));
            dispatcher.register(alias);
        });
    }

    /**
     * Vanilla-style Tab completion for item ids: typing a prefix (e.g. "c")
     * lists every item whose id starts with it. "minecraft:" entries are also
     * offered without the namespace so names are easy to type.
     */
    public static CompletableFuture<Suggestions> suggestItems(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        try {
            Registry<Item> items = ctx.getSource().getServer().registryAccess().lookup(Registries.ITEM).orElse(null);
            if (items == null) {
                return builder.buildFuture();
            }
            String typed = builder.getRemainingLowerCase();
            Set<String> seen = new HashSet<>();
            for (Identifier key : items.keySet()) {
                if ("minecraft".equals(key.getNamespace())) {
                    addSuggestion(builder, seen, key.getPath(), typed);
                }
                addSuggestion(builder, seen, key.toString(), typed);
            }
        } catch (Exception ignored) {
            // suggestions are best-effort
        }
        return builder.buildFuture();
    }

    private static void addSuggestion(SuggestionsBuilder builder, Set<String> seen, String candidate, String typed) {
        if (seen.add(candidate) && candidate.toLowerCase(Locale.ROOT).startsWith(typed)) {
            builder.suggest(candidate);
        }
    }

    private static int setTargetServer(CommandSourceStack src, String raw) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("[TrajectoryLens] this command needs a player client"));
            return 0;
        }
        String id = raw.trim();
        String ns;
        String path;
        int colon = id.indexOf(':');
        if (colon < 0) {
            ns = "minecraft";
            path = id;
        } else {
            ns = id.substring(0, colon);
            path = id.substring(colon + 1);
        }
        Identifier parsed;
        try {
            parsed = Identifier.fromNamespaceAndPath(ns, path);
        } catch (Exception ex) {
            src.sendFailure(Component.literal("[TrajectoryLens] invalid item id: " + id));
            return 0;
        }
        Registry<net.minecraft.world.item.Item> items =
                        src.getServer().registryAccess().lookup(Registries.ITEM).orElseThrow();
        if (!items.containsKey(parsed)) {
            src.sendFailure(Component.literal("[TrajectoryLens] unknown item: " + id));
            return 0;
        }
        ServerPlayNetworking.send(player, new TargetPayload("target:" + parsed));
        return 1;
    }

    private static int setColorServer(CommandSourceStack src, String itemRaw, String colorRaw) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("[TrajectoryLens] this command needs a player client"));
            return 0;
        }
        String spec = colorRaw.trim();
        if (!spec.equalsIgnoreCase("auto") && PathTools.parseColor(spec) == null) {
            src.sendFailure(Component.literal("[TrajectoryLens] bad color '" + spec
                + "' — use RRGGBB or AARRGGBB, or 'auto'"));
            return 0;
        }
        String id = itemRaw.trim();
        String ns;
        String path;
        int colon = id.indexOf(':');
        if (colon < 0) {
            ns = "minecraft";
            path = id;
        } else {
            ns = id.substring(0, colon);
            path = id.substring(colon + 1);
        }
        Identifier parsed;
        try {
            parsed = Identifier.fromNamespaceAndPath(ns, path);
        } catch (Exception ex) {
            src.sendFailure(Component.literal("[TrajectoryLens] invalid item id: " + itemRaw));
            return 0;
        }
        Registry<net.minecraft.world.item.Item> items =
            src.getServer().registryAccess().lookup(Registries.ITEM).orElseThrow();
        if (!items.containsKey(parsed)) {
            src.sendFailure(Component.literal("[TrajectoryLens] unknown item: " + itemRaw));
            return 0;
        }
        ServerPlayNetworking.send(player, new TargetPayload("color:" + parsed + ":" + spec));
        return 1;
    }

    private static int rangeTypeOp(CommandSourceStack src, String raw, String opPrefix) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("[TrajectoryLens] this command needs a player client"));
            return 0;
        }
        Identifier parsed = resolveIdentifier(raw);
        if (parsed == null) {
            src.sendFailure(Component.literal("[TrajectoryLens] invalid id: " + raw));
            return 0;
        }
        Registry<EntityType<?>> types =
            src.getServer().registryAccess().lookup(Registries.ENTITY_TYPE).orElseThrow();
        if (!types.containsKey(parsed)) {
            src.sendFailure(Component.literal("[TrajectoryLens] unknown entity type: " + raw));
            return 0;
        }
        ServerPlayNetworking.send(player, new TargetPayload(opPrefix + parsed));
        return 1;
    }

    private static int rangeColorOp(CommandSourceStack src, String typeRaw, String colorRaw) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("[TrajectoryLens] this command needs a player client"));
            return 0;
        }
        String spec = colorRaw.trim();
        if (!spec.equalsIgnoreCase("auto") && PathTools.parseColor(spec) == null) {
            src.sendFailure(Component.literal("[TrajectoryLens] bad color '" + spec
                + "' — use RRGGBB or AARRGGBB, or 'auto'"));
            return 0;
        }
        Identifier parsed = resolveIdentifier(typeRaw);
        if (parsed == null) {
            src.sendFailure(Component.literal("[TrajectoryLens] invalid id: " + typeRaw));
            return 0;
        }
        Registry<EntityType<?>> types =
            src.getServer().registryAccess().lookup(Registries.ENTITY_TYPE).orElseThrow();
        if (!types.containsKey(parsed)) {
            src.sendFailure(Component.literal("[TrajectoryLens] unknown entity type: " + typeRaw));
            return 0;
        }
        ServerPlayNetworking.send(player, new TargetPayload("rangecolor:" + parsed + ":" + spec));
        return 1;
    }

    private static @org.jspecify.annotations.Nullable Identifier resolveIdentifier(String raw) {
        String id = raw.trim();
        String ns;
        String path;
        int colon = id.indexOf(':');
        if (colon < 0) {
            ns = "minecraft";
            path = id;
        } else {
            ns = id.substring(0, colon);
            path = id.substring(colon + 1);
        }
        try {
            return Identifier.fromNamespaceAndPath(ns, path);
        } catch (Exception ex) {
            return null;
        }
    }

    /** Tab completion for entity type ids (zombie, villager, ...). */
    public static CompletableFuture<Suggestions> suggestEntityTypes(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        try {
            Registry<EntityType<?>> types = ctx.getSource().getServer().registryAccess().lookup(Registries.ENTITY_TYPE).orElse(null);
            if (types == null) {
                return builder.buildFuture();
            }
            String typed = builder.getRemainingLowerCase();
            Set<String> seen = new HashSet<>();
            for (Identifier key : types.keySet()) {
                if ("minecraft".equals(key.getNamespace())) {
                    addSuggestion(builder, seen, key.getPath(), typed);
                }
                addSuggestion(builder, seen, key.toString(), typed);
            }
        } catch (Exception ignored) {
            // best effort
        }
        return builder.buildFuture();
    }

    private static int sendToPlayer(CommandSourceStack src, TargetPayload payload, String feedback) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("[TrajectoryLens] this command needs a player client"));
            return 0;
        }
        ServerPlayNetworking.send(player, payload);
        src.sendSuccess(() -> Component.literal(feedback), false);
        return 1;
    }

    private static int runSimulate(CommandSourceStack src, double x, double y, double z, double vx, double vy, double vz) {
        var level = src.getLevel();
        ItemPhysicsSimulator sim = new ItemPhysicsSimulator(level);
        TrajectoryPath path = sim.simulate(x, y, z, vx, vy, vz, new ItemStack(Items.STICK));
        StringBuilder sb = new StringBuilder("prediction: ");
        if (path.points.isEmpty()) {
            sb.append("no motion");
        } else {
            var end = path.endPoint();
            sb.append("ticks=").append(path.ticks)
                .append(", end=").append(String.format("%.2f", end.x))
                .append(' ').append(String.format("%.2f", end.y))
                .append(' ').append(String.format("%.2f", end.z))
                .append(" (").append(new BlockPos((int) Math.floor(end.x), (int) Math.floor(end.y), (int) Math.floor(end.z)))
                .append("), reason=").append(path.endReason);
        }
        src.sendSuccess(() -> Component.literal(sb.toString()), true);
        LOGGER.info("[TrajectoryLens] " + sb);
        return 1;
    }
}