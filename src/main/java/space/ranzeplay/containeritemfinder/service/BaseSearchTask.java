package space.ranzeplay.containeritemfinder.service;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared base for search and index tasks, holding common heartbeat/cancellation logic.
 */
abstract class BaseSearchTask {
    static final long HEARTBEAT_INTERVAL = 10_000;

    final ServerPlayer source;
    protected final Vec3 center;

    final AtomicInteger blocksSearched = new AtomicInteger(0);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private long lastHeartbeatTime = 0;
    int totalContainersSearched = 0;

    private final String heartbeatKey;
    private final String cancelInfoKey;
    private final String cancelKey;

    protected BaseSearchTask(ServerPlayer source, Vec3 center, String heartbeatKey, String cancelInfoKey, String cancelKey) {
        this.source = source;
        this.center = center;
        this.heartbeatKey = heartbeatKey;
        this.cancelInfoKey = cancelInfoKey;
        this.cancelKey = cancelKey;
    }

    void sendHeartbeat(double currentDistance) {
        if (source != null && !cancelled.get()) {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastHeartbeatTime >= HEARTBEAT_INTERVAL) {
                source.sendSystemMessage(
                        Component.translatable(heartbeatKey, blocksSearched.get(), currentDistance)
                                .withStyle(ChatFormatting.GRAY)
                );
                lastHeartbeatTime = currentTime;
            }
        }
    }

    public Component cancel() {
        if (cancelled.compareAndSet(false, true) && source != null) {
            return Component.translatable(cancelInfoKey, blocksSearched.get(),
                    Math.sqrt(Math.pow(center.x, 2) + Math.pow(center.y, 2) + Math.pow(center.z, 2))
            ).withStyle(ChatFormatting.YELLOW);
        }
        return Component.translatable(cancelKey).withStyle(ChatFormatting.YELLOW);
    }

    public boolean isCancelled() {
        return cancelled.get();
    }
}
