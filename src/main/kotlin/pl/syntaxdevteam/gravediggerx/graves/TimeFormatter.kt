package pl.syntaxdevteam.gravediggerx.graves

import pl.syntaxdevteam.gravediggerx.GraveDiggerX

object TimeFormatter {

    var plugin: GraveDiggerX? = null

    fun format(totalSeconds: Int): String {
        if (totalSeconds <= 0) return if (isHourFormat()) "00:00:00" else "00:00"

        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (isHourFormat()) {
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            val totalMinutes = totalSeconds / 60
            String.format("%02d:%02d", totalMinutes, seconds)
        }
    }

    private fun isHourFormat(): Boolean {
        val format = plugin?.config?.getString("graves.time-format", "HH:MM:SS") ?: "HH:MM:SS"
        return format.equals("HH:MM:SS", ignoreCase = true)
    }
}