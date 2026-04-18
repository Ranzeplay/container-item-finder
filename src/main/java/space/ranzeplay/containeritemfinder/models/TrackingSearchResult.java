package space.ranzeplay.containeritemfinder.models;

import lombok.Getter;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

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

    public Component toText() {
        return Component.empty()
                .append(Component.literal(String.format("(%.1fm) ", distance)).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(String.format("[%d, %d, %d] ", x, y, z)).withStyle(ChatFormatting.AQUA))
                .append(Component.literal(String.format("%dx",count)).withStyle(ChatFormatting.GRAY));
    }
}
