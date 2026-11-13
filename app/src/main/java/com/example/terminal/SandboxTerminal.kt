package com.example.terminal

import androidx.compose.runtime.Immutable
import com.example.data.OrchClient
import com.example.data.OrchJson
import com.example.data.parseErrorBody
import com.example.data.toApiError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

enum class TerminalStatus { Disconnected, Connecting, Connected, Exited }

@Immutable
data class TerminalUi(
    val workspace: String = "",
    val status: TerminalStatus = TerminalStatus.Disconnected,
    val output: String = "",
    val error: String? = null
)

class SandboxTerminal(private val client: OrchClient) {
    private val _state = MutableStateFlow(TerminalUi())
    val state: StateFlow<TerminalUi> = _state.asStateFlow()

    private val lock = Any()
    private val lines = ArrayList<StringBuilder>().apply { add(StringBuilder()) }
    private var cursorCol = 0
    private var escState = EscState.Normal
    private var socket: WebSocket? = null
    private var activeWorkspace = ""
    private var currentCols = DEFAULT_COLS
    private var currentRows = DEFAULT_ROWS

    fun connect(workspace: String, cols: Int = currentCols, rows: Int = currentRows) {
        val clampedCols = cols.coerceIn(MIN_COLS, MAX_COLS)
        val clampedRows = rows.coerceIn(MIN_ROWS, MAX_ROWS)
        synchronized(lock) {
            if (socket != null && activeWorkspace == workspace &&
                (_state.value.status == TerminalStatus.Connecting || _state.value.status == TerminalStatus.Connected)
            ) {
                return
            }
            socket?.close(1000, "reconnect")
            socket = null
            activeWorkspace = workspace
            currentCols = clampedCols
            currentRows = clampedRows
        }

        _state.update {
            it.copy(
                workspace = workspace,
                status = TerminalStatus.Connecting,
                error = null
            )
        }

        val url = client.baseUrl.newBuilder()
            .addPathSegments("cloud/terminal")
            .addQueryParameter("workspace", workspace)
            .addQueryParameter("cols", clampedCols.toString())
            .addQueryParameter("rows", clampedRows.toString())
            .build()

        val request = Request.Builder().url(url).build()
        val opened = client.sseHttp.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    synchronized(lock) {
                        if (socket !== webSocket) return
                    }
                    _state.update { it.copy(status = TerminalStatus.Connected, error = null) }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    synchronized(lock) {
                        if (socket !== webSocket) return
                    }
                    val root = runCatching { OrchJson.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
                    when (root.str("type")) {
                        "data" -> {
                            val chunk = (root["data"] as? JsonPrimitive)?.contentOrNull ?: return
                            appendChunk(chunk)
                        }
                        "exit" -> {
                            synchronized(lock) {
                                if (socket === webSocket) socket = null
                            }
                            _state.update { it.copy(status = TerminalStatus.Exited) }
                        }
                    }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                    synchronized(lock) {
                        if (socket !== webSocket) return
                        socket = null
                    }
                    _state.update {
                        if (it.status == TerminalStatus.Connected || it.status == TerminalStatus.Connecting) {
                            it.copy(status = TerminalStatus.Exited)
                        } else {
                            it
                        }
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    synchronized(lock) {
                        if (socket !== webSocket) return
                        socket = null
                    }
                    val message = if (response != null && !response.isSuccessful) {
                        parseErrorBody(response.code, runCatching { response.body?.string().orEmpty() }.getOrDefault("")).message
                    } else {
                        t.toApiError().message
                    }
                    _state.update { it.copy(status = TerminalStatus.Disconnected, error = message) }
                }
            }
        )
        synchronized(lock) {
            socket = opened
        }
    }

    fun sendInput(data: String) {
        if (data.isEmpty()) return
        val ws = synchronized(lock) { socket } ?: return
        val payload = OrchJson.encodeToString(
            buildJsonObject {
                put("type", "input")
                put("data", data)
            }
        )
        ws.send(payload)
    }

    fun resize(cols: Int, rows: Int) {
        val c = cols.coerceIn(MIN_COLS, MAX_COLS)
        val r = rows.coerceIn(MIN_ROWS, MAX_ROWS)
        val ws = synchronized(lock) {
            if (c == currentCols && r == currentRows) return
            currentCols = c
            currentRows = r
            socket
        } ?: return
        val payload = OrchJson.encodeToString(
            buildJsonObject {
                put("type", "resize")
                put("cols", c)
                put("rows", r)
            }
        )
        ws.send(payload)
    }

    fun clearOutput() {
        synchronized(lock) {
            lines.clear()
            lines.add(StringBuilder())
            cursorCol = 0
        }
        _state.update { it.copy(output = "") }
    }

    fun disconnect() {
        val ws = synchronized(lock) {
            val existing = socket
            socket = null
            existing
        } ?: return
        ws.send(OrchJson.encodeToString(buildJsonObject { put("type", "close") }))
        ws.close(1000, "closed")
        _state.update { it.copy(status = TerminalStatus.Disconnected) }
    }

    private fun appendChunk(chunk: String) {
        val rendered = synchronized(lock) {
            for (ch in chunk) {
                when (escState) {
                    EscState.Normal -> when (ch) {
                        '\u001B' -> escState = EscState.Esc
                        '\u0007' -> Unit
                        '\r' -> cursorCol = 0
                        '\n' -> {
                            lines.add(StringBuilder())
                            if (lines.size > MAX_SCROLLBACK_LINES) lines.removeAt(0)
                            cursorCol = 0
                        }
                        '\b' -> if (cursorCol > 0) cursorCol--
                        '\t' -> writeChars("    ")
                        else -> if (ch >= ' ') writeChar(ch)
                    }
                    EscState.Esc -> when (ch) {
                        '[' -> escState = EscState.Csi
                        ']' -> escState = EscState.Osc
                        '(', ')', '*', '+' -> escState = EscState.Charset
                        else -> escState = EscState.Normal
                    }
                    EscState.Charset -> escState = EscState.Normal
                    EscState.Csi -> {
                        if (ch in '@'..'~') {
                            if (ch == 'K') {
                                val line = lines.last()
                                if (cursorCol < line.length) line.setLength(cursorCol)
                            } else if (ch == 'J' && lines.size == 1 && cursorCol == 0) {
                                lines.last().clear()
                            }
                            escState = EscState.Normal
                        }
                    }
                    EscState.Osc -> when (ch) {
                        '\u0007' -> escState = EscState.Normal
                        '\u001B' -> escState = EscState.OscEsc
                    }
                    EscState.OscEsc -> {
                        escState = if (ch == '\\') EscState.Normal else EscState.Osc
                    }
                }
            }
            lines.joinToString("\n") { it.toString() }
        }
        _state.update { it.copy(output = rendered) }
    }

    private fun writeChars(text: String) {
        for (ch in text) writeChar(ch)
    }

    private fun writeChar(ch: Char) {
        val line = lines.last()
        while (line.length < cursorCol) line.append(' ')
        if (cursorCol == line.length) {
            line.append(ch)
        } else {
            line.setCharAt(cursorCol, ch)
        }
        cursorCol++
    }

    private enum class EscState { Normal, Esc, Csi, Osc, OscEsc, Charset }

    private companion object {
        const val DEFAULT_COLS = 80
        const val DEFAULT_ROWS = 24
        const val MIN_COLS = 10
        const val MAX_COLS = 500
        const val MIN_ROWS = 4
        const val MAX_ROWS = 200
        const val MAX_SCROLLBACK_LINES = 1500
    }
}

private fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
