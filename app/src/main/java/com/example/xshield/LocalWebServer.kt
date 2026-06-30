package com.example.xshield

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.PrintWriter
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.concurrent.thread

class LocalWebServer(private val context: android.content.Context, private val port: Int) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    init {
        // Generate a random secure token for remote authentication
        XshieldRepository.secureToken = "xs_sec_" + UUID.randomUUID().toString().take(6)
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        XshieldRepository.isServerRunning.value = true
        thread(start = true, name = "XshieldWebServerThread") {
            try {
                serverSocket = ServerSocket(port)
                while (isRunning) {
                    val socket = serverSocket?.accept() ?: break
                    thread { handleConnection(socket) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stop() {
        isRunning = false
        XshieldRepository.isServerRunning.value = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        serverSocket = null
    }

    private fun handleConnection(socket: Socket) {
        try {
            val input = socket.getInputStream()
            val reader = BufferedReader(InputStreamReader(input))
            val output = socket.getOutputStream()
            val writer = PrintWriter(output, true)

            val firstLine = reader.readLine() ?: return
            val parts = firstLine.split(" ")
            if (parts.size < 2) return
            val method = parts[0]
            val fullPath = parts[1]

            // Parse path and query params
            val pathParts = fullPath.split("?")
            val path = pathParts[0]
            val query = if (pathParts.size > 1) pathParts[1] else ""
            val queryParams = parseQueryString(query)

            // Read headers to extract content length
            var contentLength = 0
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line!!.isEmpty()) break
                if (line!!.lowercase(Locale.ROOT).startsWith("content-length:")) {
                    contentLength = line!!.substring(15).trim().toIntOrNull() ?: 0
                }
            }

            // Read request body if present
            val body = if (contentLength > 0) {
                val bodyChars = CharArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val count = reader.read(bodyChars, read, contentLength - read)
                    if (count == -1) break
                    read += count
                }
                String(bodyChars)
            } else ""

            // Check security token
            val requestToken = queryParams["auth"] ?: ""
            val isAuthorized = requestToken == XshieldRepository.secureToken

            if (!isAuthorized) {
                send403Response(writer, output)
                return
            }

            // Route requests
            when {
                method == "GET" && path == "/" -> {
                    sendHtmlResponse(writer, output, getDashboardHtml())
                }
                method == "GET" && path == "/childagent.apk" -> {
                    sendApkResponse(output)
                }
                method == "GET" && path == "/api/state" -> {
                    sendJsonResponse(writer, output, getRepositoryStateJson())
                }
                method == "POST" && path == "/api/select-device" -> {
                    val targetDevice = getJsonValue(body, "device")
                    if (targetDevice.isNotBlank() && XshieldRepository.childDevices.contains(targetDevice)) {
                        XshieldRepository.selectDevice(targetDevice)
                    }
                    sendJsonResponse(writer, output, "{\"ok\":true}")
                }
                method == "POST" && path == "/api/block" -> {
                    val num = getJsonValue(body, "number")
                    val type = getJsonValue(body, "type")
                    if (num.isNotBlank()) {
                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        val newBlock = BlockedNumber(
                            id = (XshieldRepository.blockedList.size + 1).toString(),
                            number = num,
                            type = if (type.isNotBlank()) type else "Both",
                            date = today,
                            blocked = true
                        )
                        XshieldRepository.blockedList.add(newBlock)
                    }
                    sendJsonResponse(writer, output, "{\"ok\":true}")
                }
                method == "POST" && path == "/api/unblock" -> {
                    val id = getJsonValue(body, "id")
                    if (id.isNotBlank()) {
                        XshieldRepository.blockedList.removeAll { it.id == id }
                    }
                    sendJsonResponse(writer, output, "{\"ok\":true}")
                }
                method == "POST" && path == "/api/delete-call" -> {
                    val id = getJsonValue(body, "id")
                    if (id.isNotBlank()) {
                        XshieldRepository.callsList.removeAll { it.id == id }
                    }
                    sendJsonResponse(writer, output, "{\"ok\":true}")
                }
                else -> {
                    send404Response(writer, output)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            socket.close()
        }
    }

    private fun parseQueryString(query: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        if (query.isBlank()) return result
        val pairs = query.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf("=")
            if (idx > 0) {
                val key = pair.substring(0, idx)
                val value = pair.substring(idx + 1)
                result[key] = value
            }
        }
        return result
    }

    private fun sendHtmlResponse(writer: PrintWriter, output: OutputStream, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        writer.print("HTTP/1.1 200 OK\r\n")
        writer.print("Content-Type: text/html; charset=utf-8\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Connection: close\r\n")
        writer.print("\r\n")
        writer.flush()
        output.write(bytes)
        output.flush()
    }

    private fun sendJsonResponse(writer: PrintWriter, output: OutputStream, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        writer.print("HTTP/1.1 200 OK\r\n")
        writer.print("Content-Type: application/json; charset=utf-8\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Access-Control-Allow-Origin: *\r\n")
        writer.print("Connection: close\r\n")
        writer.print("\r\n")
        writer.flush()
        output.write(bytes)
        output.flush()
    }

    private fun sendApkResponse(output: OutputStream) {
        try {
            val inputStream = context.assets.open("childagent.apk")
            val size = inputStream.available()
            
            // Check if the file is the small text placeholder rather than the compiled APK
            if (size < 10000) {
                inputStream.close()
                val writer = PrintWriter(output, true)
                sendHtmlResponse(writer, output, "<html><head><title>APK Not Built</title></head><body style=\"background-color:#1A1D26;color:#E3E7ED;font-family:sans-serif;text-align:center;padding-top:100px;\"><h2>APK Not Built Yet</h2><p>The Child Agent APK has not been compiled and packaged yet.<br>Please run/build the project in Android Studio to generate the APK.</p></body></html>")
                return
            }
            
            val writer = PrintWriter(output, true)
            writer.print("HTTP/1.1 200 OK\r\n")
            writer.print("Content-Type: application/vnd.android.package-archive\r\n")
            writer.print("Content-Length: $size\r\n")
            writer.print("Content-Disposition: attachment; filename=\"childagent.apk\"\r\n")
            writer.print("Connection: close\r\n")
            writer.print("\r\n")
            writer.flush()
            
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
            }
            output.flush()
            inputStream.close()
        } catch (e: Exception) {
            e.printStackTrace()
            val writer = PrintWriter(output, true)
            send404Response(writer, output)
        }
    }

    private fun send403Response(writer: PrintWriter, output: OutputStream) {
        val html = "<html><head><title>403 Forbidden</title></head><body style=\"background-color:#1A1D26;color:#E3E7ED;font-family:sans-serif;text-align:center;padding-top:100px;\"><h2>403 Forbidden</h2><p>Access Denied: Invalid Security Token.</p></body></html>"
        val bytes = html.toByteArray(Charsets.UTF_8)
        writer.print("HTTP/1.1 403 Forbidden\r\n")
        writer.print("Content-Type: text/html; charset=utf-8\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Connection: close\r\n")
        writer.print("\r\n")
        writer.flush()
        output.write(bytes)
        output.flush()
    }

    private fun send404Response(writer: PrintWriter, output: OutputStream) {
        val html = "<html><head><title>404 Not Found</title></head><body style=\"background-color:#1A1D26;color:#E3E7ED;font-family:sans-serif;text-align:center;padding-top:100px;\"><h2>404 Not Found</h2><p>Requested URL was not found on this server.</p></body></html>"
        val bytes = html.toByteArray(Charsets.UTF_8)
        writer.print("HTTP/1.1 404 Not Found\r\n")
        writer.print("Content-Type: text/html; charset=utf-8\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        writer.print("Connection: close\r\n")
        writer.print("\r\n")
        writer.flush()
        output.write(bytes)
        output.flush()
    }

    // Escape characters to build safe JSON string builders manually
    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
    }

    private fun getRepositoryStateJson(): String {
        val childDevicesJson = XshieldRepository.childDevices.joinToString(",") { "\"${escapeJson(it)}\"" }
        
        val blockedJson = XshieldRepository.blockedList.joinToString(",") { b ->
            "{\"id\":\"${b.id}\",\"number\":\"${escapeJson(b.number)}\",\"type\":\"${b.type}\",\"date\":\"${b.date}\",\"blocked\":${b.blocked}}"
        }

        val callsJson = XshieldRepository.callsList.joinToString(",") { c ->
            "{\"id\":\"${c.id}\",\"type\":\"${c.type}\",\"name\":\"${escapeJson(c.name)}\",\"number\":\"${escapeJson(c.number)}\",\"duration\":\"${c.duration}\",\"date\":\"${c.date}\",\"address\":\"${escapeJson(c.address)}\"}"
        }

        val smsJson = XshieldRepository.smsList.joinToString(",") { s ->
            "{\"id\":\"${s.id}\",\"type\":\"${s.type}\",\"name\":\"${escapeJson(s.name)}\",\"message\":\"${escapeJson(s.message)}\",\"number\":\"${escapeJson(s.number)}\",\"date\":\"${s.date}\",\"address\":\"${escapeJson(s.address)}\"}"
        }

        return "{" +
                "\"selectedDevice\":\"${escapeJson(XshieldRepository.selectedDevice.value)}\"," +
                "\"childDevices\":[$childDevicesJson]," +
                "\"blockedList\":[$blockedJson]," +
                "\"callsList\":[$callsJson]," +
                "\"smsList\":[$smsJson]" +
                "}"
    }

    private fun getJsonValue(json: String, key: String): String {
        val pattern = "\"$key\"\\s*:\\s*\"?([^\",}]+)\"?".toRegex()
        val match = pattern.find(json)
        return match?.groupValues?.get(1)?.trim()?.replace("\"", "") ?: ""
    }

    companion object {
        fun getLocalIpAddress(): String {
            try {
                val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
                for (networkInterface in interfaces) {
                    val addresses = Collections.list(networkInterface.inetAddresses)
                    for (address in addresses) {
                        if (!address.isLoopbackAddress) {
                            val ip = address.hostAddress
                            val isIPv4 = ip.indexOf(':') < 0
                            if (isIPv4) return ip
                        }
                    }
                }
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
            return "127.0.0.1"
        }
    }

    private fun getDashboardHtml(): String {
        return """
<!DOCTYPE html>
<html>
<head>
    <title>Xshield - Remote Parent Portal</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <style>
        body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #1A1D26; color: #E3E7ED; margin: 0; padding: 20px; }
        .container { max-width: 900px; margin: 0 auto; }
        header { display: flex; justify-content: space-between; align-items: center; border-bottom: 2px solid #2C3240; padding-bottom: 15px; margin-bottom: 20px; }
        h1 { margin: 0; color: #00A8B5; font-size: 24px; }
        .status { background-color: rgba(92, 184, 92, 0.15); border: 1px solid #5CB85C; color: #5CB85C; padding: 4px 8px; border-radius: 4px; font-size: 11px; font-weight: bold; }
        .device-selector { background-color: #262D38; border: 1px solid #3A4250; color: white; padding: 8px 12px; border-radius: 6px; font-size: 13px; font-weight: bold; cursor: pointer; }
        .card { background-color: #212833; border: 1px solid #2C3240; border-radius: 10px; padding: 20px; margin-bottom: 20px; box-shadow: 0 4px 6px rgba(0,0,0,0.15); }
        .tabs { display: flex; gap: 10px; margin-bottom: 20px; }
        .tab-btn { background-color: #262D38; border: 1px solid #2C3240; color: #E3E7ED; padding: 10px 20px; border-radius: 6px; cursor: pointer; font-weight: bold; font-size: 13px; transition: 0.2s; }
        .tab-btn.active { background-color: #00A8B5; border-color: #00A8B5; color: #1E252D; }
        table { width: 100%; border-collapse: collapse; margin-top: 10px; }
        th, td { text-align: left; padding: 12px 10px; border-bottom: 1px solid #2C3240; font-size: 13px; }
        th { color: #A0A5B0; font-weight: 600; text-transform: uppercase; font-size: 11px; }
        .badge { display: inline-block; padding: 2px 6px; border-radius: 4px; font-size: 10px; font-weight: bold; }
        .badge.incoming { background-color: rgba(46, 125, 50, 0.15); color: #81C784; }
        .badge.outgoing { background-color: rgba(21, 101, 192, 0.15); color: #64B5F6; }
        .badge.missed { background-color: rgba(198, 40, 40, 0.15); color: #E57373; }
        .btn-delete { background-color: rgba(255, 23, 68, 0.15); border: 1px solid #FF1744; color: #FF1744; padding: 4px 8px; border-radius: 4px; cursor: pointer; font-size: 11px; font-weight: bold; }
        .form-group { display: flex; gap: 10px; align-items: center; margin-top: 15px; }
        .form-control { background-color: #262D38; border: 1px solid #3A4250; color: white; padding: 8px 12px; border-radius: 6px; font-size: 13px; }
        .btn-submit { background-color: #00A8B5; border: none; color: #1E252D; padding: 8px 16px; border-radius: 6px; cursor: pointer; font-weight: bold; font-size: 13px; }
        .empty-msg { text-align: center; color: #A0A5B0; padding: 20px 0; font-style: italic; font-size: 13px; }
    </style>
</head>
<body>
    <div class="container">
        <header>
            <div>
                <h1>Xshield Parent Dashboard</h1>
                <div style="font-size: 11px; color: #A0A5B0; margin-top: 4px;">Remote Cloud Sync Active</div>
            </div>
            <div style="display: flex; align-items: center; gap: 12px;">
                <span class="status">LIVE SYSTEM</span>
                <select id="deviceSelect" class="device-selector" onchange="switchDevice(this.value)">
                </select>
            </div>
        </header>

        <div class="tabs">
            <button class="tab-btn active" onclick="showTab('overview')">Overview</button>
            <button class="tab-btn" onclick="showTab('calls')">Calls Log</button>
            <button class="tab-btn" onclick="showTab('sms')">SMS Log</button>
            <button class="tab-btn" onclick="showTab('block')">Block Numbers</button>
            <a id="downloadApkLink" href="#" class="tab-btn" style="text-decoration: none; background-color: #2E7D32; border-color: #2E7D32; color: white;">Download Agent APK</a>
        </div>

        <!-- Overview Tab -->
        <div id="overview-tab" class="tab-content">
            <div class="card" style="display: flex; gap: 40px; justify-content: space-around;">
                <div style="text-align: center;">
                    <div style="font-size: 11px; color: #A0A5B0;">Calls Tracked</div>
                    <div id="overview-calls" style="font-size: 32px; font-weight: bold; color: #00A8B5; margin-top: 5px;">0</div>
                </div>
                <div style="text-align: center;">
                    <div style="font-size: 11px; color: #A0A5B0;">SMS Tracked</div>
                    <div id="overview-sms" style="font-size: 32px; font-weight: bold; color: #64B5F6; margin-top: 5px;">0</div>
                </div>
                <div style="text-align: center;">
                    <div style="font-size: 11px; color: #A0A5B0;">Blocked Numbers</div>
                    <div id="overview-blocked" style="font-size: 32px; font-weight: bold; color: #E57373; margin-top: 5px;">0</div>
                </div>
            </div>
            
            <div class="card">
                <h3 style="margin-top: 0; color: #00A8B5;">Recent Tracking Logs</h3>
                <div id="recent-logs-list"></div>
            </div>
        </div>

        <!-- Calls Tab -->
        <div id="calls-tab" class="tab-content" style="display: none;">
            <div class="card">
                <h3 style="margin-top: 0;">Call Logs Telemetry</h3>
                <table>
                    <thead>
                        <tr>
                            <th>Action</th>
                            <th>Type</th>
                            <th>Name</th>
                            <th>Number</th>
                            <th>Duration</th>
                            <th>Date</th>
                            <th>Address</th>
                        </tr>
                    </thead>
                    <tbody id="calls-table-body">
                    </tbody>
                </table>
            </div>
        </div>

        <!-- SMS Tab -->
        <div id="sms-tab" class="tab-content" style="display: none;">
            <div class="card">
                <h3 style="margin-top: 0;">SMS Logs Telemetry</h3>
                <table>
                    <thead>
                        <tr>
                            <th>Type</th>
                            <th>Name</th>
                            <th>Message</th>
                            <th>Number</th>
                            <th>Date</th>
                        </tr>
                    </thead>
                    <tbody id="sms-table-body">
                    </tbody>
                </table>
            </div>
        </div>

        <!-- Block Tab -->
        <div id="block-tab" class="tab-content" style="display: none;">
            <div class="card">
                <h3 style="margin-top: 0;">Block New Number</h3>
                <div class="form-group">
                    <input type="text" id="blockNumberInput" class="form-control" placeholder="Phone Number (e.g. +1 555-0199)">
                    <select id="blockTypeSelect" class="form-control">
                        <option value="Incoming">Incoming</option>
                        <option value="Outgoing">Outgoing</option>
                        <option value="Both">Both</option>
                    </select>
                    <button class="btn-submit" onclick="submitBlockNumber()">Block Number</button>
                </div>
            </div>

            <div class="card">
                <h3 style="margin-top: 0;">Active Block List</h3>
                <table>
                    <thead>
                        <tr>
                            <th>Number</th>
                            <th>Block Scope</th>
                            <th>Blocked Date</th>
                            <th>Action</th>
                        </tr>
                    </thead>
                    <tbody id="blocked-table-body">
                    </tbody>
                </table>
            </div>
        </div>
    </div>

    <script>
        const urlParams = new URLSearchParams(window.location.search);
        const auth = urlParams.get('auth') || '';

        function getAuthQuery() {
            return '?auth=' + encodeURIComponent(auth);
        }

        async function fetchState() {
            try {
                const res = await fetch('/api/state' + getAuthQuery());
                if (res.status === 403) {
                    document.body.innerHTML = '<div style="max-width: 500px; margin: 100px auto; text-align: center;" class="card"><h2>403 Forbidden</h2><p>Access Denied: Invalid Security Token</p></div>';
                    return;
                }
                const data = await res.json();
                renderState(data);
            } catch (err) {
                console.error("Error fetching state: ", err);
            }
        }

        function renderState(state) {
            // Update device selector
            const selector = document.getElementById('deviceSelect');
            const currentDevicesHtml = state.childDevices.map(d => '<option value="' + d + '" ' + (d === state.selectedDevice ? 'selected' : '') + '>' + d + '</option>').join('');
            if (selector.innerHTML !== currentDevicesHtml) {
                selector.innerHTML = currentDevicesHtml;
            }

            // Overview stats
            document.getElementById('overview-calls').innerText = state.callsList.length;
            document.getElementById('overview-sms').innerText = state.smsList.length;
            document.getElementById('overview-blocked').innerText = state.blockedList.length;

            // Overview logs
            const mixedLogs = [
                ...state.callsList.map(c => ({ type: 'call', date: c.date, text: c.type + ' Call - ' + c.name + ' (' + c.number + ')' })),
                ...state.smsList.map(s => ({ type: 'sms', date: s.date, text: 'SMS ' + s.type + ' (' + s.name + '): "' + s.message + '"' }))
            ].sort((a,b) => b.date.localeCompare(a.date)).slice(0, 5);

            const logsList = document.getElementById('recent-logs-list');
            if (mixedLogs.length === 0) {
                logsList.innerHTML = '<div class="empty-msg">No logs logged yet.</div>';
            } else {
                logsList.innerHTML = mixedLogs.map(l => 
                    '<div style="padding: 10px 0; border-bottom: 1px solid #2C3240; display: flex; justify-content: space-between; font-size: 13px;">' +
                        '<span><b style="color: ' + (l.type==='call'?'#00A8B5':'#64B5F6') + ';">[' + l.type.toUpperCase() + ']</b> ' + l.text + '</span>' +
                        '<span style="color: #A0A5B0; font-size: 11px;">' + l.date + '</span>' +
                    '</div>'
                ).join('');
            }

            // Render Calls Table
            const callsBody = document.getElementById('calls-table-body');
            if (state.callsList.length === 0) {
                callsBody.innerHTML = '<tr><td colspan="7" class="empty-msg">No call logs recorded.</td></tr>';
            } else {
                callsBody.innerHTML = state.callsList.map(c => 
                    '<tr>' +
                        '<td><button class="btn-delete" onclick="deleteCall(\'' + c.id + '\')">Delete</button></td>' +
                        '<td><span class="badge ' + c.type.toLowerCase() + '">' + c.type + '</span></td>' +
                        '<td><b>' + c.name + '</b></td>' +
                        '<td>' + c.number + '</td>' +
                        '<td>' + formatDuration(c.duration) + '</td>' +
                        '<td>' + c.date + '</td>' +
                        '<td style="color:#64B5F6">' + c.address + '</td>' +
                    '</tr>'
                ).join('');
            }

            // Render SMS Table
            const smsBody = document.getElementById('sms-table-body');
            if (state.smsList.length === 0) {
                smsBody.innerHTML = '<tr><td colspan="5" class="empty-msg">No SMS logs recorded.</td></tr>';
            } else {
                smsBody.innerHTML = state.smsList.map(s => 
                    '<tr>' +
                        '<td><span class="badge ' + s.type.toLowerCase() + '">' + s.type + '</span></td>' +
                        '<td><b>' + s.name + '</b></td>' +
                        '<td style="color:#E3E7ED; max-width: 300px;">"' + s.message + '"</td>' +
                        '<td>' + s.number + '</td>' +
                        '<td>' + s.date + '</td>' +
                    '</tr>'
                ).join('');
            }

            // Render Blocked Table
            const blockedBody = document.getElementById('blocked-table-body');
            if (state.blockedList.length === 0) {
                blockedBody.innerHTML = '<tr><td colspan="4" class="empty-msg">No numbers currently blocked.</td></tr>';
            } else {
                blockedBody.innerHTML = state.blockedList.map(b => 
                    '<tr>' +
                        '<td><b>' + b.number + '</b></td>' +
                        '<td><span class="badge outgoing">' + b.type + '</span></td>' +
                        '<td>' + b.date + '</td>' +
                        '<td><button class="btn-delete" onclick="unblockNumber(\'' + b.id + '\')">Remove</button></td>' +
                    '</tr>'
                ).join('');
            }
        }

        function formatDuration(secStr) {
            const sec = parseInt(secStr);
            if (isNaN(sec) || sec === 0) return '-';
            const m = Math.floor(sec / 60);
            const s = sec % 60;
            return (m < 10 ? '0' : '') + m + ':' + (s < 10 ? '0' : '') + s;
        }

        function showTab(tabId) {
            document.querySelectorAll('.tab-content').forEach(c => c.style.display = 'none');
            document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
            document.getElementById(tabId + '-tab').style.display = 'block';
            event.currentTarget.classList.add('active');
        }

        async function switchDevice(device) {
            await fetch('/api/select-device' + getAuthQuery(), {
                method: 'POST',
                body: JSON.stringify({ device })
            });
            fetchState();
        }

        async function submitBlockNumber() {
            const number = document.getElementById('blockNumberInput').value.trim();
            const type = document.getElementById('blockTypeSelect').value;
            if (!number) return;
            await fetch('/api/block' + getAuthQuery(), {
                method: 'POST',
                body: JSON.stringify({ number, type })
            });
            document.getElementById('blockNumberInput').value = '';
            fetchState();
        }

        async function unblockNumber(id) {
            await fetch('/api/unblock' + getAuthQuery(), {
                method: 'POST',
                body: JSON.stringify({ id })
            });
            fetchState();
        }

        async function deleteCall(id) {
            await fetch('/api/delete-call' + getAuthQuery(), {
                method: 'POST',
                body: JSON.stringify({ id })
            });
            fetchState();
        }

        document.getElementById('downloadApkLink').href = '/childagent.apk' + getAuthQuery();
        // Poll every 2.5 seconds
        fetchState();
        setInterval(fetchState, 2500);
    </script>
</body>
</html>
"""
    }
}
