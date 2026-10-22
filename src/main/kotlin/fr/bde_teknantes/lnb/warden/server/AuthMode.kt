package fr.bde_teknantes.lnb.warden.server

/** How strictly the server checks players with LNB's API. See `auth.mode` in `lnbwarden-server.toml`. */
enum class AuthMode {
    /** Every player must be allowed by LNB's API. */
    REQUIRED,

    /** Players without a ticket join as guests, unless their username is linked to an LNB account. */
    OPTIONAL,

    /** No authentication and no API calls, for development and private test servers. */
    OFF;

    val id: String = name.lowercase()
}
