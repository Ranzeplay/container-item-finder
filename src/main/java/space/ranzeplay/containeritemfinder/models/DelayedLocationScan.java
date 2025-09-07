package space.ranzeplay.containeritemfinder.models;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Date;

@Getter
@EqualsAndHashCode
@AllArgsConstructor
public class DelayedLocationScan {
    private final Location location;
    private final long submitTimeMillis;
}
