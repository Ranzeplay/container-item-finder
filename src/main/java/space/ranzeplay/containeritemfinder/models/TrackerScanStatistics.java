package space.ranzeplay.containeritemfinder.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
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

    public Text toText() {
        return Text.empty()
                .append(Text.translatable("info.cif.db.stat.title").formatted(Formatting.GREEN, Formatting.BOLD)).append("\n  ")
                .append(Text.translatable("info.cif.db.stat.areas").formatted(Formatting.YELLOW))
                .append(Text.literal(String.valueOf(scannedAreas)).formatted(Formatting.WHITE)).append("\n  ")
                .append(Text.translatable("info.cif.db.stat.containers").formatted(Formatting.YELLOW))
                .append(Text.literal(String.valueOf(totalContainers)).formatted(Formatting.WHITE)).append("\n  ")
                .append(Text.translatable("info.cif.db.stat.items").formatted(Formatting.YELLOW))
                .append(Text.literal(String.valueOf(totalItems)).formatted(Formatting.WHITE)).append("\n  ")
                .append(Text.translatable("info.cif.db.stat.duration").formatted(Formatting.YELLOW))
                .append(Text.literal(formatDuration()).formatted(Formatting.WHITE));
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
