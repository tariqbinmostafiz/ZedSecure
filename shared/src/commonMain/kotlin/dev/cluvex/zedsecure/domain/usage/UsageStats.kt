package dev.cluvex.zedsecure.domain.usage

data class DayUsage(val down: Long = 0L, val up: Long = 0L) {
    val total: Long get() = down + up
}

enum class UsageRange { Day, Week, Month }

class UsageBucket(
    val range: UsageRange,
    val startDay: Int,
    val endDay: Int,
    val down: Long,
    val up: Long,
) {
    val total: Long get() = down + up
}

/** Calendar maths on "epoch days" (days since 1970-01-01) so no platform date API is needed. */
object UsageCalendar {
    const val MILLIS_PER_DAY = 86_400_000L

    fun epochDay(utcMillis: Long, offsetMillis: Long): Int = (utcMillis + offsetMillis).floorDiv(MILLIS_PER_DAY).toInt()

    /** Returns [year, month (1-12), day (1-31)]. */
    fun civil(epochDay: Int): IntArray {
        val z = epochDay + 719_468
        val era = z.floorDiv(146_097)
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val day = doy - (153 * mp + 2) / 5 + 1
        val month = if (mp < 10) mp + 3 else mp - 9
        val year = yoe + era * 400 + if (month <= 2) 1 else 0
        return intArrayOf(year, month, day)
    }

    fun daysFromCivil(year: Int, month: Int, day: Int): Int {
        val y = if (month <= 2) year - 1 else year
        val era = y.floorDiv(400)
        val yoe = y - era * 400
        val mp = if (month > 2) month - 3 else month + 9
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146_097 + doe - 719_468
    }

    /** 0 = Monday ... 6 = Sunday. 1970-01-01 was a Thursday. */
    fun weekdayMondayFirst(epochDay: Int): Int = (epochDay + 3).mod(7)

    fun weekStart(epochDay: Int): Int = epochDay - weekdayMondayFirst(epochDay)

    fun monthStart(epochDay: Int): Int {
        val c = civil(epochDay)
        return daysFromCivil(c[0], c[1], 1)
    }

    fun addMonthsToMonthStart(monthStart: Int, months: Int): Int {
        val c = civil(monthStart)
        val index = c[0] * 12 + (c[1] - 1) + months
        return daysFromCivil(index.floorDiv(12), index.mod(12) + 1, 1)
    }
}

object UsageStats {
    const val DAY_COUNT = 14
    const val WEEK_COUNT = 8
    const val MONTH_COUNT = 12

    private fun sum(days: Map<Int, DayUsage>, from: Int, to: Int): UsageBucketSum {
        var down = 0L
        var up = 0L
        for (d in from..to) {
            val u = days[d] ?: continue
            down += u.down
            up += u.up
        }
        return UsageBucketSum(down, up)
    }

    private class UsageBucketSum(val down: Long, val up: Long)

    private fun bucket(days: Map<Int, DayUsage>, range: UsageRange, start: Int, end: Int): UsageBucket {
        val s = sum(days, start, end)
        return UsageBucket(range, start, end, s.down, s.up)
    }

    /** Oldest first; the last bucket is the current day, week or month. */
    fun series(days: Map<Int, DayUsage>, range: UsageRange, today: Int): List<UsageBucket> = when (range) {
        UsageRange.Day -> (0 until DAY_COUNT).map { i ->
            val d = today - (DAY_COUNT - 1 - i)
            bucket(days, range, d, d)
        }
        UsageRange.Week -> {
            val current = UsageCalendar.weekStart(today)
            (0 until WEEK_COUNT).map { i ->
                val start = current - 7 * (WEEK_COUNT - 1 - i)
                bucket(days, range, start, start + 6)
            }
        }
        UsageRange.Month -> {
            val current = UsageCalendar.monthStart(today)
            (0 until MONTH_COUNT).map { i ->
                val start = UsageCalendar.addMonthsToMonthStart(current, -(MONTH_COUNT - 1 - i))
                val end = UsageCalendar.addMonthsToMonthStart(start, 1) - 1
                bucket(days, range, start, end)
            }
        }
    }

    fun current(days: Map<Int, DayUsage>, range: UsageRange, today: Int): UsageBucket = series(days, range, today).last()
}

/** Tiny text format: first line "v1", then one "epochDay,down,up" line per day. */
object UsageCodec {
    private const val HEADER = "v1"
    private const val KEEP_DAYS = 800

    fun encode(days: Map<Int, DayUsage>): String = buildString {
        append(HEADER).append('\n')
        for (key in days.keys.sorted()) {
            val u = days.getValue(key)
            append(key).append(',').append(u.down).append(',').append(u.up).append('\n')
        }
    }

    fun decode(text: String?): Map<Int, DayUsage> {
        if (text.isNullOrBlank()) return emptyMap()
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.firstOrNull() != HEADER) return emptyMap()
        val out = HashMap<Int, DayUsage>()
        for (line in lines.drop(1)) {
            val p = line.split(',')
            if (p.size != 3) continue
            val day = p[0].toIntOrNull() ?: continue
            val down = p[1].toLongOrNull()?.takeIf { it >= 0 } ?: continue
            val up = p[2].toLongOrNull()?.takeIf { it >= 0 } ?: continue
            out[day] = DayUsage(down, up)
        }
        return out
    }

    fun add(days: Map<Int, DayUsage>, day: Int, down: Long, up: Long): Map<Int, DayUsage> {
        if (down <= 0 && up <= 0) return days
        val next = HashMap(days)
        val old = next[day] ?: DayUsage()
        next[day] = DayUsage(old.down + down.coerceAtLeast(0), old.up + up.coerceAtLeast(0))
        val oldest = day - KEEP_DAYS
        next.keys.removeAll { it < oldest }
        return next
    }
}
