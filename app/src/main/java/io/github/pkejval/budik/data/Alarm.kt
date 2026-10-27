package io.github.pkejval.budik.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.DayOfWeek
import java.time.LocalDate

enum class DismissMethod { SLIDE_HORIZONTAL, SLIDE_VERTICAL, BUTTON }

@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hour: Int = 7,
    val minute: Int = 0,
    val label: String = "",
    val enabled: Boolean = true,
    /** Bit 0 = Monday … bit 6 = Sunday. 0 = one-time alarm. */
    val daysMask: Int = 0,
    /** Epoch day of a one-time alarm with a fixed date; null = next occurrence of the time. */
    val dateEpochDay: Long? = null,
    /** Occurrences at or before this epoch millis are skipped ("skip next"). */
    val skipUntil: Long = 0,
    /** null = system default alarm sound. */
    val ringtoneUri: String? = null,
    val vibrate: Boolean = true,
    /** Seconds to ramp volume from silence to full; 0 = full volume immediately. */
    val rampSeconds: Int = 30,
    /** Minutes after which an unanswered alarm stops and counts as missed. */
    val autoSilenceMinutes: Int = 10,
    val dismissMethod: DismissMethod = DismissMethod.SLIDE_HORIZONTAL,
    /** Do not ring on public holidays / calendar days off (see Settings). */
    val skipOnDaysOff: Boolean = false,
) {
    val isRepeating get() = daysMask != 0
    val date: LocalDate? get() = dateEpochDay?.let(LocalDate::ofEpochDay)

    fun ringsOn(day: DayOfWeek) = daysMask and (1 shl (day.value - 1)) != 0

    companion object {
        const val WORKDAYS = 0b0011111
        const val EVERY_DAY = 0b1111111
    }
}
