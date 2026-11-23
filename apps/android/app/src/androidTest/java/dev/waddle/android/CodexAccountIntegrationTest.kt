package dev.waddle.android

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import dev.waddle.android.codex.CodexController
import dev.waddle.android.terminal.WaddleRuntime
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class CodexAccountIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun <T> main(block: () -> T): T { val value = AtomicReference<T>(); instrumentation.runOnMainSync { value.set(block()) }; return value.get() }
    private fun await(check: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < end) { if (main(check)) return; SystemClock.sleep(100) }
        fail("Account operation timed out")
    }
    @Test fun keyLoginAndLogoutAreSharedWithCliInAnIsolatedHome() {
        assumeTrue(BuildConfig.BUNDLED_AGENTS)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val runtime = WaddleRuntime(context).apply { prepare() }
        val isolated = File(context.cacheDir, "account-fixture-${System.nanoTime()}").apply { mkdirs() }
        val namespace = "test_account_${System.nanoTime()}"
        val processes = mutableListOf<Process>()
        fun builder(vararg args: String) = runtime.command(runtime.executable("codex"), *args).apply {
            environment()["CODEX_HOME"] = isolated.path
        }
        fun controller(key: String) = main { CodexController(context, preferenceName = key, startProcess = {
            builder("app-server", "--listen", "stdio://").start().also { processes += it }
        }, credentialState = { File(isolated, "auth.json").let { "${it.lastModified()}:${it.length()}" } }) }
        val account = controller(namespace)
        val existingChat = controller(namespace + "_chat")
        try {
            main { account.connect(); existingChat.connect() }
            await { (account.connected && existingChat.connected) || account.error != null || existingChat.error != null }
            assertNull(main { account.error }); assertFalse(main { account.signedIn })
            main { account.loginWithKey("sk-waddle-isolated-fixture-not-a-real-key") }
            await { account.signedIn || account.error != null }
            assertNull(main { account.error }); assertEquals("apiKey", main { account.accountType })
            main { existingChat.refreshAccount() }
            await { existingChat.signedIn || existingChat.error != null }
            assertNull(main { existingChat.error }); assertTrue(main { existingChat.signedIn })
            val cli = builder("login", "status").redirectOutput(ProcessBuilder.Redirect.to(File("/dev/null"))).redirectError(ProcessBuilder.Redirect.to(File("/dev/null"))).start()
            assertTrue(cli.waitFor(20, java.util.concurrent.TimeUnit.SECONDS)); assertEquals(0, cli.exitValue())
            main { account.logout() }
            await { !account.signedIn }
            assertFalse(File(isolated, "auth.json").exists())
            main { existingChat.refreshAccount() }
            await { existingChat.connected && !existingChat.signedIn }
            assertNull(main { existingChat.error })
        } finally {
            main { account.disconnect(); existingChat.disconnect() }; processes.forEach { it.destroy(); it.waitFor(5, java.util.concurrent.TimeUnit.SECONDS) }
            isolated.deleteRecursively(); context.getSharedPreferences(namespace, 0).edit().clear().commit()
            context.getSharedPreferences(namespace + "_chat", 0).edit().clear().commit()
        }
    }
}
