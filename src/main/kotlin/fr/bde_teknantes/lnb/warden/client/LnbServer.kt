package fr.bde_teknantes.lnb.warden.client

import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.ConnectScreen
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.multiplayer.ServerData
import net.minecraft.client.multiplayer.resolver.ServerAddress

/** LNB's Minecraft server, at [ClientConfig.SERVER_ADDRESS]. */
object LnbServer {
    private val address: String
        get() = ClientConfig.SERVER_ADDRESS.get().trim()

    /** Whether [ip], as typed in a server entry, points at LNB's server. Case-insensitive, port ignored. */
    fun isLnb(ip: String?): Boolean {
        if (ip == null || !ServerAddress.isValidAddress(ip.trim())) return false
        return ServerAddress.parseString(ip.trim()).host.equals(ServerAddress.parseString(address).host, ignoreCase = true)
    }

    fun connect(parent: Screen) {
        ConnectScreen.startConnecting(
            parent,
            Minecraft.getInstance(),
            ServerAddress.parseString(address),
            ServerData("LNB", address, ServerData.Type.OTHER),
            false,
            null,
        )
    }
}
