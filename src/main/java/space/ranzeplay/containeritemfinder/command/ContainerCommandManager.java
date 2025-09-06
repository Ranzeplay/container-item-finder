package space.ranzeplay.containeritemfinder.command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import space.ranzeplay.containeritemfinder.Main;
import space.ranzeplay.containeritemfinder.service.ContainerSearchService;
import space.ranzeplay.containeritemfinder.service.ContainerIndexService;

public class ContainerCommandManager {
    private final ContainerSearchService searchService;
    private final ContainerSearchCommand searchCommand;
    private final ContainerIndexCommand indexCommand;
    private final DatabaseCommands databaseCommands;

    public ContainerCommandManager(ContainerSearchService searchService, ContainerIndexService indexService) {
        this.searchService = searchService;
        this.searchCommand = new ContainerSearchCommand(searchService);
        this.indexCommand = new ContainerIndexCommand(indexService);
        this.databaseCommands = new DatabaseCommands();
    }

    public void register() {
        Main.getLogger().info("Registering commands");
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // Register cancel command
            dispatcher.register(
                CommandManager.literal("cif")
                    .then(CommandManager.literal("cancel")
                        .executes(context -> {
                            Text result = searchService.cancelSearch(context.getSource());
                            context.getSource().sendMessage(result);
                            return 1;
                        }))
            );
        });

        // Register search and index commands
        searchCommand.register();
        indexCommand.register();

        databaseCommands.register();
    }
} 