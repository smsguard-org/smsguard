package com.example.smsguard

import android.content.Context
import android.provider.ContactsContract

/** A phone contact the user can import into the trusted list. */
data class DeviceContact(
    val name: String,
    val number: String
)

/** Reads the device address book so a trusted contact can be chosen instead of typed. */
object ContactUtils {

    /**
     * Every contact that has a phone number, deduplicated by name+digits and sorted by name.
     *
     * Callers must hold [android.Manifest.permission.READ_CONTACTS]; the query is wrapped so a
     * missing grant returns an empty list rather than crashing the screen.
     */
    fun loadPhoneContacts(context: Context): List<DeviceContact> {
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val contacts = mutableListOf<DeviceContact>()
        val seen = mutableSetOf<String>()

        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )?.use { cursor ->
                val nameIndex =
                    cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex =
                    cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex)?.trim().orEmpty()
                    val number = cursor.getString(numberIndex)?.trim().orEmpty()
                    if (number.isBlank()) continue

                    val key = "${name.lowercase()}|${PhoneNumbers.normalize(number)}"
                    if (!seen.add(key)) continue
                    contacts += DeviceContact(name.ifBlank { number }, number)
                }
            }
        }
        return contacts
    }
}
