package com.zurrtum.create.infrastructure.packet.c2s;

import com.zurrtum.create.AllHandle;
import com.zurrtum.create.AllPackets;
import com.zurrtum.create.catnip.codecs.stream.CatnipStreamCodecs;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketType;
import net.minecraft.network.protocol.game.ServerGamePacketListener;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;

/**
 * Shows a client-predicted hand swing to other players. Replaces vanilla's ServerboundSwingPacket, which 26.3 removed.
 */
public record SwingHandPacket(InteractionHand hand) implements Packet<ServerGamePacketListener> {
    public static final StreamCodec<ByteBuf, SwingHandPacket> CODEC = CatnipStreamCodecs.HAND.map(
        SwingHandPacket::new,
        SwingHandPacket::hand
    );

    @Override
    public void handle(ServerGamePacketListener listener) {
        AllHandle.onSwingHand((ServerGamePacketListenerImpl) listener, this);
    }

    @Override
    public PacketType<SwingHandPacket> type() {
        return AllPackets.SWING_HAND;
    }
}
