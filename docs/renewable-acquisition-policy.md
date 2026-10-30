# Renewable NPC output policy (source-only integration)

This is source-local implementation, not native or pack acceptance. No cooking dependency, upstream
JAR patch, reflection, deployment, activation, Packwiz refresh, commit or push is introduced.
The pre-existing dirty `build.gradle.kts` is not part of this change and was not edited.

## API

`com.bettercontent.spiritcommerce.api.RenewableAcquisitionPolicy`:

```java
static void register(ResourceLocation owner, Predicate<ItemStack> allowed);
static boolean allows(ItemStack output);
static OutputPolicyRegistry.Decision<ResourceLocation> decision(ItemStack output);
static boolean allowsAll(Iterable<ItemStack> outputs);
static boolean commitIfAllowed(List<ItemStack> generatedOutputs, BooleanSupplier transaction);
static boolean allowsMerchant(Merchant merchant, ItemStack output);
```

Register during common setup, before the first output query. Duplicate owner IDs and registration
after the first query throw visibly. Predicates must be pure/server-safe, return true for outputs they
do not restrict, and receive independent ItemStack copies. Exceptions identify the failing owner and
propagate before debit; they never become permission. Multiple/overlapping providers combine by
logical AND: any denial wins. `Decision` includes immutable denying-owner IDs for diagnostics.
Without a provider, all previously allowed outputs remain allowed. Commerce registers no cooking
policy itself. Cooking owns the explicit `FoodProfile.role == meal` predicate; raw ingredients,
basic staples and nonfood therefore remain unrestricted by that policy.

The pure `OutputPolicyRegistry<K,T>` implements this same gate and is directly tested without a
Minecraft bootstrap. `commitIfAllowed` re-evaluates generated outputs immediately before invoking
the real transaction callback. An empty generated-output list means a transfer of finite existing
stock, not permission to omit outputs from a production transaction.

## Actual resident coverage / dispositions

The real registered path is `BetterSpiritCommerce -> ResidentInteractions -> ResidentNetwork ->
ResidentBarter.offers/quote/execute`, plus `ResidentService` ticks. The obsolete spirit-villager
catalogue is not used to claim resident coverage.

| Surface | Disposition |
|---|---|
| Existing finite resident items, including old food or player gifts | Still offered/transferred subject to existing surplus/needs; no new generation |
| Resident consumption of existing old food | Unchanged; no consumption blacklist |
| `ResidentBarter.context` recipe index / generated offer listing | Denied outputs excluded before listing |
| `craft`, all recursive subrecipes | Every new result checked before staged input work; denied intermediate cannot be laundered through an allowed final result |
| Crafting remainders | Checked and retained in the generated-output list for final validation |
| Resident direct execution / stale quote | Fresh plan and final generated-output gate before `takePayment`, resident inventory replacement, result delivery, stats/events; no debit-then-refund workaround |
| Resident barter cycles | `preview` uses the same fresh guarded preparation before any cycle inventory is replaced |
| Resident purchase from a player stall | Existing `paymentPlan` uses guarded new NPC manufacture; finite NPC payment stock remains usable |
| Player-stocked stall sale/output, configuration, deposits, proceeds | Untouched; no policy import added to PlayerStallTrade/BlockEntity/ResidentState |
| New resident join stock/tool seed | Each generated item checked before insertion |
| New settlement resident seed | Same `ResidentService.addGenerated` gate; existing saved stock not rewritten |
| Water collection / purification | Generated output checked before bottle, input, fuel debit |
| Crop harvest | All actual rolled drops checked before seed shrink or crop reset; denied drops are not emitted into the world |
| Surface block harvest | Rolled drops checked before equipment movement, destruction, tool wear or inventory/drop insertion |
| Whole-tree harvest | All actual per-log drop lists precomputed and checked before equipping/debiting a tool or destroying the first log; no partial-tree debit on a later denied drop |
| Player gifts / transfers of existing equipment | Untouched; not routed through a global ResidentState.add blacklist |

Drop planning uses read-only selection of the tool the resident would equip. It does not re-roll
accepted drops after policy approval. Native tool/entity-dependent custom loot behavior still needs
in-world verification; source compilation is not proof of every third-party loot condition.

A player-stocked meal sale is not forbidden. If its price itself requires an NPC to **manufacture**
a new forbidden meal because the NPC lacks finite payment stock, that new manufacture is denied,
not the player's stocked output. The player can still sell for permitted raw/staple/nonfood payment
or finite payment already held by the buyer.

## Actual merchant coverage and owner boundary

Native `AbstractVillager.getOffers` applies a final server-side filter **after** custom daily,
themed, starter and font appends. Plague Doctor daily generation skips denied selected outputs,
without drawing replacement rows/unlocking additional stock. Themed wandering generation skips
denied rows, and catalogue validation treats intentionally denied rows as satisfied; filtering must
not trigger a rebuild that resets remaining permitted offers' uses.

`allowsMerchant` applies only when the actual merchant is a server-side `AbstractVillager`.
Non-NPC/player-funded Merchant implementations (including a custom Wares-style merchant) bypass
this NPC production policy. There is deliberately **no global ownerless MerchantOffer.take mixin**.
Resident finite-stock barter uses its separate custom path and is not an AbstractVillager merchant
output even when its resident entity is a villager.

Mapped Forge47.4.13 (this project's actual pin) and Forge47.4.22 bytecode was inspected;
the three relevant class disassemblies are identical across those pins, not guessed:

- `MerchantResultSlot` holds the actual `merchant` and `slots` fields.
- `onTake` calls achievement hooks before two `MerchantOffer.take` invocations and updates XP later.
- `MerchantMenu.quickMoveStack` moves result slot2 into player inventory **before** calling `onTake`.

The new owner-aware mixins therefore gate:

1. MerchantContainer final preview installation (clear denied result/active offer/future XP; preserve payment slots).
2. MerchantResultSlot pickup permission and result removal.
3. MerchantMenu result-slot shift-click **before** output inventory movement.
4. MerchantResultSlot `onTake` at HEAD, before achievements/XP/payment, plus an owner-aware redirect
   at both actual `MerchantOffer.take` call sites.

A stale denied NPC result cannot use the ordinary native paths to debit payment, award output,
achievements or XP. No refund substitution or automatic stock reset is used. Policies are pure and
server execution is serialized; a provider that mutates its own decision inside one transaction
violates the registration contract and is not a supported synchronization mechanism.

### Explicit unresolved ownership/native audit boundaries

- An unknown plugin could use an `AbstractVillager` as the backing entity for player-funded stock.
  That is inside the current NPC type boundary and needs an explicit audited owner adapter/exemption,
  not a guessed exclusion. This is a qualification gap, not a claim of compatibility.
- A custom non-AbstractVillager NPC Merchant is not silently classified as renewable. Its actual
  registered endpoint must be audited before adding an owner adapter. Resident rats using the
  registered resident barter path are covered independently of their Java merchant class.
- Third-party menus that do not use the native MerchantMenu/ResultSlot transaction seams require
  independent native integration. Do not infer coverage merely from their UI looking like a villager.

## Validation / evidence

- Bounded Java17 direct compilation against actual cached mapped Forge47.4.22 and source-local
  Commerce classes passed: `/home/dev/.tmp/commerce-acquisition-20261028/javac.log`.
  Repeated against the project's exact Forge47.4.13 pin: PASS
  `/home/dev/.tmp/commerce-acquisition-20261028/javac-forge47.4.13.log`.
- **17 direct deterministic/source-wiring tests passed** in
  `/home/dev/.tmp/commerce-acquisition-20261028/direct-tests.log`.
  They cover no provider, meal-vs-raw/staple policy, duplicate/late/conjunctive overlap, isolated
  copies, stale listing/commit denial before any payment/output/refund callback, denied suboutputs,
  finite stock, exceptions, owner-negative behavior, actual endpoint wiring, both seed outlets,
  before-debit source ordering, owner-aware native hook wiring, and permitted wandering-stock retention.
- Actual mapped bytecode retained at `/home/dev/.tmp/commerce-merchant-{slot,menu,container}-javap.txt`.
- Source `git diff --check` passed.
- Coordinator-authorized source-local `compileJava test stageRuntimeJar` passed in20seconds under
  the shared fail-fast Gradle lock, Java17, Xmx2G, 2workers and 2CPU: **89 JUnit tests / 34 suites,
  zero failures/errors**. Log `/home/dev/.tmp/commerce-acquisition-20261028/gradle-final-20261028T1015.log`.
- Final source-local JAR `build/libs/better-spirit-commerce-1.0.1.jar` SHA256:
  `0a3eebf5e0308c21c62933dd06e789831845a1a2a4d0233148d2c64463b7435e`.
- Exact source-input fingerprint:
  `fe7a4441894ddc8eb6d559f7809f8847b13c8ba529550f38bd8223d7e3b5ce60`.
  Defined as SHA256 of canonical sorted path-to-fileSHA JSON for244files under src/main, src/test,
  gradle, plus build.gradle.kts/settings.gradle.kts/gradle.properties/gradlew. Every bound file was
  verified unchanged before/after the build. Manifest:
  `/home/dev/.tmp/commerce-acquisition-20261028/source-before-build.json`.
- Explicit current bundled inputs via `BC_CUSTOM_MOD_JAR_DIR=/home/dev/better-content-modpack/mods`
  were read-only; their before/after SHA checks passed. These artifacts were not rebuilt or declared
  newly qualified:
  - Dimension Fonts1.0.0 `c20b73289bd51b406976748d0bae507e8b0f34482f0f67907f857cf366b7a5d2`
  - Compat Fixes0.1.9 `eea20e71dc25faa195e0ea0285cbe5e9eaa8f3819cd7a3c02707d0bc01fe68d8`
  - Gameplay Notices1.0.0 `a684b3211867e98b1fcd50f34274ad2517fa08bf718476068998837038c102c2`
- The pre-final source-local JAR is retained separately at
  `/home/dev/.tmp/commerce-acquisition-20261028/pre-final-better-spirit-commerce-1.0.1.jar`, hash
  `e4ad958fe629e1e63f7ec25ea3c7c39a209ba3f04782438d9d58c7b3ce31da78`; it is not the final guard build.
- Native mixin application, menu/shift-click, stale-client, mixed-owner and process persistence
  cases are **not run or accepted** by these source checks. This handoff is source/JAR build evidence,
  not native gameplay or pack qualification.
