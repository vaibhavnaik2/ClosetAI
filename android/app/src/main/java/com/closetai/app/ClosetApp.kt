package com.closetai.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class AppTab(val title: String) {
    Today("Today"), Closet("Closet"), Import("Import"), Stylist("Stylist"), Account("Account"), Utilities("Utilities")
}

@Composable
fun ClosetAndroidApp(vm: ClosetViewModel) {
    val scheme = if (vm.preferences.theme == "dark" || (vm.preferences.theme == "system" && isSystemInDarkTheme())) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = scheme) {
        Surface(Modifier.fillMaxSize()) {
            if (vm.session?.isUsable == true) MainShell(vm) else AuthScreen(vm)
        }
    }
}

@Composable
private fun AuthScreen(vm: ClosetViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var create by remember { mutableStateOf(false) }
    var accepted by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Checkroom, null, Modifier.size(54.dp))
        Spacer(Modifier.height(18.dp))
        Text("ClosetAI", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text("Your entire wardrobe, understood.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(30.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())

        if (create) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(accepted, { accepted = it })
                Text("I accept the Terms and Privacy Policy (2026-09).")
            }
            Text("Passwords require at least 10 characters. Wardrobe images are stored privately per account.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        vm.message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = { if (create) vm.signUp(email, password) else vm.signIn(email, password) },
            enabled = !vm.busy && email.isNotBlank() && password.isNotBlank() && (!create || accepted),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (vm.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(if (create) "Create secure account" else "Sign in")
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton({ create = !create }) { Text(if (create) "Already have an account?" else "Create account") }
            TextButton({ vm.resetPassword(email) }, enabled = email.isNotBlank()) { Text("Forgot password") }
        }
        Spacer(Modifier.height(24.dp))
        SecurityLine("Private cloud wardrobe", "Row-level security + private image storage")
        SecurityLine("AI with evidence", "Brand confidence, taxonomy and semantic wardrobe search")
        SecurityLine("Safe imports", "Sanitized files, protected HTTPS links and encrypted Drive OAuth")
    }
}

@Composable
private fun SecurityLine(title: String, detail: String) {
    Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
        Icon(Icons.Default.Security, null, Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MainShell(vm: ClosetViewModel) {
    var tab by remember { mutableStateOf(AppTab.Today) }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var exportBytes by remember { mutableStateOf<ByteArray?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) exportBytes?.let { bytes -> context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } }
        exportBytes = null
        vm.consumeExport()
    }

    LaunchedEffect(vm.message) {
        vm.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }
    LaunchedEffect(vm.pendingExternalUrl) {
        vm.pendingExternalUrl?.let { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it))); vm.consumeExternalUrl() }
    }
    LaunchedEffect(vm.pendingExport) {
        vm.pendingExport?.let { exportBytes = it; exportLauncher.launch("ClosetAI-Export.json") }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { item ->
                    val icon = when (item) {
                        AppTab.Today -> Icons.Default.Home
                        AppTab.Closet -> Icons.Default.Checkroom
                        AppTab.Import -> Icons.Default.AddPhotoAlternate
                        AppTab.Stylist -> Icons.Default.AutoAwesome
                        AppTab.Account -> Icons.Default.AccountCircle
                        AppTab.Utilities -> Icons.Default.Checkroom
                    }
                    NavigationBarItem(tab == item, { tab = item }, { Icon(icon, item.title) }, label = { Text(item.title) })
                }
            }
        }
    ) { inset ->
        Box(Modifier.padding(inset).fillMaxSize()) {
            when (tab) {
                AppTab.Today -> TodayScreen(vm) { tab = it }
                AppTab.Closet -> ClosetScreen(vm)
                AppTab.Import -> ImportScreen(vm)
                AppTab.Stylist -> StylistScreen(vm)
                AppTab.Account -> AccountScreen(vm)
                AppTab.Utilities -> UtilitiesScreen(vm)
            }
            if (vm.busy) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun TodayScreen(vm: ClosetViewModel, navigate: (AppTab) -> Unit) {
    val available = vm.items.count { it.status == "available" }
    val favorites = vm.items.count { it.favorite }
    val underused = vm.items.count { it.wearCount <= 1 && it.status == "available" }
    val unavailable = vm.items.count { it.status in listOf("laundry", "repair", "storage") }

    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Today", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("A live view of your wardrobe", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(vm::refresh) { Icon(Icons.Default.Refresh, "Refresh") }
            }
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("$available", "Available", Modifier.weight(1f)); MetricCard("$favorites", "Favorites", Modifier.weight(1f)) } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { MetricCard("$underused", "Underused", Modifier.weight(1f)); MetricCard("$unavailable", "Care / storage", Modifier.weight(1f)) } }
        item {
            Card(onClick = { navigate(AppTab.Stylist) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Icon(Icons.Default.AutoAwesome, null)
                    Spacer(Modifier.height(8.dp))
                    Text("Ask your wardrobe", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Outfits, packing, closet audit, purchase compatibility and care advice from pieces you actually own.")
                }
            }
        }
        item {
            Card(onClick = { navigate(AppTab.Import) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Icon(Icons.Default.Cloud, null)
                    Spacer(Modifier.height(8.dp))
                    Text("Grow your digital closet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("Add sanitized photos, folders, secure HTTPS image links or your connected Google Drive.")
                }
            }
        }
    }
}

@Composable
private fun MetricCard(value: String, title: String, modifier: Modifier = Modifier) {
    Card(modifier) { Column(Modifier.padding(16.dp)) { Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClosetScreen(vm: ClosetViewModel) {
    var text by remember { mutableStateOf("") }
    var aiQuery by remember { mutableStateOf("") }
    var selection by remember { mutableStateOf(FilterSelection()) }
    var showFilters by remember { mutableStateOf(false) }
    var presetName by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.refreshUtilities() }
    val source = vm.searchResults ?: vm.items
    val filtered = source.filter { item ->
        val local = text.isBlank() || listOfNotNull(item.name, item.brand, item.category, item.productType, item.color, item.material, item.mood).any { it.contains(text, true) }
        local &&
            (selection.brands.isEmpty() || item.brand in selection.brands) &&
            (selection.colors.isEmpty() || item.color in selection.colors) &&
            (selection.categories.isEmpty() || item.category in selection.categories) &&
            (selection.productTypes.isEmpty() || item.productType in selection.productTypes)
    }

    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Closet", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            OutlinedButton({ showFilters = true }) {
                Icon(Icons.Default.FilterList, null); Spacer(Modifier.width(6.dp))
                Text(if (selection.count == 0) "Filters" else "Filters ${selection.count}")
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(text, { text = it }, label = { Text("Search wardrobe") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(aiQuery, { aiQuery = it }, label = { Text("AI search: navy quiet-luxury clothes for a client dinner") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ vm.naturalSearch(aiQuery) }, enabled = aiQuery.isNotBlank()) { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(5.dp)); Text("Search") }
            if (vm.searchResults != null) TextButton(vm::clearSearch) { Text("Clear AI results") }
        }
        if (vm.searchInterpretation.isNotBlank()) Text(vm.searchInterpretation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(vm.utilityData["wardrobe_filter_presets"].orEmpty()) { preset ->
                AssistChip({
                    val f = preset.optJSONObject("filters") ?: org.json.JSONObject()
                    selection = FilterSelection(f.optJSONArray("brands").toStringList().toSet(), f.optJSONArray("colors").toStringList().toSet(), f.optJSONArray("categories").toStringList().toSet(), f.optJSONArray("product_types").toStringList().toSet())
                }, { Text(preset.optString("name")) })
            }
        }
        if (selection.count > 0) Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(presetName, { presetName = it }, label = { Text("Preset name") }, modifier = Modifier.weight(1f))
            TextButton({ vm.savePreset(presetName, selection) }, enabled = presetName.isNotBlank()) { Text("Save") }
        }
        Text("${filtered.size} items", Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyVerticalGrid(GridCells.Adaptive(when (vm.preferences.gridDensity) { "compact" -> 130.dp; "gallery" -> 240.dp; else -> 165.dp }), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
            gridItems(filtered, key = { it.id }) { WardrobeCard(vm, it) }
        }
    }
    if (showFilters) FilterSheet(vm.items, selection, { selection = it }, { showFilters = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(items: List<WardrobeItem>, selected: FilterSelection, onChange: (FilterSelection) -> Unit, onDismiss: () -> Unit) {
    val brands = items.mapNotNull { it.brand }.distinct().sorted()
    val colors = items.mapNotNull { it.color }.distinct().sorted()
    val categories = items.mapNotNull { it.category }.distinct().sorted()
    val products = items.mapNotNull { it.productType }.distinct().sorted()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxHeight(0.82f).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Marketplace filters", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); TextButton({ onChange(FilterSelection()) }) { Text("Clear") } } }
            item { FacetRow("Colours", colors, selected.colors) { onChange(selected.copy(colors = toggle(selected.colors, it))) } }
            item { FacetRow("Brands", brands, selected.brands) { onChange(selected.copy(brands = toggle(selected.brands, it))) } }
            item { FacetRow("Categories", categories, selected.categories) { onChange(selected.copy(categories = toggle(selected.categories, it))) } }
            item { FacetRow("Product type", products, selected.productTypes) { onChange(selected.copy(productTypes = toggle(selected.productTypes, it))) } }
            item { Button(onDismiss, Modifier.fillMaxWidth()) { Text("Show wardrobe") }; Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
private fun FacetRow(title: String, values: List<String>, selected: Set<String>, onToggle: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            items(values) { value -> FilterChip(value in selected, { onToggle(value) }, { Text(value, maxLines = 1) }) }
        }
    }
}

private fun toggle(set: Set<String>, value: String): Set<String> = if (value in set) set - value else set + value

@Composable
private fun WardrobeCard(vm: ClosetViewModel, item: WardrobeItem) {
    var editing by remember { mutableStateOf(false) }
    var itemName by remember(item.name) { mutableStateOf(item.name) }
    var status by remember(item.status) { mutableStateOf(item.status) }
    if (editing) AlertDialog(onDismissRequest = { editing = false }, title = { Text("Edit item") },
        text = { Column {
            OutlinedTextField(itemName, { itemName = it }, label = { Text("Name") })
            Text("Status")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(listOf("available", "laundry", "repair", "storage", "sold")) { value -> FilterChip(status == value, { status = value }, { Text(value.replaceFirstChar { it.uppercase() }) }) }
            }
        } },
        confirmButton = { TextButton({ vm.updateItem(item.id, itemName, status); editing = false }, enabled = itemName.trim().length in 1..180 && !vm.busy) { Text("Save") } },
        dismissButton = { TextButton({ editing = false }) { Text("Cancel") } })
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth()) {
        Box {
            if (item.storagePath != null) AuthenticatedImage(vm, item.storagePath, Modifier.fillMaxWidth().height(170.dp))
            else Box(Modifier.fillMaxWidth().height(170.dp).background(MaterialTheme.colorScheme.surfaceVariant))
            IconButton({ vm.toggleFavorite(item) }, Modifier.align(Alignment.TopEnd).background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f), CircleShape)) {
                Icon(if (item.favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite")
            }
        }
        Column(Modifier.padding(11.dp)) {
            TextButton({ vm.logWear(item.id) }, enabled = !vm.busy) { Text("Wore today") }
            TextButton({ editing = true }, enabled = !vm.busy) { Text("Edit item") }
            Text(item.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(item.brand, item.color).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(listOfNotNull(item.productType ?: item.category, item.fit).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun AuthenticatedImage(vm: ClosetViewModel, path: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(null, path, vm.session?.accessToken) {
        value = withContext(Dispatchers.IO) { runCatching { val bytes = vm.imageBytes(path); BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }.getOrNull() }
    }
    if (bitmap != null) Image(bitmap!!.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = modifier)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Default.Checkroom, null) }
}

@Composable
private fun ImportScreen(vm: ClosetViewModel) {
    var link by remember { mutableStateOf("") }
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.importUris(it) }
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(vm::importFolder) }

    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text("Import", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Text("Local images are decoded, resized and rewritten before upload, stripping embedded metadata.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Button({ photos.launch(arrayOf("image/*")) }, Modifier.weight(1f)) { Icon(Icons.Default.AddPhotoAlternate, null); Spacer(Modifier.width(5.dp)); Text("Photos") }
                OutlinedButton({ folder.launch(null) }, Modifier.weight(1f)) { Icon(Icons.Default.UploadFile, null); Spacer(Modifier.width(5.dp)); Text("Folder") }
            }
        }
        if (vm.importTotal > 0) item { Text("Import progress: ${vm.importDone} / ${vm.importTotal}") }
        item {
            HorizontalDivider()
            Text("Secure HTTPS link", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedTextField(link, { link = it }, label = { Text("https://…/clothing-image.jpg") }, modifier = Modifier.fillMaxWidth())
            Button({ vm.importUrl(link) }, enabled = link.startsWith("https://")) { Icon(Icons.Default.Link, null); Spacer(Modifier.width(5.dp)); Text("Import link through firewall") }
        }
        item {
            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Google Drive", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            !vm.driveStatus.configured -> "Server OAuth credentials are not configured yet."
                            vm.driveStatus.connected -> "Connected ${vm.driveStatus.email ?: ""}"
                            else -> "Not connected"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button({ if (vm.driveStatus.connected) vm.refreshDrive() else vm.connectDrive() }) { Text(if (vm.driveStatus.connected) "Refresh" else "Connect") }
            }
            Text("Drive tokens are encrypted server-side and never stored in this Android app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (vm.driveFiles.isNotEmpty()) {
            items(vm.driveFiles.take(100), key = { it.id }) { file ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Cloud, null); Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(file.mimeType, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        TextButton({ vm.importDrive(file) }) { Text("Import") }
                    }
                }
            }
        }
    }
}

@Composable
private fun StylistScreen(vm: ClosetViewModel) {
    val modes = listOf("outfit" to "Outfits", "packing" to "Packing", "wardrobe_gap" to "Gaps", "purchase_advice" to "Purchase", "closet_audit" to "Audit", "care" to "Care")
    var mode by remember { mutableStateOf("outfit") }
    var prompt by remember { mutableStateOf("Give me five polished quiet-luxury outfits for a client dinner.") }

    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("AI Stylist", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Text("Every recommended item ID is validated against your real wardrobe.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) { items(modes) { pair -> FilterChip(mode == pair.first, { mode = pair.first }, { Text(pair.second) }) } } }
        item {
            OutlinedTextField(prompt, { prompt = it }, label = { Text("What do you need?") }, minLines = 3, modifier = Modifier.fillMaxWidth())
            Button({ vm.askStylist(mode, prompt) }, enabled = prompt.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text("Ask ClosetAI") }
        }
        vm.stylistResponse?.let { response ->
            item { Text(response.summary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            items(response.looks, key = { it.id }) { look ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row { Text(look.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("${look.score}%") }
                        Text(look.rationale, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val names = look.itemIds.mapNotNull { id -> vm.items.firstOrNull { it.id == id }?.name }
                        if (names.isNotEmpty()) Text(names.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        Row {
                            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) { items(look.tags) { AssistChip({}, { Text(it) }) } }
                            TextButton({ vm.saveLook(look) }) { Text("Save") }
                        }
                    }
                }
            }
            if (response.advice.isNotEmpty()) item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(15.dp)) { Text("Stylist notes", fontWeight = FontWeight.Bold); response.advice.forEach { Text("• $it", Modifier.padding(top = 5.dp)) } } } }
            if (response.followUps.isNotEmpty()) item { Text("Try next", fontWeight = FontWeight.Bold); response.followUps.forEach { suggestion -> TextButton({ prompt = suggestion }) { Text(suggestion) } } }
        }
    }
}

@Composable
private fun AccountScreen(vm: ClosetViewModel) {
    var prefs by remember { mutableStateOf(vm.preferences) }
    var favoriteColors by remember { mutableStateOf(vm.preferences.preferredColors.joinToString(", ")) }
    var avoidColors by remember { mutableStateOf(vm.preferences.avoidColors.joinToString(", ")) }
    var styleGoals by remember { mutableStateOf(vm.preferences.styleGoals.joinToString(", ")) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(vm.preferences) {
        prefs = vm.preferences
        favoriteColors = prefs.preferredColors.joinToString(", ")
        avoidColors = prefs.avoidColors.joinToString(", ")
        styleGoals = prefs.styleGoals.joinToString(", ")
    }

    LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        item { Text("Account & Customize", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold); Text(vm.session?.email ?: "ClosetAI account", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Interface", fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(listOf("system", "light", "dark")) { v -> FilterChip(prefs.theme == v, { prefs = prefs.copy(theme = v) }, { Text(v.replaceFirstChar { it.uppercase() }) }) } }
                    Text("Stylist persona", fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(listOf("quiet_luxury", "classic", "minimal", "editorial", "practical")) { v -> FilterChip(prefs.stylistPersona == v, { prefs = prefs.copy(stylistPersona = v) }, { Text(v.replace("_", " ")) }) } }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Style DNA", fontWeight = FontWeight.Bold)
                    OutlinedTextField(favoriteColors, { favoriteColors = it }, label = { Text("Preferred colours") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(avoidColors, { avoidColors = it }, label = { Text("Avoid colours") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(styleGoals, { styleGoals = it }, label = { Text("Style goals") }, modifier = Modifier.fillMaxWidth())
                    Button({
                        val next = prefs.copy(preferredColors = csv(favoriteColors), avoidColors = csv(avoidColors), styleGoals = csv(styleGoals))
                        prefs = next
                        vm.savePreferences(next)
                    }, Modifier.fillMaxWidth()) { Icon(Icons.Default.Settings, null); Spacer(Modifier.width(5.dp)); Text("Save customization") }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Security & privacy", fontWeight = FontWeight.Bold)
                    SecurityLine("Android Keystore", "Access and refresh tokens are encrypted at rest on this device.")
                    SecurityLine("Private cloud", "RLS restricts wardrobe rows and storage paths to your user ID.")
                    SecurityLine("Data firewall", "Local metadata is stripped; remote links are SSRF-, MIME-, size- and signature-checked.")
                    SecurityLine("Connected Drive", "OAuth tokens are encrypted on the server and excluded from account exports.")
                }
            }
        }
        item {
            OutlinedButton(vm::exportAccount, Modifier.fillMaxWidth()) { Text("Export my ClosetAI data") }
            OutlinedButton(vm::signOut, Modifier.fillMaxWidth()) { Text("Sign out") }
            TextButton({ showDelete = true }, Modifier.fillMaxWidth()) { Text("Delete account and wardrobe", color = MaterialTheme.colorScheme.error) }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Delete your ClosetAI account?") },
            text = { Text("This permanently removes your private wardrobe files and cloud account. This cannot be undone.") },
            confirmButton = { Button({ showDelete = false; vm.deleteAccount() }) { Text("Delete permanently") } },
            dismissButton = { TextButton({ showDelete = false }) { Text("Cancel") } }
        )
    }
}

private fun csv(value: String): List<String> = value.split(",").map { it.trim() }.filter { it.isNotBlank() }.distinct()
