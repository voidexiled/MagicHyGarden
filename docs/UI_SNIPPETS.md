# UI Snippets and Patterns

This note captures reusable UI patterns from vanilla assets and external mods, with local examples already implemented in MagicHyGarden.

## Primary references
- Vanilla merchant/card layout:
  - `AssetsVanilla/Common/UI/Custom/Pages/BarterPage.ui`
  - `AssetsVanilla/Common/UI/Custom/Pages/BarterTradeRow.ui`
- Party-style tab navigation:
  - `other-plugins-example/hytale-party-mod-main/src/main/resources/Common/UI/Custom/PartyMenu.ui`
- Current MGHG implementations:
  - `src/main/resources/Common/UI/Custom/Pages/Mghg_FarmShopPage.ui`
  - `src/main/resources/Common/UI/Custom/Pages/Mghg_FarmShopPage_V2.ui`
  - `src/main/resources/Common/UI/Custom/Pages/Mghg_FarmShopTradeCard_V2.ui`

## Pattern: decorated full-screen page
Use this for modal, player-focused screens.

```ui
$C = "../Common.ui";

$C.@PageOverlay {}

Group #MyPageRoot {
  Anchor: (Left: 0, Top: 0, Right: 0, Bottom: 0);

  $C.@DecoratedContainer {
    Anchor: (Width: 980, Height: 720);
    #Title { Group { $C.@Title #PageTitle { @Text = "TITLE"; } } }
    #Content { LayoutMode: Top; }
  }
}

$C.@BackButton {}
```

## Pattern: scrollable wrapped card grid
Use for shop offers, catalogs, recipes, etc.

```ui
Group #GridFrame {
  LayoutMode: Top;
  Background: (TexturePath: "../Common/ContainerPanelPatch.png", Border: 4, Color: #0d1b2a);
  Padding: (Horizontal: 6, Vertical: 6);

  Group #GridScroller {
    Anchor: (Left: 0, Top: 0, Right: 0, Bottom: 0);
    LayoutMode: TopScrolling;
    ScrollbarStyle: $C.@DefaultScrollbarStyle;

    Group #GridHost {
      LayoutMode: LeftCenterWrap;
    }
  }
}
```

Server-side append/update flow:
- `commandBuilder.clear("#PageRoot #GridHost");`
- `commandBuilder.append("#PageRoot #GridHost", "Pages/MyCardTemplate.ui");`
- Target each instance with `#GridHost[index] ...`.

## Pattern: reusable card template
Use one `.ui` card template with stable ids (`#TradeButton`, `#OutputItem`, `#Stock`, etc.) and append it per entry from Java.

Why this matters:
- avoids giant static page markup
- keeps card logic isolated
- easy to iterate visuals without changing page structure

## Pattern: icon tabs (party-style)
Use `TabNavigation` + custom textures when sections are conceptually different (buy/sell/settings), not for tiny option toggles.

Guideline:
- Keep selected state very clear.
- Avoid hover effects that move layout or create flicker.
- Keep tab icons consistent size and contrast.

## Pattern: tooltips with rich text from Java
For dynamic item pricing details, build `Message` spans in Java and assign to `TooltipTextSpans`.

```java
commandBuilder.set("#Root #Card #TradeButton.TooltipTextSpans", tooltipMessage);
```

Use this for:
- computed pricing formulas
- per-item stock/balance context
- mutation/rarity details without cluttering the base card

## Pattern: localization-safe UI text
Set all visible labels from Java with language keys (`server.<key>`), with fallback text.

Checklist:
- no hardcoded gameplay text in `.ui` beyond placeholders
- command feedback lines and UI labels share keys
- both `en-US` and `es-ES` include the same keys for shop-critical paths

## Pattern: V2 shop wiring
Current V2 action entrypoint:
- `/farm shop openv2` (alias `/farm shop v2`)

Files:
- UI: `src/main/resources/Common/UI/Custom/Pages/Mghg_FarmShopPage_V2.ui`
- Card: `src/main/resources/Common/UI/Custom/Pages/Mghg_FarmShopTradeCard_V2.ui`
- Logic: `src/main/java/com/voidexiled/magichygarden/features/farming/ui/MghgFarmShopPageV2.java`
- Command hook: `src/main/java/com/voidexiled/magichygarden/commands/farm/subcommands/shop/FarmShopCommand.java`
