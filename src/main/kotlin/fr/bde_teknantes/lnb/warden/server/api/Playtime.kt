package fr.bde_teknantes.lnb.warden.server.api

import fr.bde_teknantes.lnb.warden.common.auth.DisconnectReasons
import fr.bde_teknantes.lnb.warden.server.playtime.PlaytimeView
import net.minecraft.network.chat.Component

/** The disconnect message for a used-up allowance: when the player can join again, or after the reset. */
fun WardenServerApi.Playtime?.disconnectReason(): Component =
    DisconnectReasons.playtimeLimit(PlaytimeView.formatDate(this?.windowEndsAt ?: this?.resetsAt))
