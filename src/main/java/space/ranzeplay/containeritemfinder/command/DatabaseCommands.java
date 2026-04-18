package space.ranzeplay.containeritemfinder.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.commands.arguments.item.ItemInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.item.Item;
import space.ranzeplay.containeritemfinder.Main;

public class DatabaseCommands {
    public void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    LiteralArgumentBuilder.<CommandSourceStack>literal("dif")
                            .then(LiteralArgumentBuilder.<CommandSourceStack>literal("search")
                                    .then(RequiredArgumentBuilder.<CommandSourceStack, ItemInput>argument("item", ItemArgument.item(registryAccess))
                                            .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("count", IntegerArgumentType.integer(1))
                                                    .then(RequiredArgumentBuilder.<CommandSourceStack, Integer>argument("range", IntegerArgumentType.integer(1))
                                                            .executes(this::executeSearch))
                                                    .executes(this::executeSearch)
                                            )
                                            .executes(this::executeSearch)
                                    )
                            )
                            .then(LiteralArgumentBuilder.<CommandSourceStack>literal("stats")
                                    .executes(this::executeStats))
                            .then(LiteralArgumentBuilder.<CommandSourceStack>literal("rescan")
                                    .executes(this::rescan))
            );
        });
    }

    private int rescan(CommandContext<CommandSourceStack> context) {
        if(context.getSource().getPlayer().permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.ADMINS))) {
            if(Main.getTrackingService().isScanning()) {
                context.getSource().sendSystemMessage(Component.translatable("info.cif.db.rescan.busy").withStyle(ChatFormatting.RED));
            } else {
                context.getSource().sendSystemMessage(Component.translatable("info.cif.db.rescan.start").withStyle(ChatFormatting.GREEN));
                Main.getTrackingService().manualScan(context.getSource().getServer());
            }
        } else {
            context.getSource().sendSystemMessage(Component.translatable("info.cif.db.rescan.noperm").withStyle(ChatFormatting.RED));
        }

        return 1;
    }

    private int executeSearch(CommandContext<CommandSourceStack> context) {
        Item item = ItemArgument.getItem(context, "item").item().value();
        Integer count = null;
        Integer range = null;

        try {
            count = context.getArgument("count", Integer.class);
        } catch (IllegalArgumentException ignored) {
        }

        try {
            range = context.getArgument("range", Integer.class);
        } catch (IllegalArgumentException ignored) {
        }

        Main.getTrackingService().searchTrackingItem((CommandSource) context.getSource(), context.getSource().getLevel(), context.getSource().getPosition(), range, item, count);

        return 1;
    }

    private int executeStats(CommandContext<CommandSourceStack> context) {
        final var source = context.getSource();
        if (Main.getTrackingService().getLatestStatistics() == null) {
            source.sendSystemMessage(Component.translatable("info.cif.stat.n_a").withStyle(ChatFormatting.RED));
        } else {
            source.sendSystemMessage(Main.getTrackingService().getLatestStatistics().toText());
        }
        if (Main.getTrackingService().isScanning()) {
            source.sendSystemMessage(Component.translatable("info.cif.stat.scanner.pre").append(Component.translatable("info.cif.stat.scanner.active").withStyle(ChatFormatting.GREEN)));
        } else {
            source.sendSystemMessage(Component.translatable("info.cif.stat.scanner.pre").append(Component.translatable("info.cif.stat.scanner.inactive").withStyle(ChatFormatting.YELLOW)));
        }

        return 1;
    }
}
