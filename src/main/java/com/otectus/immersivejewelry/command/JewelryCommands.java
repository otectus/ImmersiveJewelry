package com.otectus.immersivejewelry.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.otectus.immersivejewelry.api.gem.GemData;
import com.otectus.immersivejewelry.api.jewelry.BlankData;
import com.otectus.immersivejewelry.api.jewelry.JewelryData;
import com.otectus.immersivejewelry.data.DefinitionEvents;
import com.otectus.immersivejewelry.data.JewelryDefinitions;
import com.otectus.immersivejewelry.data.cut.CutDefinition;
import com.otectus.immersivejewelry.data.gem.GemDefinition;
import com.otectus.immersivejewelry.effect.JewelryWearers;
import com.otectus.immersivejewelry.equipment.JewelryEquipment;
import com.otectus.immersivejewelry.item.CutGemItem;
import com.otectus.immersivejewelry.item.RoughGemItem;
import com.otectus.immersivejewelry.jewelry.JewelryFactory;
import com.otectus.immersivejewelry.jewelry.JewelryResolver;
import com.otectus.immersivejewelry.jewelry.ResolvedJewelry;
import com.otectus.immersivejewelry.minigame.JewelrySession;
import com.otectus.immersivejewelry.minigame.SessionManager;
import com.otectus.immersivejewelry.minigame.cutting.CuttingService;
import com.otectus.immersivejewelry.network.ModNetwork;
import com.otectus.immersivejewelry.network.packet.SyncDefinitionsPacket;
import com.otectus.immersivejewelry.quality.Workmanship;
import com.otectus.immersivejewelry.recipe.DesignKnowledge;
import com.otectus.immersivejewelry.recipe.JewelryDesignRecipe;
import com.otectus.immersivejewelry.registry.ModRecipeTypes;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/** {@code /immersivejewelry}: developer and pack-author commands (spec 71). Permission level 2. */
public final class JewelryCommands {
    private static final SuggestionProvider<CommandSourceStack> GEMS = (c, b) ->
            SharedSuggestionProvider.suggestResource(JewelryDefinitions.server().gems().stream().map(GemDefinition::id), b);
    private static final SuggestionProvider<CommandSourceStack> CUTS = (c, b) ->
            SharedSuggestionProvider.suggestResource(JewelryDefinitions.server().cuts().stream().map(CutDefinition::id), b);
    private static final SuggestionProvider<CommandSourceStack> FORMS = (c, b) ->
            SharedSuggestionProvider.suggestResource(JewelryDefinitions.server().forms().stream().map(f -> f.id()), b);
    private static final SuggestionProvider<CommandSourceStack> FINISHES = (c, b) ->
            SharedSuggestionProvider.suggestResource(JewelryDefinitions.server().finishes().stream().map(f -> f.id()), b);
    private static final SuggestionProvider<CommandSourceStack> METALS = (c, b) ->
            SharedSuggestionProvider.suggestResource(JewelryDefinitions.server().metals().stream().map(m -> m.id()), b);
    private static final SuggestionProvider<CommandSourceStack> DESIGNS = (c, b) -> SharedSuggestionProvider.suggestResource(
            c.getSource().getServer().getRecipeManager().getAllRecipesFor(ModRecipeTypes.JEWELRY_DESIGN.get()).stream().map(JewelryDesignRecipe::getId), b);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
        dispatcher.register(Commands.literal("immersivejewelry").requires(s -> s.hasPermission(2))
                .then(Commands.literal("give").then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("form", ResourceLocationArgument.id()).suggests(FORMS)
                        .then(Commands.argument("metal", ResourceLocationArgument.id()).suggests(METALS)
                        .then(Commands.argument("gem", ResourceLocationArgument.id()).suggests(GEMS)
                                .executes(c -> give(c, null, null, 0.75F))
                                .then(Commands.argument("cut", ResourceLocationArgument.id()).suggests(CUTS)
                                        .executes(c -> give(c, id(c, "cut"), null, 0.75F))
                                        .then(Commands.argument("finish", ResourceLocationArgument.id()).suggests(FINISHES)
                                                .executes(c -> give(c, id(c, "cut"), id(c, "finish"), 0.75F))
                                                .then(Commands.argument("quality", FloatArgumentType.floatArg(0F, 1F))
                                                        .executes(c -> give(c, id(c, "cut"), id(c, "finish"), FloatArgumentType.getFloat(c, "quality")))))))))))
                .then(Commands.literal("givegem").then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("gem", ResourceLocationArgument.id()).suggests(GEMS)
                                .executes(c -> giveGem(c, null, 0F))
                                .then(Commands.argument("cut", ResourceLocationArgument.id()).suggests(CUTS)
                                        .executes(c -> giveGem(c, id(c, "cut"), 0.8F))
                                        .then(Commands.argument("quality", FloatArgumentType.floatArg(0F, 1F))
                                                .executes(c -> giveGem(c, id(c, "cut"), FloatArgumentType.getFloat(c, "quality"))))))))
                .then(Commands.literal("inspect").executes(JewelryCommands::inspect))
                .then(Commands.literal("setquality").then(Commands.argument("stage", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("smithing", "cut", "setting", "integrity"), b))
                        .then(Commands.argument("value", FloatArgumentType.floatArg(0F, 1F))
                                .executes(c -> setQuality(c, StringArgumentType.getString(c, "stage"), FloatArgumentType.getFloat(c, "value"))))))
                .then(Commands.literal("appraise").executes(JewelryCommands::appraise))
                .then(Commands.literal("listgems").executes(JewelryCommands::listGems))
                .then(Commands.literal("listcuts").executes(JewelryCommands::listCuts))
                .then(Commands.literal("reloaddebug").executes(JewelryCommands::reloadDebug))
                .then(Commands.literal("sessions").executes(JewelryCommands::sessions))
                .then(Commands.literal("worn").then(Commands.argument("target", EntityArgument.player()).executes(JewelryCommands::worn)))
                .then(Commands.literal("learn").then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.literal("all").executes(c -> learn(c, null)))
                        .then(Commands.argument("design", ResourceLocationArgument.id()).suggests(DESIGNS).executes(c -> learn(c, id(c, "design")))))));
    }

    private static ResourceLocation id(CommandContext<CommandSourceStack> c, String name) {
        return ResourceLocationArgument.getId(c, name);
    }

    private static int give(CommandContext<CommandSourceStack> c, ResourceLocation cutId, ResourceLocation finishId, float quality)
            throws CommandSyntaxException {
        JewelryDefinitions defs = JewelryDefinitions.server();
        GemDefinition gem = defs.gem(id(c, "gem"));
        if (gem == null) return fail(c, "unknown_gem", id(c, "gem"));
        CutDefinition cut = cutId != null ? defs.cut(cutId) : JewelryFactory.preferredCut(defs, gem);
        if (cut == null) return fail(c, "unknown_cut", String.valueOf(cutId));
        ResourceLocation finish = finishId != null ? finishId : new ResourceLocation("immersive_jewelry", "polished");
        ItemStack stack = JewelryFactory.jewelry(id(c, "form"), JewelryFactory.metal(defs, id(c, "metal")),
                List.of(JewelryFactory.sampleCut(gem, cut, quality)), null, finish, quality, quality, c.getSource().getTextName());
        if (stack.isEmpty()) return fail(c, "unknown_form", id(c, "form"));
        return giveAll(c, stack);
    }

    private static int giveGem(CommandContext<CommandSourceStack> c, ResourceLocation cutId, float quality) throws CommandSyntaxException {
        JewelryDefinitions defs = JewelryDefinitions.server();
        GemDefinition gem = defs.gem(id(c, "gem"));
        if (gem == null) return fail(c, "unknown_gem", id(c, "gem"));
        ItemStack stack;
        if (cutId == null) {
            stack = RoughGemItem.create(CuttingService.appraise(gem, c.getSource().getLevel().random));
        } else {
            CutDefinition cut = defs.cut(cutId);
            if (cut == null) return fail(c, "unknown_cut", cutId);
            stack = CutGemItem.create(JewelryFactory.sampleCut(gem, cut, quality));
        }
        return giveAll(c, stack);
    }

    private static int giveAll(CommandContext<CommandSourceStack> c, ItemStack stack) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(c, "targets");
        for (ServerPlayer p : targets) {
            ItemStack copy = stack.copy();
            if (!p.getInventory().add(copy)) p.drop(copy, false);
        }
        c.getSource().sendSuccess(() -> Component.translatable("command.immersive_jewelry.given", stack.getHoverName(), targets.size()), true);
        return targets.size();
    }

    private static int inspect(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        List<Component> lines = new ArrayList<>();
        JewelryData.get(held).ifPresent(d -> {
            ResolvedJewelry r = JewelryResolver.resolve(d, JewelryDefinitions.server());
            lines.add(debug("piece", d.form(), d.metal().id(), d.design(), d.finish()));
            lines.add(debug("quality",
                    d.smithingQuality(), d.cutQuality(), d.settingQuality(), d.workmanship(), d.integrity(), d.reworks()));
            lines.add(debug("power",
                    r.basePower(), r.stability(), r.responsiveness(), r.inert(), d.defects()));
            for (GemData g : d.gems()) lines.add(describe(g));
            for (ResolvedJewelry.Expression e : r.expressions()) {
                lines.add(debug("effect", e.definition().type(), e.definition().category().id(),
                        e.power(), e.fromMetal() ? Component.translatable("command.immersive_jewelry.debug.from_metal") : Component.empty()));
            }
        });
        BlankData.get(held).filter(b -> JewelryData.get(held).isEmpty()).ifPresent(b ->
                lines.add(debug("blank", b.metal().id(),
                        b.smithingQuality(), b.integrity(), b.reworks(), b.fallback())));
        if (lines.isEmpty()) {
            RoughGemItem.data(held).or(() -> CutGemItem.data(held)).ifPresent(g -> lines.add(describe(g)));
        }
        if (lines.isEmpty()) return fail(c, "not_jewelry", held.getHoverName());
        for (Component line : lines) c.getSource().sendSuccess(() -> line, false);
        return 1;
    }

    /** Keep diagnostic ids intact, but let the receiving client translate the surrounding labels. */
    private static Component debug(String key, Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] instanceof Float || values[i] instanceof Double) values[i] = String.format(Locale.ROOT, "%.3f", values[i]);
            else if (values[i] instanceof Boolean b) values[i] = Component.translatable(b ? "gui.yes" : "gui.no");
            else if (values[i] == null) values[i] = Component.translatable("command.immersive_jewelry.debug.none");
            else if (!(values[i] instanceof Component)) values[i] = values[i].toString();
        }
        return Component.translatable("command.immersive_jewelry.debug." + key, values);
    }

    private static Component describe(GemData g) {
        return debug("stone", g.gem(), g.size(),
                g.originalSize(), g.naturalQuality(), g.inclusions(), g.cut(), g.cutQuality(), g.damage().id(), g.qualityCap());
    }

    private static int setQuality(CommandContext<CommandSourceStack> c, String stage, float value) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        JewelryData d = JewelryData.get(held).orElse(null);
        if (d == null) return fail(c, "not_jewelry", held.getHoverName());
        JewelryData next = switch (stage) {
            case "smithing" -> new JewelryData(d.form(), d.metal(), d.gems(), d.design(), d.finish(), value, d.settingQuality(),
                    Workmanship.compute(value, d.cutQuality(), d.settingQuality()), d.integrity(), d.reworks(), d.defects(), d.maker(),
                    d.legacyConverted(), d.smithing());
            case "cut" -> {
                List<GemData> gems = d.gems().stream().map(g -> g.withCutQuality(value)).toList();
                JewelryData withGems = d.withGems(gems);
                yield withGems.withSetting(d.settingQuality(), Workmanship.compute(d.smithingQuality(), withGems.cutQuality(), d.settingQuality()), d.defects());
            }
            case "setting" -> d.withSetting(value, Workmanship.compute(d.smithingQuality(), d.cutQuality(), value), d.defects());
            case "integrity" -> d.withIntegrity(value);
            default -> null;
        };
        if (next == null) return fail(c, "unknown_stage", stage);
        next.apply(held);
        JewelryWearers.markDirty(player);
        c.getSource().sendSuccess(() -> Component.translatable("command.immersive_jewelry.quality_set", stage, value), false);
        return 1;
    }

    private static int appraise(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        GemDefinition gem = JewelryDefinitions.server().gemForSource(held);
        if (gem == null) return fail(c, "not_gem_source", held.getHoverName());
        held.shrink(1);
        ItemStack stone = RoughGemItem.create(CuttingService.appraise(gem, player.getRandom()));
        if (!player.getInventory().add(stone)) player.drop(stone, false);
        return 1;
    }

    private static int listGems(CommandContext<CommandSourceStack> c) {
        for (GemDefinition g : JewelryDefinitions.server().gems()) {
            Component line = debug("gem", g.id(), g.trait(), g.hardness(),
                    g.brittleness(), Component.translatable("command.immersive_jewelry.rarity." + g.rarity().name().toLowerCase(Locale.ROOT)), g.supportedCuts().isEmpty() ? Component.translatable("command.immersive_jewelry.debug.all") : g.supportedCuts(),
                    g.auto() ? Component.translatable("command.immersive_jewelry.debug.auto") : Component.empty());
            c.getSource().sendSuccess(() -> line, false);
        }
        return JewelryDefinitions.server().gems().size();
    }

    private static int listCuts(CommandContext<CommandSourceStack> c) {
        for (CutDefinition cut : JewelryDefinitions.server().cuts()) {
            Component line = debug("cut", cut.id(),
                    cut.facets(), Component.translatable("command.immersive_jewelry.pattern." + cut.pattern().name().toLowerCase(Locale.ROOT)), cut.difficulty(), cut.qualityModifier(), cut.sizeRetention(), cut.stability());
            c.getSource().sendSuccess(() -> line, false);
        }
        return JewelryDefinitions.server().cuts().size();
    }

    private static int reloadDebug(CommandContext<CommandSourceStack> c) {
        DefinitionEvents.rebuildServer();
        JewelryWearers.invalidateAll();
        ModNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), SyncDefinitionsPacket.of(JewelryDefinitions.server()));
        JewelryDefinitions d = JewelryDefinitions.server();
        c.getSource().sendSuccess(() -> Component.translatable("command.immersive_jewelry.reloaded", d.gems().size(), d.cuts().size(),
                d.finishes().size(), d.forms().size(), d.traits().size(), d.metals().size()), true);
        return 1;
    }

    private static int sessions(CommandContext<CommandSourceStack> c) {
        Collection<JewelrySession> all = SessionManager.all();
        c.getSource().sendSuccess(() -> Component.translatable("command.immersive_jewelry.sessions", all.size()), false);
        for (JewelrySession s : all) {
            Component line = debug("session", s.id, s.getClass().getSimpleName(), s.pos.toShortString(),
                    s.nextStep(), s.maxSteps());
            c.getSource().sendSuccess(() -> line, false);
        }
        return all.size();
    }

    private static int worn(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer target = EntityArgument.getPlayer(c, "target");
        JewelryWearers.refreshNow(target);
        var worn = JewelryEquipment.worn(target);
        c.getSource().sendSuccess(() -> Component.translatable("command.immersive_jewelry.worn", target.getDisplayName(), worn.size(),
                JewelryWearers.activePieces(target)), false);
        worn.forEach((slot, stack) -> c.getSource().sendSuccess(() -> debug("slot", slot, stack.getHoverName()), false));
        return worn.size();
    }

    private static int learn(CommandContext<CommandSourceStack> c, ResourceLocation design) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(c, "targets");
        List<JewelryDesignRecipe> all = c.getSource().getServer().getRecipeManager().getAllRecipesFor(ModRecipeTypes.JEWELRY_DESIGN.get());
        int count = 0;
        for (ServerPlayer p : targets) {
            for (JewelryDesignRecipe r : all) {
                if ((design == null || r.getId().equals(design)) && DesignKnowledge.learn(p, r.getId())) count++;
            }
        }
        int learned = count;
        c.getSource().sendSuccess(() -> Component.translatable("command.immersive_jewelry.learned", learned), true);
        return learned;
    }

    private static int fail(CommandContext<CommandSourceStack> c, String key, Object arg) {
        c.getSource().sendFailure(Component.translatable("command.immersive_jewelry." + key, arg instanceof Component ? arg : String.valueOf(arg)));
        return 0;
    }

    private JewelryCommands() {}
}
