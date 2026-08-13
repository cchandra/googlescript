package com.charlie.scriptwatch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class AppsScriptApi {
    private val client = OkHttpClient()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun runFunction(token: String, deploymentId: String, functionName: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val body = JSONObject().apply {
                put("function", functionName)
                put("devMode", false)
            }.toString().toRequestBody(jsonType)
            val request = Request.Builder()
                .url("https://script.googleapis.com/v1/scripts/$deploymentId:run")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("HTTP ${response.code}: $text")
                val obj = JSONObject(text)
                if (obj.has("error")) error(obj.getJSONObject("error").toString(2))
                obj.optJSONObject("response")?.opt("result")?.toString() ?: "Completed"
            }
        }
    }

    suspend fun listProcesses(token: String, scriptId: String): Result<List<ScriptProcess>> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = URLEncoder.encode(scriptId, StandardCharsets.UTF_8.toString())
            val url = "https://script.googleapis.com/v1/processes?pageSize=20&userProcessFilter.scriptId=$encoded"
            val request = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $token")
                .get().build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("HTTP ${response.code}: $text")
                val arr = JSONObject(text).optJSONArray("processes") ?: return@use emptyList()
                buildList {
                    for (i in 0 until arr.length()) {
                        val p = arr.getJSONObject(i)
                        add(ScriptProcess(
                            functionName = p.optString("functionName", "—"),
                            status = p.optString("processStatus", "UNKNOWN"),
                            type = p.optString("processType", "UNKNOWN"),
                            startTime = p.optString("startTime", ""),
                            duration = p.optString("duration", "")
                        ))
                    }
                }
            }
        }
    }

    suspend fun getMetrics(token: String, scriptId: String): Result<MetricsSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://script.googleapis.com/v1/projects/$scriptId/metrics?metricsGranularity=DAILY"
            val request = Request.Builder().url(url)
                .addHeader("Authorization", "Bearer $token")
                .get().build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("HTTP ${response.code}: $text")
                val obj = JSONObject(text)
                fun sum(field: String): Long {
                    val arr = obj.optJSONArray(field) ?: return 0
                    var total = 0L
                    for (i in 0 until arr.length()) total += arr.getJSONObject(i).optString("value", "0").toLongOrNull() ?: 0
                    return total
                }
                MetricsSummary(total = sum("totalExecutions"), failed = sum("failedExecutions"))
            }
        }
    }
}
