package space.ranzeplay.containeritemfinder.models;

import lombok.AllArgsConstructor;
import lombok.Getter;
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
        logger.info("  Duration: {} ms", duration.toMillis());
    }
}
