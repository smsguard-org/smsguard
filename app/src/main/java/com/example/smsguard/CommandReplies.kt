package com.example.smsguard

/**
 * Builders for SMS reply bodies. Kept free of Android types so the wording can be unit tested.
 */
object CommandReplies {

    /**
     * Reply to `#CALLME#PIN`. It acknowledges the callback request back to the sender and echoes
     * the configured trusted contact, or "Not set" when none is saved.
     */
    fun callMe(from: String, trustedContact: String): String =
        "SMSGuard Alert:\n" +
            "Callback request acknowledged for $from.\n" +
            "Trusted Contact: ${trustedContact.ifBlank { "Not set" }}"
}
