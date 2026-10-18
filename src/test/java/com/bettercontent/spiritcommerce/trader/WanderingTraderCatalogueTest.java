package com.bettercontent.spiritcommerce.trader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

final class WanderingTraderCatalogueTest {
    @Test void catalogueOwnsEightThemesAndThirteenRowsEach() {
        assertEquals(8, WanderingTraderCatalogue.themeCount());
        assertEquals(112, WanderingTraderCatalogue.authoredRowCount());
    }
}
