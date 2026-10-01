package br.com.paulosergio;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.util.thread.ReentrantBlockableEventLoop;
import net.minecraft.world.entity.player.Player;
import twilightforest.network.IPayloadContext;

/** Regression scenarios running against Minecraft 1.21.1's actual task executor. */
public final class PacketOrderingTest {
    private static final class ClientLoop extends ReentrantBlockableEventLoop<Runnable> {
        private final Thread owner = Thread.currentThread();

        ClientLoop() { super("bossbar-regression"); }
        @Override protected Runnable wrapRunnable(Runnable work) { return work; }
        @Override protected boolean shouldRun(Runnable work) { return true; }
        @Override protected Thread getRunningThread() { return owner; }
        @Override public void executeIfPossible(Runnable work) { tell(work); }
        void post(Runnable work) { tell(work); }
        void drain() { while (pollTask()) { } }
    }

    private static IPayloadContext originalContext(ClientLoop loop) {
        return new IPayloadContext() {
            @Override public Player player() { return null; }
            @Override public PacketFlow flow() { return PacketFlow.CLIENTBOUND; }
            @Override public void enqueueWork(Runnable work) { loop.execute(work); }
        };
    }

    private static final class Bar {
        float progress = 1.0f;
        int color;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        UUID id = UUID.randomUUID();
        ClientLoop baselineLoop = new ClientLoop();
        Map<UUID, Bar> baselineBars = new HashMap<>();
        AtomicReference<Throwable> baselineError = new AtomicReference<>();
        IPayloadContext baseline = originalContext(baselineLoop);
        // Fabric dispatches custom ADD, then vanilla dispatches UPDATE_PROGRESS.
        baselineLoop.post(() -> baseline.enqueueWork(() -> baselineBars.put(id, new Bar())));
        baselineLoop.post(() -> {
            try { baselineBars.get(id).progress = 0.5f; }
            catch (NullPointerException expected) { baselineError.set(expected); }
        });
        baselineLoop.drain();
        require(baselineError.get() instanceof NullPointerException,
            "Original code must reproduce the missing-bossbar NullPointerException");
        System.out.println("PASS: original scheduling reproduces NullPointerException before bossbar ADD");

        ClientLoop loop = new ClientLoop();
        Map<UUID, Bar> bars = new HashMap<>();
        IPayloadContext fixed = new OrderedBossbarContext(originalContext(loop), loop);
        AtomicReference<Throwable> fixedError = new AtomicReference<>();
        loop.post(() -> fixed.enqueueWork(() -> bars.put(id, new Bar())));
        loop.post(() -> {
            try { bars.get(id).progress = 0.5f; }
            catch (Throwable error) { fixedError.set(error); }
        });
        loop.post(() -> fixed.enqueueWork(() -> bars.get(id).color = 0xBE23FF));
        loop.drain();
        require(fixedError.get() == null, "Fixed ADD must run before vanilla UPDATE_PROGRESS");
        require(bars.get(id).progress == 0.5f && bars.get(id).color == 0xBE23FF,
            "Progress and custom style updates must both apply");
        System.out.println("PASS: fixed ADD, vanilla UPDATE_PROGRESS and custom STYLE preserve order");

        bars.clear();
        loop.post(() -> fixed.enqueueWork(() -> bars.put(id, new Bar())));
        loop.post(() -> bars.remove(id));
        loop.drain();
        require(bars.isEmpty(), "ADD must not be delayed past REMOVE and resurrect a stale bar");
        System.out.println("PASS: ADD followed by REMOVE leaves no stale bossbar");

        AtomicBoolean workerRan = new AtomicBoolean();
        AtomicReference<Thread> mutationThread = new AtomicReference<>();
        Thread worker = new Thread(() -> fixed.enqueueWork(() -> {
            workerRan.set(true);
            mutationThread.set(Thread.currentThread());
        }), "simulated-network-thread");
        worker.start();
        worker.join();
        require(!workerRan.get(), "Off-thread work must remain queued");
        loop.drain();
        require(workerRan.get() && mutationThread.get() == Thread.currentThread(),
            "Off-thread work must execute on the client thread");
        require(fixed.flow() == IPayloadContext.PacketFlow.CLIENTBOUND && fixed.player() == null,
            "Context player and flow must retain their original values");
        System.out.println("PASS: network-thread work stays queued for the client thread");
    }
}
