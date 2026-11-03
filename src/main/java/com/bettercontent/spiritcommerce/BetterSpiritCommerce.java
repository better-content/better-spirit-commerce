package com.bettercontent.spiritcommerce;

import com.bettercontent.spiritcommerce.config.EconomyConfig;
import com.bettercontent.spiritcommerce.registry.SpiritProfessions;
import com.bettercontent.spiritcommerce.registry.CurrencyItems;
import com.bettercontent.spiritcommerce.spirit.SpiritAcquisition;
import com.bettercontent.spiritcommerce.spirit.SpiritPouchAccess;
import com.bettercontent.spiritcommerce.spirit.SpiritPouchGameTests;
import com.bettercontent.spiritcommerce.resident.ResidentGameTests;
import com.bettercontent.spiritcommerce.spirit.EconomySpiritTypes;
import com.bettercontent.spiritcommerce.trader.ProfessionBehaviors;
import com.bettercontent.spiritcommerce.trader.AuthoredTradeSignals;
import com.bettercontent.spiritcommerce.trader.VillagerCatalogue;
import com.bettercontent.spiritcommerce.trader.WanderingTraderGameTests;
import com.bettercontent.spiritcommerce.trader.WanderingTraderCatalogue;
import com.bettercontent.spiritcommerce.trader.WanderingTraderVisits;
import com.bettercontent.spiritcommerce.trader.LocalMarketNetwork;
import com.bettercontent.spiritcommerce.trader.TraderCampRegistries;
import com.bettercontent.spiritcommerce.ops.ObservationalExportCommand;
import com.bettercontent.spiritcommerce.resident.ResidentService;
import com.bettercontent.spiritcommerce.resident.ResidentInteractions;
import com.bettercontent.spiritcommerce.resident.ResidentNetwork;
import com.bettercontent.spiritcommerce.resident.ResidentCommand;
import com.bettercontent.spiritcommerce.resident.SettlementGeneration;
import com.bettercontent.spiritcommerce.resident.ResidentTerrainData;
import com.bettercontent.spiritcommerce.resident.PlayerStallRegistries;
import com.bettercontent.spiritcommerce.resident.PlayerStallNetwork;
import com.bettercontent.spiritcommerce.resident.PlayerStallCommand;
import com.bettercontent.spiritcommerce.resident.PlayerStallGameTests;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterGameTestsEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(BetterSpiritCommerce.MOD_ID)
public final class BetterSpiritCommerce {
    public static final String MOD_ID = "better_spirit_commerce";
    public BetterSpiritCommerce() {
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, EconomyConfig.SPEC);
        modBus.addListener(this::registerGameTests);
        LocalMarketNetwork.register();
        ResidentNetwork.register();
        PlayerStallNetwork.register();
        com.bettercontent.spiritcommerce.trader.SpiritCampfires.register(modBus);
        TraderCampRegistries.register(modBus);
        PlayerStallRegistries.register(modBus);
        SpiritProfessions.register(modBus);
        EconomySpiritTypes.initialize();
        CurrencyItems.register(modBus);
        MinecraftForge.EVENT_BUS.register(WanderingTraderVisits.class);
        MinecraftForge.EVENT_BUS.register(WanderingTraderCatalogue.class);
        MinecraftForge.EVENT_BUS.register(ResidentService.class);
        MinecraftForge.EVENT_BUS.register(ResidentInteractions.class);
        MinecraftForge.EVENT_BUS.register(SettlementGeneration.class);
        MinecraftForge.EVENT_BUS.register(ResidentTerrainData.class);
        MinecraftForge.EVENT_BUS.register(SpiritAcquisition.class);
        MinecraftForge.EVENT_BUS.register(ProfessionBehaviors.class);
        MinecraftForge.EVENT_BUS.register(AuthoredTradeSignals.class);
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> {
            SpiritPouchAccess.register(event.getDispatcher());
            ObservationalExportCommand.register(event.getDispatcher());
            ResidentCommand.register(event.getDispatcher());
            PlayerStallCommand.register(event.getDispatcher());
        });
    }

    private void registerGameTests(RegisterGameTestsEvent event) {
        event.register(WanderingTraderGameTests.class);
        event.register(com.bettercontent.spiritcommerce.trader.SpiritCampfireGameTests.class);
        event.register(com.bettercontent.spiritcommerce.trader.SpiritCollectionGameTests.class);
        event.register(com.bettercontent.spiritcommerce.trader.SpiritFairGameTests.class);
        event.register(SpiritPouchGameTests.class);
        event.register(ResidentGameTests.class);
        event.register(PlayerStallGameTests.class);
    }
}
