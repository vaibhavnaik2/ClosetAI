package com.closetai.app

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

class ClosetViewModel(application: Application) : AndroidViewModel(application) {
    val api = ClosetApi()
    private val secureStore = SecureSessionStore(application)

    var session by mutableStateOf<Session?>(secureStore.load())
        private set
    var items by mutableStateOf<List<WardrobeItem>>(emptyList())
        private set
    var preferences by mutableStateOf(UserPreferencesAndroid())
        private set
    var driveStatus by mutableStateOf(DriveStatus(false, false, null))
        private set
    var driveFiles by mutableStateOf<List<DriveFile>>(emptyList())
        private set
    var stylistResponse by mutableStateOf<StylistResponse?>(null)
        private set
    var searchResults by mutableStateOf<List<WardrobeItem>?>(null)
        private set
    var searchInterpretation by mutableStateOf("")
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var importDone by mutableIntStateOf(0)
        private set
    var importTotal by mutableIntStateOf(0)
        private set
    var pendingExternalUrl by mutableStateOf<String?>(null)
        private set
    var pendingExport by mutableStateOf<ByteArray?>(null)
        private set

    private var realtimeSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var realtimeRefreshJob: Job? = null

    init {
        if (session?.isUsable == true) {
            viewModelScope.launch { bootstrapAndRefresh() }
        }
    }

    fun consumeMessage() { message = null }
    fun consumeExternalUrl() { pendingExternalUrl = null }
    fun consumeExport() { pendingExport = null }

    private suspend fun currentSession(): Session {
        var s = session ?: error("Please sign in")
        val now = System.currentTimeMillis() / 1000
        if (s.expiresAtEpochSeconds <= now + 120 && s.refreshToken.isNotBlank()) {
            s = api.refresh(s.refreshToken)
            secureStore.save(s)
            session = s
        }
        return s
    }

    fun signIn(email: String, password: String) {
        runBusy {
            val result = api.signIn(email, password)
            val s = result.session ?: error(result.message)
            acceptSession(s)
            message = result.message
            bootstrapAndRefresh()
        }
    }

    fun signUp(email: String, password: String) {
        runBusy {
            require(password.length >= 10) { "Use at least 10 characters for your password" }
            val result = api.signUp(email, password)
            if (result.session != null) {
                acceptSession(result.session)
                bootstrapAndRefresh()
            }
            message = result.message
        }
    }

    fun resetPassword(email: String) {
        runBusy {
            require(email.isNotBlank()) { "Enter your email first" }
            api.sendPasswordReset(email)
            message = "Password reset email requested."
        }
    }

    private fun acceptSession(s: Session) {
        session = s
        secureStore.save(s)
        startRealtime(s)
    }

    fun signOut() {
        realtimeSocket?.cancel()
        heartbeatJob?.cancel()
        realtimeSocket = null
        session = null
        items = emptyList()
        searchResults = null
        stylistResponse = null
        secureStore.clear()
    }

    private suspend fun bootstrapAndRefresh() {
        val s = currentSession()
        runCatching { api.bootstrap(s.accessToken) }
        supervisorScope {
            val wardrobe = async { api.items(s.accessToken) }
            val prefs = async { api.preferences(s.accessToken, s.userId) }
            val drive = async { api.driveStatus(s.accessToken) }
            items = wardrobe.await()
            preferences = prefs.await()
            driveStatus = drive.await()
        }
        if (driveStatus.connected) {
            runCatching { driveFiles = api.driveFiles(s.accessToken) }
        }
        startRealtime(s)
    }

    fun refresh() {
        runBusy { bootstrapAndRefresh() }
    }

    fun naturalSearch(query: String) {
        runBusy {
            val s = currentSession()
            val result = api.naturalSearch(s.accessToken, query)
            searchResults = result.items
            searchInterpretation = result.interpretation
            message = if (result.items.isEmpty()) "No wardrobe items matched that request." else null
        }
    }

    fun clearSearch() {
        searchResults = null
        searchInterpretation = ""
    }

    fun toggleFavorite(item: WardrobeItem) {
        viewModelScope.launch {
            runCatching {
                val s = currentSession()
                api.setFavorite(s.accessToken, item.id, !item.favorite)
                items = items.map { if (it.id == item.id) it.copy(favorite = !item.favorite) else it }
            }.onFailure { message = it.message }
        }
    }

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            busy = true
            importDone = 0
            importTotal = uris.size
            try {
                val s = currentSession()
                val context = getApplication<Application>()
                for (chunk in uris.chunked(4)) {
                    supervisorScope {
                        chunk.map { uri ->
                            async(Dispatchers.IO) {
                                val sanitized = ImageSanitizer.sanitize(context, uri)
                                api.uploadSanitized(s, sanitized)
                            }
                        }.forEach { deferred ->
                            runCatching { deferred.await() }
                                .onFailure { message = it.message }
                            importDone += 1
                        }
                    }
                }
                items = api.items(s.accessToken)
                message = "Import finished: $importDone of $importTotal processed."
            } catch (t: Throwable) {
                message = t.message ?: "Import failed"
            } finally {
                busy = false
            }
        }
    }

    fun importFolder(tree: Uri) {
        val context = getApplication<Application>()
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                tree,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val uris = ImageSanitizer.imageUrisInTree(context, tree, 1000)
        if (uris.isEmpty()) message = "No supported images were found in that folder."
        else importUris(uris)
    }

    fun importUrl(url: String) {
        runBusy {
            require(url.startsWith("https://")) { "Only HTTPS image links are accepted" }
            val s = currentSession()
            api.importUrl(s.accessToken, url)
            items = api.items(s.accessToken)
            message = "Secure link import completed."
        }
    }

    fun connectDrive() {
        runBusy {
            val s = currentSession()
            val status = api.driveStatus(s.accessToken)
            driveStatus = status
            if (!status.configured) {
                message = "Google Drive OAuth credentials still need to be configured on the ClosetAI server."
                return@runBusy
            }
            pendingExternalUrl = api.driveAuthUrl(s.accessToken)
        }
    }

    fun refreshDrive() {
        runBusy {
            val s = currentSession()
            driveStatus = api.driveStatus(s.accessToken)
            driveFiles = if (driveStatus.connected) api.driveFiles(s.accessToken) else emptyList()
            if (driveStatus.connected) message = "Google Drive connected securely."
        }
    }

    fun importDrive(file: DriveFile) {
        runBusy {
            val s = currentSession()
            api.importDrive(s.accessToken, file.id)
            items = api.items(s.accessToken)
            message = "Imported ${file.name}"
        }
    }

    fun askStylist(mode: String, prompt: String) {
        runBusy {
            require(prompt.isNotBlank()) { "Tell the stylist what you need" }
            val s = currentSession()
            stylistResponse = api.stylist(s.accessToken, mode, prompt)
        }
    }

    fun saveLook(look: StylistLook) {
        runBusy {
            val s = currentSession()
            api.saveLook(s.accessToken, s.userId, look, preferences.defaultOccasion, preferences.stylistPersona)
            message = "Saved ${look.title}"
        }
    }

    fun savePreferences(next: UserPreferencesAndroid) {
        runBusy {
            val s = currentSession()
            preferences = api.savePreferences(s.accessToken, s.userId, next)
            message = "Customization synced to your account."
        }
    }

    fun exportAccount() {
        runBusy {
            val s = currentSession()
            pendingExport = api.accountExport(s.accessToken)
        }
    }

    fun deleteAccount() {
        runBusy {
            val s = currentSession()
            api.deleteAccount(s.accessToken)
            signOut()
            message = "ClosetAI account deleted."
        }
    }

    fun onDeepLink(uri: Uri?) {
        if (uri?.scheme == "closetai" && uri.host == "drive-connected") {
            refreshDrive()
        }
    }

    suspend fun imageBytes(path: String): ByteArray {
        val s = currentSession()
        return api.imageBytes(s.accessToken, path)
    }

    private fun runBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy = true
            try {
                block()
            } catch (t: Throwable) {
                message = t.message ?: "Something went wrong"
            } finally {
                busy = false
            }
        }
    }

    private fun startRealtime(s: Session) {
        realtimeSocket?.cancel()
        heartbeatJob?.cancel()

        val wsUrl = ClosetConfig.baseUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://") +
            "/realtime/v1/websocket?apikey=${ClosetConfig.publishableKey}&vsn=1.0.0"

        val request = Request.Builder()
            .url(wsUrl)
            .header("Authorization", "Bearer ${s.accessToken}")
            .build()

        realtimeSocket = api.http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val changes = JSONArray().put(
                    JSONObject()
                        .put("event", "*")
                        .put("schema", "public")
                        .put("table", "wardrobe_items")
                        .put("filter", "user_id=eq.${s.userId}")
                )
                val config = JSONObject()
                    .put("broadcast", JSONObject().put("self", false))
                    .put("presence", JSONObject().put("key", ""))
                    .put("postgres_changes", changes)

                val join = JSONObject()
                    .put("topic", "realtime:public:wardrobe_items")
                    .put("event", "phx_join")
                    .put("payload", JSONObject().put("config", config))
                    .put("ref", "1")
                webSocket.send(join.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (text.contains("postgres_changes")) {
                    realtimeRefreshJob?.cancel()
                    realtimeRefreshJob = viewModelScope.launch {
                        delay(350)
                        runCatching {
                            val current = currentSession()
                            items = api.items(current.accessToken)
                        }
                    }
                }
            }
        })

        heartbeatJob = viewModelScope.launch {
            var ref = 2
            while (session?.userId == s.userId) {
                delay(20_000)
                val heartbeat = JSONObject()
                    .put("topic", "phoenix")
                    .put("event", "heartbeat")
                    .put("payload", JSONObject())
                    .put("ref", (ref++).toString())
                realtimeSocket?.send(heartbeat.toString())
            }
        }
    }

    fun deviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
}
