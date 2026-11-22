package dev.waddle.android

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import dev.waddle.android.terminal.TerminalService
import dev.waddle.android.terminal.TerminalWorkspace
import dev.waddle.android.ui.WaddleScreen

class MainActivity : ComponentActivity() {
    var workspace by mutableStateOf<TerminalWorkspace?>(null)
        private set
    private var bound = false
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val service = (binder as TerminalService.LocalBinder).service
            service.openInitialSession()
            workspace = service.workspace
        }
        override fun onServiceDisconnected(name: ComponentName) { workspace = null }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.rgb(16, 21, 18)),
        )
        startTerminalService()
        bound = bindService(Intent(this, TerminalService::class.java), connection, Context.BIND_AUTO_CREATE)
        if (savedInstanceState == null && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            WaddleScreen(workspace, onNewSession = { startTerminalService(); workspace?.create() })
        }
    }

    private fun startTerminalService() {
        ContextCompat.startForegroundService(this, Intent(this, TerminalService::class.java))
    }

    override fun onResume() {
        super.onResume()
        workspace?.files?.refreshStorage()
    }

    override fun onDestroy() {
        if (bound) unbindService(connection)
        workspace = null
        super.onDestroy()
    }
}
