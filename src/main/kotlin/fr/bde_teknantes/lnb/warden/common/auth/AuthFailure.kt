package fr.bde_teknantes.lnb.warden.common.auth

/** Why the client couldn't send a join ticket, in `lnbwarden:auth_response`. Append only: sent as the ordinal. */
enum class AuthFailure {
    NO_TOKEN,
    TOKEN_REJECTED,
    API_UNREACHABLE,
    UNTRUSTED_SERVER,
    LINK_DECLINED,
}
