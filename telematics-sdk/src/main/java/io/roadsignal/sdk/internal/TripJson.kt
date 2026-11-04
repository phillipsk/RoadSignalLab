package io.roadsignal.sdk.internal

import io.roadsignal.core.*
import org.json.JSONArray
import org.json.JSONObject

internal object TripJson {
    fun encode(t: TripSummary): String =
        JSONObject()
            .apply {
                put("schema", 1)
                put("id", t.id)
                put("start", t.startElapsedMs)
                put("end", t.endElapsedMs)
                put("distance", t.distanceMeters)
                put("maxSpeed", t.maxSpeedMps)
                put("reason", t.endReason.name)
                put("role", t.role.name)
                put("source", t.source.name)
                put(
                    "events",
                    JSONArray().apply {
                        t.events.forEach {
                            put(
                                JSONObject()
                                    .put("kind", it.kind.name)
                                    .put("time", it.elapsedMs)
                                    .put("magnitude", it.magnitude),
                            )
                        }
                    },
                )
                put(
                    "route",
                    JSONArray().apply {
                        t.route.forEach { put(JSONArray().put(it.latitude).put(it.longitude)) }
                    },
                )
            }
            .toString()

    fun decode(text: String): TripSummary {
        val o = JSONObject(text)
        require(o.getInt("schema") == 1)
        val e = o.getJSONArray("events")
        val r = o.getJSONArray("route")
        return TripSummary(
            o.getString("id"),
            o.getLong("start"),
            o.getLong("end"),
            o.getDouble("distance"),
            o.getDouble("maxSpeed"),
            List(e.length()) { i ->
                e.getJSONObject(i).let {
                    DrivingEvent(
                        EventKind.valueOf(it.getString("kind")),
                        it.getLong("time"),
                        it.getDouble("magnitude"),
                    )
                }
            },
            List(r.length()) { i ->
                r.getJSONArray(i).let { RoutePoint(it.getDouble(0), it.getDouble(1)) }
            },
            TripEnd.valueOf(o.getString("reason")),
            TripRole.valueOf(o.getString("role")),
            SourceMode.valueOf(o.getString("source")),
        )
    }
}
