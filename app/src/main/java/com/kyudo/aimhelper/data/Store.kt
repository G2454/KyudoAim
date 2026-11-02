package com.kyudo.aimhelper.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Saves sessions as one JSON file and settings in SharedPreferences. Fine for a POC. */
class Store(context: Context) {
    private val file = File(context.filesDir, "sessions.json")
    private val prefs = context.getSharedPreferences("kyudo_settings", Context.MODE_PRIVATE)

    fun loadSessions(): List<Session> = try {
        if (!file.exists()) emptyList() else {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { sessionFrom(arr.getJSONObject(it)) }
        }
    } catch (e: Exception) {
        emptyList()
    }

    @Synchronized
    fun saveSessions(list: List<Session>) {
        val arr = JSONArray()
        list.forEach { arr.put(sessionTo(it)) }
        val text = arr.toString()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }

    fun loadSettings(): Settings {
        val d = Settings()
        return Settings(
            nerai = runCatching { NeraiRef.valueOf(prefs.getString("nerai", d.nerai.name)!!) }
                .getOrDefault(d.nerai),
            voice = prefs.getBoolean("voice", d.voice),
            frontCamera = prefs.getBoolean("frontCamera", d.frontCamera),
            minKaiSec = prefs.getFloat("minKaiSec", d.minKaiSec),
            lineTolDeg = prefs.getFloat("lineTolDeg", d.lineTolDeg),
            arrowTolDeg = prefs.getFloat("arrowTolDeg", d.arrowTolDeg),
        )
    }

    fun saveSettings(s: Settings) {
        prefs.edit()
            .putString("nerai", s.nerai.name)
            .putBoolean("voice", s.voice)
            .putBoolean("frontCamera", s.frontCamera)
            .putFloat("minKaiSec", s.minKaiSec)
            .putFloat("lineTolDeg", s.lineTolDeg)
            .putFloat("arrowTolDeg", s.arrowTolDeg)
            .apply()
    }

    // ---- JSON mapping ----------------------------------------------------

    private fun sessionTo(s: Session) = JSONObject().apply {
        put("id", s.id)
        put("start", s.start)
        put("label", s.label)
        put("shots", JSONArray().also { arr -> s.shots.forEach { arr.put(shotTo(it)) } })
    }

    private fun sessionFrom(o: JSONObject): Session {
        val arr = o.optJSONArray("shots") ?: JSONArray()
        return Session(
            id = o.getLong("id"),
            start = o.getLong("start"),
            label = o.optString("label", "Practice"),
            shots = (0 until arr.length()).map { shotFrom(arr.getJSONObject(it)) },
        )
    }

    private fun shotTo(s: Shot) = JSONObject().apply {
        put("id", s.id)
        put("time", s.time)
        s.kaiMs?.let { put("kaiMs", it) }
        putNum("arrowAngle", s.arrowAngle)
        putNum("shoulderDev", s.shoulderDev)
        putNum("hipDev", s.hipDev)
        putNum("legSpread", s.legSpread)
        putNum("bowElbow", s.bowElbow)
        putNum("headMove", s.headMove)
        put("flags", JSONArray().also { arr -> s.flags.forEach { arr.put(it) } })
        s.result?.let { r ->
            put("result", JSONObject().apply {
                put("hit", r.hit)
                putNum("x", r.x)
                putNum("y", r.y)
                r.miss?.let { put("miss", it) }
            })
        }
    }

    private fun shotFrom(o: JSONObject): Shot {
        val flags = o.optJSONArray("flags") ?: JSONArray()
        val r = o.optJSONObject("result")
        return Shot(
            id = o.getLong("id"),
            time = o.getLong("time"),
            kaiMs = if (o.has("kaiMs") && !o.isNull("kaiMs")) o.getLong("kaiMs") else null,
            arrowAngle = o.floatOrNull("arrowAngle"),
            shoulderDev = o.floatOrNull("shoulderDev"),
            hipDev = o.floatOrNull("hipDev"),
            legSpread = o.floatOrNull("legSpread"),
            bowElbow = o.floatOrNull("bowElbow"),
            headMove = o.floatOrNull("headMove"),
            flags = (0 until flags.length()).map { flags.getString(it) },
            result = r?.let {
                ShotResult(
                    hit = it.getBoolean("hit"),
                    x = it.floatOrNull("x") ?: 0f,
                    y = it.floatOrNull("y") ?: 0f,
                    miss = if (it.has("miss") && !it.isNull("miss")) it.getString("miss") else null,
                )
            },
        )
    }

    private fun JSONObject.putNum(key: String, v: Float?) {
        if (v != null && v.isFinite()) put(key, v.toDouble())
    }

    private fun JSONObject.floatOrNull(key: String): Float? =
        if (has(key) && !isNull(key)) getDouble(key).toFloat() else null
}
