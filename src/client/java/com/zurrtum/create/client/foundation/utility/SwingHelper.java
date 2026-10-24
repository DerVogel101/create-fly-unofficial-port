package com.zurrtum.create.client.foundation.utility;

import com.zurrtum.create.infrastructure.packet.c2s.SwingHandPacket;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

public class SwingHelper {
    /**
     * Client-predicted swing that other players see too, like {@code LocalPlayer#swing} did before 26.3.
     */
    public static void swing(LocalPlayer player, InteractionHand hand) {
        player.swing(hand, player.getItemInHand(hand).getInteractAnimation(), false);
        player.connection.send(new SwingHandPacket(hand));
    }
}
