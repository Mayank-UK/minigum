package com.minigum.ui.screens.logic

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

/**
 * Client-side AI helper for the Logic game.
 *
 * Calls Google's free Gemini REST API directly from the device (no backend), using an
 * API key the user supplies. The circuit is serialized to plain text, so the model
 * reasons over the *graph* (deterministic, cheap) rather than pixels.
 *
 * Uses only [HttpsURLConnection] + org.json, so no extra dependencies are needed.
 */
object AiChecker {

    /**
     * Bundled fallback key so the AI works without setup. NOTE: anything shipped in an
     * APK is extractable, and this one has already been shared — rotate it in AI Studio
     * and prefer a user-supplied key (set in-app) for anything distributed.
     */
    const val BUNDLED_KEY = "AQ.Ab8RN6IiwEvPgYPvpImnYUgn6L6tIIOEPXBliHkfCkT9YD9zNA"

    private const val MODEL = "gemini-2.0-flash"
    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    /**
     * Ask Gemini to judge whether the player's flowchart solves [problem]. The flow is
     * serialized to readable text (blocks + arrows). Returns the model's reply, whose first
     * line is expected to be "CORRECT" or "INCORRECT".
     */
    fun judge(
        apiKey: String,
        problem: LogicViewModel.Problem,
        blocks: List<LogicViewModel.Block>,
        connections: List<LogicViewModel.Connection>,
    ): String {
        require(apiKey.isNotBlank()) { "No AI key configured." }

        val name = blocks.associate { it.id to labelFor(it) }
        val flow = buildString {
            appendLine("Problem: ${problem.statement}")
            appendLine("Input variable: ${problem.inputVar}. Available variables: ${problem.variables.joinToString(", ")}.")
            appendLine("Expected output: ${problem.outputDescription}.")
            appendLine("Blocks:")
            if (blocks.isEmpty()) appendLine("  (none)") else blocks.forEach { b -> appendLine("  - [${b.id}] ${name[b.id]}") }
            appendLine("Arrows (from --socket--> to):")
            if (connections.isEmpty()) appendLine("  (none)")
            connections.forEach { c ->
                appendLine("  - ${name[c.from]} --${c.socket.label}--> ${name[c.to]}")
            }
        }

        val prompt = """
            You are a programming tutor judging a flowchart a student built. The flowchart is made
            of blocks connected by arrows showing the order of execution. Block types: Start, Read
            (read input into a variable), Set (variable = expression), If (condition, with "yes"
            and "no" branches), While (condition, with a "body" that repeats and an "exit"), Output,
            End. A While loop re-checks its condition after its body finishes.

            Decide whether the flowchart correctly solves the problem for all valid inputs. Be
            strict: it must be a complete, correct algorithm (initialise variables, loop correctly,
            and output the right value).

            Reply with EXACTLY this format:
            Line 1: the single word CORRECT or INCORRECT
            Line 2 onward: a brief, friendly explanation (under 70 words). If incorrect, say what is
            missing or wrong and give a small hint — do not write the full solution.

            $flow
        """.trimIndent()

        val reply = callGemini(apiKey, prompt)
        if (reply.isBlank()) return "INCORRECT\nThe model returned an empty response."
        return reply.trim()
    }

    private fun labelFor(block: LogicViewModel.Block): String = when (val p = block.params) {
        is LogicViewModel.Params.Start -> "Start"
        is LogicViewModel.Params.End -> "End"
        is LogicViewModel.Params.Read -> "Read ${p.variable}"
        is LogicViewModel.Params.Assign -> "Set ${p.target} = ${p.rhs.render()}"
        is LogicViewModel.Params.If -> "If ${p.cond.render()}"
        is LogicViewModel.Params.While -> "While ${p.cond.render()}"
        is LogicViewModel.Params.Output -> "Output ${p.value.render()}"
    }

    private fun callGemini(apiKey: String, prompt: String): String {
        val url = URL("$ENDPOINT?key=" + URLEncoder.encode(apiKey, "UTF-8"))
        val body = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().put("text", prompt))
                    })
                })
            })
        }.toString()

        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            doOutput = true
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw RuntimeException(parseError(text, code))
            }
            val json = JSONObject(text)
            return json.optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)
                ?.optString("text").orEmpty()
        } finally {
            conn.disconnect()
        }
    }

    private fun parseError(body: String, code: Int): String {
        val msg = runCatching {
            JSONObject(body).optJSONObject("error")?.optString("message")
        }.getOrNull()
        return "Gemini error $code: ${msg ?: body.take(160)}"
    }
}
