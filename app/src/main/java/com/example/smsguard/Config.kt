package com.example.smsguard

/** SMS commands recognized by the app and their PIN format. */
object SmsGuardCommand {
    const val LOCATE = "LOCATE"
    const val ALARM = "ALARM"
    const val STOP = "STOP"

    const val PIN_MIN_LENGTH = 4
    const val PIN_MAX_LENGTH = 8

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