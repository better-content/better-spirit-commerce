package com.bettercontent.spiritcommerce.trader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

final class WanderingTraderCatalogueTest {
    @Test void catalogueOwnsEightThemesAndThirtySixPoolRowsEach() {
        assertEquals(8, WanderingTraderCatalogue.themeCount());
        assertEquals(288, WanderingTraderCatalogue.authoredRowCount());
    }
}
