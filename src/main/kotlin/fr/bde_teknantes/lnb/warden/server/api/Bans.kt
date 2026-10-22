package fr.bde_teknantes.lnb.warden.server.api

import fr.bde_teknantes.lnb.warden.common.auth.DisconnectReasons
import net.minecraft.network.chat.Component
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val BAN_END = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z").withZone(ZoneId.systemDefault())

/** The disconnect message for this ban: its reason, and its end date or "permanent". */
fun WardenServerApi.Ban?.disconnectReason(): Component {
    val until = this?.expiresAt?.let { runCatching { BAN_END.format(Instant.parse(it)) }.getOrDefault(it) }
    return DisconnectReasons.banned(this?.reason ?: "?", until)
}
