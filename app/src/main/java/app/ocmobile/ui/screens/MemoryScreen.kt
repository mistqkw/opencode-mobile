package app.ocmobile.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ocmobile.Graph
import app.ocmobile.data.Memory
import app.ocmobile.ui.Nav
import app.ocmobile.ui.components.IconBtn
import app.ocmobile.ui.components.OcDialog
import app.ocmobile.ui.components.OcSheet
import app.ocmobile.ui.components.OcSwitch
import app.ocmobile.ui.components.OcTextField
import app.ocmobile.ui.components.Pill
import app.ocmobile.ui.components.SettingsGroup
import app.ocmobile.ui.components.SettingsRow
import app.ocmobile.ui.components.SmallButton
import app.ocmobile.ui.components.TopBar
import app.ocmobile.ui.components.Txt
import app.ocmobile.ui.theme.Ic
import app.ocmobile.ui.theme.Oc
import java.io.File

private fun pretty(f: File) = f.nameWithoutExtension.replace('-', ' ').replace('_', ' ').replaceFirstChar { it.uppercase() }

/** Память: полки-файлы, которые агент ведёт сам, плюс инструкция, как их вести. */
@Composable
fun MemoryScreen(nav: Nav) {
    val c = Oc.c
    val prefs = Graph.prefs
    var rev by remember { mutableStateOf(0) }
    var enabled by remember { mutableStateOf(prefs.memoryEnabled) }
    var note by remember { mutableStateOf("") }
    var custom by remember { mutableStateOf(prefs.memoryCustom) }
    var customSaved by remember { mutableStateOf(true) }
    var open by remember { mutableStateOf<File?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    val shelves = remember(rev) { Memory.shelves() }
    val index = remember(rev) { runCatching { File(Memory.dir(), "MEMORY.md").readText() }.getOrDefault("") }

    Column(Modifier.fillMaxSize().background(c.bg)) {
        TopBar("Memory", left = { IconBtn(Ic.Back, { nav.pop() }) })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).imePadding().navigationBarsPadding().padding(bottom = 32.dp)) {
            SettingsGroup {
                SettingsRow("Use memory", subtitle = "OpenCode reads it when a chat starts and saves lasting facts on shelves.", icon = Ic.Scroll, trailing = {
                    OcSwitch(enabled) { enabled = it; prefs.memoryEnabled = it; Memory.sync() }
                })
            }
            Txt("Shelves", Modifier.padding(start = 28.dp, top = 22.dp, bottom = 8.dp), size = 16.sp, color = c.textDim)
            if (shelves.isEmpty()) {
                Txt("Nothing here yet. Tell OpenCode \"remember that…\" in a chat, or add a note below.", Modifier.padding(horizontal = 28.dp), size = 15.sp, color = c.textDim, lineHeight = 21.sp)
            } else SettingsGroup {
                shelves.forEach { f ->
                    val first = runCatching { f.useLines { l -> l.firstOrNull { it.isNotBlank() && !it.startsWith("#") } } }.getOrNull().orEmpty()
                    SettingsRow(pretty(f), subtitle = first.take(80).ifBlank { "${f.length()} bytes" }, icon = Ic.File, onClick = { open = f })
                }
            }
            if (index.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                SettingsGroup { SettingsRow("Index (MEMORY.md)", icon = Ic.ListCheck, onClick = { open = File(Memory.dir(), "MEMORY.md") }) }
            }

            Txt("Add a note", Modifier.padding(start = 28.dp, top = 24.dp, bottom = 8.dp), size = 16.sp, color = c.textDim)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OcTextField(note, { note = it }, placeholder = "I live in Kraków and work nights", singleLine = false, minLines = 2)
                Txt("It goes to the inbox; OpenCode sorts it onto the right shelf in your next chat.", size = 13.sp, color = c.textDim)
                Pill("Add", { Memory.addNote(note); note = ""; rev++ }, enabled = note.isNotBlank(), modifier = Modifier.fillMaxWidth())
            }

            Txt("How to keep memory", Modifier.padding(start = 28.dp, top = 24.dp, bottom = 8.dp), size = 16.sp, color = c.textDim)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OcTextField(custom, { custom = it; customSaved = false }, placeholder = "Your own rules, e.g. keep each shelf under 30 lines. Never store addresses.", singleLine = false, minLines = 4)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton(if (customSaved) "Saved" else "Save", { prefs.memoryCustom = custom.trim(); Memory.sync(); customSaved = true }, filled = !customSaved)
                    SmallButton("Show the full prompt", { open = File("prompt") })
                }
            }

            if (shelves.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                SettingsGroup { SettingsRow("Clear all memory", icon = Ic.Trash, titleColor = c.danger, onClick = { confirmClear = true }) }
            }
        }
    }

    open?.let { f ->
        if (f.name == "prompt") {
            OcSheet({ open = null }, "Memory prompt", skipHalf = false) {
                Txt(Memory.prompt(), Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp), size = 14.sp, color = c.textDim, lineHeight = 20.sp, family = Oc.mono)
            }
        } else {
            var text by remember(f) { mutableStateOf(runCatching { f.readText() }.getOrDefault("")) }
            OcSheet({ open = null }, pretty(f), skipHalf = false) {
                Column(Modifier.imePadding().padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OcTextField(text, { text = it }, singleLine = false, minLines = 8, mono = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        if (f.name != "MEMORY.md") SmallButton("Delete shelf", { f.delete(); rev++; open = null }, danger = true)
                        Spacer(Modifier.weight(1f))
                        SmallButton("Save", { f.writeText(text); rev++; open = null }, filled = true)
                    }
                }
            }
        }
    }
    if (confirmClear) {
        OcDialog("Clear all memory?", { confirmClear = false }, "Clear", {
            Memory.shelves().forEach { it.delete() }
            File(Memory.dir(), "MEMORY.md").writeText("# Memory index\n\nNothing saved yet.\n")
            rev++; confirmClear = false
        }, danger = true) { Txt("All shelves are deleted. This cannot be undone.", size = 16.sp, color = c.textDim) }
    }
}
