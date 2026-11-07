package com.saudeplus.app

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class Medication(
    val id: Int,
    val name: String,
    val dose: String,
    val hour: Int,
    val minute: Int,
) {
    val timeText: String get() = "%02d:%02d".format(hour, minute)
}

/**
 * Armazenamento simples em SharedPreferences (JSON).
 * - Lista de medicamentos
 * - Registros de "tomado": chave "id|aaaa-mm-dd" -> horário (epoch ms)
 */
object MedStore {
    private const val PREFS = "saudeplus"
    private const val KEY_MEDS = "meds"
    private const val KEY_TAKEN = "taken"
    private const val KEY_NEXT_ID = "next_id"
    private const val KEEP_DAYS = 90L

    private lateinit var prefs: SharedPreferences

    private val _meds = MutableStateFlow<List<Medication>>(emptyList())
    val meds: StateFlow<List<Medication>> = _meds.asStateFlow()

    private val _taken = MutableStateFlow<Map<String, Long>>(emptyMap())
    val taken: StateFlow<Map<String, Long>> = _taken.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        reload()
    }

    @Synchronized
    fun reload() {
        _meds.value = parseMeds(prefs.getString(KEY_MEDS, "[]") ?: "[]")
        _taken.value = parseTaken(prefs.getString(KEY_TAKEN, "{}") ?: "{}")
    }

    fun key(medId: Int, date: LocalDate = LocalDate.now()) = "$medId|$date"

    fun takenAt(medId: Int, date: LocalDate = LocalDate.now()): Long? = _taken.value[key(medId, date)]

    fun find(id: Int): Medication? = _meds.value.firstOrNull { it.id == id }

    @Synchronized
    fun add(name: String, dose: String, hour: Int, minute: Int): Medication {
        val id = prefs.getInt(KEY_NEXT_ID, 1)
        val med = Medication(id, name.trim(), dose.trim(), hour, minute)
        val list = _meds.value + med
        prefs.edit().putInt(KEY_NEXT_ID, id + 1).putString(KEY_MEDS, medsToJson(list)).apply()
        _meds.value = list
        return med
    }

    @Synchronized
    fun remove(id: Int) {
        val list = _meds.value.filterNot { it.id == id }
        val taken = _taken.value.filterKeys { !it.startsWith("$id|") }
        prefs.edit()
            .putString(KEY_MEDS, medsToJson(list))
            .putString(KEY_TAKEN, takenToJson(taken))
            .apply()
        _meds.value = list
        _taken.value = taken
    }

    @Synchronized
    fun setTaken(medId: Int, taken: Boolean, date: LocalDate = LocalDate.now()) {
        val cutoff = LocalDate.now().minusDays(KEEP_DAYS)
        val map = _taken.value.toMutableMap()
        if (taken) map[key(medId, date)] = System.currentTimeMillis() else map.remove(key(medId, date))
        // Remove registros antigos para manter o armazenamento pequeno.
        map.keys.removeAll { k ->
            val d = runCatching { LocalDate.parse(k.substringAfter('|')) }.getOrNull()
            d == null || d.isBefore(cutoff)
        }
        prefs.edit().putString(KEY_TAKEN, takenToJson(map)).apply()
        _taken.value = map
    }

    private fun parseMeds(json: String): List<Medication> = runCatching {
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Medication(
                id = o.getInt("id"),
                name = o.getString("name"),
                dose = o.optString("dose", ""),
                hour = o.getInt("hour"),
                minute = o.getInt("minute"),
            )
        }
    }.getOrDefault(emptyList())

    private fun medsToJson(list: List<Medication>): String {
        val arr = JSONArray()
        list.forEach { m ->
            arr.put(
                JSONObject()
                    .put("id", m.id)
                    .put("name", m.name)
                    .put("dose", m.dose)
                    .put("hour", m.hour)
                    .put("minute", m.minute)
            )
        }
        return arr.toString()
    }

    private fun parseTaken(json: String): Map<String, Long> = runCatching {
        val o = JSONObject(json)
        o.keys().asSequence().associateWith { o.getLong(it) }
    }.getOrDefault(emptyMap())

    private fun takenToJson(map: Map<String, Long>): String {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        return o.toString()
    }
}
