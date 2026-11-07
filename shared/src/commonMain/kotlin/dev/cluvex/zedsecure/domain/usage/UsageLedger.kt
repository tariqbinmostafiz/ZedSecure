package dev.cluvex.zedsecure.domain.usage

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Daily VPN traffic history, fed by the platform while a tunnel is connected.
 * The platform loads it at startup and saves [encode] to disk now and then.
 */
object UsageLedger {
    private val state = MutableStateFlow<Map<Int, DayUsage>>(emptyMap())
    val days: StateFlow<Map<Int, DayUsage>> = state.asStateFlow()

    /** The platform sets this at startup to persist [days] to disk; a no-op until then. */
    var persist: () -> Unit = {}

    fun load(text: String?) {
        state.value = UsageCodec.decode(text)
    }

    /** Like [load], but also saves immediately — used when restoring a full backup. */
    fun restore(text: String?) {
        load(text)
        persist()
    }

    fun record(down: Long, up: Long, nowMillis: Long, offsetMillis: Long) {
        val day = UsageCalendar.epochDay(nowMillis, offsetMillis)
        state.update { UsageCodec.add(it, day, down, up) }
    }

    fun encode(): String = UsageCodec.encode(state.value)

    fun clear() {
        state.value = emptyMap()
    }
}

/** The platform sets these once at startup. */
object UsageClock {
    var nowMillis: () -> Long = { 0L }
    var offsetMillis: () -> Long = { 0L }

    fun todayEpochDay(): Int = UsageCalendar.epochDay(nowMillis(), offsetMillis())
}
