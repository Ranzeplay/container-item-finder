package space.ranzeplay.containeritemfinder;

import net.fabricmc.api.ModInitializer;
import space.ranzeplay.containeritemfinder.command.ContainerItemFinderCommand;
import space.ranzeplay.containeritemfinder.service.ChestSearchService;

public class Main implements ModInitializer {
    @Override
    public void onInitialize() {
        ChestSearchService searchService = new ChestSearchService();
        ContainerItemFinderCommand command = new ContainerItemFinderCommand(searchService);
        command.register();
    }
}
