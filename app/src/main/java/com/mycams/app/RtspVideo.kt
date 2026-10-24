package com.mycams.app

import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource

/** Видео с камеры по RTSP. */
@OptIn(UnstableApi::class)
@Composable
fun RtspVideo(
    url: String,
    modifier: Modifier = Modifier,
    muted: Boolean = true,
    onTexture: (TextureView) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var retry by remember { mutableIntStateOf(0) }
    var status by remember(url, retry) { mutableStateOf("Подключение…") }
    var failed by remember(url, retry) { mutableStateOf(false) }
    var aspect by remember(url) { mutableFloatStateOf(16f / 9f) }
    val texture = remember { TextureView(context) }

    val player = remember(url, retry) {
        ExoPlayer.Builder(context).build().apply {
            val source = RtspMediaSource.Factory()
                .setForceUseRtpTcp(true)   // TCP надёжнее через VPN
                .setTimeoutMs(10_000)
                .createMediaSource(MediaItem.fromUri(url))
            setMediaSource(source)
            playWhenReady = true
            prepare()
        }
    }

    LaunchedEffect(muted, player) { player.volume = if (muted) 0f else 1f }
    LaunchedEffect(texture) { onTexture(texture) }

    DisposableEffect(player) {
        player.setVideoTextureView(texture)
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> if (!failed) status = "Подключение…"
                    Player.STATE_READY -> status = ""
                    Player.STATE_ENDED -> { status = "Поток прервался"; failed = true }
                    else -> {}
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                status = "Нет связи с камерой"
                failed = true
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    aspect = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                }
            }
        }
        player.addListener(listener)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> player.stop()
                Lifecycle.Event.ON_START -> { player.prepare(); player.play() }
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player.removeListener(listener)
            player.release()
        }
    }

    Box(modifier.background(Color.Black)) {
        AndroidView(
            factory = { texture },
            modifier = Modifier.align(Alignment.Center).aspectRatio(aspect)
        )
        if (status.isNotEmpty()) {
            Column(
                Modifier.align(Alignment.Center).padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(status, color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center)
                if (failed) {
                    TextButton(onClick = { retry++ }) { Text("Повторить") }
                }
            }
        }
    }
}
