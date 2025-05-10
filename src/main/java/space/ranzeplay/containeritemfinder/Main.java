package space.ranzeplay.containeritemfinder;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.ranzeplay.containeritemfinder.command.ContainerItemFinderCommand;
import space.ranzeplay.containeritemfinder.service.ChestSearchService;

public class Main implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("modid");

    @Override
    public void onInitialize() {
        ChestSearchService searchService = new ChestSearchService();
        ContainerItemFinderCommand command = new ContainerItemFinderCommand(searchService);
        command.register();
    }

    public static Logger getLogger() {
        return LOGGER;
    }
}
