package space.ranzeplay.containeritemfinder.models;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor
public class AABB {
    private Point p1;
    private Point p2;
    private String world;
}
