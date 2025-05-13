package space.ranzeplay.containeritemfinder.command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.text.Text;
import space.ranzeplay.containeritemfinder.Main;
import space.ranzeplay.containeritemfinder.service.ContainerIndexService;
import space.ranzeplay.containeritemfinder.service.ContainerSearchService;

public class CommandManager {
    private final ContainerSearchService searchService;
    private final ContainerSearchCommand searchCommand;
    private final ContainerIndexCommand indexCommand;

    public CommandManager(ContainerSearchService searchService, ContainerIndexService indexService) {
        this.searchService = searchService;
        this.searchCommand = new ContainerSearchCommand(searchService);
        this.indexCommand = new ContainerIndexCommand(indexService);
    }

    public void register() {
        Main.getLogger().info("Registering commands");
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // Register cancel command
            dispatcher.register(
                net.minecraft.server.command.CommandManager.literal("cif")
                    .then(net.minecraft.server.command.CommandManager.literal("cancel")
                        .executes(context -> {
                            Text result = searchService.cancelSearch(context.getSource());
                            context.getSource().sendMessage(result);
                            return 1;
                        }))
            );

            // Register search and index commands
            searchCommand.register();
            indexCommand.register();
        });
    }
} 