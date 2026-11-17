package com.bettercontent.spiritcommerce.trader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

final class WanderingTraderCatalogueTest {
    @Test void catalogueOwnsFiveLiveThemesAndUnmodifiedAuthoredGoodsSource() {
        assertEquals(5, WanderingTraderCatalogue.themeCount());
        assertEquals(288, WanderingTraderCatalogue.authoredRowCount());
    }
}
