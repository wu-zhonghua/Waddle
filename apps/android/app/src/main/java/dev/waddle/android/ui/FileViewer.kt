package dev.waddle.android.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.drawable.Animatable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.waddle.android.files.*
import kotlinx.coroutines.*
import java.io.ByteArrayInputStream

@Composable
fun FileViewer(entry: FileEntry, onClose: () -> Unit, onImport: (() -> Unit)? = null) {
    val context = LocalContext.current
    val service = remember(context) { FileViewing(context) }
    val scope = rememberCoroutineScope()
    var notice by remember(entry.uri) { mutableStateOf<String?>(null) }
    var busy by remember(entry.uri) { mutableStateOf(false) }
    var formatted by rememberSaveable(entry.uri.toString()) { mutableStateOf(true) }
    var retry by remember { mutableIntStateOf(0) }
    var loadError by remember(entry.uri) { mutableStateOf<String?>(null) }
    LaunchedEffect(entry.uri, retry) {
        loadError = null
        try { withContext(Dispatchers.IO) { service.checked(entry) } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { loadError = fileError(e) }
    }
    fun action(block: suspend () -> String?) {
        if (busy) return
        busy = true; notice = null
        scope.launch {
            try { notice = block() }
            catch (e: CancellationException) { throw e }
            catch (_: ActivityNotFoundException) { notice = "没有安装能打开此文件的应用，请先安装支持此格式的应用，或选择分享/保存到手机。" }
            catch (e: Exception) { notice = fileError(e) }
            finally { busy = false }
        }
    }
    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(entry.mime)) { uri ->
        if (uri != null) action { withContext(Dispatchers.IO) { service.saveTo(entry, uri) }; "已保存到所选手机位置。" }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    ToolIcon("back", "返回", onClick=onClose)
                    Text(entry.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        ToolIcon("more", "文件操作", !busy) { menu=true }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("用其他应用打开") }, onClick = { menu = false; action {
                                val uri = withContext(Dispatchers.IO) { service.shareUri(entry) }
                                context.startActivity(service.intent(entry, uri, false)); null
                            } })
                            DropdownMenuItem(text = { Text("分享") }, onClick = { menu = false; action {
                                val uri = withContext(Dispatchers.IO) { service.shareUri(entry) }
                                context.startActivity(Intent.createChooser(service.intent(entry, uri, true), "分享 ${entry.name}")); null
                            } })
                            DropdownMenuItem(text = { Text("保存到手机") }, onClick = { menu = false
                                if (Build.VERSION.SDK_INT >= 29) action { withContext(Dispatchers.IO) { service.save(entry) } }
                                else saveAs.launch(entry.name)
                            })
                            if (onImport != null) DropdownMenuItem(text = { Text("导入副本") }, onClick = { menu = false; onImport() })
                            DropdownMenuItem(text = { Text("重新加载") }, onClick = { menu = false; retry++ })
                        }
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                notice?.let { Text(it, Modifier.fillMaxWidth().padding(12.dp)) }
                val kind = entry.viewKind()
                if (kind == ViewKind.MARKDOWN || kind == ViewKind.HTML) Row {
                    QuietButton(onClick = { formatted = true }, enabled = !formatted) { Text("排版") }
                    QuietButton(onClick = { formatted = false }, enabled = formatted) { Text("原文") }
                }
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (loadError != null) Text(loadError!!, Modifier.padding(24.dp))
                    else key(entry.uri, retry) {
                        when (kind) {
                            ViewKind.IMAGE -> FileImage(entry, Modifier.fillMaxSize())
                            ViewKind.PDF -> PdfPages(entry)
                            ViewKind.MEDIA -> if (entry.mime.startsWith("audio/")) AudioFile(entry) else MediaFile(entry)
                            ViewKind.TEXT, ViewKind.HTML, ViewKind.MARKDOWN -> TextFile(entry, formatted && kind != ViewKind.TEXT)
                            ViewKind.OTHER -> Text("Waddle 暂不支持预览此格式。请在“文件操作”中选择用其他应用打开、分享或保存到手机。", Modifier.padding(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FileImage(entry: FileEntry, modifier: Modifier = Modifier, thumbnail: Boolean = false) {
    val context = LocalContext.current
    var drawable by remember(entry.uri) { mutableStateOf<Drawable?>(null) }
    var error by remember(entry.uri) { mutableStateOf<String?>(null) }
    LaunchedEffect(entry.uri) {
        try { drawable = withContext(Dispatchers.IO) { FileViewing(context).image(entry, if (thumbnail) 480 else 2560) } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = fileError(e) }
        catch (e: OutOfMemoryError) { error = fileError(e) }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            error != null -> Text(error!!, Modifier.padding(16.dp))
            drawable == null -> CircularProgressIndicator()
            else -> drawable?.let { loaded ->
                AndroidView(factory = { ZoomImageView(it, !thumbnail) }, update = { it.show(loaded); it.contentDescription = if (thumbnail) "图片缩略图 ${entry.name}" else "图片 ${entry.name}，双指缩放、拖动" },
                    onRelease = { it.release() }, modifier = Modifier.fillMaxSize().clipToBounds())
            }
        }
    }
}

/** Fit first, then bounded pinch/pan. A thumbnail deliberately leaves gestures to its clickable parent. */
internal class ZoomImageView(context: Context, private val zoomable: Boolean = true) : android.view.View(context) {
    private var picture: Drawable? = null
    private var zoom = 1f; private var dx = 0f; private var dy = 0f
    private var lastX = 0f; private var lastY = 0f
    private val detector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean { zoom = (zoom * detector.scaleFactor).coerceIn(1f, 8f); constrain(); invalidate(); return true }
    })
    fun show(value: Drawable) {
        if (picture === value) return
        release(); picture = value; value.callback = this; zoom = 1f; dx = 0f; dy = 0f
        (value as? Animatable)?.start(); invalidate()
    }
    fun release() { (picture as? Animatable)?.stop(); picture?.callback = null; picture = null }
    override fun verifyDrawable(who: Drawable) = who === picture || super.verifyDrawable(who)
    override fun onDetachedFromWindow() { (picture as? Animatable)?.stop(); super.onDetachedFromWindow() }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); (picture as? Animatable)?.start() }
    private fun constrain() {
        val d = picture ?: return
        val fit = minOf(width.toFloat() / maxOf(1, d.intrinsicWidth), height.toFloat() / maxOf(1, d.intrinsicHeight))
        val maxX = maxOf(0f, (d.intrinsicWidth * fit * zoom - width) / 2)
        val maxY = maxOf(0f, (d.intrinsicHeight * fit * zoom - height) / 2)
        dx = dx.coerceIn(-maxX, maxX); dy = dy.coerceIn(-maxY, maxY)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = picture ?: return
        val ratio = minOf(width.toFloat() / maxOf(1, d.intrinsicWidth), height.toFloat() / maxOf(1, d.intrinsicHeight))
        val w = (d.intrinsicWidth * ratio).toInt(); val h = (d.intrinsicHeight * ratio).toInt()
        canvas.save()
        // Compose's AndroidView host need not clip child drawing. Keep zoomed
        // content inside the preview so it cannot paint over viewer controls.
        canvas.clipRect(0, 0, width, height)
        canvas.translate(width / 2f + dx, height / 2f + dy); canvas.scale(zoom, zoom)
        d.setBounds(-w / 2, -h / 2, w / 2, h / 2); d.draw(canvas); canvas.restore()
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!zoomable) return false
        detector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_MOVE && event.pointerCount == 1 && !detector.isInProgress) {
            dx += event.x - lastX; dy += event.y - lastY; constrain(); invalidate()
        }
        lastX = event.x; lastY = event.y
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }
}

@Composable
private fun PdfPages(entry: FileEntry) {
    val context = LocalContext.current
    var page by rememberSaveable(entry.uri.toString()) { mutableIntStateOf(0) }
    var count by remember { mutableIntStateOf(0) }
    var picture by remember { mutableStateOf<Drawable?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(entry.uri, page) {
        picture = null; error = null
        try {
            val result = withContext(Dispatchers.IO) { FileViewing(context).pdf(entry, page) }
            picture = BitmapDrawable(context.resources, result.first); count = result.second
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = fileError(e) }
        catch (e: OutOfMemoryError) { error = fileError(e) }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            QuietButton(onClick = { page-- }, enabled = page > 0 && picture != null) { Text("上一页") }
            Text(if (count == 0) "PDF" else "${page + 1} / $count")
            QuietButton(onClick = { page++ }, enabled = page + 1 < count && picture != null) { Text("下一页") }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                error != null -> Text(error!!, Modifier.padding(24.dp))
                picture == null -> CircularProgressIndicator()
                else -> picture?.let { loaded ->
                    AndroidView(factory = { ZoomImageView(it) }, update = { it.show(loaded); it.contentDescription = "PDF 第 ${page + 1} 页，双指缩放" }, onRelease = { it.release() }, modifier = Modifier.fillMaxSize().clipToBounds())
                }
            }
        }
    }
}

@Composable
private fun TextFile(entry: FileEntry, formatted: Boolean) {
    val context = LocalContext.current
    var preview by remember { mutableStateOf<FilePreview?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(entry.uri) {
        try { preview = withContext(Dispatchers.IO) { FileAccess(context).preview(entry) } }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = fileError(e) }
    }
    Column(Modifier.fillMaxSize()) {
        error?.let { Text(it, Modifier.padding(24.dp)) }
        if (preview == null && error == null) CircularProgressIndicator(Modifier.padding(24.dp))
        preview?.let { value ->
            if (value.truncated) Text("仅预览前 64 KiB；分享和保存保留完整文件。", Modifier.padding(12.dp))
            if (formatted) {
                val html = remember(value.text, entry.viewKind()) { if (entry.viewKind() == ViewKind.MARKDOWN) MarkdownPreview.html(value.text) else value.text }
                AndroidView(factory = { WebView(it).apply {
                    setBackgroundColor(android.graphics.Color.rgb(16,18,22))
                    settings.javaScriptEnabled = false; settings.allowFileAccess = false; settings.allowContentAccess = false
                    settings.blockNetworkLoads = true; settings.setSupportZoom(true); settings.builtInZoomControls = true; settings.displayZoomControls = false
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                    }
                } }, update = { if (it.tag != html) { it.tag = html; it.loadDataWithBaseURL("https://waddle.invalid/", html, "text/html", "UTF-8", null) } },
                    onRelease = { it.stopLoading(); it.destroy() }, modifier = Modifier.fillMaxSize())
            } else SelectionContainer { Text(value.text.ifEmpty { "（空文件）" }, Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), fontFamily = FontFamily.Monospace) }
        }
    }
}

/** Audio must not wait for VideoView's SurfaceHolder when switching viewer windows. */
@Composable
private fun AudioFile(entry: FileEntry) {
    val context = LocalContext.current
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var player by remember(entry.uri) { mutableStateOf<android.media.MediaPlayer?>(null) }
    var ready by remember(entry.uri) { mutableStateOf(false) }
    var playing by remember(entry.uri) { mutableStateOf(false) }
    var duration by remember(entry.uri) { mutableIntStateOf(0) }
    var position by rememberSaveable(entry.uri.toString()) { mutableIntStateOf(0) }
    var error by remember(entry.uri) { mutableStateOf<String?>(null) }
    var checked by remember(entry.uri) { mutableStateOf(false) }
    LaunchedEffect(entry.uri) {
        try { withContext(Dispatchers.IO) { FileViewing(context).checked(entry) }; checked = true }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = fileError(e) }
    }
    val audioManager = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager }
    val focusRequest = remember(entry.uri) {
        android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener { change ->
                if (change < 0 && ready) { player?.pause(); playing = false }
            }.build()
    }
    DisposableEffect(entry.uri, checked, owner) {
        val audio = if (checked) android.media.MediaPlayer() else null
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE && ready) { audio?.pause(); playing = false }
        }
        if (audio != null) {
            player = audio
            audio.setOnPreparedListener {
                duration = it.duration.coerceAtLeast(0); ready = true
                it.seekTo(position.toLong(), android.media.MediaPlayer.SEEK_CLOSEST)
            }
            audio.setOnCompletionListener { playing = false }
            audio.setOnErrorListener { _, _, _ ->
                error = "无法播放，文件可能损坏或手机不支持此音频编码。可用其他应用打开。"; ready = false; true
            }
            runCatching { audio.setDataSource(context, entry.uri); audio.prepareAsync() }
                .onFailure { error = fileError(it) }
            owner.lifecycle.addObserver(observer)
        }
        onDispose {
            owner.lifecycle.removeObserver(observer)
            ready = false; player = null
            audio?.release()
            audioManager.abandonAudioFocusRequest(focusRequest)
        }
    }
    LaunchedEffect(player, ready) {
        while (ready) { player?.let { position = it.currentPosition; playing = it.isPlaying }; delay(250) }
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when { error != null -> Text(error!!, Modifier.padding(24.dp)); !ready -> CircularProgressIndicator(); else -> WaddleIcon("file") }
        }
        if (ready && error == null) {
            Text("${position / 1000} / ${duration / 1000} 秒", Modifier.padding(horizontal = 16.dp))
            Slider(value = position.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
                onValueChange = { position = it.toInt(); player?.seekTo(position.toLong(), android.media.MediaPlayer.SEEK_CLOSEST) },
                valueRange = 0f..duration.coerceAtLeast(1).toFloat(), modifier = Modifier.padding(horizontal = 16.dp).semantics { contentDescription = "播放进度" })
            QuietButton(onClick = {
                if (playing) { player?.pause(); playing = false; audioManager.abandonAudioFocusRequest(focusRequest) }
                else if (audioManager.requestAudioFocus(focusRequest) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { player?.start(); playing = true }
            }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(if (playing) "暂停" else "播放") }
        }
    }
}

@Composable
private fun MediaFile(entry: FileEntry) {
    val context = LocalContext.current
    val video = remember(entry.uri) { VideoView(context) }
    var media by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var ready by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var duration by remember { mutableIntStateOf(0) }
    var position by rememberSaveable(entry.uri.toString()) { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var checked by remember { mutableStateOf(false) }
    LaunchedEffect(entry.uri) {
        try { withContext(Dispatchers.IO) { FileViewing(context).checked(entry) }; checked = true }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = fileError(e) }
    }
    LaunchedEffect(video, ready) { while (ready) { run { position = video.currentPosition; playing = video.isPlaying }; delay(250) } }
    // Playback belongs to this viewer only. Backgrounding/closing never affects terminal services.
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val playerView = video
    DisposableEffect(owner, playerView) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) { playerView.pause(); playing = false }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); playerView.stopPlayback() }
    }
    Column(Modifier.fillMaxSize()) {
        if (error != null) Text(error!!, Modifier.padding(24.dp))
        else if (checked) AndroidView(factory = { video.apply {
            setOnPreparedListener { player -> media = player; duration = player.duration.coerceAtLeast(0); ready = true; player.seekTo(position.toLong(), android.media.MediaPlayer.SEEK_CLOSEST) }
            setOnErrorListener { _, _, _ -> error = "无法播放，文件可能损坏或手机不支持此音视频编码。可用其他应用打开。"; ready = false; true }
            setOnCompletionListener { playing = false }
            setVideoURI(entry.uri)
        } }, onRelease = { it.stopPlayback() }, modifier = Modifier.weight(1f).fillMaxWidth())
        else Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        if (ready && error == null) {
            Text("${position / 1000} / ${duration / 1000} 秒", Modifier.padding(horizontal = 16.dp))
            Slider(value = position.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat()), onValueChange = { position = it.toInt(); media?.seekTo(position.toLong(), android.media.MediaPlayer.SEEK_CLOSEST) }, valueRange = 0f..duration.coerceAtLeast(1).toFloat(), modifier = Modifier.padding(horizontal = 16.dp).semantics { contentDescription = "播放进度" })
            QuietButton(onClick = { if (playing) video.pause() else video.start(); playing = !playing }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(if (playing) "暂停" else "播放") }
        }
    }
}
