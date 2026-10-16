package com.matteo.aihelper

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * One-shot diagnostic call for a user-provided public image URL.
 * The API key is used only in this request and is never saved or logged.
 */
object RoboflowWorkflowClient {
    private const val ENDPOINT =
        "https://serverless.roboflow.com/matteos-workspace-0ayrg/workflows/brawl-stars-spielassistent"

    data class Result(
        val httpStatus: Int,
        val rawBody: String,
        val predictionCount: Int?,
        val outputKeys: List<String>
    )

    fun runOnce(apiKey: String, imageUrl: String): Result {
        require(apiKey.isNotBlank()) { "Bitte einen API-Schlüssel eingeben." }
        require(imageUrl.startsWith("https://", ignoreCase = true) ||
                imageUrl.startsWith("http://", ignoreCase = true)) {
            "Bitte eine gültige http(s)-Bild-URL eingeben."
        }

        val encodedKey = URLEncoder.encode(apiKey.trim(), "UTF-8")
        val connection = (URL("$ENDPOINT?api_key=$encodedKey").openConnection() as HttpURLConnection)
        connection.requestMethod = "POST"
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Accept", "application/json")

        try {
            val payload = JSONObject().apply {
                put("inputs", JSONObject().put("image", JSONObject()
                    .put("type", "url")
                    .put("value", imageUrl.trim())))
                put("confidence", 0.4)
                put("iou_threshold", 0.3)
                put("class_agnostic_nms", false)
                put("max_detections", 1000)
            }.toString()

            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
            }.orEmpty()

            val keys = mutableListOf<String>()
            var count: Int? = null
            try {
                val root = JSONObject(body)
                val outputs = root.optJSONArray("outputs")
                if (outputs != null) {
                    for (i in 0 until outputs.length()) {
                        val item = outputs.optJSONObject(i) ?: continue
                        val value = item.opt("value")
                        when (value) {
                            is JSONObject -> {
                                val innerPredictions = value.optJSONArray("predictions")
                                if (innerPredictions != null) count = innerPredictions.length()
                                value.keys().forEach { key -> if (!keys.contains(key)) keys.add(key) }
                            }
                            is JSONArray -> if (item.optString("name") == "predictions") count = value.length()
                        }
                        val name = item.optString("name")
                        if (name.isNotBlank() && !keys.contains(name)) keys.add(name)
                    }
                }
                if (count == null) {
                    val predictions = root.optJSONArray("predictions")
                    if (predictions != null) count = predictions.length()
                }
                root.keys().forEach { key -> if (!keys.contains(key)) keys.add(key) }
            } catch (_: Exception) {
                // Preserve the real response body for diagnostics even when it is not JSON.
            }
            return Result(status, body, count, keys)
        } finally {
            connection.disconnect()
        }
    }
}
