package io.github.pkejval.budik.data

import android.content.Context

/** Global days-off configuration used by alarms with [Alarm.skipOnDaysOff]. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var czechHolidays: Boolean
        get() = prefs.getBoolean(KEY_CZ_HOLIDAYS, true)
        set(value) = prefs.edit().putBoolean(KEY_CZ_HOLIDAYS, value).apply()

    var calendarIds: Set<Long>
        get() = prefs.getStringSet(KEY_CALENDARS, emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
        set(value) = prefs.edit().putStringSet(KEY_CALENDARS, value.map { it.toString() }.toSet()).apply()

    /** Comma separated; an event counts as a day off only if its title contains one. Empty = any event. */
    var calendarKeywords: String
        get() = prefs.getString(KEY_KEYWORDS, "")!!
        set(value) = prefs.edit().putString(KEY_KEYWORDS, value).apply()

    var ringingAppearance: RingingAppearance
        get() {
            val default = RingingAppearance()
            return RingingAppearance(
                style = enumOrDefault(prefs.getString(KEY_RING_STYLE, null), default.style),
                animated = prefs.getBoolean(KEY_RING_ANIMATED, default.animated),
                clockWeight = enumOrDefault(prefs.getString(KEY_RING_WEIGHT, null), default.clockWeight),
                showDate = prefs.getBoolean(KEY_RING_DATE, default.showDate),
            )
        }
        set(value) = prefs.edit()
            .putString(KEY_RING_STYLE, value.style.name)
            .putBoolean(KEY_RING_ANIMATED, value.animated)
            .putString(KEY_RING_WEIGHT, value.clockWeight.name)
            .putBoolean(KEY_RING_DATE, value.showDate)
            .apply()

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T) =
        enumValues<T>().firstOrNull { it.name == name } ?: default

    private companion object {
        const val KEY_CZ_HOLIDAYS = "cz_holidays"
        const val KEY_CALENDARS = "calendar_ids"
        const val KEY_KEYWORDS = "calendar_keywords"
        const val KEY_RING_STYLE = "ring_style"
        const val KEY_RING_ANIMATED = "ring_animated"
        const val KEY_RING_WEIGHT = "ring_clock_weight"
        const val KEY_RING_DATE = "ring_show_date"
    }
}
