package com.example.smsguard

/** SMS commands recognized by the app and their PIN format. */
object SmsGuardCommand {
    const val LOCATE = "LOCATE"
    const val ALARM = "ALARM"
    const val STOP = "STOP"
    const val LOCK = "LOCK"
    const val DATA = "DATA"
    const val WIFI = "WIFI"
    const val BATTERY = "BATTERY"
    const val FLASH = "FLASH"
    const val INFO = "INFO"
    const val CALLME = "CALLME"
    const val CONTACTS = "CONTACTS"
    const val WIPE = "WIPE"
    const val STATUS = "STATUS"

    val ALL_COMMANDS = listOf(
        LOCATE, ALARM, STOP, LOCK, DATA, WIFI, BATTERY, FLASH, INFO, CALLME, CONTACTS, WIPE, STATUS
    )

    const val PIN_MIN_LENGTH = 4
    const val PIN_MAX_LENGTH = 8

    // The trailing (?!\d) stops a 4-8 digit PIN from partially matching inside a longer run of
    // digits (e.g. "#CALLME#123456789" must not resolve to the PIN "12345678").
    private val COMMAND_PATTERN = Regex("""#([A-Z]+)#(\d{4,8})(?!\d)#?""")

    /** A recognized `#COMMAND#PIN` found in an SMS body. */
    data class Match(val command: String, val pin: String)

    /**
     * Finds the first command in [body]. The caller is expected to have upper-cased the text
     * already, which is how an incoming SMS is normalized before matching.
     */
    fun parse(body: String): Match? {
        val match = COMMAND_PATTERN.find(body) ?: return null
        return Match(match.groupValues[1], match.groupValues[2])
    }

    fun forPin(pin: String): String? {
        val p = pin.trim()
        return if (p.length in PIN_MIN_LENGTH..PIN_MAX_LENGTH && p.all { it.isDigit() }) p else null
    }
}

/** Intents used to start/stop the siren foreground service. */
object SirenServiceActions {
    const val START = "com.example.smsguard.action.START_SIREN"
    const val STOP = "com.example.smsguard.action.STOP_SIREN"
}
