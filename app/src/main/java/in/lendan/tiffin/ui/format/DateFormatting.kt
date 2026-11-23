package `in`.lendan.tiffin.ui.format

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * java.util based formatting (no java.time: min SDK 24 without desugaring).
 * SimpleDateFormat is not thread-safe, so a new instance is made per call.
 */
object DateFormatting {

    /** For example "24 Nov 2026, 3:10 PM", in the device's locale and time zone. */
    fun dateTime(millis: Long): String = format("d MMM yyyy, h:mm a", millis)

    /** For example "24 Nov 2026". */
    fun date(millis: Long): String = format("d MMM yyyy", millis)

    private fun format(pattern: String, millis: Long): String =
        SimpleDateFormat(pattern, Locale.getDefault())
            .apply { timeZone = TimeZone.getDefault() }
            .format(Date(millis))
}
