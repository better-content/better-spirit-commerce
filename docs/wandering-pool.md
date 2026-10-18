# Wandering spirit pool

## Authority and boundary

`src/main/resources/data/better_spirit_commerce/spiritcommerce/wandering_pool.json`
(`bc.wandering_pool.v1`) owns the active wandering goods, prices, selection buckets and explicit
commodity ceilings. `WanderingPool` validates it; `EconomyPolicy.wanderingRows` exposes the full
active pool to affordability and other consumers. The legacy economy v4 resource continues to own
acquisition, retired currencies, legacy profession declarations and the unchanged Doctor catalogue.
It is not a second active wandering price authority.

These are direct matching-spirit purchases, independent of [resident barter](commerce-systems.md).
No customer ingredients, production/demand simulation, workstations, commissions, profession levels,
player progression checks or acquisition/remapping changes are introduced.

## Pool and assortment

Each of eight identities has **35 goods plus its own campfire**: 288 distinct authored outputs.
A fully populated pack trader receives:

| Authored bucket | Chosen per visit |
|---|---:|
| Fixed identity staples | 4 |
| Specialist supplies | 4 |
| Equipment/components (care supplies for Renewal) | 3 |
| Standout purchase | 1 |
| Matching campfire | 1 |
| **Core goods** | **13** |

Other than the four staples and campfire, selection is uniform without replacement within its
bucket. The deterministic seed uses the trader's natively saved UUID **and actual theme**; it does
not depend on menu opens, world time, players or current stock. A preliminary join-time fallback
theme therefore cannot lock in the wrong basket when the visit owner applies the final theme.
Different traders can carry different assortments. Native save/reload retains the same UUID/theme,
selection and used offers. Repairing an old/foreign menu carries already-spent output units forward
rather than replenishing its selected goods.

The existing village-starter egg remains an **independent extra listing**, as does a genuine native
surveyed Font map when available: **13 core goods + egg + optional map**, not thirteen total
including those surfaces. The Doctor retains its separate 48-row, eight-selected-per-day catalogue.
No villager fixed shops are enabled.

Commerce also runs in optional-mod/source-test profiles. Missing item registrations are omitted,
never replaced by air or duplicated. If a bucket is short, remaining registered goods of the same
theme fill it up to twelve; if fewer than twelve goods exist, the menu is smaller. The fully
populated pack uses the complete authored 4/4/3/1 split.

## Prices, state and stock

Prices are fixed **1–64 matching spirits per complete batch**. Quantity and `maxUses` separately
define the batch and number of purchases per visit. There is no second ingredient/payment slot.
The matching campfire remains one spirit for one fire, with sixteen purchases per visit; purchased
recovery and scenic camp ownership/no-drops behavior are unchanged.

New equipment uses native default item instances. Bags, tanks and waterskins are empty; pressure
weapons include no air; loose upgrades include no base equipment. Books, parchment and maps are
blank, except the separate native surveyed-map offer. Flight rockets explicitly have Flight 1 and
no explosive payload. There are no undefined enchanted-book, potion, repair-kit or TCon material
placeholder outputs.

World-finite import stock is keyed by **output commodity**, not selected row, price, currency,
trader UUID or visit. A new trader and a Doctor selling the same spirit-paid output cannot mint
another allocation. Depletion disables purchases without rerolling the basket.

- The existing **22 global commodity ceilings, totalling 64 units**, are frozen explicitly and
  retain their prior all-merchant scope; sampled menu sizes no longer calculate those ceilings.
- Additional mineral/material-bearing goods have the explicit proposed ceilings in the authored
  pool. These limits apply only to **spirit-paid imports**, not independently produced resident
  goods-for-goods barter. Biological/renewable goods use their visit limits.
- The stock save upgrades from v1/v2 to v3. Existing balances, including zero, never replenish.
  New import commodity keys seed once. A missing legacy global key in an old save fails closed
  at zero, rather than issuing another diamond/netherite/Elytra allocation. Managed missing keys
  also fail closed at purchase time; a missing known key in an existing v3 save does not reseed
  on reload. Future commodity additions need an explicit stock-version migration.

The selected prices/ceilings are authored starting balance, not measured claims about kills/hour.
Mob income includes explicit modded mappings, native category defaults and the conditional hostile
fallback, followed by current regional currency allocation. Nothing here assumes only vanilla mobs
supply spirits or that a dangerous default-mapped boss yields a large ordinary-currency jackpot.

## Validation

Deterministic tests cover full pool validation, all eight bucket quotas, reachability and diversity,
input-order independence, saved-UUID/theme selection, optional profiles, fixed legacy ceilings and
non-replenishing stock migration. Native GameTests exercise menu reopen/save/reload/repair with
spent goods and eggs, and currency-independent finite imports without coupling resident barter.
Use the repository's documented source gate. Full-pack runtime/visual acceptance is separate and
must use the exact integrated candidate under the [shared testing procedure](../../../better-content-modpack/docs/testing.md).
