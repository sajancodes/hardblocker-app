package dev.sajancodes.hardblock.data.repository

import dev.sajancodes.hardblock.domain.policy.*
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant

object PolicyCodec {
    fun encode(p: RestrictionPolicy): String = JSONObject().apply {
        put("id", p.id); put("packages", JSONArray(p.packages.sorted())); put("created", p.createdAt.toEpochMilli())
        put("activated", p.activatedAt?.toEpochMilli()); put("expires", p.expiresAt?.toEpochMilli()); put("state", p.state.name)
        put("daily", p.dailyLimit?.toMillis()); put("reset", p.resetMinute); put("zone", p.zoneId)
        put("until", p.blockedUntil?.toEpochMilli()); put("always", p.alwaysBlocked); put("cooldown", p.relaxationCooldown.toMillis())
        put("warnings", JSONArray(p.warningMinutes.sorted()))
        put("schedule", JSONArray().apply { p.schedule.sortedWith(compareBy({ it.day }, { it.startMinute }, { it.endMinute })).forEach {
            put(JSONArray(listOf(it.day, it.startMinute, it.endMinute)))
        } })
    }.toString()
    fun decode(value: String): RestrictionPolicy {
        val j = JSONObject(value)
        fun instant(key: String) = if (j.has(key)) Instant.ofEpochMilli(j.getLong(key)) else null
        val packages = j.getJSONArray("packages")
        val schedule = j.getJSONArray("schedule")
        return RestrictionPolicy(j.getString("id"), (0 until packages.length()).map { packages.getString(it) }.toSet(),
            Instant.ofEpochMilli(j.getLong("created")), instant("activated"), instant("expires"), PolicyState.valueOf(j.getString("state")),
            if (j.has("daily")) Duration.ofMillis(j.getLong("daily")) else null, j.getInt("reset"), j.getString("zone"),
            instant("until"), j.getBoolean("always"), (0 until schedule.length()).map {
                val a = schedule.getJSONArray(it); WeeklyWindow(a.getInt(0), a.getInt(1), a.getInt(2))
            }.toSet(), Duration.ofMillis(j.getLong("cooldown")),
            j.optJSONArray("warnings")?.let { a -> (0 until a.length()).map { a.getInt(it) }.toSet() } ?: setOf(5, 1))
    }
}
