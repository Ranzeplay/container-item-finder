package space.ranzeplay.containeritemfinder;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.ranzeplay.containeritemfinder.command.ContainerItemFinderCommand;
import space.ranzeplay.containeritemfinder.service.ContainerSearchService;

public class Main implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("modid");

    @Override
    public void onInitialize() {
        ContainerSearchService searchService = new ContainerSearchService();
        ContainerItemFinderCommand command = new ContainerItemFinderCommand(searchService);
        command.register();
    }

    public static Logger getLogger() {
        return LOGGER;
    }
}
