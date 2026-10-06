package app.notify.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.notify.AppVM

private val Tabs = listOf("Today", "Bank", "Rhythm")

@Composable
fun App(vm: AppVM, resumeTick: Int) {
    val c = Look.c
    var tab by rememberSaveable { mutableStateOf(0) }
    var folderId by rememberSaveable { mutableStateOf<Long?>(null) }

    BackHandler(enabled = folderId != null && tab == 1) { folderId = null }

    Column(Modifier.fillMaxSize().background(c.bg).windowInsetsPadding(WindowInsets.statusBars)) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> TodayScreen(vm, resumeTick, goBank = { tab = 1 }, goRhythm = { tab = 2 })
                1 -> {
                    val id = folderId
                    if (id == null) BankScreen(vm, onOpen = { folderId = it })
                    else FolderScreen(vm, id, onBack = { folderId = null })
                }
                else -> RhythmScreen(vm)
            }
        }
        NavBar(tab) {
            if (it == 1 && tab == 1) folderId = null // tapping Bank again goes back to the folder list
            tab = it
        }
    }
}

@Composable
private fun NavBar(selected: Int, onSelect: (Int) -> Unit) {
    val c = Look.c
    Row(Modifier.fillMaxWidth().background(c.raised).navigationBarsPadding()) {
        Tabs.forEachIndexed { i, name ->
            val on = i == selected
            Column(
                Modifier.weight(1f).selectable(selected = on, role = Role.Tab, onClick = { onSelect(i) }).padding(bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.width(32.dp).height(3.dp).background(if (on) c.ink else Color.Transparent))
                Gap(13)
                Text(name, style = Type.action, color = if (on) c.ink else c.dim)
            }
        }
    }
}
