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
- Scheduled wandering traders pitch a theme-coloured travelling awning on a tall camp post, stay near their camp, and leave when no offers remain. Pick up and re-place the linked post to send its trader to a new camp. The compact cloth shelter has connected front stakes and a crossbar, not a counter or goods display.
- Plague Doctors draw eight unique oddities from a 42-item mixed-spirit cabinet. Each doctor keeps its stock for one day, then rolls a fresh catalogue at dawn; missing optional-mod goods are skipped safely.
- `data/better_spirit_commerce/economy/spirit_economy_policy.json` is the single loaded and validated authority for acquisition rules, 245 villager rows, 91 wandering goods, 42 Plague Doctor oddities, and retired item IDs.
- Create Deco coins and coin stacks, emerald-priced merchant offers, the old purse/wallet, and superseded Malum harvesting equipment are removed, inert, or hidden. Recipe reload also rejects non-kill recipes whose output is one of the seven commerce spirits.
- Malum's native Spirit Pouch is the specialist storage surface. Its pack recipe costs exactly three leather and two string.

Run `./gradlew verifyFull stageRuntimeJar` before committing or pushing.

## Optional Spirit Fair

When Better Celestial Events is installed, its server-authoritative Spirit Fair night admits one
**additional** themed wandering trader and camp near a living, non-spectator Overworld player.
The ordinary trader schedule and village residents are unchanged. The Fair rotates the existing
themes with its own saved cursor and uses their normal catalogue, prices and finite stock: no
new currency, discounts, gates or restocking. Both trader-spawning and mob-spawning gamerules
apply. Searches use already-loaded, collision-free supported terrain and retry at most once per
1,200 game ticks, including when no eligible player or site exists.

Admission is flushed to saved data before entity insertion; the successful trader UUID is recorded
separately and only successful insertion advances the theme cursor. A known canceled insertion
that never entered the world rolls back only its unsucceeded reservation and retains the one-minute
retry deadline. Death, exhaustion, chunk unload, reconnect and restart cannot grant another visit
for a successfully admitted occurrence. A crash after durable admission can conservatively lose
the visit rather than duplicate its stock; it does not advance the theme cursor.
Fair posts retain independent UUID-keyed relocation targets and occurrence markers alongside the
ordinary camp. Forced event end/replacement retires old visitors/posts before the next visit can
spawn. Loaded Fair traders depart at dawn, and unloaded expired traders/posts expire on joining
again. If the optional provider is removed, saved visitors still expire at their original dawn.

Compilation requires the canonical `better-celestial-events-0.1.0.jar`, resolved from
`BC_CUSTOM_MOD_JAR_DIR` or the sibling provider's `build/libs/`; it is not bundled or a mandatory
runtime dependency. The default source gate exercises provider absence. Run
`./gradlew verifyFull stageRuntimeJar -PcelestialRuntime=true` for the provider-present native
profile. Both profiles exercise the production admission/spawn/camp path with native server
entities and explicit Survival fake-player search anchors; they are not networked multiplayer or
full-pack acceptance.

The native development runtime includes JEED at the committed pack pin (CurseForge 532286 / 6172935)
and its required JEI runtime (238222 / 7920915) to register Malum's unconditional
`jeed:effect_provider` recipes. Its resources-only `nativeFixture`
source set materializes exactly the four existing authored zinc advancement fixes from
`../../better-content-modpack/kubejs/data/malum/advancements/recipes/` (`impetus_creation_zinc`,
`node_focusing_zinc`, `zinc_from_node_smelting`, `zinc_from_node_blasting`). Those upstream Malum
6646111 conditional advancements are malformed when Create's zinc tags exist. Resource tests bind
the exact native defects, authored input hashes and copied bytes. The same profile materializes
the existing authored `malum:create/crushing/crush_rare_earths` recipe fix (seven outputs, rather
than the upstream eight exceeding Create's limit). The GameTest run alone adds this source set;
staging rejects accidental inclusion of profile data/classes in the production Commerce JAR.
Native preparation also exposes the generated dev refmap, creates local server properties, and
materializes fresh registered SERVER config defaults through the actual native config specs.
These fixture inputs neither modify the pack nor add production dependencies, recipes or advancements.

`headlessGameTest` also runs `scripts/check_native_diagnostics.py` against the current invocation's
complete native log and nonce-bound config/loaded-mod metadata. Its narrow optional-target,
Forge-language metadata, union-resource URL and first-creation registered-config cases are bound
to native descriptor/class hashes and independently tested; unknown/malformed warnings, all errors,
stack traces and later runtime lag fail. At most one exact startup lag notice is recorded as a
nonblocking performance diagnostic: either between the original first-tick end and second-tick
start, or between the real second-tick end and third-tick start with a complete nonce-bound
first-three-tick clock/counter/schedule proof. The latter subtracts only the measured post-first-tick
setup interval and requires the conservative remaining debt to be strictly below vanilla's
2,000 ms overload threshold. Raw warnings, setup/residual measurements and schedule evidence remain
in `run-gametest/native-diagnostics.json`; this does not claim repaired performance or identify the
setup hotspot. Missing or inconsistent proof fails. Timing classes remain nonshipping. This is a
focused development-profile classifier; shared full-pack diagnostics remain unchanged, and this
is not a full-pack acceptance claim.

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

The non-shipping `visualServer` / `visualClient` configurations run a dedicated server and a real Forge client through the production block entity renderer. Prepare and freeze their JVM launch inputs under the shared Forge cache lock, then launch the prepared Java processes directly (under Xvfb when headless). Do not keep a Gradle server invocation holding the cache lock while trying to start a second Gradle client invocation. Every Gradle preparation/verification invocation holds the full-lifetime POSIX `fcntl.lockf` lock at `/home/dev/.worklane/forge-cache.lock`; `flock` is not interchangeable.

On first use, accept the Minecraft EULA in generated `run-visual-server/eula.txt`, and set `online-mode=false` and `enforce-secure-profile=false` in generated `server.properties` for the local offline `Dev` client. Register runtime/build/capture outputs before producing them and dispose of them after the active review, as required by the shared policy. In the server console, run:

```text
campvisual prepare <player>
campvisual capture <player> camps-overview
campvisual view <player> profile
campvisual capture <player> awning-profile
campvisual view <player> front
campvisual capture <player> awning-front
campvisual view <player> detail
campvisual capture <player> awning-detail
campvisual view <player> distant
campvisual capture <player> camps-distant
```

`campvisual scene <player> day|night|facings|uneven` recreates the eight current theme tints at fixed fixture coordinates. `facings` includes all four native horizontal directions; `uneven` alternates raised camp pads. Repeat identical `overview`, `profile`, `front`, `detail`, and `distant` views before and after a geometry change. Capture names accept lowercase letters, digits and hyphens (up to 60 characters), allowing separate baseline/candidate sets. A prepared camp owns the fixture camera instead of the resident auto-review; capturing closes any fixture screen through the client API and waits for native ticks. After removing raised fixture pads, refresh the client's skylight packets by a native console teleport beyond view distance (for example `teleport <player> 128 90 128`), wait for chunk unload, then return with the chosen `campvisual view` before capture. Retained stale skylight can otherwise make poles and ground falsely black. No pointer, scroll, resize, or simulated key input is used.

Captures are written to `run-visual-client/screenshots/`. Run `./gradlew verifyVisualHarness` under the cache lock to check the three conventional overview/profile/detail files, then actually inspect every required view for shape, clipping, attachment, lighting, facing and theme colors. That task checks file existence, not visual acceptance. Software-rendered captures are real native framebuffers, not GPU performance evidence or full-pack acceptance.

The render-only mesh keeps the original 1.1 × 2.02-block cloth footprint and full-sheet wool UVs. Its rear/front heights are 2.78/2.42 blocks above the post base, with a 0.06-block downward sag and a closed 0.012-block thickness. The lowest cloth underside is 2.408 blocks; the front beam underside is 2.38 blocks, leaving at least 2.1 blocks over the block-wide central approach on level camp ground. Both faces and timber use outward geometry-derived normals and backface culling. Timber UVs use block-sized tiles rather than stretching one square across a tall stake. Render rotation follows the unchanged native facing/AI stand target, including east and west. Both Forge's actual frustum box and the renderer's distance predicate use the existing expanded bounds, which cover every transformed vertex. This shelter does not level terrain or change post linking, movement, goods, stock, or departure behavior; the raised-pad fixture is not proof of clearance on arbitrary terrain.

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
