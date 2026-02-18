package com.voidexiled.magichygarden.features.farming.ui;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.interface_.Page;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.modules.i18n.I18nModule;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.economy.MghgEconomyManager;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcel;
import com.voidexiled.magichygarden.features.farming.parcels.MghgParcelManager;
import com.voidexiled.magichygarden.features.farming.perks.MghgFarmPerkManager;
import com.voidexiled.magichygarden.features.farming.perks.MghgFarmPerksConfig;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MghgFarmPerksPage extends InteractiveCustomUIPage<MghgFarmPerksPage.EventPayload> {
    private static final String UI_PATH = "Pages/Mghg_FarmPerksPage.ui";
    private static final String CARD_TEMPLATE_PATH = "Pages/Mghg_FarmPerkCard.ui";
    private static final String ROOT = "#MghgFarmPerksPage";
    private static final String LANG_PREFIX = "server.";

    private static final String ACTION_CLOSE = "Close";
    private static final String ACTION_SELECT_PERK = "SelectPerk";
    private static final String ACTION_UPGRADE = "Upgrade";

    private static final String PERK_FERTILE_SOIL = "fertile_soil";
    private static final String PERK_SELL_MULTIPLIER = "sell_multiplier";
    private static final String PERK_MUTATION_LUCK = "mutation_luck";

    private static final int MAX_PERKS = 32;
    private static final String FOOTER_INFO_COLOR = "#7caacc";
    private static final String FOOTER_SUCCESS_COLOR = "#8fe388";
    private static final String FOOTER_ERROR_COLOR = "#f6a2a2";

    private final ArrayList<PerkView> renderedPerks = new ArrayList<>();
    private String selectedPerkId = PERK_FERTILE_SOIL;
    private String footerMessage = "";
    private String footerColor = FOOTER_INFO_COLOR;

    public MghgFarmPerksPage(@Nonnull PlayerRef playerRef) {
        super(playerRef, CustomPageLifetime.CanDismiss, EventPayload.CODEC);
    }

    public static @Nullable String openForPlayer(
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerEntityRef,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return "No pude obtener el componente de jugador.";
        }

        player.getPageManager().openCustomPage(playerEntityRef, store, new MghgFarmPerksPage(playerRef));
        return null;
    }

    public static void closeForPlayer(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerEntityRef) {
        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        CustomUIPage currentPage = player.getPageManager().getCustomPage();
        if (currentPage instanceof MghgFarmPerksPage) {
            player.getPageManager().setPage(playerEntityRef, store, Page.None);
        }
    }

    public static void refreshForPlayer(@Nonnull Store<EntityStore> store, @Nonnull Ref<EntityStore> playerEntityRef) {
        Player player = store.getComponent(playerEntityRef, Player.getComponentType());
        if (player == null) {
            return;
        }
        CustomUIPage currentPage = player.getPageManager().getCustomPage();
        if (currentPage instanceof MghgFarmPerksPage page) {
            page.refresh(playerEntityRef, store);
        }
    }

    @Override
    public void build(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull UICommandBuilder commandBuilder,
            @Nonnull UIEventBuilder eventBuilder,
            @Nonnull Store<EntityStore> store
    ) {
        commandBuilder.append(UI_PATH);
        render(ref, store, commandBuilder, eventBuilder);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull EventPayload data) {
        String action = normalize(data.action);
        if (ACTION_CLOSE.equalsIgnoreCase(action)) {
            close();
            return;
        }

        if (ACTION_SELECT_PERK.equalsIgnoreCase(action)) {
            PerkView selected = resolvePerk(data);
            if (selected != null) {
                selectedPerkId = selected.id();
                setFooter(tr("mghg.perks.ui.footer.selected", "Selected perk updated."), FOOTER_INFO_COLOR);
            }
            refresh(ref, store);
            return;
        }

        if (ACTION_UPGRADE.equalsIgnoreCase(action)) {
            handleUpgrade(ref, store);
            refresh(ref, store);
        }
    }

    public void refresh(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        UICommandBuilder commandBuilder = new UICommandBuilder();
        UIEventBuilder eventBuilder = new UIEventBuilder();
        render(ref, store, commandBuilder, eventBuilder);
        sendUpdate(commandBuilder, eventBuilder, false);
    }

    private void render(
            @Nonnull Ref<EntityStore> ref,
            @Nonnull Store<EntityStore> store,
            @Nonnull UICommandBuilder commandBuilder,
            @Nonnull UIEventBuilder eventBuilder
    ) {
        bindStaticEvents(eventBuilder);
        applyLocalizedUiText(commandBuilder);

        Player player = store.getComponent(ref, Player.getComponentType());
        if (player == null) {
            commandBuilder.set(ROOT + " #HeaderParcelValue.Text", tr("mghg.perks.ui.header.parcel.unavailable", "Parcel unavailable"));
            commandBuilder.set(ROOT + " #HeaderBalanceValue.Text", formatMoney(0.0d));
            commandBuilder.set(ROOT + " #FooterStatus.Text", tr("mghg.perks.ui.footer.unavailable", "Perks unavailable."));
            commandBuilder.set(ROOT + " #FooterStatus.Style.TextColor", FOOTER_ERROR_COLOR);
            commandBuilder.set(ROOT + " #UpgradeButton.Disabled", true);
            return;
        }

        MghgParcel parcel = MghgParcelManager.getByOwner(playerRef.getUuid());
        double balance = MghgEconomyManager.getBalance(playerRef.getUuid());

        commandBuilder.set(
                ROOT + " #HeaderParcelValue.Text",
                parcel == null
                        ? tr("mghg.perks.ui.header.parcel.missing", "No parcel yet")
                        : tr("mghg.perks.ui.header.parcel.ready", "Parcel active")
        );
        commandBuilder.set(ROOT + " #HeaderBalanceValue.Text", formatMoney(balance));

        List<PerkView> perks = buildPerkViews(parcel, balance);
        renderPerkList(commandBuilder, eventBuilder, perks);

        PerkView selected = resolveSelectedPerk(perks);
        renderDetails(commandBuilder, selected);

        String effectiveFooterMessage = footerMessage == null || footerMessage.isBlank()
                ? tr("mghg.perks.ui.footer.default", "Choose a perk and upgrade when ready.")
                : footerMessage;
        String effectiveFooterColor = footerColor == null || footerColor.isBlank()
                ? FOOTER_INFO_COLOR
                : footerColor;

        commandBuilder.set(ROOT + " #FooterStatus.Text", effectiveFooterMessage);
        commandBuilder.set(ROOT + " #FooterStatus.Style.TextColor", effectiveFooterColor);
    }

    private void bindStaticEvents(@Nonnull UIEventBuilder eventBuilder) {
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                ROOT + " #UpgradeButton",
                new EventData().append("Action", ACTION_UPGRADE),
                false
        );
        eventBuilder.addEventBinding(
                CustomUIEventBindingType.Activating,
                ROOT + " #CloseButton",
                new EventData().append("Action", ACTION_CLOSE),
                false
        );
    }

    private void renderPerkList(
            @Nonnull UICommandBuilder commandBuilder,
            @Nonnull UIEventBuilder eventBuilder,
            @Nonnull List<PerkView> perks
    ) {
        commandBuilder.clear(ROOT + " #PerkList");
        renderedPerks.clear();

        int count = Math.min(MAX_PERKS, perks.size());
        for (int i = 0; i < count; i++) {
            PerkView perk = perks.get(i);
            renderedPerks.add(perk);

            commandBuilder.append(ROOT + " #PerkList", CARD_TEMPLATE_PATH);
            String selector = ROOT + " #PerkList[" + i + "]";

            boolean selected = perk.id().equalsIgnoreCase(selectedPerkId);
            commandBuilder.set(selector + " #PerkIcon.ItemId", perk.iconItemId());
            commandBuilder.set(selector + " #PerkName.Text", perk.name());
            commandBuilder.set(selector + " #PerkState.Text", perk.stateLabel());
            commandBuilder.set(selector + " #PerkState.Style.TextColor", perk.stateColor());
            commandBuilder.set(selector + " #PerkSummary.Text", perk.summary());
            commandBuilder.set(selector + " #UnavailableOverlay.Visible", !perk.available());
            commandBuilder.set(selector + " #SelectedTop.Visible", selected);
            commandBuilder.set(selector + " #SelectedBottom.Visible", selected);
            commandBuilder.set(selector + " #SelectedLeft.Visible", selected);
            commandBuilder.set(selector + " #SelectedRight.Visible", selected);

            eventBuilder.addEventBinding(
                    CustomUIEventBindingType.Activating,
                    selector + " #SelectButton",
                    new EventData()
                            .append("Action", ACTION_SELECT_PERK)
                            .append("Slot", Integer.toString(i))
                            .append("PerkId", perk.id()),
                    false
            );
        }
    }

    private void renderDetails(@Nonnull UICommandBuilder commandBuilder, @Nonnull PerkView selected) {
        selectedPerkId = selected.id();

        commandBuilder.set(ROOT + " #DetailsPerkName.Text", selected.name());
        commandBuilder.set(ROOT + " #DetailsPerkState.Text", selected.stateLabel());
        commandBuilder.set(ROOT + " #DetailsPerkState.Style.TextColor", selected.stateColor());
        commandBuilder.set(ROOT + " #DetailsPerkDescription.Text", selected.description());
        commandBuilder.set(ROOT + " #DetailsCurrentLevel.Text", selected.currentLevelLine());
        commandBuilder.set(ROOT + " #DetailsCurrentUsage.Text", selected.currentUsageLine());
        commandBuilder.set(ROOT + " #DetailsCurrentCap.Text", selected.currentCapLine());
        commandBuilder.set(ROOT + " #DetailsNextLevel.Text", selected.nextLevelLine());
        commandBuilder.set(ROOT + " #DetailsNextCap.Text", selected.nextCapLine());
        commandBuilder.set(ROOT + " #DetailsNextCost.Text", selected.nextCostLine());
        commandBuilder.set(ROOT + " #UpgradeButton.Text", selected.upgradeLabel());
        commandBuilder.set(ROOT + " #UpgradeButton.Disabled", !selected.upgradeEnabled());
    }

    private void handleUpgrade(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        if (PERK_FERTILE_SOIL.equalsIgnoreCase(selectedPerkId)) {
            handleFertileSoilUpgrade(ref, store);
            return;
        }
        if (PERK_SELL_MULTIPLIER.equalsIgnoreCase(selectedPerkId)) {
            handleSellMultiplierUpgrade(ref, store);
            return;
        }
        setFooter(tr("mghg.perks.ui.footer.coming_soon", "This perk is not available yet."), FOOTER_ERROR_COLOR);
        refresh(ref, store);
    }

    private void handleFertileSoilUpgrade(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        MghgParcel parcel = MghgParcelManager.getByOwner(playerRef.getUuid());
        if (parcel == null) {
            setFooter(tr("mghg.perks.ui.footer.no_parcel", "Use /farm home first to unlock perks."), FOOTER_ERROR_COLOR);
            return;
        }

        MghgFarmPerkManager.UpgradeResult result =
                MghgFarmPerkManager.tryUpgradeFertileSoil(playerRef.getUuid(), parcel);

        switch (result.getStatus()) {
            case SUCCESS -> setFooter(
                    tf(
                            "mghg.perks.ui.footer.upgrade.success.fertile",
                            "Fertile Soil upgraded. Level %d, cap %d.",
                            result.getCurrentLevel(),
                            result.getCurrentCap()
                    ),
                    FOOTER_SUCCESS_COLOR
            );
            case MAX_LEVEL -> setFooter(
                    tr("mghg.perks.ui.footer.upgrade.max", "This perk is already max level."),
                    FOOTER_INFO_COLOR
            );
            case INSUFFICIENT_FUNDS -> setFooter(
                    tf(
                            "mghg.perks.ui.footer.upgrade.funds",
                            "Not enough balance. Need %s.",
                            formatMoney(result.getUpgradeCost())
                    ),
                    FOOTER_ERROR_COLOR
            );
            case INVALID_TARGET -> setFooter(
                    tr("mghg.perks.ui.footer.upgrade.invalid", "Could not apply upgrade."),
                    FOOTER_ERROR_COLOR
            );
        }

        refresh(ref, store);
    }

    private void handleSellMultiplierUpgrade(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store) {
        MghgParcel parcel = MghgParcelManager.getByOwner(playerRef.getUuid());
        if (parcel == null) {
            setFooter(tr("mghg.perks.ui.footer.no_parcel", "Use /farm home first to unlock perks."), FOOTER_ERROR_COLOR);
            return;
        }

        MghgFarmPerkManager.UpgradeResult result =
                MghgFarmPerkManager.tryUpgradeSellMultiplier(playerRef.getUuid(), parcel);

        switch (result.getStatus()) {
            case SUCCESS -> setFooter(
                    tf(
                            "mghg.perks.ui.footer.upgrade.success.sell",
                            "Sell Multiplier upgraded. Level %d.",
                            result.getCurrentLevel()
                    ),
                    FOOTER_SUCCESS_COLOR
            );
            case MAX_LEVEL -> setFooter(
                    tr("mghg.perks.ui.footer.upgrade.max", "This perk is already max level."),
                    FOOTER_INFO_COLOR
            );
            case INSUFFICIENT_FUNDS -> setFooter(
                    tf(
                            "mghg.perks.ui.footer.upgrade.funds",
                            "Not enough balance. Need %s.",
                            formatMoney(result.getUpgradeCost())
                    ),
                    FOOTER_ERROR_COLOR
            );
            case INVALID_TARGET -> setFooter(
                    tr("mghg.perks.ui.footer.upgrade.invalid", "Could not apply upgrade."),
                    FOOTER_ERROR_COLOR
            );
        }

        refresh(ref, store);
    }

    private @Nonnull List<PerkView> buildPerkViews(@Nullable MghgParcel parcel, double balance) {
        ArrayList<PerkView> perks = new ArrayList<>();
        perks.add(buildFertileSoilPerk(parcel, balance));
        perks.add(buildSellMultiplierPerk(parcel, balance));
        perks.add(buildComingSoonPerk(
                PERK_MUTATION_LUCK,
                tr("mghg.perks.ui.perk.mutation_luck.name", "Mutation Luck"),
                tr("mghg.perks.ui.perk.mutation_luck.summary", "Increase chance of rare mutation rolls."),
                tr("mghg.perks.ui.perk.mutation_luck.description", "Planned: boost Gold/Rainbow and special mutation probability."),
                "Mghg_Plant_Seeds_Lettuce"
        ));
        return perks;
    }

    private @Nonnull PerkView buildFertileSoilPerk(@Nullable MghgParcel parcel, double balance) {
        if (parcel == null) {
            return new PerkView(
                    PERK_FERTILE_SOIL,
                    "Mghg_Soil_Dirt_Tilled",
                    tr("mghg.perks.ui.perk.fertile_soil.name", "Fertile Soil"),
                    tr("mghg.perks.ui.perk.fertile_soil.state.locked", "Locked"),
                    "#f6a2a2",
                    tr("mghg.perks.ui.perk.fertile_soil.summary.locked", "Unlock your farm to enable fertile soil."),
                    tr("mghg.perks.ui.perk.fertile_soil.description", "Controls how many fertile soil blocks your parcel can track."),
                    tf("mghg.perks.ui.details.current_level", "Current level: %s", "-"),
                    tf("mghg.perks.ui.details.current_usage", "Used fertile blocks: %s", "-"),
                    tf("mghg.perks.ui.details.current_cap", "Current cap: %s", "-"),
                    tf("mghg.perks.ui.details.next_level", "Next level: %s", "-"),
                    tf("mghg.perks.ui.details.next_cap", "Next cap: %s", "-"),
                    tf("mghg.perks.ui.details.next_cost", "Upgrade cost: %s", "-"),
                    tr("mghg.perks.ui.button.upgrade.locked", "Locked"),
                    false,
                    false
            );
        }

        int level = MghgFarmPerkManager.getFertileSoilLevel(parcel);
        int used = MghgFarmPerkManager.getTrackedFertileCount(parcel);
        int cap = MghgFarmPerkManager.getFertileSoilCap(parcel);
        MghgFarmPerksConfig.FertileSoilLevel next = MghgFarmPerkManager.getNextFertileLevel(parcel);

        String state;
        String stateColor;
        String upgradeLabel;
        boolean upgradeEnabled;
        String nextLevelText;
        String nextCapText;
        String nextCostText;

        if (next == null) {
            state = tr("mghg.perks.ui.perk.state.max", "Maxed");
            stateColor = "#8fe388";
            upgradeLabel = tr("mghg.perks.ui.button.upgrade.max", "Max level");
            upgradeEnabled = false;
            nextLevelText = "-";
            nextCapText = "-";
            nextCostText = "-";
        } else {
            double cost = Math.max(0.0d, next.getUpgradeCost());
            boolean canUpgrade = balance + 0.00001d >= cost;
            state = canUpgrade
                    ? tr("mghg.perks.ui.perk.state.ready", "Ready to upgrade")
                    : tr("mghg.perks.ui.perk.state.funds", "Need more balance");
            stateColor = canUpgrade ? "#8fe388" : "#f2d896";
            upgradeLabel = canUpgrade
                    ? tf("mghg.perks.ui.button.upgrade.buy", "Upgrade (%s)", formatMoney(cost))
                    : tf("mghg.perks.ui.button.upgrade.need", "Need %s", formatMoney(cost));
            upgradeEnabled = canUpgrade;
            nextLevelText = Integer.toString(next.getLevel());
            nextCapText = Integer.toString(next.getMaxFertileBlocks());
            nextCostText = formatMoney(cost);
        }

        return new PerkView(
                PERK_FERTILE_SOIL,
                "Mghg_Soil_Dirt_Tilled",
                tr("mghg.perks.ui.perk.fertile_soil.name", "Fertile Soil"),
                state,
                stateColor,
                tf("mghg.perks.ui.perk.fertile_soil.summary", "Tracked fertile blocks: %d / %d", used, cap),
                tr("mghg.perks.ui.perk.fertile_soil.description", "Controls how many fertile soil blocks your parcel can track."),
                tf("mghg.perks.ui.details.current_level", "Current level: %s", Integer.toString(level)),
                tf("mghg.perks.ui.details.current_usage", "Used fertile blocks: %s", Integer.toString(used)),
                tf("mghg.perks.ui.details.current_cap", "Current cap: %s", Integer.toString(cap)),
                tf("mghg.perks.ui.details.next_level", "Next level: %s", nextLevelText),
                tf("mghg.perks.ui.details.next_cap", "Next cap: %s", nextCapText),
                tf("mghg.perks.ui.details.next_cost", "Upgrade cost: %s", nextCostText),
                upgradeLabel,
                true,
                upgradeEnabled
        );
    }

    private @Nonnull PerkView buildSellMultiplierPerk(@Nullable MghgParcel parcel, double balance) {
        if (parcel == null) {
            return new PerkView(
                    PERK_SELL_MULTIPLIER,
                    "Mghg_Plant_Crop_Lettuce_Item",
                    tr("mghg.perks.ui.perk.sell_multiplier.name", "Sell Multiplier"),
                    tr("mghg.perks.ui.perk.fertile_soil.state.locked", "Locked"),
                    "#f6a2a2",
                    tr("mghg.perks.ui.perk.sell_multiplier.summary.locked", "Unlock your farm to enable this perk."),
                    tr("mghg.perks.ui.perk.sell_multiplier.description", "Increase crop sell value globally."),
                    tf("mghg.perks.ui.details.current_level", "Current level: %s", "-"),
                    tf("mghg.perks.ui.details.current_usage", "Used fertile blocks: %s", "-"),
                    tf("mghg.perks.ui.details.current_cap", "Current cap: %s", tf("mghg.perks.ui.details.current_sell_multiplier", "Sell multiplier: x%s", "-")),
                    tf("mghg.perks.ui.details.next_level", "Next level: %s", "-"),
                    tf("mghg.perks.ui.details.next_cap", "Next cap: %s", tf("mghg.perks.ui.details.next_sell_multiplier", "Next sell multiplier: x%s", "-")),
                    tf("mghg.perks.ui.details.next_cost", "Upgrade cost: %s", "-"),
                    tr("mghg.perks.ui.button.upgrade.locked", "Locked"),
                    false,
                    false
            );
        }

        int level = MghgFarmPerkManager.getSellMultiplierLevel(parcel);
        double multiplier = MghgFarmPerkManager.getSellMultiplier(parcel);
        MghgFarmPerksConfig.SellMultiplierLevel next = MghgFarmPerkManager.getNextSellMultiplierLevel(parcel);

        String state;
        String stateColor;
        String upgradeLabel;
        boolean upgradeEnabled;
        String nextLevelText;
        String nextMultiplierText;
        String nextCostText;

        if (next == null) {
            state = tr("mghg.perks.ui.perk.state.max", "Maxed");
            stateColor = "#8fe388";
            upgradeLabel = tr("mghg.perks.ui.button.upgrade.max", "Max level");
            upgradeEnabled = false;
            nextLevelText = "-";
            nextMultiplierText = "-";
            nextCostText = "-";
        } else {
            double cost = Math.max(0.0d, next.getUpgradeCost());
            boolean canUpgrade = balance + 0.00001d >= cost;
            state = canUpgrade
                    ? tr("mghg.perks.ui.perk.state.ready", "Ready to upgrade")
                    : tr("mghg.perks.ui.perk.state.funds", "Need more balance");
            stateColor = canUpgrade ? "#8fe388" : "#f2d896";
            upgradeLabel = canUpgrade
                    ? tf("mghg.perks.ui.button.upgrade.buy", "Upgrade (%s)", formatMoney(cost))
                    : tf("mghg.perks.ui.button.upgrade.need", "Need %s", formatMoney(cost));
            upgradeEnabled = canUpgrade;
            nextLevelText = Integer.toString(next.getLevel());
            nextMultiplierText = formatMultiplier(next.getMultiplier());
            nextCostText = formatMoney(cost);
        }

        return new PerkView(
                PERK_SELL_MULTIPLIER,
                "Mghg_Plant_Crop_Lettuce_Item",
                tr("mghg.perks.ui.perk.sell_multiplier.name", "Sell Multiplier"),
                state,
                stateColor,
                tf("mghg.perks.ui.perk.sell_multiplier.summary", "Current sell multiplier: x%s", formatMultiplier(multiplier)),
                tr("mghg.perks.ui.perk.sell_multiplier.description", "Increase crop sell value globally."),
                tf("mghg.perks.ui.details.current_level", "Current level: %s", Integer.toString(level)),
                tf("mghg.perks.ui.details.current_usage", "Used fertile blocks: %s", tr("mghg.perks.ui.details.current_sell_scope", "Applies to crop sales in your farm.")),
                tf("mghg.perks.ui.details.current_cap", "Current cap: %s", tf("mghg.perks.ui.details.current_sell_multiplier", "Sell multiplier: x%s", formatMultiplier(multiplier))),
                tf("mghg.perks.ui.details.next_level", "Next level: %s", nextLevelText),
                tf("mghg.perks.ui.details.next_cap", "Next cap: %s", tf("mghg.perks.ui.details.next_sell_multiplier", "Next sell multiplier: x%s", nextMultiplierText)),
                tf("mghg.perks.ui.details.next_cost", "Upgrade cost: %s", nextCostText),
                upgradeLabel,
                true,
                upgradeEnabled
        );
    }

    private @Nonnull PerkView buildComingSoonPerk(
            @Nonnull String id,
            @Nonnull String name,
            @Nonnull String summary,
            @Nonnull String description,
            @Nonnull String iconItemId
    ) {
        return new PerkView(
                id,
                iconItemId,
                name,
                tr("mghg.perks.ui.perk.state.coming_soon", "Coming soon"),
                "#9cb0c6",
                summary,
                description,
                tf("mghg.perks.ui.details.current_level", "Current level: %s", "-"),
                tf("mghg.perks.ui.details.current_usage", "Used fertile blocks: %s", "-"),
                tf("mghg.perks.ui.details.current_cap", "Current cap: %s", "-"),
                tf("mghg.perks.ui.details.next_level", "Next level: %s", "-"),
                tf("mghg.perks.ui.details.next_cap", "Next cap: %s", "-"),
                tf("mghg.perks.ui.details.next_cost", "Upgrade cost: %s", "-"),
                tr("mghg.perks.ui.button.upgrade.coming_soon", "Coming soon"),
                false,
                false
        );
    }

    private @Nonnull PerkView resolveSelectedPerk(@Nonnull List<PerkView> perks) {
        for (PerkView perk : perks) {
            if (perk.id().equalsIgnoreCase(selectedPerkId)) {
                return perk;
            }
        }
        if (!perks.isEmpty()) {
            return perks.get(0);
        }
        return buildComingSoonPerk(
                "empty",
                tr("mghg.perks.ui.empty.name", "No perks"),
                tr("mghg.perks.ui.empty.summary", "No perk entries are available."),
                tr("mghg.perks.ui.empty.description", "Configure perks in the server resources."),
                "unknown"
        );
    }

    private @Nullable PerkView resolvePerk(@Nonnull EventPayload payload) {
        if (payload.perkId != null && !payload.perkId.isBlank()) {
            for (PerkView perk : renderedPerks) {
                if (perk.id().equalsIgnoreCase(payload.perkId)) {
                    return perk;
                }
            }
        }
        int slot = resolveSlot(payload);
        if (slot < 0 || slot >= renderedPerks.size()) {
            return null;
        }
        return renderedPerks.get(slot);
    }

    private void applyLocalizedUiText(@Nonnull UICommandBuilder commandBuilder) {
        commandBuilder.set(ROOT + " #PageTitle.Text", tr("mghg.perks.ui.title", "Magic HyGarden Perks"));
        commandBuilder.set(ROOT + " #PerkListTitle.Text", tr("mghg.perks.ui.list.title", "Perk Catalog"));
        commandBuilder.set(ROOT + " #HeaderBalanceLabel.Text", tr("mghg.perks.ui.header.balance", "Balance"));
        commandBuilder.set(ROOT + " #HeaderParcelLabel.Text", tr("mghg.perks.ui.header.parcel", "Parcel"));
        commandBuilder.set(ROOT + " #DetailsSectionTitle.Text", tr("mghg.perks.ui.details.title", "Perk Details"));
        commandBuilder.set(ROOT + " #UpgradeButton.Text", tr("mghg.perks.ui.button.upgrade", "Upgrade"));
        commandBuilder.set(ROOT + " #CloseButton.Text", tr("mghg.perks.ui.button.close", "Close"));
    }

    private void setFooter(@Nonnull String message, @Nonnull String color) {
        footerMessage = safeText(message, tr("mghg.perks.ui.footer.default", "Choose a perk and upgrade when ready."));
        footerColor = color == null || color.isBlank() ? FOOTER_INFO_COLOR : color;
    }

    private @Nonnull String tr(@Nonnull String key, @Nonnull String fallback) {
        String translated = translateKey(playerRef, key);
        if (translated.equals(key) || translated.equals(LANG_PREFIX + key) || translated.isBlank()) {
            return fallback;
        }
        return translated;
    }

    private @Nonnull String tf(@Nonnull String key, @Nonnull String fallback, Object... args) {
        String template = tr(key, fallback);
        try {
            return String.format(Locale.ROOT, template, args);
        } catch (Exception ignored) {
            return String.format(Locale.ROOT, fallback, args);
        }
    }

    private static String translateKey(@Nullable PlayerRef playerRef, @Nonnull String key) {
        I18nModule i18n = I18nModule.get();
        if (i18n == null) {
            return key;
        }

        String language = playerRef != null ? playerRef.getLanguage() : null;
        String normalized = key.startsWith(LANG_PREFIX) ? key : (LANG_PREFIX + key);

        String direct = i18n.getMessages(language).get(key);
        if (direct != null && !direct.isBlank()) {
            return direct;
        }

        String prefixed = i18n.getMessages(language).get(normalized);
        if (prefixed != null && !prefixed.isBlank()) {
            return prefixed;
        }
        return key;
    }

    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim();
    }

    private static int resolveSlot(@Nonnull EventPayload payload) {
        if (payload.slotIndex != null) {
            return payload.slotIndex;
        }
        if (payload.slot != null && !payload.slot.isBlank()) {
            try {
                return Integer.parseInt(payload.slot.trim());
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private static String safeText(@Nullable String value, @Nonnull String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static String formatMoney(double value) {
        return String.format(Locale.ROOT, "$%.2f", Math.max(0.0d, value));
    }

    private static String formatMultiplier(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return "1.00";
        }
        return String.format(Locale.ROOT, "%.2f", Math.max(0.0d, value));
    }

    private record PerkView(
            @Nonnull String id,
            @Nonnull String iconItemId,
            @Nonnull String name,
            @Nonnull String stateLabel,
            @Nonnull String stateColor,
            @Nonnull String summary,
            @Nonnull String description,
            @Nonnull String currentLevelLine,
            @Nonnull String currentUsageLine,
            @Nonnull String currentCapLine,
            @Nonnull String nextLevelLine,
            @Nonnull String nextCapLine,
            @Nonnull String nextCostLine,
            @Nonnull String upgradeLabel,
            boolean available,
            boolean upgradeEnabled
    ) {
    }

    public static final class EventPayload {
        public static final BuilderCodec<EventPayload> CODEC = BuilderCodec.builder(EventPayload.class, EventPayload::new)
                .append(new KeyedCodec<>("Action", Codec.STRING), (e, v) -> e.action = v, e -> e.action)
                .add()
                .append(new KeyedCodec<>("PerkId", Codec.STRING, true), (e, v) -> e.perkId = v, e -> e.perkId)
                .add()
                .append(new KeyedCodec<>("Slot", Codec.STRING, true), (e, v) -> e.slot = v, e -> e.slot)
                .add()
                .append(new KeyedCodec<>("SlotIndex", Codec.INTEGER, true), (e, v) -> e.slotIndex = v, e -> e.slotIndex)
                .add()
                .build();

        private String action;
        private String perkId;
        private String slot;
        private Integer slotIndex;
    }
}
