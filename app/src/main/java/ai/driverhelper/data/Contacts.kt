package ai.driverhelper.data

import org.json.JSONArray

/** Emergency contact; its label is message `contact_<id>`. Empty number = not configured yet (rental company, insurer). */
data class Contact(val id: String, val number: String)

/** assets/contacts.json: `[{ "id", "number" }]`. Rental company and insurer are filled in per fleet. */
fun parseContacts(json: String): List<Contact> {
    val arr = JSONArray(json)
    return (0 until arr.length()).map { i -> arr.getJSONObject(i).let { Contact(it.getString("id"), it.optString("number")) } }
}
