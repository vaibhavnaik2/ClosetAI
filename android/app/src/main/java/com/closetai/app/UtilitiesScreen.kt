package com.closetai.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun UtilitiesScreen(vm: ClosetViewModel) {
    var table by remember { mutableStateOf("outfits") }
    var name by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Pair<String, String>?>(null) }
    var deleting by remember { mutableStateOf<Pair<String, String>?>(null) }
    var planning by remember { mutableStateOf<String?>(null) }
    var draft by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(java.time.LocalDate.now().toString()) }
    var occasion by remember { mutableStateOf("") }
    editing?.let { (kind, id) ->
        AlertDialog(onDismissRequest = { editing = null }, title = { Text("Rename") },
            text = { OutlinedTextField(draft, { draft = it }, label = { Text("Name") }) },
            confirmButton = { TextButton({ vm.renameUtility(kind, id, draft); editing = null }, enabled = draft.trim().length in 1..80 && !vm.busy) { Text("Save") } },
            dismissButton = { TextButton({ editing = null }) { Text("Cancel") } })
    }
    deleting?.let { (kind, id) ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete this entry?") },
            text = { Text("This removes the saved entry. Your wardrobe images remain in your closet.") },
            confirmButton = { TextButton({ vm.deleteUtility(kind, id); deleting = null; selected = null }, enabled = !vm.busy) { Text("Delete") } },
            dismissButton = { TextButton({ deleting = null }) { Text("Cancel") } })
    }
    planning?.let { id ->
        AlertDialog(onDismissRequest = { planning = null }, title = { Text("Plan this look") },
            text = { Column { OutlinedTextField(date, { date = it }, label = { Text("Date (YYYY-MM-DD)") }); OutlinedTextField(occasion, { occasion = it }, label = { Text("Occasion") }) } },
            confirmButton = { TextButton({ vm.planOutfit(id, date, occasion); planning = null }, enabled = runCatching { java.time.LocalDate.parse(date) }.isSuccess && !vm.busy) { Text("Plan") } },
            dismissButton = { TextButton({ planning = null }) { Text("Cancel") } })
    }
    LaunchedEffect(Unit) { vm.refreshUtilities() }
    val tabs = listOf("outfits" to "Looks", "wardrobe_collections" to "Collections", "packing_lists" to "Packing", "wear_events" to "Wear history", "outfit_plans" to "Calendar", "wardrobe_filter_presets" to "Presets")
    val childTable = if (table == "packing_lists") "packing_list_items" else "wardrobe_collection_items"
    val parentKey = if (table == "packing_lists") "packing_list_id" else "collection_id"
    val members = vm.utilityData[childTable].orEmpty().filter { it.optString(parentKey) == selected }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Your wardrobe tools", style = MaterialTheme.typography.headlineMedium) }
        item { LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(tabs) { (key, label) -> FilterChip(table == key, { table = key; selected = null }, { Text(label) }) }
        } }
        if (table in setOf("wardrobe_collections", "packing_lists")) item {
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            Button({ vm.createUtility(table, name); name = "" }, enabled = name.isNotBlank() && !vm.busy) { Text("Create") }
        }
        if (vm.utilityData[table].isNullOrEmpty()) item { Text("Nothing here yet. Create a collection or packing list, save a stylist look, or record a wear from your closet.") }
        items(vm.utilityData[table].orEmpty(), key = { it.optString("id") }) { row ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val title = when (table) {
                        "wear_events" -> (vm.items.firstOrNull { it.id == row.optString("item_id") }?.name ?: "Wardrobe item") + " · " + row.optString("worn_at").take(10)
                        "wardrobe_collections", "wardrobe_filter_presets" -> row.optString("name")
                        "outfit_plans" -> (vm.utilityData["outfits"].orEmpty().firstOrNull { it.optString("id") == row.optString("outfit_id") }?.optString("title") ?: "Planned look") + " · " + runCatching { java.time.OffsetDateTime.parse(row.optString("planned_for")).atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDate().toString() }.getOrDefault(row.optString("planned_for").take(10))
                        else -> row.optString("title")
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    if (table == "outfits") {
                        Text(row.optString("rationale"))
                        Text(row.optJSONArray("item_ids").toStringList().mapNotNull { id -> vm.items.firstOrNull { it.id == id }?.name }.joinToString(" · "))
                    }
                    if (table != "wear_events") Row {
                        if (table != "outfit_plans") TextButton({ draft = title; editing = table to row.optString("id") }, enabled = !vm.busy) { Text("Rename") }
                        TextButton({ deleting = table to row.optString("id") }, enabled = !vm.busy) { Text("Delete") }
                        if (table == "outfits") TextButton({ planning = row.optString("id") }, enabled = !vm.busy) { Text("Plan") }
                    }
                    if (table == "outfit_plans") Text(row.optString("occasion"))
                    if (table in setOf("wardrobe_collections", "packing_lists")) TextButton({ selected = row.optString("id") }) { Text("Manage items") }
                }
            }
        }
        if (selected != null && table in setOf("wardrobe_collections", "packing_lists")) {
            item { Text("Select wardrobe items", style = MaterialTheme.typography.titleLarge) }
            items(vm.items, key = { "item-${it.id}" }) { item ->
                val member = members.firstOrNull { it.optString("item_id") == item.id }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(item.name, modifier = Modifier.weight(1f))
                    if (member == null) TextButton({ vm.addToUtility(childTable, selected!!, item.id) }, enabled = !vm.busy) { Text("Add") }
                    else if (table == "packing_lists") FilterChip(member.optBoolean("packed"), { vm.setPacked(selected!!, item.id, !member.optBoolean("packed")) }, { Text(if (member.optBoolean("packed")) "Packed" else "To pack") })
                    else Text("Added")
                    if (member != null) TextButton({ vm.removeMember(childTable, selected!!, item.id) }, enabled = !vm.busy) { Text("Remove") }
                }
            }
        }
    }
}
