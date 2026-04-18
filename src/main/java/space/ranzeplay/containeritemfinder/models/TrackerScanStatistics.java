package space.ranzeplay.containeritemfinder.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

import java.time.Duration;

@Getter
@AllArgsConstructor
public class TrackerScanStatistics {
    private int scannedAreas;
    private int totalContainers;
    private int totalItems;
    private Duration duration;

    public void log(Logger logger) {
        logger.info("Tracking scan completed: ");
        logger.info("  Scanned Areas: {}", scannedAreas);
        logger.info("  Total Containers: {}", totalContainers);
        logger.info("  Total Items: {}", totalItems);
        logger.info("  Duration: {}", formatDuration());
    }

    public Component toText() {
        return Component.empty()
                .append(Component.translatable("info.cif.db.stat.title").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)).append("\n  ")
                .append(Component.translatable("info.cif.db.stat.areas").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(String.valueOf(scannedAreas)).withStyle(ChatFormatting.WHITE)).append("\n  ")
                .append(Component.translatable("info.cif.db.stat.containers").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(String.valueOf(totalContainers)).withStyle(ChatFormatting.WHITE)).append("\n  ")
                .append(Component.translatable("info.cif.db.stat.items").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(String.valueOf(totalItems)).withStyle(ChatFormatting.WHITE)).append("\n  ")
                .append(Component.translatable("info.cif.db.stat.duration").withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(formatDuration()).withStyle(ChatFormatting.WHITE));
    }

    private String formatDuration() {
        return String.format(
                "%dh %dm %ds %dms",
                duration.toHours(),
                duration.toMinutes() % 60,
                duration.toSeconds() % 60,
                duration.toMillis() % 1000
        );
    }
}
