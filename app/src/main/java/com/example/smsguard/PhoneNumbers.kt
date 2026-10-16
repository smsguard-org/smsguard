package com.example.smsguard

/**
 * Compares phone numbers the way a human would, so a command from a saved contact still matches
 * when the sender or the OS formats the number differently ("+1 (555) 010-9999" vs "15550109999").
 */
object PhoneNumbers {

    /**
     * The minimum number of shared trailing digits before a suffix match is trusted. Keeping this
     * at 7 stops short codes and partial numbers from colliding with a real contact.
     */
    private const val MIN_SUFFIX_DIGITS = 7

    /** Digits only, so punctuation and surrounding whitespace never block a match. */
    fun normalize(raw: String?): String = raw.orEmpty().filter { it.isDigit() }

    /**
     * True when both numbers resolve to the same subscriber. Exact digit matches are always
     * accepted; otherwise one number may carry a country/area-code prefix the other omits, so a
     * long-enough suffix match counts as the same person.
     */
    fun matches(a: String?, b: String?): Boolean {
        val digitsA = normalize(a)
        val digitsB = normalize(b)
        if (digitsA.isEmpty() || digitsB.isEmpty()) return false
        if (digitsA == digitsB) return true

        val shorter = if (digitsA.length <= digitsB.length) digitsA else digitsB
        val longer = if (digitsA.length <= digitsB.length) digitsB else digitsA
        return shorter.length >= MIN_SUFFIX_DIGITS && longer.endsWith(shorter)
    }
}
