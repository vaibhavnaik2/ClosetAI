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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancelChildren
import kotlin.coroutines.coroutineContext
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

    private val sessionMutex = Mutex()
    var utilityData by mutableStateOf<Map<String, List<JSONObject>>>(emptyMap())
        private set

    private suspend fun loadUtilities(token: String) {
        val tables = listOf("outfits", "wardrobe_collections", "packing_lists", "wear_events", "wardrobe_filter_presets", "packing_list_items", "wardrobe_collection_items")
        utilityData = supervisorScope {
            tables.map { table -> async { table to api.utilityRows(token, table) } }.map { it.await() }.toMap()
        }
    }

    fun refreshUtilities() = runBusy { loadUtilities(currentSession().accessToken) }

    fun createUtility(table: String, title: String) = runBusy {
        require(table in setOf("wardrobe_collections", "packing_lists"))
        require(title.trim().length in 1..100) { "Use a name between 1 and 100 characters" }
        val s = currentSession()
        val body = JSONObject().put("user_id", s.userId).put(if (table == "packing_lists") "title" else "name", title.trim())
        api.addUtility(s.accessToken, table, body)
        loadUtilities(s.accessToken)
    }

    fun addToUtility(table: String, parentId: String, itemId: String) = runBusy {
        require(table in setOf("packing_list_items", "wardrobe_collection_items"))
        require(items.any { it.id == itemId })
        val s = currentSession()
        val key = if (table == "packing_list_items") "packing_list_id" else "collection_id"
        api.addUtility(s.accessToken, table, JSONObject().put(key, parentId).put("item_id", itemId))
        loadUtilities(s.accessToken)
    }

    fun setPacked(listId: String, itemId: String, packed: Boolean) = runBusy {
        val s = currentSession()
        api.setPacked(s.accessToken, listId, itemId, packed)
        loadUtilities(s.accessToken)
    }

    fun logWear(itemId: String) = runBusy {
        require(items.any { it.id == itemId })
        val s = currentSession()
        api.addUtility(s.accessToken, "wear_events", JSONObject().put("user_id", s.userId).put("item_id", itemId))
        items = api.items(s.accessToken)
        loadUtilities(s.accessToken)
        message = "Wear recorded"
    }

    fun savePreset(name: String, filters: FilterSelection) = runBusy {
        require(name.trim().length in 1..80) { "Use a preset name between 1 and 80 characters" }
        val s = currentSession()
        val body = JSONObject().put("user_id", s.userId).put("name", name.trim()).put("filters", JSONObject()
            .put("brands", JSONArray(filters.brands.toList())).put("colors", JSONArray(filters.colors.toList()))
            .put("categories", JSONArray(filters.categories.toList())).put("product_types", JSONArray(filters.productTypes.toList())))
        api.addUtility(s.accessToken, "wardrobe_filter_presets", body)
        loadUtilities(s.accessToken)
        message = "Filter preset saved"
    }

    private var realtimeSocket: WebSocket? = null
    private var heartbeatJob: Job? = null
    private var realtimeRefreshJob: Job? = null

    init {
        if (session?.isUsable == true) {
            runBusy { bootstrapAndRefresh() }
        }
    }

    fun consumeMessage() { message = null }
    fun consumeExternalUrl() { pendingExternalUrl = null }
    fun consumeExport() { pendingExport = null }

    private suspend fun currentSession(): Session = withContext(Dispatchers.Main.immediate) { sessionMutex.withLock {
        var s = session ?: error("Please sign in")
        val now = System.currentTimeMillis() / 1000
        if (s.expiresAtEpochSeconds <= now + 120 && s.refreshToken.isNotBlank()) {
            s = api.refresh(s.refreshToken)
            coroutineContext.ensureActive()
            secureStore.save(s)
            session = s
            startRealtime(s)
        }
        s
    } }

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
        val previousToken = session?.accessToken
        viewModelScope.coroutineContext.cancelChildren()
        realtimeSocket?.cancel()
        heartbeatJob?.cancel()
        realtimeRefreshJob?.cancel()
        realtimeSocket = null
        session = null
        items = emptyList()
        searchResults = null
        stylistResponse = null
        utilityData = emptyMap()
        preferences = UserPreferencesAndroid()
        driveStatus = DriveStatus(false, false, null)
        driveFiles = emptyList()
        pendingExport = null
        pendingExternalUrl = null
        searchInterpretation = ""
        message = null
        busy = false
        importDone = 0
        importTotal = 0
        secureStore.clear()
        if (previousToken != null) viewModelScope.launch { runCatching { api.signOut(previousToken) } }
    }

    private suspend fun bootstrapAndRefresh() {
        val s = currentSession()
        api.bootstrap(s.accessToken)
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
        if (uris.isEmpty() || busy) return
        viewModelScope.launch {
            busy = true
            importDone = 0
            importTotal = uris.size
            var failures = 0
            try {
                val context = getApplication<Application>()
                for (chunk in uris.chunked(2)) {
                    supervisorScope {
                        chunk.map { uri ->
                            async(Dispatchers.IO) {
                                val sanitized = ImageSanitizer.sanitize(context, uri)
                                api.uploadSanitized(currentSession(), sanitized)
                            }
                        }.forEach { deferred ->
                            runCatching { deferred.await() }
                                .onFailure { if (it is CancellationException) throw it; failures += 1 }
                            importDone += 1
                        }
                    }
                }
                items = api.items(currentSession().accessToken)
                message = "Import finished: ${importDone - failures} succeeded, $failures failed."
            } catch (t: CancellationException) {
                throw t
            } catch (t: Exception) {
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
        viewModelScope.launch {
            try {
                val uris = withContext(Dispatchers.IO) { ImageSanitizer.imageUrisInTree(context, tree, 1000) }
                if (uris.isEmpty()) message = "No supported images were found in that folder."
                else importUris(uris)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "Unable to read folder" }
        }
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
        if (busy) return
        viewModelScope.launch {
            busy = true
            try {
                block()
            } catch (t: CancellationException) {
                throw t
            } catch (t: Exception) {
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
                    .put("payload", JSONObject().put("config", config).put("access_token", s.accessToken))
                    .put("ref", "1")
                webSocket.send(join.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                if (runCatching { JSONObject(text).optString("event") }.getOrNull() == "postgres_changes") {
                    realtimeRefreshJob?.cancel()
                    realtimeRefreshJob = viewModelScope.launch {
                        delay(350)
                        runCatching {
                            if (session?.userId != s.userId) return@launch
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

    override fun onCleared() {
        realtimeSocket?.cancel()
        super.onCleared()
    }

    fun deviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
}
