package br.com.paulosergio;

import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.entity.player.Player;
import twilightforest.network.IPayloadContext;

/** Preserve packet order when Fabric has already dispatched to the client thread. */
public final class OrderedBossbarContext implements IPayloadContext {
    private final IPayloadContext delegate;
    private final BlockableEventLoop<?> client;

    public OrderedBossbarContext(IPayloadContext delegate, BlockableEventLoop<?> client) {
        this.delegate = delegate;
        this.client = client;
    }

    @Override
    public Player player() {
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
        if (client.isSameThread()) {
            work.run();
        } else {
            delegate.enqueueWork(work);
        }
    }
}
