package fr.bde_teknantes.lnb.warden.mixin;

import fr.bde_teknantes.lnb.warden.server.session.SessionTracker;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells kicks from quits, so LNB sessions end with the right reason. */
@Mixin(ServerCommonPacketListenerImpl.class)
abstract class ServerCommonPacketListenerImplMixin {
    @Inject(method = "disconnect(Lnet/minecraft/network/DisconnectionDetails;)V", at = @At("HEAD"))
    private void lnbwarden$markKicked(DisconnectionDetails details, CallbackInfo ci) {
        // A keep-alive timeout is the connection dropping, not the server's decision.
        if (details.reason().getContents() instanceof TranslatableContents contents
                && contents.getKey().equals("disconnect.timeout")) {
            return;
        }
        if ((Object) this instanceof ServerGamePacketListenerImpl game) {
            SessionTracker.markKicked(game.player.getUUID());
        }
    }
}
