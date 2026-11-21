package dev.waddle.android

import android.os.Bundle
import android.net.Uri
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.InputDevice
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.MutableState
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.waddle.android.codex.CodexController
import dev.waddle.android.terminal.TerminalWorkspace
import dev.waddle.android.files.GrantedFolder
import dev.waddle.android.files.FileAccess
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Offline navigation tests. No login, model request, shared storage access or settings mutation. */
@RunWith(AndroidJUnit4::class)
class NavigationIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun <T> main(block: () -> T): T {
        val result = AtomicReference<T>()
        instrumentation.runOnMainSync { result.set(block()) }
        return result.get()
    }
    private fun await(message: String, check: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15_000
        while (SystemClock.uptimeMillis() < deadline) {
            if (check()) return
            SystemClock.sleep(100)
        }
        fail(message + "; UI=" + nodes().mapNotNull { it.text?.toString() ?: it.contentDescription?.toString() }.take(65))
    }
    private fun nodes(): List<AccessibilityNodeInfo> {
        fun visit(node: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> =
            if (node == null) emptyList() else listOf(node) + (0 until node.childCount).flatMap { visit(node.getChild(it)) }
        return visit(instrumentation.uiAutomation.rootInActiveWindow)
    }
    private fun visible(label: String) = nodes().any { it.text?.toString() == label || it.contentDescription?.toString() == label }
    private fun click(label: String) {
        await("Cannot click $label") {
            var target = nodes().firstOrNull { it.text?.toString() == label || it.contentDescription?.toString() == label }
            var clicked = false
            while (target != null && !clicked) {
                if (target.isClickable && target.isEnabled) clicked = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                target = target.parent
            }
            clicked
        }
        instrumentation.waitForIdleSync()
    }
    private fun workspace(scenario: ActivityScenario<MainActivity>): TerminalWorkspace {
        var value: TerminalWorkspace? = null
        await("Workspace not connected") { scenario.onActivity { value = it.workspace }; value != null }
        return value!!
    }
    private fun openDrawer() { click("打开导航抽屉"); await("Drawer not open") { visible("会话") } }
    private fun swipeOpenDrawer() {
        val display = instrumentation.targetContext.resources.displayMetrics
        val start = SystemClock.uptimeMillis()
        val y = display.heightPixels * 0.4f // Includes the horizontally scrolling breadcrumb area.
        fun touch(action: Int, x: Float) {
            val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        touch(MotionEvent.ACTION_DOWN, 3 * display.density)
        for (step in 1..12) {
            SystemClock.sleep(20)
            touch(MotionEvent.ACTION_MOVE, 3 * display.density + display.widthPixels * 0.65f * step / 12)
        }
        touch(MotionEvent.ACTION_UP, display.widthPixels * 0.7f)
        await("Left edge did not open drawer") { visible("会话") }
    }
    private fun closeDrawer() {
        instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
        await("Drawer did not close") { !visible("会话") }
    }

    @Test fun locationsSeparateSourceSelectionFromBrowsingAndPermissionIsExplicit() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val workspace = workspace(scenario)
            main { workspace.files.showLocations() }
            click("打开文件")
            await("Locations missing") { visible("手机存储") && visible("Waddle 文件") && visible("其他来源") }
            assertFalse(visible("在当前文件夹打开终端"))
            click("Waddle 文件")
            await("Local directory missing") { visible("在当前文件夹打开 Codex") }
            assertFalse(visible("其他来源"))
            assertFalse(visible("选择文件"))
            click("位置")
            click("文件选项"); click("手机存储授权")
            await("Permission explanation missing") { visible("直接访问手机存储") }
            click("暂不授权")
            await("Permission dialog not dismissed") { !visible("前往系统设置") }
            assertTrue(main { workspace.files.showingLocations })
        }
    }

    @Test fun folderCodexPanelsKeepDirectoryDraftAndMessagesAcrossSwitchAndRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val shells = workspace(scenario)
            lateinit var panels: dev.waddle.android.codex.CodexWorkspace
            scenario.onActivity { panels = it.codexWorkspace!! }
            val initial = main { panels.sessions.toList() }
            val original = main { panels.selected!! }
            val root = File(shells.runtime.home, "codex-ui-${System.nanoTime()}").apply { mkdir() }
            val folder = File(root, "中文 ' folder").apply { mkdir() }
            val auth = File(shells.runtime.config, "auth.json")
            val before = if (auth.exists()) auth.readBytes() else null
            fun signedInForDraftOnly(client: CodexController) {
                await("Local Codex did not connect") { main { client.connected } }
                main {
                    val field = client.javaClass.getDeclaredField("signedIn" + "$" + "delegate").apply { isAccessible = true }
                    @Suppress("UNCHECKED_CAST")
                    (field.get(client) as MutableState<Boolean>).value = true
                }
            }
            try {
                click("打开文件")
                main { shells.files.openLocal(root) }
                await("Folder not loaded") { main { !shells.files.loading && shells.files.current?.file == root.canonicalFile } }
                click("文件夹 ${folder.name} 的操作"); click("在所选文件夹打开 Codex")
                val first = main { panels.selected!! }
                assertNotSame(original, first)
                assertEquals(folder.canonicalFile, main { first.client.workingDirectory })
                signedInForDraftOnly(first.client)
                await("First draft field missing") { nodes().any { it.isEditable } }
                assertTrue(nodes().first { it.isEditable }.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,
                    Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "第一个目录的草稿") }))
                main {
                    first.client.messages += dev.waddle.android.codex.CodexMessage("fixture", "Codex", "第一个目录的消息")
                    first.client.approvals += dev.waddle.android.codex.CodexApproval("fixture-approval", "测试待审批", "只用于界面回归，不执行命令")
                }
                click("稍后处理")
                await("Approval dialog not deferred") { !visible("测试待审批") }
                click("打开文件")
                click("在当前文件夹打开 Codex")
                val second = main { panels.selected!! }
                assertNotSame(first, second)
                assertEquals(root.canonicalFile, main { second.client.workingDirectory })
                signedInForDraftOnly(second.client)
                await("Second draft field missing") { nodes().any { it.isEditable } }
                assertFalse(visible("第一个目录的草稿")); assertFalse(visible("第一个目录的消息"))
                assertTrue(main { second.client.approvals.isEmpty() })
                openDrawer(); click(first.label)
                await("First panel state lost") { visible("第一个目录的草稿") && visible("第一个目录的消息") }
                scenario.recreate()
                await("Recreation lost panel state") { visible("第一个目录的草稿") && visible("第一个目录的消息") }
                scenario.onActivity { assertSame(first.client, it.codex) }
                assertEquals(1, main { first.client.approvals.size })
                assertTrue(visible("查看审批"))
                openDrawer(); click("关闭 ${second.label}")
                click("保留")
                await("Close dialog still open") { !visible("关闭对话") }
                assertTrue(main { second in panels.sessions })
                click("关闭 ${second.label}"); click("关闭对话")
                await("Panel did not close") { main { second !in panels.sessions } }
                assertTrue(main { first.client.connected })
                await("Close confirmation still visible") { !visible("关闭对话") && visible("会话") }
                closeDrawer()
                await("Draft lost after closing other panel") { visible("第一个目录的草稿") }
                click("查看审批")
                await("Deferred approval lost") { visible("测试待审批") }
                click("拒绝")
                await("Approval was not resolved") { main { first.client.approvals.isEmpty() } }
                if (before == null) assertFalse(auth.exists()) else assertArrayEquals(before, auth.readBytes())
            } finally {
                main {
                    panels.sessions.filter { it !in initial }.toList().forEach(panels::close)
                    panels.select(original); shells.files.openLocal(shells.runtime.home)
                }
                root.deleteRecursively()
            }
        }
    }

    @Test fun folderCardAndCurrentFolderOpenNewShellsHereWithoutInterruptingOriginal() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val workspace = workspace(scenario)
            val original = main { workspace.selected!! }
            val initial = main { workspace.sessions.toList() }
            val root = File(workspace.runtime.home, "open-here-${System.nanoTime()}").apply { mkdir() }
            val folder = File(root, "中文 ' folder").apply { mkdir() }
            File(folder, "existing.txt").writeText("already here")
            try {
                await("Original shell not ready") { main { original.promptReady } }
                val cwd = main { original.cwd }
                main { original.terminal.write("read -r keep_original_running\r") }
                await("Original read did not start") { main { !original.promptReady } }
                click("打开文件")
                main { workspace.files.clearNotice(); workspace.files.openLocal(root) }
                await("Folder list missing") { main { !workspace.files.loading && workspace.files.entries.any { it.name == folder.name } } }
                click("文件夹 ${folder.name} 的操作"); click("在所选文件夹打开终端")
                await("New shell did not start in selected child") {
                    main { workspace.selected !== original && workspace.selected?.promptReady == true && workspace.selected?.cwd == folder.canonicalPath }
                }
                val first = main { workspace.selected!! }
                assertEquals(initial.size + 1, main { workspace.sessions.size })
                assertFalse(main { original.promptReady })
                assertEquals(cwd, main { original.cwd })
                assertTrue(main { original.running })
                instrumentation.waitForIdleSync()
                instrumentation.sendStringSync("pwd > cwd.txt; ls > listing.txt; printf 'created here' > created.txt")
                instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_ENTER)
                await("Command did not write in selected directory") { File(folder, "created.txt").exists() && main { first.promptReady } }
                assertEquals(folder.canonicalPath, File(folder, "cwd.txt").readText().trim())
                assertTrue(File(folder, "listing.txt").readText().contains("existing.txt"))
                assertEquals("created here", File(folder, "created.txt").readText())
                assertFalse(File(root, "created.txt").exists())
                click("打开文件")
                await("Browser moved while opening child shell") { main { workspace.files.current?.file == root.canonicalFile } }
                click(folder.name)
                await("Cannot enter folder") { main { !workspace.files.loading && workspace.files.current?.file == folder.canonicalFile } }
                click("在当前文件夹打开终端")
                await("Current-folder action reused existing shell") {
                    main { workspace.selected !== first && workspace.selected !== original && workspace.selected?.promptReady == true }
                }
                assertEquals(folder.canonicalPath, main { workspace.selected?.cwd })
                assertEquals(initial.size + 2, main { workspace.sessions.size })
                assertFalse(main { original.promptReady })
            } finally {
                main {
                    original.terminal.write("\u0003")
                    workspace.sessions.toList().filter { it !in initial }.forEach(workspace::close)
                    workspace.select(original); workspace.files.openLocal(workspace.runtime.home)
                }
                root.deleteRecursively()
            }
        }
    }

    @Test fun missingFolderAndSessionLimitDoNotCreateOrSwitchShell() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val workspace = workspace(scenario)
            val initial = main { workspace.sessions.toList() }
            val original = main { workspace.selected!! }
            val root = File(workspace.runtime.home, "limit-here-${System.nanoTime()}").apply { mkdir() }
            val folder = File(root, "remove-me").apply { mkdir() }
            try {
                click("打开文件")
                main { workspace.files.clearNotice(); workspace.files.openLocal(root) }
                await("Folder not listed") { main { !workspace.files.loading && workspace.files.entries.any { it.name == folder.name } } }
                folder.delete()
                click("文件夹 ${folder.name} 的操作"); click("在所选文件夹打开终端")
                await("Missing directory not reported") { main { workspace.files.notice?.contains("目录不存在") == true } }
                assertEquals(initial.size, main { workspace.sessions.size })
                assertSame(original, main { workspace.selected })
                await("Folder action dialog did not close") { visible("文件选项") }
                folder.mkdir()
                main { while (workspace.sessions.size < 8) workspace.create(); workspace.select(original); workspace.files.clearNotice() }
                click("文件夹 ${folder.name} 的操作"); click("在所选文件夹打开终端")
                await("Session limit not reported") { main { workspace.files.notice?.contains("8 个终端") == true } }
                assertEquals(8, main { workspace.sessions.size })
                assertSame(original, main { workspace.selected })
                assertTrue(main { runCatching { workspace.create(folder) }.isFailure })
            } finally {
                main {
                    workspace.sessions.toList().filter { it !in initial }.forEach(workspace::close)
                    workspace.select(original); workspace.files.clearNotice(); workspace.files.openLocal(workspace.runtime.home)
                }
                root.deleteRecursively()
            }
        }
    }

    @Test fun sharedFolderRequiresExplicitCopyAndOpensOnlyTheImportedDirectory() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val workspace = workspace(scenario)
            val initial = main { workspace.sessions.toList() }
            val original = main { workspace.selected!! }
            val uri = Uri.parse("content://dev.waddle.android.test.files/tree/root")
            lateinit var panels: dev.waddle.android.codex.CodexWorkspace
            scenario.onActivity { panels = it.codexWorkspace!! }
            val initialPanels = main { panels.sessions.toList() }
            val originalPanel = main { panels.selected!! }
            var imported: File? = null
            try {
                click("打开文件")
                main { workspace.files.clearNotice(); workspace.files.openGrant(GrantedFolder(uri, "项目 中文", false)) }
                await("Shared children not listed") { main { !workspace.files.loading && workspace.files.entries.any { it.directory } } }
                click("文件夹 子 目录 的操作"); click("复制所选文件夹")
                await("Copy confirmation missing") { visible("复制到 Waddle？") }
                click("取消")
                await("Copy confirmation did not close") { !visible("复制到 Waddle？") }
                assertFalse(main { workspace.files.importBusy })
                assertNull(main { workspace.files.imported })
                assertEquals(initial.size, main { workspace.sessions.size })
                click("复制到 Waddle…"); click("复制并打开")
                await("Import did not finish") { main { !workspace.files.importBusy && workspace.files.imported != null } }
                imported = main { workspace.files.imported!!.file }
                await("Copy not entered") { main { !workspace.files.loading && workspace.files.current?.local == true && workspace.files.current?.file?.canonicalFile == imported?.canonicalFile } }
                assertTrue(visible("Waddle 文件 · 副本"))
                assertFalse(visible("复制到 Waddle…"))
                click("在当前文件夹打开终端")
                await("Imported shell not ready") { main { workspace.selected !== original && workspace.selected?.promptReady == true } }
                assertEquals(imported!!.canonicalPath, main { workspace.selected?.cwd })
                assertEquals("你好，shared file\n", File(imported, "note '你好.txt").readText())
                click("打开文件"); click("在当前文件夹打开 Codex")
                await("Imported Codex not selected") { main { panels.selected !== originalPanel } }
                assertEquals(imported!!.canonicalFile, main { panels.selected!!.client.workingDirectory })
                val source = FileAccess(instrumentation.targetContext)
                assertEquals("你好，shared file\n", source.preview(source.document(android.provider.DocumentsContract.buildDocumentUriUsingTree(uri, "note"))).text)
            } finally {
                main {
                    panels.sessions.filter { it !in initialPanels }.toList().forEach(panels::close)
                    panels.select(originalPanel)
                    workspace.sessions.toList().filter { it !in initial }.forEach(workspace::close)
                    workspace.select(original); workspace.files.clearNotice(); workspace.files.openLocal(workspace.runtime.home)
                }
                imported?.parentFile?.deleteRecursively()
            }
        }
    }

    @Test fun fileFirstNavigationPreservesShellsAndCloseConfirmation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val workspace = workspace(scenario)
            val previous = main { workspace.selected }
            val session = main { workspace.create() }
            val fixture = File(workspace.runtime.home, "导航 空格-${System.nanoTime()}").apply { mkdirs() }
            try {
                await("Shell not ready") { main { session.promptReady } }
                main { session.terminal.write("printf '__NAV_KEEP__\\n'\r") }
                await("Output missing") { main { session.terminal.emulator.screen.transcriptText.contains("__NAV_KEEP__") } }
                val cwd = main { session.cwd }
                val handle = session.terminal.mHandle
                click("打开文件")
                main { workspace.files.openLocal(fixture) }
                await("Directory missing") { main { !workspace.files.loading && workspace.files.current?.file == fixture.canonicalFile } }
                assertEquals(cwd, main { session.cwd })
                swipeOpenDrawer()
                val texts = nodes().mapNotNull { it.text?.toString() }
                assertTrue(texts.indexOf("文件") < texts.indexOf("会话"))
                closeDrawer()
                assertEquals(fixture.canonicalFile, main { workspace.files.current?.file })
                click("更多"); click("切换当前终端到此目录")
                await("Explicit cd did not finish") { main { session.promptReady && session.cwd == fixture.canonicalPath } }
                assertEquals(handle, main { workspace.selected!!.terminal.mHandle })
                openDrawer(); click("＋ 新建终端")
                await("Second shell missing") { main { workspace.selected !== session } }
                val second = main { workspace.selected!! }
                openDrawer(); click(session.label)
                assertSame(session, main { workspace.selected })
                assertTrue(main { session.terminal.emulator.screen.transcriptText.contains("__NAV_KEEP__") })
                openDrawer(); click("关闭 ${second.label}"); click("保留")
                assertTrue(main { second.running && workspace.sessions.contains(second) })
                click("关闭 ${second.label}"); click("关闭终端")
                assertFalse(main { workspace.sessions.contains(second) })
                assertTrue(main { session.running })
                await("Close confirmation still visible") { !visible("关闭终端") && visible("会话") }
                closeDrawer()
            } finally {
                main { workspace.close(session); previous?.let(workspace::select); workspace.files.openLocal(workspace.runtime.home) }
                fixture.deleteRecursively()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun offlineFlag(client: CodexController, name: String, value: Boolean) {
        // Only expose the signed-in UI for draft tests; never create RPC or read/copy credentials.
        val field = CodexController::class.java.getDeclaredField(name + "\$delegate").apply { isAccessible = true }
        (field.get(client) as MutableState<Boolean>).value = value
    }

    @Test fun codexDraftAndFileSelectionSurviveNavigationAndActivityRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val workspace = workspace(scenario)
            lateinit var client: CodexController
            var panelLabel = ""
            scenario.onActivity { client = it.codex!!; panelLabel = it.codexWorkspace!!.selected!!.label }
            val fixture = File(workspace.runtime.home, "draft-nav-${System.nanoTime()}").apply { mkdirs() }
            val originalMessageCount = main { client.messages.size }
            val auth = File(client.runtime.config, "auth.json")
            val originalAuth = if (auth.exists()) auth.readBytes() else null
            try {
                main { offlineFlag(client, "connected", true); offlineFlag(client, "signedIn", true) }
                click("打开文件")
                main { workspace.files.openLocal(fixture) }
                await("Directory missing") { main { !workspace.files.loading && workspace.files.current?.file == fixture.canonicalFile } }
                click("更多"); click("交给 Codex")
                await("Selected file missing") { visible("已选择本地文件或目录 · 尚未发送") && visible(fixture.canonicalPath) }
                await("Draft field missing") { nodes().any { it.isEditable } }
                val field = nodes().first { it.isEditable }
                assertTrue(field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "保留这段未发送的草稿")
                }))
                click("打开文件")
                assertEquals(fixture.canonicalFile, main { workspace.files.current?.file })
                openDrawer(); click(panelLabel)
                await("Draft lost on switch") { visible("保留这段未发送的草稿") }
                scenario.recreate()
                workspace(scenario)
                await("Draft lost on recreation") { visible("保留这段未发送的草稿") && visible(fixture.canonicalPath) }
                assertEquals(originalMessageCount, main { client.messages.size })
                assertFalse(main { client.busy })
                if (originalAuth == null) assertFalse(auth.exists()) else assertArrayEquals(originalAuth, auth.readBytes())
                click("移除选择")
                await("Selected file not removed") { !visible("已选择本地文件或目录 · 尚未发送") }
                click("打开文件"); scenario.recreate(); workspace(scenario)
                await("File page lost on recreation") { visible(fixture.name) }
            } finally {
                main { offlineFlag(client, "connected", false); offlineFlag(client, "signedIn", false); workspace.files.openLocal(workspace.runtime.home) }
                fixture.deleteRecursively()
            }
        }
    }

    @Test fun settingsAndHelpAreReachableWithoutConnectingCodex() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            workspace(scenario)
            lateinit var client: CodexController
            scenario.onActivity { client = it.codex!! }
            openDrawer(); click("设置与运行环境")
            await("Settings missing") { visible("读取手机公开设置") }
            click("完成")
            openDrawer(); click("帮助")
            await("Help missing") { visible("使用 Waddle") }
            click("知道了")
            assertFalse(main { client.connected || client.connecting })
        }
    }
}
