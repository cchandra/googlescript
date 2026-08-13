package com.charlie.scriptwatch

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ScriptStore(context: Context) {
    private val prefs = context.getSharedPreferences("scriptwatch", Context.MODE_PRIVATE)

    fun load(): List<ScriptConfig> {
        val raw = prefs.getString("scripts", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(ScriptConfig(
                    name = o.optString("name"),
                    scriptId = o.optString("scriptId"),
                    deploymentId = o.optString("deploymentId"),
                    functionName = o.optString("functionName"),
                    extraScopes = o.optString("extraScopes")
                ))
            }
        }
    }

    fun save(items: List<ScriptConfig>) {
        val arr = JSONArray()
        items.forEach { s ->
            arr.put(JSONObject().apply {
                put("name", s.name)
                put("scriptId", s.scriptId)
                put("deploymentId", s.deploymentId)
                put("functionName", s.functionName)
                put("extraScopes", s.extraScopes)
            })
        }
        prefs.edit().putString("scripts", arr.toString()).apply()
    }
}
