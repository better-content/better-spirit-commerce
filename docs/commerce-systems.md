# Spirit traders and village economies are separate systems

## Scope and authority

This document owns the gameplay boundary between the two systems implemented in Better Spirit
Commerce. Sharing a mod, artwork or interface helpers does not make them one economy.
Implementation lives in the source areas linked below; pack integration is described in
[Content systems](../../../better-content-modpack/docs/content_systems.md#spirit-commerce).

## Wandering spirit commerce

Wandering spirit traders are **simple spirit-for-goods merchants**. A themed trader offers an
authored catalogue: the player pays the matching physical spirits and receives the listed goods.
Prices, output quantities, visit stock and the existing finite-stock rules govern those offers.
Arrival and camp behavior belong to the trader system too.

This is not a village production economy. Trader offers do **not** depend on resident inventories,
food, water, rest, demand, nearby workstations, recipe ingredients supplied by the customer, or
village production chains. Do not introduce commissions or villager-economy prerequisites when
expanding or balancing the spirit catalogue.

The implementing owners are [WanderingTraderCatalogue](../src/main/java/com/bettercontent/spiritcommerce/trader/WanderingTraderCatalogue.java),
[WanderingTraderVisits](../src/main/java/com/bettercontent/spiritcommerce/trader/WanderingTraderVisits.java)
and the authored catalogue in
[spirit_economy_policy.json](../src/main/resources/data/better_spirit_commerce/spiritcommerce/spirit_economy_policy.json),
with effective rows derived by [EconomyPolicy](../src/main/java/com/bettercontent/spiritcommerce/config/EconomyPolicy.java).
Plague Doctors also have their own authored merchant catalogue; they are not resident barter.

## Economy-driven villages

Village residents use a separate **goods-for-goods barter and local production system**. Personal
supplies, needs, useful payments, nearby recipes and workstations determine what a resident can
offer. Player stalls and resident exchanges belong to that system, not to the wandering spirit
catalogue.

The implementing owners are [ResidentRules](../src/main/java/com/bettercontent/spiritcommerce/resident/ResidentRules.java),
[ResidentBarter](../src/main/java/com/bettercontent/spiritcommerce/resident/ResidentBarter.java)
and [ResidentService](../src/main/java/com/bettercontent/spiritcommerce/resident/ResidentService.java).
[AbstractVillagerMixin](../src/main/java/com/bettercontent/spiritcommerce/mixin/AbstractVillagerMixin.java)
clears village villagers' fixed merchant offers; ordinary resident interaction opens their barter
interface instead. This does not make wandering traders depend on that simulation.

## Catalogue work and the 280 legacy rows

The 280 profession-catalogue rows are existing authored offer definitions, not 280 currently
available village trades. Fixed profession catalogues and profession conversion are disabled for
village villagers.

When those rows are considered for the spirit catalogue, treat them as an **offer pool to review
and integrate into wandering spirit commerce**. That does not authorize reactivating fixed village
shops, adding specialist village markets, or importing resident supply, needs, workstations or
commissions into spirit trading.

Prioritize each spirit's identity, useful purchases and pack progression. Which mobs supply a
spirit, when players encounter them and how many units they release should inform the offered
goods, spirit prices, batch quantities and stock. They are balance inputs to a simple merchant
catalogue, not a reason to build another village-economy layer. Catalogue work also does not by
itself authorize changes to spirit acquisition or the separate village system.
