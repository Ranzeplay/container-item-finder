package space.ranzeplay.containeritemfinder.models;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
@AllArgsConstructor
public class Config {
    public Config() {
        enableTracking = false;
        trackingAreas = List.of(new AABB(new Point(0,0,0), new Point(0,0,0), "minecraft:overworld"));
        databaseConnectionString = "";
        refreshIntervalMinutes = 10080; // Default to 7 days
        indexThreads = 4;
    }

    private boolean enableTracking;
    private List<AABB> trackingAreas;
    private int refreshIntervalMinutes;
    private String databaseConnectionString;
    private int indexThreads;
}
