package local.twilightfix;

import net.minecraft.class_1255;
import net.minecraft.class_1657;
import twilightforest.network.IPayloadContext;

/** Preserve packet order when Fabric has already dispatched to the client thread. */
public final class OrderedBossbarContext implements IPayloadContext {
    private final IPayloadContext delegate;
    private final class_1255<?> client;

    public OrderedBossbarContext(IPayloadContext delegate, class_1255<?> client) {
        this.delegate = delegate;
        this.client = client;
    }

    @Override
    public class_1657 player() {
        return delegate.player();
    }

    @Override
    public PacketFlow flow() {
        return delegate.flow();
    }

    @Override
    public void enqueueWork(Runnable work) {
        // Minecraft's reentrant executor defers execute() inside another task,
        // even on the client thread. A vanilla boss_event already in the queue
        // would then overtake the custom ADD that creates its bossbar.
        if (client.method_18854()) { // ThreadExecutor.isOnThread, MC 1.21.1
            work.run();
        } else {
            delegate.enqueueWork(work);
        }
    }
}
