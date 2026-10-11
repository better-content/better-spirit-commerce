# Better Spirit Commerce

## Scope and authority

This repository owns its mod-specific behavior and authoring inputs. Read [local instructions](AGENTS.md)
and the [shared documentation/policy index](../../better-content-modpack/docs/README.md).


Forge 1.20.1 mod making Malum spirits the pack's player-effort currency and village identity system.

- Commerce uses sacred, wicked, arcane, aerial, aqueous, earthen, and infernal spirits. Eldritch and umbral spirits retain their special Malum roles and never appear in trade.
- A living entity releases spirits only when the kill is credited to a player, that player's projectile or spell, or an owned creature. Spawner-origin mobs, villagers, traders, guards, golems, and players release none.
- Existing Malum mappings retain their authored drops. Any otherwise-unmapped hostile deterministically releases two ordinary spirits. Both paths spawn Malum's floating, homing `SpiritItemEntity`; no harvesting tool is required.
- Village residents barter from personal supplies and nearby recipes. Their stock, food, safe water, rest, current task, and recent exchanges persist with the entity. Existing spirit profession items remain for compatibility, but fixed spirit catalogues and profession conversion are disabled for village villagers.
- Seven wandering trader identities each have a matching robe, 13 fixed goods, and one one-use offer of two matching spirits for two eggs already assigned to the corresponding profession.
- Scheduled wandering traders pitch a theme-coloured cloth awning on a tall camp post, stay near their stall, and leave when no offers remain. Pick up and re-place the linked post to send its trader to a new camp.
- Plague Doctors draw eight unique oddities from a 42-item mixed-spirit cabinet. Each doctor keeps its stock for one day, then rolls a fresh catalogue at dawn; missing optional-mod goods are skipped safely.
- `data/better_spirit_commerce/economy/spirit_economy_policy.json` is the single loaded and validated authority for acquisition rules, 245 villager rows, 91 wandering goods, 42 Plague Doctor oddities, and retired item IDs.
- Create Deco coins and coin stacks, emerald-priced merchant offers, the old purse/wallet, and superseded Malum harvesting equipment are removed, inert, or hidden. Recipe reload also rejects non-kill recipes whose output is one of the seven commerce spirits.
- Malum's native Spirit Pouch is the specialist storage surface. Its pack recipe costs exactly three leather and two string.

Run `./gradlew verifyFull stageRuntimeJar` before committing or pushing.

## Better Villagers

Right-click a village villager or a settlement rat in Ratlantis to open the barter journal. Select one of their stocked or craftable goods, select a good from your inventory, and adjust the amount you give. The quote explains whether the resident needs more payment, ingredients, or a nearby workstation. Giving supplies directly is always available. Wandering traders and Plague Doctors keep their authored merchant inventories.

Residents use a nearby crafting table, furnace, smoker, blast furnace, stonecutter, Farmer's Delight cutting board and heated cooking pot, Hexerei woodcutter, and Tinkers' Construct part builder and tinker station. Crafting runs when a trade commits. Recipe chains are bounded to three steps, and all ingredients and fuel come from the resident's supplies. Nearby residents can settle barter cycles, including goods crafted at exchange time. Starter workshops with food, water, a bed or rat hole, and basic surfaces appear near established Overworld villages and in generated Ratlantis settlements. Residents can harvest ripe crops, collect water, fell natural trees, and gather exposed surface stone and soil when short on supplies.

### Player stalls

Craft and place a Player Stall from a barrel, oak fences, oak planks, and white wool. Right-click it to edit up to six exact offers. Each offer names the item and amount you sell and the item and amount you receive. Deposit real goods in **Stock**; collect received goods from **Proceeds**. An offer can be enabled, disabled, or cleared without losing its stock. The owner can search the item registry or use a held item to set a template, including item tags. Other players can view the stall but cannot edit or withdraw. Breaking a stall drops its stored goods.

Loaded village residents and Ratlantis resident rats within 24 blocks can walk to the front of an owned stall and buy useful goods. A buyer pays the exact listed amount from personal stock or by crafting it at trade time with nearby workstations. Buyers keep their basic survival reserves and decline prices above their demand limit. The base limit is the existing item value, with a 25% allowance for goods with a recipe and another 25% when food or safe water is urgently needed. The stall shows stock state, approaching customers, and its most recent completed trade. The owner may leave; the stall does not keep chunks loaded.

The `BCV1` console API extends `/bettervillagers stall` with `inspect <pos>`, `quote <pos> <resident_uuid> <offer_0_to_5>`, `configure <pos> <offer> <sale_id> <sale_count> <payment_id> <payment_count>`, `clear <pos> <offer>`, `deposit <pos> <player_inventory_slot>`, `withdraw <pos> stock|proceeds <slot>`, and `execute <pos> <resident_uuid> <offer>`. Operator-only `seed <pos> <item_id> <count>` prepares an automation fixture. Server-side ownership, distance, stock, and buyer checks also apply to commands. The non-shipping visual harness has `/residentvisual stallfixture <player>` and `/residentvisual showstall <player>` for a live screen and arrival fixture.

The console API emits one `BCV1` JSON line per response. Use a resident UUID from `inspect` or the test fixture:

```text
bettervillagers inspect <uuid>
bettervillagers offers <uuid>
bettervillagers events <uuid>
bettervillagers quote <uuid> <result_item_id> <result_count> <payment_item_id> <payment_count>
bettervillagers execute <uuid> <result_item_id> <result_count> <payment_item_id> <payment_count>
```

`execute` requires a player command source with the goods in inventory within eight blocks. The `runVisualServer` / `runVisualClient` source set also provides `/residentvisual fixture <player>` to create two complementary residents and print their UUIDs, `/residentvisual prepare <player>` for payment goods, and `/residentvisual show` and `capture` for production-screen review. These fixture commands are absent from the shipping JAR.

## Trader camp screenshots

The non-shipping visual harness runs a dedicated server and a real Forge client through the production block entity renderer. On first use, accept the Minecraft EULA in the generated `run-visual-server/eula.txt`, and set `online-mode=false` and `enforce-secure-profile=false` in its generated `server.properties` for the local offline `Dev` client. Start `./gradlew runVisualServer --no-daemon`, then start `./gradlew runVisualClient --no-daemon` under Xvfb. In the server console, run:

```text
campvisual prepare <player>
campvisual capture <player> camps-overview
campvisual view <player> profile
campvisual capture <player> awning-profile
campvisual view <player> detail
campvisual capture <player> awning-detail
```

Captures are written to `run-visual-client/screenshots/`. Run `./gradlew verifyVisualHarness` to check the expected files, then inspect the images for cloth shape, clipping, lighting, and theme colors. The harness does not use synthetic player input.

## Local Market screenshots

Use the same `runVisualServer` and Xvfb `runVisualClient` pair. From the server console, prepare two nearby fixture merchants and capture each production GUI state:

```text
marketvisual prepare <player>
marketvisual show <player> populated
marketvisual capture <player> market-populated
marketvisual show <player> filtered
marketvisual capture <player> market-filtered
marketvisual show <player> empty
marketvisual capture <player> market-empty
marketvisual show <player> no_matches
marketvisual capture <player> market-no-matches
marketvisual show <player> narrow
marketvisual capture <player> market-narrow
marketvisual select <player> lantern
marketvisual capture <player> market-selected
```

Run `./gradlew verifyMarketVisualHarness` and inspect the six images in `run-visual-client/screenshots/` for readable costs and results, visible merchant and stock context, usable search and navigation, distinct empty and no-match states, no clipped controls, and the selected Lantern offer in the vanilla merchant screen. The narrow scene uses a 960×720 window at GUI scale 3 (320×240 scaled); other scenes use 1600×900 at scale 2. This harness opens the real Local Market screen without simulated pointer input.
