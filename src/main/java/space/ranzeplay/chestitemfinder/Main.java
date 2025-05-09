package space.ranzeplay.chestitemfinder;

import net.fabricmc.api.ModInitializer;
import space.ranzeplay.chestitemfinder.command.ChestItemFinderCommand;
import space.ranzeplay.chestitemfinder.service.ChestSearchService;

public class Main implements ModInitializer {
    @Override
    public void onInitialize() {
        ChestSearchService searchService = new ChestSearchService();
        ChestItemFinderCommand command = new ChestItemFinderCommand(searchService);
        command.register();
    }
}
