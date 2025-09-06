package space.ranzeplay.containeritemfinder;

import com.google.gson.GsonBuilder;
import lombok.Getter;
import lombok.SneakyThrows;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.ranzeplay.containeritemfinder.command.ContainerCommandManager;
import space.ranzeplay.containeritemfinder.models.Config;
import space.ranzeplay.containeritemfinder.service.ContainerSearchService;
import space.ranzeplay.containeritemfinder.service.ContainerIndexService;
import space.ranzeplay.containeritemfinder.service.TrackingService;

import java.nio.file.Files;

public class Main implements ModInitializer {
    public static final String MOD_ID = "containeritemfinder";
    @Getter
    private static final Logger logger = LoggerFactory.getLogger(MOD_ID);
    @Getter
    private static Config config;
    @Getter
    private static TrackingService trackingService;

    @Override
    public void onInitialize() {
        ContainerSearchService searchService = new ContainerSearchService();
        ContainerIndexService indexService = new ContainerIndexService();
        ContainerCommandManager commandManager = new ContainerCommandManager(searchService, indexService);
        commandManager.register();

        loadConfig();
        
        if(config.isEnableTracking()) {
            try {
                trackingService = new TrackingService(config);

                ServerTickEvents.END_SERVER_TICK.register(server -> {
                    trackingService.tryScan(server);
                });
            } catch (Exception e) {
                logger.error("Failed to initialize tracking service: ", e);
                trackingService = null;
            }
        }

        logger.info("ContainerItemFinder initialized successfully!");
    }

    @SneakyThrows
    private void loadConfig() {
        var gson = new GsonBuilder().setPrettyPrinting().create();
        var configPath = FabricLoader.getInstance().getConfigDir().resolve("cif.json");
        if(!configPath.toFile().exists()) {
            logger.info("Config file does not exist, creating a new one.");
            var config = new Config();
            Files.createFile(configPath);
            Files.writeString(configPath, gson.toJson(config));
        } else {
            logger.info("Config file already exists, loading from file");
        }

        config = gson.fromJson(Files.readString(configPath), Config.class);
    }
}
