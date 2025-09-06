package space.ranzeplay.containeritemfinder.models;

import lombok.Getter;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.sql.ResultSet;
import java.sql.SQLException;

@Getter
public class TrackingSearchResult {
    private final int count;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final double distance;

    public TrackingSearchResult(ResultSet rs) throws SQLException {
        this.count = rs.getInt("count");
        this.world = rs.getString("world");
        this.x = rs.getInt("x");
        this.y = rs.getInt("y");
        this.z = rs.getInt("z");
        this.distance = rs.getDouble("dist");
    }

    public Text toText() {
        return Text.empty()
                .append(Text.literal(String.format("(%.1fm) ", distance)).formatted(Formatting.YELLOW))
                .append(Text.literal(String.format("[%d, %d, %d] ", x, y, z)).formatted(Formatting.AQUA))
                .append(Text.literal(String.format("%dx",count)).formatted(Formatting.GRAY));
    }
}
