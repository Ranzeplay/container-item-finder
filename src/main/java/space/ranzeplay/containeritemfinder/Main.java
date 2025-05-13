package space.ranzeplay.containeritemfinder;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.ranzeplay.containeritemfinder.command.ContainerCommandManager;
import space.ranzeplay.containeritemfinder.service.ContainerSearchService;
import space.ranzeplay.containeritemfinder.service.ContainerIndexService;

public class Main implements ModInitializer {
    public static final String MOD_ID = "containeritemfinder";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ContainerSearchService searchService = new ContainerSearchService();
        ContainerIndexService indexService = new ContainerIndexService();
        ContainerCommandManager commandManager = new ContainerCommandManager(searchService, indexService);
        commandManager.register();
        
        LOGGER.info("ContainerItemFinder initialized successfully!");
    }

    public static Logger getLogger() {
        return LOGGER;
    }
}
