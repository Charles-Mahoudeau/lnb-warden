package fr.bde_teknantes.lnb.warden.client

import com.mojang.blaze3d.platform.InputConstants
import fr.bde_teknantes.lnb.warden.LnbWarden
import fr.bde_teknantes.lnb.warden.common.network.PingPayload
import net.minecraft.Util
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent
import net.neoforged.neoforge.client.settings.KeyConflictContext
import net.neoforged.neoforge.network.PacketDistributor
import org.lwjgl.glfw.GLFW

object WardenKeyMappings {
    private const val CATEGORY = "key.categories.${LnbWarden.ID}"

    val PING: KeyMapping by lazy {
        KeyMapping(
            "key.${LnbWarden.ID}.ping",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            CATEGORY,
        )
    }

    fun register(event: RegisterKeyMappingsEvent) {
        event.register(PING)
    }

    fun onClientTick(event: ClientTickEvent.Post) {
        while (PING.consumeClick()) {
            if (Minecraft.getInstance().connection != null) {
                PacketDistributor.sendToServer(PingPayload(Util.getMillis()))
            }
        }
    }
}
