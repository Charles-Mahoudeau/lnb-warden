package fr.bde_teknantes.lnb.warden.client.changelog

import net.minecraft.client.Minecraft
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal object ChangelogFormat {
    private fun locale(): Locale = Locale.forLanguageTag(Minecraft.getInstance().languageManager.selected.replace('_', '-'))

    /** A publication date in the game's language, such as "12 Jan 2027". */
    fun date(iso: String?): String {
        val instant = runCatching { Instant.parse(iso ?: return "") }.getOrNull() ?: return iso.orEmpty()
        return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale()).withZone(ZoneId.of("Europe/Paris")).format(instant)
    }

    /** The day of the month and the short month with the year, for the older rows: "12" and "Jan 2027". */
    fun dayAndMonth(iso: String?): Pair<String, String> {
        val instant = runCatching { Instant.parse(iso ?: return "" to "") }.getOrNull() ?: return "" to ""
        val zone = ZoneId.of("Europe/Paris")
        return DateTimeFormatter.ofPattern("d").withLocale(locale()).withZone(zone).format(instant) to
            DateTimeFormatter.ofPattern("MMM yyyy").withLocale(locale()).withZone(zone).format(instant)
    }
}
