package local.twilightfix;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import twilightforest.network.IPayloadContext;
import twilightforest.network.TFBossBarPacket.AddTFBossBarPacket;
import twilightforest.network.TFBossBarPacket.UpdateTFBossBarStylePacket;

public final class TfBossbarPacketFix implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("tf_bossbar_packet_fix");
    private boolean firstAddLogged;

    @Override
    public void onInitializeClient() {
        // All client initializers, including Twilight Forest's original packet
        // registration, have completed before CLIENT_STARTED fires.
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            ClientPlayNetworking.unregisterGlobalReceiver(AddTFBossBarPacket.TYPE.comp_2242());
            boolean addRegistered = ClientPlayNetworking.registerGlobalReceiver(
                AddTFBossBarPacket.TYPE,
                (packet, context) -> {
                    AddTFBossBarPacket.handle(packet,
                        new OrderedBossbarContext(IPayloadContext.fromClientNetworking(context), context.client()));
                    if (!firstAddLogged) {
                        firstAddLogged = true;
                        LOGGER.info("Handled first Twilight Forest bossbar ADD in packet order");
                    }
                });

            ClientPlayNetworking.unregisterGlobalReceiver(UpdateTFBossBarStylePacket.TYPE.comp_2242());
            boolean styleRegistered = ClientPlayNetworking.registerGlobalReceiver(
                UpdateTFBossBarStylePacket.TYPE,
                (packet, context) -> UpdateTFBossBarStylePacket.handle(packet,
                    new OrderedBossbarContext(IPayloadContext.fromClientNetworking(context), context.client())));

            if (!addRegistered || !styleRegistered) {
                throw new IllegalStateException("Could not install Twilight Forest bossbar packet ordering fix");
            }
            LOGGER.info("Twilight Forest bossbar packet ordering fix active (ADD and STYLE)");
        });
    }
}
