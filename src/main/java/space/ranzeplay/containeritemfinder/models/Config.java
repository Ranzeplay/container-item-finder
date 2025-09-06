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
        trackingAreas = new ArrayList<>();
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
