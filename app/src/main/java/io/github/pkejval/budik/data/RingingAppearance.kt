package io.github.pkejval.budik.data

/** Background gradient of the ringing screen as ARGB: top, middle and the animated accent. */
enum class RingingStyle(val top: Long, val middle: Long, val accent: Long) {
    NIGHT(0xFF0F0C29, 0xFF302B63, 0xFFF97316),
    OCEAN(0xFF0B132B, 0xFF1C2541, 0xFF3A86FF),
    FOREST(0xFF081C15, 0xFF1B4332, 0xFF52B788),
    SUNSET(0xFF3C1053, 0xFFAD5389, 0xFFFFB199),
    AMOLED(0xFF000000, 0xFF000000, 0xFF4B5563),

    /** Colors come from the system (Material You) palette at runtime. */
    SYSTEM(0, 0, 0),
}

enum class ClockWeight { THIN, LIGHT, BOLD }

data class RingingAppearance(
    val style: RingingStyle = RingingStyle.NIGHT,
    val animated: Boolean = true,
    val clockWeight: ClockWeight = ClockWeight.THIN,
    val showDate: Boolean = true,
)
