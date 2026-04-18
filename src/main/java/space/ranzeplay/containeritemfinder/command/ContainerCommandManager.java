package space.ranzeplay.containeritemfinder.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
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
        CommandRegistrationCallback.EVENT.register((dispatcher, _, _) -> {
            // Register cancel command
            dispatcher.register(
                LiteralArgumentBuilder.<CommandSourceStack>literal("cif")
                    .then(LiteralArgumentBuilder.<CommandSourceStack>literal("cancel")
                        .executes(context -> {
                            var source = context.getSource();
                            Component result = searchService.cancelSearch(source);
                            source.sendSystemMessage(result);
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
