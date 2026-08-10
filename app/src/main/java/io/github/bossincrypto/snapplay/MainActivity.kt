package io.github.bossincrypto.snapplay

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ComponentActivity.settingsDataStore by preferencesDataStore("settings")
private val PlaybackSpeedKey = floatPreferencesKey("playback_speed")
private val Ink = Color(0xFF080B10)
private val Panel = Color(0xFF121824)
private val Signal = Color(0xFF52D9CB)
private val Warm = Color(0xFFFFC857)

@androidx.annotation.OptIn(UnstableApi::class)
class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer
    private var playerView: PlayerView? = null
    private var inPip by mutableStateOf(false)
    private var hasVideo by mutableStateOf(false)
    private var playing by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        player = ExoPlayer.Builder(this).build().apply {
            setSeekParameters(SeekParameters.CLOSEST_SYNC)
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playing = isPlaying
                    updatePipParams()
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    hasVideo = mediaItem != null
                    updatePipParams()
                }

                override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                    updatePipParams()
                }
            })
        }

        setContent {
            val speedFlow = remember { settingsDataStore.data.map { it[PlaybackSpeedKey] ?: 1f } }
            val speed by speedFlow.collectAsStateWithLifecycle(initialValue = 1f)

            LaunchedEffect(speed) { player.setPlaybackSpeed(speed) }
            SnapPlayScreen(speed)
        }

        openIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openIntent(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && shouldEnterPip()) {
            enterPictureInPictureMode(buildPipParams())
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inPip = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode) player.pause()
    }

    override fun onDestroy() {
        playerView?.player = null
        player.release()
        super.onDestroy()
    }

    private fun openIntent(intent: Intent) {
        intent.data?.let(::play)
    }

    private fun play(uri: Uri) {
        player.setMediaItem(MediaItem.fromUri(uri))
        player.prepare()
        player.play()
        hasVideo = true
        updatePipParams()
    }

    private fun shouldEnterPip() = hasVideo && playing &&
        packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)

    private fun buildPipParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
        playerView?.let { view ->
            val rect = Rect()
            if (view.getGlobalVisibleRect(rect) && !rect.isEmpty) builder.setSourceRectHint(rect)
        }

        val size = player.videoSize
        if (size.width > 0 && size.height > 0) {
            val ratio = size.width.toFloat() / size.height
            if (ratio in 0.42f..2.39f) builder.setAspectRatio(Rational(size.width, size.height))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(shouldEnterPip())
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun updatePipParams() {
        if (::player.isInitialized && packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            setPictureInPictureParams(buildPipParams())
        }
    }

    @Composable
    private fun SnapPlayScreen(speed: Float) {
        var showUrl by remember { mutableStateOf(false) }
        var showSpeed by remember { mutableStateOf(false) }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            play(uri)
        }

        MaterialTheme {
            Surface(color = Ink, modifier = Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxSize()) {
                    AndroidView(
                        factory = { context ->
                            PlayerView(context).apply {
                                player = this@MainActivity.player
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                setShutterBackgroundColor(android.graphics.Color.BLACK)
                                playerView = this
                                addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updatePipParams() }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { it.player = player }
                    )

                    if (!inPip) {
                        if (!hasVideo) {
                            EmptyState(
                                onFile = { picker.launch(arrayOf("video/*")) },
                                onUrl = { showUrl = true }
                            )
                        } else {
                            PlayerControls(
                                speed = speed,
                                onSpeed = { showSpeed = true },
                                onOpen = { picker.launch(arrayOf("video/*")) },
                                onPip = {
                                    if (shouldEnterPip()) enterPictureInPictureMode(buildPipParams())
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showUrl) UrlDialog(onDismiss = { showUrl = false }) { uri ->
            showUrl = false
            play(uri)
        }
        if (showSpeed) SpeedDialog(speed, onDismiss = { showSpeed = false })
    }

    @Composable
    private fun EmptyState(onFile: () -> Unit, onUrl: () -> Unit) {
        Column(
            modifier = Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("SNAP / PLAY", color = Signal, fontSize = 13.sp, letterSpacing = 3.sp)
            Spacer(Modifier.height(18.dp))
            Text("Видео без ожидания.", color = Color.White, fontSize = 36.sp, lineHeight = 39.sp)
            Spacer(Modifier.height(12.dp))
            Text("Откройте файл или прямую HTTP-ссылку. Скорость сохранится для всех видео.", color = Color(0xFFAAB4C4), fontSize = 16.sp)
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onFile, colors = ButtonDefaults.buttonColors(containerColor = Signal, contentColor = Ink)) {
                    Text("Открыть файл")
                }
                OutlinedButton(onClick = onUrl) { Text("Вставить ссылку") }
            }
        }
    }

    @Composable
    private fun PlayerControls(speed: Float, onSpeed: () -> Unit, onOpen: () -> Unit, onPip: () -> Unit) {
        var position by remember { mutableLongStateOf(0L) }
        var duration by remember { mutableLongStateOf(0L) }
        var dragging by remember { mutableStateOf(false) }
        var dragPosition by remember { mutableLongStateOf(0L) }

        LaunchedEffect(Unit) {
            while (true) {
                if (!dragging) position = player.currentPosition.coerceAtLeast(0)
                duration = player.duration.coerceAtLeast(0)
                delay(250)
            }
        }

        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(
                Modifier.fillMaxWidth().background(Color(0x88080B10)).padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("SNAPPLAY", color = Signal, letterSpacing = 2.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CompactButton("${formatSpeed(speed)}×", onSpeed)
                    CompactButton("PiP", onPip)
                    CompactButton("Файл", onOpen)
                }
            }

            Column(Modifier.fillMaxWidth().background(Color(0xCC080B10)).padding(16.dp)) {
                Slider(
                    value = (if (dragging) dragPosition else position).toFloat(),
                    onValueChange = {
                        dragging = true
                        dragPosition = it.toLong()
                    },
                    onValueChangeFinished = {
                        player.seekTo(dragPosition)
                        position = dragPosition
                        dragging = false
                    },
                    valueRange = 0f..duration.coerceAtLeast(1).toFloat()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("${formatTime(if (dragging) dragPosition else position)} / ${formatTime(duration)}", color = Color(0xFFAAB4C4), fontSize = 13.sp)
                    Button(
                        onClick = { if (player.isPlaying) player.pause() else player.play() },
                        colors = ButtonDefaults.buttonColors(containerColor = Warm, contentColor = Ink),
                        modifier = Modifier.width(120.dp)
                    ) { Text(if (playing) "Пауза" else "Играть") }
                }
            }
        }
    }

    @Composable
    private fun CompactButton(label: String, action: () -> Unit) {
        Surface(color = Panel, shape = RoundedCornerShape(10.dp), modifier = Modifier.clickable(onClick = action)) {
            Text(label, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp), fontSize = 13.sp)
        }
    }

    @Composable
    private fun UrlDialog(onDismiss: () -> Unit, onOpen: (Uri) -> Unit) {
        var value by remember { mutableStateOf("") }
        val uri = parseVideoUrl(value)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Прямая ссылка") },
            text = {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("https://…/video.mp4") },
                    singleLine = true
                )
            },
            confirmButton = { Button(onClick = { uri?.let { onOpen(Uri.parse(it)) } }, enabled = uri != null) { Text("Открыть") } },
            dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Отмена") } }
        )
    }

    @Composable
    private fun SpeedDialog(current: Float, onDismiss: () -> Unit) {
        var speed by remember(current) { mutableStateOf(current) }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Скорость для всех видео") },
            text = {
                Column {
                    Text("${formatSpeed(speed)}×", fontSize = 32.sp, color = Signal)
                    Slider(
                        value = speed,
                        onValueChange = { speed = (it * 20).roundToInt() / 20f },
                        valueRange = 0.25f..3f
                    )
                    Text("От 0,25× до 3×. Изменение применяется сразу и сохраняется.")
                }
            },
            confirmButton = {
                Button(onClick = {
                    player.setPlaybackSpeed(speed)
                    lifecycleScope.launch {
                        settingsDataStore.edit { it[PlaybackSpeedKey] = speed }
                    }
                    onDismiss()
                }) { Text("Сохранить") }
            },
            dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Отмена") } }
        )
    }
}

private fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private fun formatSpeed(speed: Float): String =
    if (speed % 1f == 0f) speed.toInt().toString() else "%.2f".format(speed).trimEnd('0')
