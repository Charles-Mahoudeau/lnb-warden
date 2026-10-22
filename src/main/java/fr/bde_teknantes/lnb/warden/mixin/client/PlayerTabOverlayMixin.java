package fr.bde_teknantes.lnb.warden.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fr.bde_teknantes.lnb.warden.client.ClientRealNames;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Shows the player's real name after their name in the tab list. The column widens to fit it. */
@Mixin(PlayerTabOverlay.class)
abstract class PlayerTabOverlayMixin {
    @ModifyReturnValue(method = "getNameForDisplay", at = @At("RETURN"))
    private Component lnbwarden$appendRealName(Component name, PlayerInfo playerInfo) {
        Component realName = ClientRealNames.get(playerInfo.getProfile().getId());
        if (realName == null) return name;
        return Component.empty().append(name).append(CommonComponents.SPACE).append(realName);
    }
}
