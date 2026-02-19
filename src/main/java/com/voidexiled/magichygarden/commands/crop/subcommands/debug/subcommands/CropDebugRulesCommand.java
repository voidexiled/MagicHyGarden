package com.voidexiled.magichygarden.commands.crop.subcommands.debug.subcommands;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.voidexiled.magichygarden.features.farming.modifiers.MghgCropGrowthModifierAsset;
import com.voidexiled.magichygarden.features.farming.state.MghgMutationRule;
import com.voidexiled.magichygarden.features.farming.state.MghgMutationRuleSet;
import com.voidexiled.magichygarden.features.farming.state.MghgMutationRules;
import javax.annotation.Nonnull;

import java.util.List;

public class CropDebugRulesCommand extends AbstractPlayerCommand {
    public CropDebugRulesCommand() {
        super("rules", "magichygarden.command.crop.debug.rules.description");
    }

    @Override
    protected void execute(
            @Nonnull CommandContext ctx,
            @Nonnull Store<EntityStore> store,
            @Nonnull Ref<EntityStore> playerEntityRef,
            @Nonnull PlayerRef playerRef,
            @Nonnull World world
    ) {
        MghgCropGrowthModifierAsset cfg = MghgCropGrowthModifierAsset.getLastLoaded();
        MghgMutationRuleSet rules = MghgMutationRules.getActive(cfg);
        int count = (rules == null || rules.getRules() == null) ? 0 : rules.getRules().size();

        StringBuilder sb = new StringBuilder(256);
        sb.append("Mutation rules loaded: ").append(count).append('\n');

        if (cfg != null) {
            sb.append("DefaultCooldownSeconds: ").append(cfg.getMutationRollCooldownSeconds()).append('\n');
        } else {
            sb.append("DefaultCooldownSeconds: <no growth config loaded>\n");
        }

        if (rules != null && !rules.isEmpty()) {
            List<MghgMutationRule> list = rules.getRules();
            int limit = Math.min(8, list.size());
            for (int i = 0; i < limit; i++) {
                MghgMutationRule rule = list.get(i);
                if (rule == null) continue;
                sb.append("- ");
                String id = rule.getId();
                if (id != null && !id.isBlank()) {
                    sb.append(id);
                } else {
                    sb.append("<no-id>");
                }
                sb.append(" slot=").append(rule.getSlot());
                sb.append(" chance=").append(rule.getChance());
                sb.append(" priority=").append(rule.getPriority());
                sb.append('\n');
            }
            if (list.size() > limit) {
                sb.append("... (").append(list.size() - limit).append(" more)\n");
            }
        }

        ctx.sendMessage(com.voidexiled.magichygarden.utils.chat.MghgChat.text(sb.toString()));
    }
}
