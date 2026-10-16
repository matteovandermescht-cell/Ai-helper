package com.matteo.aihelper

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One-shot diagnostic for a user-provided public image URL; never saves or logs the API key. */
object RoboflowWorkflowClient {
    private const val ENDPOINT =
        "https://serverless.roboflow.com/matteos-workspace-0ayrg/workflows/brawl-stars-entscheidungsprototyp-1792181498461"

    data class Result(
        val httpStatus: Int,
        val summary: String,
        val predictionCount: Int?,
        val outputKeys: List<String>,
        val detections: List<Detection>,
        val messageText: String,
        val responseBody: String,
        val isDetectionResponse: Boolean
    )

    fun runOnce(apiKey: String, imageUrl: String): Result {
        require(apiKey.isNotBlank()) { "Bitte einen API-Schlüssel eingeben." }
        require(imageUrl.startsWith("https://", ignoreCase = true)) {
            "Bitte eine öffentlich erreichbare HTTPS-Bild-URL eingeben."
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

            val root = try { JSONObject(body) } catch (_: Exception) { null }
            val keys = linkedSetOf<String>()
            val predictions = mutableListOf<Detection>()
            if (root != null) {
                root.keys().forEach { keys.add(it) }
                collectNamedOutputKeys(root, keys)
                collectPredictions(root, predictions)
            }
            val unique = predictions.distinctBy {
                listOf(it.label, it.confidence, it.left, it.top, it.right, it.bottom)
            }
            val messageText = root?.let { responseMessage(it) }.orEmpty()
            val hasPredictionPayload = root?.let { containsPredictionPayload(it) } == true
            val safeBody = redactSecrets(body, apiKey).take(MAX_RESPONSE_CHARS)
            val detectionResponse = root != null && hasPredictionPayload && status in 200..299 && messageText.isBlank()
            val summary = when {
                status !in 200..299 -> "HTTP $status · Anfrage fehlgeschlagen"
                root == null -> "HTTP $status · Antwort ist kein gültiges JSON"
                !hasPredictionPayload && messageText.isNotBlank() ->
                    "HTTP $status · API-/Workflow-Nachricht statt Erkennung: $messageText"
                !hasPredictionPayload -> "HTTP $status · Keine Erkennungsausgabe gefunden; Antwortformat prüfen."
                messageText.isNotBlank() -> "HTTP $status · Antwort enthält eine Fehlermeldung: $messageText"
                else -> "HTTP $status · Erkennungsausgabe geparst · " + unique.size +
                    " auswertbare Box(en). Das tatsächliche Live-Format muss auf dem Gerät bestätigt werden."
            }
            return Result(status, summary, if (detectionResponse) unique.size else null,
                keys.toList(), unique, messageText, safeBody, detectionResponse)
        } finally {
            connection.disconnect()
        }
    }

    private fun responseMessage(root: JSONObject): String {
        for (key in listOf("message", "error", "detail", "errors")) {
            val value = root.opt(key) ?: continue
            val text = usefulMessage(value)
            if (text.isNotBlank()) return text
        }
        return findNamedMessage(root)
    }

    private fun usefulMessage(value: Any?): String {
        if (value == null || value == JSONObject.NULL) return ""
        val text = value.toString().trim()
        return if (text.isBlank() || text == "null" || text == "{}" || text == "[]") "" else text
    }

    private fun findNamedMessage(value: Any?): String {
        when (value) {
            is JSONObject -> {
                val name = value.optString("name")
                if (name.equals("message", ignoreCase = true) ||
                    name.equals("error", ignoreCase = true) ||
                    name.equals("detail", ignoreCase = true)) {
                    usefulMessage(value.opt("value")).takeIf { it.isNotBlank() }?.let { return it }
                }
                val it = value.keys()
                while (it.hasNext()) {
                    val key = it.next()
                    if (key != "output_image" && key != "image") {
                        val found = findNamedMessage(value.opt(key))
                        if (found.isNotBlank()) return found
                    }
                }
            }
            is JSONArray -> for (i in 0 until value.length()) {
                val found = findNamedMessage(value.opt(i))
                if (found.isNotBlank()) return found
            }
            is String -> {
                val trimmed = value.trim()
                if (trimmed.startsWith("{") && trimmed.length < 1_000_000) {
                    try { return findNamedMessage(JSONObject(trimmed)) } catch (_: Exception) { return "" }
                }
            }
        }
        return ""
    }

    private fun containsPredictionPayload(value: Any?): Boolean = when (value) {
        is JSONObject -> {
            if (value.has("predictions") && value.opt("predictions") is JSONArray) true
            else {
                val it = value.keys()
                var found = false
                while (it.hasNext() && !found) {
                    val key = it.next()
                    if (key != "output_image" && key != "image") found = containsPredictionPayload(value.opt(key))
                }
                found
            }
        }
        is JSONArray -> (0 until value.length()).any { containsPredictionPayload(value.opt(it)) }
        is String -> {
            val trimmed = value.trim()
            if (trimmed.startsWith("{") && trimmed.length < 1_000_000) {
                try { containsPredictionPayload(JSONObject(trimmed)) } catch (_: Exception) { false }
            } else false
        }
        else -> false
    }

    private fun redactSecrets(body: String, apiKey: String): String {
        var safe = if (apiKey.isBlank()) body else body.replace(apiKey.trim(), "[REDACTED]")
        val pattern = Regex("(?i)(api_key\\s*[=:]\\s*)[^&\\s\\\"']+")
        safe = pattern.replace(safe) { match -> match.groupValues[1] + "[REDACTED]" }
        return safe
    }

    private const val MAX_RESPONSE_CHARS = 4000

    private fun collectNamedOutputKeys(value: Any?, keys: MutableSet<String>) {
        when (value) {
            is JSONObject -> {
                val name = value.optString("name")
                if (name.isNotBlank() && name != "null") keys.add(name)
                val it = value.keys()
                while (it.hasNext()) {
                    val key = it.next()
                    if (key != "output_image" && key != "image") collectNamedOutputKeys(value.opt(key), keys)
                }
            }
            is JSONArray -> for (i in 0 until value.length()) collectNamedOutputKeys(value.opt(i), keys)
            is String -> {
                val trimmed = value.trim()
                if (trimmed.startsWith("{") && trimmed.length < 1_000_000) {
                    try { collectNamedOutputKeys(JSONObject(trimmed), keys) } catch (_: Exception) {}
                }
            }
        }
    }

    private fun collectPredictions(value: Any?, out: MutableList<Detection>) {
        when (value) {
            is JSONObject -> {
                val predictionsValue = value.opt("predictions")
                if (predictionsValue is JSONArray) {
                    for (i in 0 until predictionsValue.length()) {
                        val p = predictionsValue.optJSONObject(i) ?: continue
                        val label = sequenceOf("class", "label", "class_name")
                            .map { p.optString(it) }
                            .firstOrNull { it.isNotBlank() && it != "null" } ?: continue
                        val x = number(p, "x") ?: continue
                        val y = number(p, "y") ?: continue
                        val width = number(p, "width") ?: continue
                        val height = number(p, "height") ?: continue
                        val confidence = number(p, "confidence") ?: continue
                        if (!x.isFinite() || !y.isFinite() || !width.isFinite() || !height.isFinite() ||
                            !confidence.isFinite() || width <= 0f || height <= 0f) continue
                        // Roboflow x/y are box centers, not top-left corners.
                        out.add(Detection(
                            label = label,
                            confidence = confidence.coerceIn(0f, 1f),
                            left = x - width / 2f,
                            top = y - height / 2f,
                            right = x + width / 2f,
                            bottom = y + height / 2f
                        ))
                    }
                } else if (predictionsValue is JSONObject) {
                    collectPredictions(predictionsValue, out)
                }
                val it = value.keys()
                while (it.hasNext()) {
                    val key = it.next()
                    if (key != "output_image" && key != "image" && key != "predictions") {
                        collectPredictions(value.opt(key), out)
                    }
                }
            }
            is JSONArray -> for (i in 0 until value.length()) collectPredictions(value.opt(i), out)
            is String -> {
                val trimmed = value.trim()
                if (trimmed.startsWith("{") && trimmed.length < 1_000_000) {
                    try { collectPredictions(JSONObject(trimmed), out) } catch (_: Exception) {}
                }
            }
        }
    }

    private fun number(json: JSONObject, key: String): Float? {
        if (!json.has(key) || json.isNull(key)) return null
        return try { json.getDouble(key).toFloat() } catch (_: Exception) { null }
    }
}
