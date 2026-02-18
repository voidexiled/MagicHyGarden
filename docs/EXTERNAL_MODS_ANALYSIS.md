# External Mods Analysis and Adoption Plan

Date: 2026-02-17
Scope: Analysis of external reference mods under `other-plugins-example/` and concrete adoption plan for MagicHyGarden.

## Repositories Reviewed

- `other-plugins-example/hytale-party-mod-main`
- `other-plugins-example/Hytrade-main`
- `other-plugins-example/SocialMenu-main`
- `other-plugins-example/DynamicTooltipsLib-main`
- `other-plugins-example/hytale-messagetags-main`

## Licensing and Reuse Constraints

- `other-plugins-example/Hytrade-main/LICENSE.md`: custom permissive license, commercial server use allowed, resale forbidden.
- `other-plugins-example/SocialMenu-main/LICENSE.md`: same model as Hytrade.
- `other-plugins-example/DynamicTooltipsLib-main/LICENSE`: permissive with anti-resale/anti-trivial-fork constraints.
- `other-plugins-example/hytale-messagetags-main/LICENSE`: MIT.
- `other-plugins-example/hytale-party-mod-main/README.md` claims MIT, but no LICENSE file found in repo root. Treat as inspiration-only unless explicit license file is added.

## Findings by Mod

### 1) Party Mod (UI + Group System Inspiration)

Relevant files:
- `other-plugins-example/hytale-party-mod-main/src/main/resources/Common/UI/Custom/PartyMenu.ui`
- `other-plugins-example/hytale-party-mod-main/src/main/java/com/gaukh/partymod/pages/PartyMenuPage.java`
- `other-plugins-example/hytale-party-mod-main/src/main/java/com/gaukh/partymod/party/Party.java`

What is worth adopting:
- State-driven page with tabs and sub-views inside a single custom page.
- Reusable UI style tokens (`@SectionTitleStyle`, `@AccessButtonStyle`, etc.).
- Dynamic list rendering pattern with append/clear for entries (members, invites, public groups).

Critical issues in the reference code (do not copy as-is):
- Inverted membership checks in role-control methods:
  - `Party.java:228` (`setRole`)
  - `Party.java:235` (`promote`)
  - `Party.java:248` (`demote`)
  - `Party.java:261` (`transferLeadership`)
- These conditions return early for valid members and break expected behavior.

Conclusion:
- Use this mod as UI/UX and page-architecture reference only.
- Reimplement group logic from scratch in MGHG with strict permission checks and explicit tests.

### 2) HyTrade (Trade System + UI Reference)

Relevant files:
- `other-plugins-example/Hytrade-main/src/main/java/com/clayrok/hytrade/Hytrade.java`
- `other-plugins-example/Hytrade-main/src/main/java/com/clayrok/hytrade/data/TradeData.java`
- `other-plugins-example/Hytrade-main/src/main/java/com/clayrok/hytrade/pages/TradePanel.java`
- `other-plugins-example/Hytrade-main/src/main/resources/Common/UI/Custom/TradePanel.ui`

What is worth adopting:
- Good two-column trade UI with clear ownership zones and confirmation flow.
- Trade request dialog pattern (yes/no/ignore).
- Per-player settings page (layout presets, ignore list, language).

Technical risks to avoid:
- Money tracked as float (`TradeData.java:21`, `TradeData.java:71`) -> precision risk.
- Global mutable list for active trades (`Hytrade.java:46`) without stronger lifecycle guards.
- Finalization order performs item transition then money settlement (`Hytrade.java:217`, `Hytrade.java:236`, `Hytrade.java:242`) and lacks robust rollback strategy if post-item step fails.

Conclusion:
- Adopt UX flow and layout ideas.
- Rebuild transaction engine with integer cents and atomic finalize semantics.

### 3) SocialMenu (Player Wheel-Click Panel)

Relevant files:
- `other-plugins-example/SocialMenu-main/src/main/java/com/clayrok/SocialMenuInteractionWatcher.java`
- `other-plugins-example/SocialMenu-main/src/main/java/com/clayrok/pages/SocialMenuPage.java`
- `other-plugins-example/SocialMenu-main/src/main/resources/config/config.json`

What is worth adopting:
- Open social panel from interaction packet watcher on player target.
- Config-driven action groups with permission filters.
- Action forms with typed user input variables.

Risk to control:
- Command-template execution can become command injection or privilege abuse if not hard-validated.

Conclusion:
- Adopt the interaction model and grouped actions UI.
- Execute only server-registered actions (enum/registry), not raw command strings.

### 4) DynamicTooltipsLib (Tooltip API)

Relevant files:
- `other-plugins-example/DynamicTooltipsLib-main/src/main/java/org/herolias/tooltips/api/DynamicTooltipsApi.java`
- `other-plugins-example/DynamicTooltipsLib-main/src/main/java/org/herolias/tooltips/api/TooltipProvider.java`
- `other-plugins-example/DynamicTooltipsLib-main/src/main/java/org/herolias/tooltips/internal/TooltipPacketAdapter.java`

What is worth adopting:
- Provider-based API design with priority ordering.
- Separation of additive lines vs destructive overrides.
- Explicit refresh/invalidate API hooks.

Complexity warning:
- Internal packet virtualization layer is deep and invasive (`TooltipPacketAdapter.java` handles outbound+inbound rewrites and virtual IDs across many packet paths).

Conclusion:
- Integrate as optional dependency first (adapter layer), then register MGHG providers.
- Do not rewrite virtual-ID packet engine inside MGHG unless absolutely required.

### 5) MessageTags (Formatting/Parsing)

Relevant files:
- `other-plugins-example/hytale-messagetags-main/messagetags-api/src/eu/koboo/messagetags/api/MessageTags.java`
- `other-plugins-example/hytale-messagetags-main/messagetags-api/src/eu/koboo/messagetags/api/MessageParser.java`

What is worth adopting:
- Lightweight, extensible tag parsing for rich text with custom handlers.
- Easy static API (`parse`, `strip`) and custom parser creation.
- Good for unifying UI labels, notifications, logs, and chat formatting.

Conclusion:
- This is a clean dependency candidate (MIT) for formatting standardization.

## Proposed Architecture for MagicHyGarden

### 1) Gangs (Guilds)

Goal:
- Replace party concept with persistent economy-linked gangs.

Suggested module layout:
- `src/main/java/com/voidexiled/magichygarden/features/social/gangs/`
  - `MghgGang`, `MghgGangMember`, `MghgGangRole`, `MghgGangPermissions`
  - `MghgGangManager`, `MghgGangInviteService`, `MghgGangStorage`
  - `MghgGangSellBoostService`

Data and economy rules:
- Gang-wide configurable sell boost based on active members.
- Base formula:
  - `finalSell = cropSellValue * gangBoostMultiplier`
  - `gangBoostMultiplier = 1 + min(maxCap, onlineMembers * perMemberBoost)`
- All values config-driven, persisted, and shown in sell UI tooltip.

Permissions model (minimum):
- `OWNER`, `OFFICER`, `MEMBER`.
- Permissions:
  - invite, kick, edit roles, edit settings, disband, gang bank actions (future).

### 2) Trade System

Goal:
- Safe player-to-player exchange for items + money, compatible with MGHG crops/meta pricing.

Suggested module layout:
- `src/main/java/com/voidexiled/magichygarden/features/social/trade/`
  - `MghgTradeSession`, `MghgTradeOffer`, `MghgTradeManager`
  - `MghgTradeValidationService`, `MghgTradeFinalizeService`
  - `MghgTradeUiPage`, `MghgTradeRequestDialog`

Critical implementation rules:
- Money uses integer cents (`long`) only.
- Two-phase finalize:
  1) Validate both sides (inventory space, funds, ownership, constraints).
  2) Commit all transfers in one world task.
- If any validation fails, no partial transfer.
- Do not allow duplicate session per player.

MGHG-specific rules:
- Preserve crop metadata when moved in trade.
- Support selling/trading mutated crops as normal item stacks without metadata loss.

### 3) Social Panel

Goal:
- Right-click/pick target player -> compact action menu.

Suggested module layout:
- `src/main/java/com/voidexiled/magichygarden/features/social/panel/`
  - `MghgSocialPanelWatcher`, `MghgSocialPanelPage`, `MghgSocialActionsRegistry`

Safe action model:
- UI actions resolve to registered handlers, not free-text command execution.
- Action args are typed and validated before execution.

Default actions:
- Trade request
- Gang invite
- Parcel visit request (future)
- Message shortcut

### 4) Dynamic Tooltips Integration

Goal:
- Rich tooltips in inventory for crops/seeds with pricing factors and MGHG data.

Strategy:
- Optional integration layer:
  - `src/main/java/com/voidexiled/magichygarden/features/farming/tooltips/MghgTooltipBridge.java`
  - Register provider only if library is present.
- Provider outputs:
  - crop type, quality/mutation labels
  - base price, multipliers, final value
  - gang bonus contribution (if applicable)

Fallback:
- If dependency missing, current in-shop custom tooltip behavior remains unchanged.

### 5) Message Formatting Layer

Goal:
- One consistent message formatting path for commands/HUD/notifications/UI labels.

Suggested module layout:
- `src/main/java/com/voidexiled/magichygarden/utils/text/`
  - `MghgMessageFormat`, `MghgTextTags`, `MghgTextPreset`

Policy:
- Centralize all colored/styled messages.
- Forbid hardcoded color snippets spread in command classes.

## UI/UX Direction to Adopt

From Party + Hytrade + SocialMenu references:
- Keep high readability with strong section titles and clear action groups.
- Split action areas by context (left/right ownership or category).
- Use modal confirm steps only for destructive/transactional actions.
- Keep settings page independent from operational page.
- Reuse common UI components across pages (button rows, list entries, headers).

## Recommended Delivery Phases

Phase A (foundation, low risk)
- Add `MessageTags` integration wrapper.
- Add shared UI component kit for social pages.
- Add social action registry scaffold.

Phase B (core gameplay value)
- Implement gangs backend + commands + basic UI.
- Hook gang sell boost into shop pricing pipeline.

Phase C (economy feature)
- Implement trade session backend + request dialog + trade panel.
- Add anti-abuse checks (distance, combat cooldown, world restrictions).

Phase D (polish)
- Add social panel trigger and wire actions.
- Add optional DynamicTooltips provider for crops/seeds.

## Immediate Next Step

- Start with **Phase A + B** (Gangs + pricing integration), because it has direct economic impact and reuses your existing farm/shop systems immediately.
