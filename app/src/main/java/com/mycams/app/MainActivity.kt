package com.mycams.app

import android.app.Activity
import android.os.Bundle
import android.view.TextureView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        val storage = Storage(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) { App(storage) }
        }
    }
}

@Composable
fun App(storage: Storage) {
    var cameras by remember { mutableStateOf(storage.load()) }
    var fullId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editing by remember { mutableStateOf<Camera?>(null) }
    var showCredentials by remember { mutableStateOf(false) }

    fun save(list: List<Camera>) {
        cameras = list
        storage.save(list)
    }

    val full = cameras.find { it.id == fullId }
    if (full != null) {
        FullScreen(full, onBack = { fullId = null })
    } else {
        CameraGrid(
            cameras = cameras,
            onOpen = { fullId = it.id },
            onEdit = { editing = it },
            onCredentials = { showCredentials = true },
            onAdd = {
                val id = (cameras.maxOfOrNull { it.id } ?: 0L) + 1
                val last = cameras.lastOrNull()
                editing = Camera(
                    id = id,
                    name = "Камера ${cameras.size + 1}",
                    host = last?.host ?: "",
                    user = last?.user ?: "admin",
                    password = last?.password ?: "",
                    channel = (last?.channel ?: 0) + 1
                )
            }
        )
    }

    if (showCredentials) {
        CredentialsDialog(
            initialUser = cameras.firstOrNull()?.user ?: "admin",
            onDismiss = { showCredentials = false },
            onSave = { u, p ->
                save(cameras.map { it.copy(user = u, password = p) })
                showCredentials = false
            }
        )
    }

    editing?.let { cam ->
        val exists = cameras.any { it.id == cam.id }
        CameraDialog(
            initial = cam,
            canDelete = exists,
            onDismiss = { editing = null },
            onSave = { c ->
                save(if (exists) cameras.map { if (it.id == c.id) c else it } else cameras + c)
                editing = null
            },
            onDelete = {
                save(cameras.filter { it.id != cam.id })
                editing = null
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CameraGrid(
    cameras: List<Camera>,
    onOpen: (Camera) -> Unit,
    onEdit: (Camera) -> Unit,
    onCredentials: () -> Unit,
    onAdd: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Камеры") },
                actions = {
                    TextButton(onClick = onCredentials) { Text("Пароль") }
                    TextButton(onClick = onAdd) { Text("+ Добавить") }
                }
            )
        }
    ) { padding ->
        if (cameras.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Нажмите «Добавить» и укажите IP-адрес камеры или регистратора, логин и пароль.\n\n" +
                        "Вне дома включите Tailscale на телефоне.",
                    textAlign = TextAlign.Center
                )
            }
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(if (cameras.size <= 1) 1 else 2),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(cameras, key = { it.id }) { cam ->
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                    RtspVideo(cam.url(sub = true), Modifier.fillMaxSize())
                    Text(
                        cam.name,
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    // Прозрачный слой для нажатий: короткое — на весь экран, долгое — настройки
                    Box(
                        Modifier.fillMaxSize().combinedClickable(
                            onClick = { onOpen(cam) },
                            onLongClick = { onEdit(cam) }
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun FullScreen(cam: Camera, onBack: () -> Unit) {
    val context = LocalContext.current
    var hd by rememberSaveable { mutableStateOf(true) }
    var muted by rememberSaveable { mutableStateOf(true) }
    var controls by remember { mutableStateOf(true) }
    var texture by remember { mutableStateOf<TextureView?>(null) }

    BackHandler(onBack = onBack)

    // Скрываем системные панели, пока открыт полный экран
    DisposableEffect(Unit) {
        val window = (context as Activity).window
        val ctrl = WindowCompat.getInsetsController(window, window.decorView)
        ctrl.hide(WindowInsetsCompat.Type.systemBars())
        ctrl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose { ctrl.show(WindowInsetsCompat.Type.systemBars()) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        RtspVideo(
            url = cam.url(sub = !hd),
            modifier = Modifier.fillMaxSize(),
            muted = muted,
            onTexture = { texture = it }
        )
        Box(
            Modifier.fillMaxSize().combinedClickableCompat { controls = !controls }
        )
        if (controls) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .safeDrawingPadding()
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) { Text("‹ Назад", color = Color.White) }
                Text(cam.name, color = Color.White, modifier = Modifier.weight(1f), maxLines = 1)
                TextButton(onClick = { hd = !hd }) { Text(if (hd) "HD" else "SD", color = Color.White) }
                TextButton(onClick = { muted = !muted }) { Text(if (muted) "Звук выкл" else "Звук вкл", color = Color.White) }
                TextButton(onClick = {
                    val bmp = texture?.bitmap
                    val ok = bmp != null && saveSnapshot(context, bmp, cam.name)
                    Toast.makeText(
                        context,
                        if (ok) "Снимок сохранён в Галерею (Pictures/Cameras)" else "Не удалось сделать снимок",
                        Toast.LENGTH_SHORT
                    ).show()
                }) { Text("Снимок", color = Color.White) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick)

@Composable
fun CameraDialog(
    initial: Camera,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (Camera) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var host by remember { mutableStateOf(initial.host) }
    var port by remember { mutableStateOf(initial.port.toString()) }
    var user by remember { mutableStateOf(initial.user) }
    var password by remember { mutableStateOf(initial.password) }
    var channel by remember { mutableStateOf(initial.channel.toString()) }
    var customUrl by remember { mutableStateOf(initial.customUrl) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (canDelete) "Камера" else "Новая камера") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true)
                OutlinedTextField(
                    host, { host = it.trim() }, label = { Text("IP-адрес (например 192.168.1.10)") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        port, { port = it.filter(Char::isDigit) }, label = { Text("Порт") },
                        singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        channel, { channel = it.filter(Char::isDigit) }, label = { Text("Канал") },
                        singleLine = true, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                OutlinedTextField(user, { user = it.trim() }, label = { Text("Логин") }, singleLine = true)
                OutlinedTextField(
                    password, { password = it }, label = { Text("Пароль") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                OutlinedTextField(
                    customUrl, { customUrl = it }, label = { Text("Свой RTSP-адрес (необязательно)") },
                    singleLine = false
                )
                Text(
                    "Канал: номер камеры на регистраторе. Для отдельной IP-камеры — 1. " +
                        "Если камера не показывает, вставьте полный rtsp:// адрес в последнее поле.",
                    fontSize = 12.sp
                )
                if (canDelete) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Удалить камеру", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = host.isNotBlank() || customUrl.isNotBlank(),
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim().ifEmpty { "Камера" },
                            host = host,
                            port = port.toIntOrNull() ?: 554,
                            user = user,
                            password = password,
                            channel = channel.toIntOrNull() ?: 1,
                            customUrl = customUrl.trim()
                        )
                    )
                }
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text("Удалить «${initial.name}»?") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Удалить") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } }
        )
    }
}

/** Логин и пароль сразу для всех камер. */
@Composable
fun CredentialsDialog(initialUser: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var user by remember { mutableStateOf(initialUser) }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Логин и пароль для всех камер") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(user, { user = it.trim() }, label = { Text("Логин") }, singleLine = true)
                OutlinedTextField(
                    password, { password = it }, label = { Text("Пароль") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                Text("Если у какой-то камеры пароль другой, измените его долгим нажатием на неё.", fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(user.ifEmpty { "admin" }, password) }) { Text("Применить") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}
