package com.voidexiled.magichygarden.features.farming.systems;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.entity.entities.player.hud.HudManager;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.voidexiled.magichygarden.commands.shared.Targeting;
import com.voidexiled.magichygarden.features.farming.components.MghgCropData;
import com.voidexiled.magichygarden.features.farming.logic.MghgCropDataAccess;
import com.voidexiled.magichygarden.features.farming.modifiers.MghgCropGrowthModifierAsset;
import com.voidexiled.magichygarden.features.farming.registry.MghgCropDefinition;
import com.voidexiled.magichygarden.features.farming.registry.MghgCropRegistry;
import com.voidexiled.magichygarden.features.farming.state.ClimateMutation;
import com.voidexiled.magichygarden.features.farming.state.LunarMutation;
import com.voidexiled.magichygarden.features.farming.state.RarityMutation;
import com.voidexiled.magichygarden.features.farming.ui.MghgCropInspectHud;
import com.voidexiled.magichygarden.features.farming.visuals.MghgCropVisualStateResolver;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MghgCropInspectHudSystem extends EntityTickingSystem<EntityStore> {
    private static final int UPDATE_INTERVAL_TICKS = 5;
    private static final double TARGET_RANGE = 6.0;
    private static final String LANG_PREFIX = "server.";

    private final Query<EntityStore> query = Query.and(
            PlayerRef.getComponentType(),
            Player.getComponentType()
    );

    private long tickCounter = 0L;
    private final Map<UUID, String> lastSignature = new ConcurrentHashMap<>();
    private final Map<UUID, MghgCropInspectHud> huds = new ConcurrentHashMap<>();

    @Override
    public Query<EntityStore> getQuery() {
        return query;
    }

    @Override
    public void tick(float dt, int index, ArchetypeChunk<EntityStore> archetypeChunk, Store<EntityStore> store, CommandBuffer<EntityStore> commandBuffer) {
        tickCounter++;
        if (tickCounter % UPDATE_INTERVAL_TICKS != 0) {
            return;
        }

        PlayerRef playerRef = archetypeChunk.getComponent(index, PlayerRef.getComponentType());
        Player player = archetypeChunk.getComponent(index, Player.getComponentType());
        if (playerRef == null || player == null) {
            return;
        }

        Ref<EntityStore> entityRef = archetypeChunk.getReferenceTo(index);
        Vector3i targetPos = Targeting.getTargetBlock(entityRef, store, TARGET_RANGE);
        if (targetPos == null) {
            clearHud(playerRef, player);
            return;
        }

        World world = store.getExternalData().getWorld();
        if (world == null) {
            clearHud(playerRef, player);
            return;
        }

        BlockType blockType = resolveBlockType(world, targetPos);
        if (blockType == null || !MghgCropRegistry.isMghgCropBlock(blockType)) {
            clearHud(playerRef, player);
            return;
        }

        MghgCropData data = MghgCropDataAccess.tryGetCropData(world, targetPos);
        if (data == null) {
            clearHud(playerRef, player);
            return;
        }

        String baseItemId = resolveBaseItemId(blockType);
        Item baseItem = baseItemId != null ? Item.getAssetMap().getAsset(baseItemId) : null;
        ItemStack previewStack = resolvePreviewStack(baseItem, data);

        double weight = data.getWeightGrams();
        String weightText = formatWeight(playerRef, weight);

        String signature = buildSignature(targetPos, data, previewStack != null ? previewStack.getItemId() : "", weightText);
        UUID playerId = playerRef.getUuid();
        String last = lastSignature.get(playerId);
        if (signature.equals(last)) {
            return;
        }
        lastSignature.put(playerId, signature);

        HudManager hudManager = player.getHudManager();
        CustomUIHud current = hudManager.getCustomHud();
        if (current != null && !(current instanceof MghgCropInspectHud)) {
            return;
        }

        MghgCropInspectHud hud = huds.computeIfAbsent(playerId, id -> new MghgCropInspectHud(playerRef));
        if (current == null) {
            hudManager.setCustomHud(playerRef, hud);
        }

        String title = buildTitle(playerRef, baseItem, data);
        String subline = buildSubline(playerRef, data, weightText);
        String climate = translateClimate(playerRef, data.getClimate());
        String lunar = translateLunar(playerRef, data.getLunar());
        String rarity = translateRarity(playerRef, data.getRarity());
        String climateColor = colorForClimate(data.getClimate());
        String lunarColor = colorForLunar(data.getLunar());
        String rarityColor = colorForRarity(data.getRarity());

        if (climate == null && lunar == null && rarity == null) {
            climate = translate(playerRef, "mghg.hud.crop.mutations.none");
            climateColor = "#b9c3cf";
        }

        //String accentColor = rarityColor != null ? rarityColor
        //        : (lunarColor != null ? lunarColor : (climateColor != null ? climateColor : "#68d7b6"));
        String climateBadgeBg = badgeBackgroundFor(climateColor);
        String lunarBadgeBg = badgeBackgroundFor(lunarColor);
        String rarityBadgeBg = badgeBackgroundFor(rarityColor);

        hud.updateContent(
                panelRootFor(data.getRarity()),
                title,
                subline,
                climate,
                lunar,
                rarity,
                climateColor,
                lunarColor,
                rarityColor,
        //        accentColor,
                climateBadgeBg,
                lunarBadgeBg,
                rarityBadgeBg,
                previewStack
        );
    }

    private void clearHud(PlayerRef playerRef, Player player) {
        HudManager hudManager = player.getHudManager();
        CustomUIHud current = hudManager.getCustomHud();
        if (current instanceof MghgCropInspectHud hud) {
            hud.hide();
        }
        UUID playerId = playerRef.getUuid();
        lastSignature.remove(playerId);
    }

    private static BlockType resolveBlockType(World world, Vector3i pos) {
        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> store = chunkStore.getStore();
        long chunkIndex = ChunkUtil.indexChunkFromBlock(pos.x, pos.z);
        Ref<ChunkStore> chunkRef = chunkStore.getChunkReference(chunkIndex);
        if (chunkRef == null || !chunkRef.isValid()) {
            return null;
        }
        BlockChunk blockChunk = store.getComponent(chunkRef, BlockChunk.getComponentType());
        if (blockChunk == null) {
            return null;
        }
        return BlockType.getAssetMap().getAsset(blockChunk.getBlock(pos.x, pos.y, pos.z));
    }

    private static String resolveBaseItemId(BlockType blockType) {
        MghgCropDefinition def = MghgCropRegistry.getDefinition(blockType);
        if (def != null && def.getItemId() != null && !def.getItemId().isBlank()) {
            return def.getItemId();
        }
        Item item = blockType.getItem();
        if (item != null) {
            return item.getId();
        }
        return null;
    }

    private static ItemStack resolvePreviewStack(Item baseItem, MghgCropData data) {
        if (baseItem == null) {
            return null;
        }
        String stateKey = MghgCropVisualStateResolver.resolveItemState(data);
        String itemId = baseItem.getItemIdForState(stateKey);
        if (itemId == null || itemId.isBlank()) {
            itemId = baseItem.getId();
        }
        return new ItemStack(itemId, 1);
    }

    private static String buildSignature(Vector3i pos, MghgCropData data, String previewItemId, String weightText) {
        return pos.x + "," + pos.y + "," + pos.z + "|"
                + data.getSize() + "|"
                + data.getClimate() + "|"
                + data.getLunar() + "|"
                + data.getRarity() + "|"
                + previewItemId + "|"
                + (weightText == null ? "" : weightText);
    }

    private static String buildTitle(PlayerRef playerRef, Item baseItem, MghgCropData data) {
        String nameKey = baseItem != null ? baseItem.getTranslationKey() : "mghg.hud.crop.unknown";
        String name = translate(playerRef, nameKey);
        String rarity = translateRarity(playerRef, data.getRarity());
        if (rarity == null || rarity.isBlank()) {
            return name;
        }
        return translate(playerRef, "mghg.hud.crop.title", Map.of(
                "name", name,
                "rarity", rarity
        ));
    }

    private static String buildSubline(PlayerRef playerRef, MghgCropData data, String weightText) {
        MghgCropGrowthModifierAsset cfg = MghgCropGrowthModifierAsset.getLastLoaded();
        int min = cfg != null ? cfg.getSizeMin() : MghgCropGrowthModifierAsset.DEFAULT_SIZE_MIN;
        int max = cfg != null ? cfg.getSizeMax() : MghgCropGrowthModifierAsset.DEFAULT_SIZE_MAX;
        String tierKey = resolveTierKey(data.getSize(), min, max);
        String tierLabel = translate(playerRef, tierKey);
        return translate(playerRef, "mghg.hud.crop.subline", Map.of(
                "size", String.valueOf(data.getSize()),
                "tier", tierLabel,
                "weight", weightText == null ? "--" : weightText
        ));
    }

    private static String formatWeight(PlayerRef playerRef, double grams) {
        if (grams <= 0.0) {
            return translate(playerRef, "mghg.hud.weight.grams", Map.of("value", "0"));
        }
        if (grams < 1000.0) {
            String value = grams < 10.0
                    ? String.format(java.util.Locale.US, "%.1f", grams)
                    : String.format(java.util.Locale.US, "%.0f", grams);
            return translate(playerRef, "mghg.hud.weight.grams", Map.of("value", value));
        }
        String value = String.format(java.util.Locale.US, "%.2f", grams / 1000.0);
        return translate(playerRef, "mghg.hud.weight.kg", Map.of("value", value));
    }

    private static String translateClimate(PlayerRef playerRef, ClimateMutation climate) {
        if (climate == null || climate == ClimateMutation.NONE) {
            return null;
        }
        String key = switch (climate) {
            case RAIN -> "mghg.hud.climate.rain";
            case SNOW -> "mghg.hud.climate.snow";
            case FROZEN -> "mghg.hud.climate.frozen";
            default -> null;
        };
        return key != null ? translate(playerRef, key) : climate.name();
    }

    private static String translateLunar(PlayerRef playerRef, LunarMutation lunar) {
        if (lunar == null || lunar == LunarMutation.NONE) {
            return null;
        }
        String key = switch (lunar) {
            case DAWNLIT -> "mghg.hud.lunar.dawnlit";
            case DAWNBOUND -> "mghg.hud.lunar.dawnbound";
            case AMBERLIT -> "mghg.hud.lunar.amberlit";
            case AMBERBOUND -> "mghg.hud.lunar.amberbound";
            default -> null;
        };
        return key != null ? translate(playerRef, key) : lunar.name();
    }

    private static String translateRarity(PlayerRef playerRef, RarityMutation rarity) {
        if (rarity == null || rarity == RarityMutation.NONE) {
            return null;
        }
        String key = switch (rarity) {
            case GOLD -> "mghg.hud.rarity.gold";
            case RAINBOW -> "mghg.hud.rarity.rainbow";
            default -> null;
        };
        return key != null ? translate(playerRef, key) : rarity.name();
    }

    private static String colorForClimate(ClimateMutation climate) {
        if (climate == null) {
            return null;
        }
        return switch (climate) {
            case RAIN -> "#55b7ff";
            case SNOW -> "#bfe7ff";
            case FROZEN -> "#7ff1ff";
            default -> null;
        };
    }

    private static String colorForLunar(LunarMutation lunar) {
        if (lunar == null) {
            return null;
        }
        return switch (lunar) {
            case DAWNLIT -> "#f3a6ff";
            case DAWNBOUND -> "#d57dff";
            case AMBERLIT -> "#ffb357";
            case AMBERBOUND -> "#ff8c42";
            default -> null;
        };
    }

    private static String colorForRarity(RarityMutation rarity) {
        if (rarity == null) {
            return null;
        }
        return switch (rarity) {
            case GOLD -> "#ffd24a";
            case RAINBOW -> "#8be9ff";
            default -> null;
        };
    }

    private static String badgeBackgroundFor(String color) {
        if (color == null || color.isBlank()) {
            return "#ffffff(0.08)";
        }
        String base = color;
        int idx = base.indexOf('(');
        if (idx >= 0) {
            base = base.substring(0, idx);
        }
        return base + "(0.14)";
    }

    private static String panelRootFor(RarityMutation rarity) {
        if (rarity == RarityMutation.GOLD) {
            return "#MghgCropInspectGold";
        }
        if (rarity == RarityMutation.RAINBOW) {
            return "#MghgCropInspectRainbow";
        }
        return "#MghgCropInspectDefault";
    }


    private static String resolveTierKey(int size, int min, int max) {
        if (max <= min) {
            return "mghg.hud.tier.3";
        }
        double t = (size - min) / (double) (max - min);
        if (t < 0.2) return "mghg.hud.tier.1";
        if (t < 0.4) return "mghg.hud.tier.2";
        if (t < 0.6) return "mghg.hud.tier.3";
        if (t < 0.8) return "mghg.hud.tier.4";
        return "mghg.hud.tier.5";
    }

    private static String normalizeLangKey(String key) {
        if (key == null || key.isBlank()) {
            return key;
        }
        return key.startsWith(LANG_PREFIX) ? key : (LANG_PREFIX + key);
    }

    private static String translate(PlayerRef playerRef, String key) {
        return translate(playerRef, key, Map.of());
    }

    private static String translate(PlayerRef playerRef, String key, Map<String, String> params) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String normalized = normalizeLangKey(key);
        String language = playerRef != null ? playerRef.getLanguage() : null;
        I18nModule i18n = I18nModule.get();
        String template = i18n != null ? i18n.getMessages(language).get(normalized) : null;
        if (template == null || template.isBlank()) {
            template = normalized;
        }
        return applyParams(template, params);
    }

    private static String applyParams(String template, Map<String, String> params) {
        if (template == null || template.isBlank() || params == null || params.isEmpty()) {
            return template == null ? "" : template;
        }
        String result = template;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue();
            result = result.replace("{" + entry.getKey() + "}", value);
        }
        return result;
    }
}
