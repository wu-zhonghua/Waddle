package dev.waddle.android.files

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File

data class StorageLocation(val label: String, val root: File, val volumeId: String)

/** Real storage volumes only. SAF/cloud URIs are not arbitrary filesystem paths. */
class PhoneStorage(private val context: Context) {
    val supported get() = Build.VERSION.SDK_INT >= 30
    fun allowed() = Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager()
    fun locations(): List<StorageLocation> {
        if (Build.VERSION.SDK_INT < 30) return emptyList()
        return context.getSystemService(StorageManager::class.java).storageVolumes.mapNotNull { volume ->
            val root = volume.directory ?: return@mapNotNull null
            if (volume.state !in setOf(Environment.MEDIA_MOUNTED, Environment.MEDIA_MOUNTED_READ_ONLY)) return@mapNotNull null
            StorageLocation(if (volume.isPrimary) "内部存储" else volume.getDescription(context), root,
                if (volume.isPrimary) "primary" else volume.uuid ?: return@mapNotNull null)
        }
    }

    fun requireAccess(file: File) {
        val path = file.canonicalFile.toPath()
        if (path.startsWith(context.dataDir.canonicalFile.toPath()) || !supported || allowed()) return
        val own = (context.getExternalFilesDirs(null).toList() + context.externalCacheDirs.toList()).filterNotNull()
        if (own.any { path.startsWith(it.canonicalFile.toPath()) }) return
        if (locations().any { path.startsWith(it.root.canonicalFile.toPath()) }) {
            throw SecurityException("请先从“位置 → 手机存储”开启所有文件访问权限。不会自动创建副本。")
        }
    }

    fun resolveDocument(documentId: String): File? {
        if (!allowed()) return null
        val separator = documentId.indexOf(':')
        if (separator < 1) return null
        val volume = locations().firstOrNull { it.volumeId.equals(documentId.substring(0, separator), ignoreCase = true) } ?: return null
        return checkedChild(volume.root, documentId.substring(separator + 1))
    }

    companion object {
        internal fun checkedChild(root: File, relative: String): File? {
            if (relative.startsWith('/') || relative.split('/').any { it == ".." || it == "." } || relative.any { it == '\u0000' || it == '\\' }) return null
            val base = root.canonicalFile
            val target = File(base, relative).canonicalFile
            if (!target.toPath().startsWith(base.toPath())) return null
            return target.takeIf { it.isDirectory && it.canRead() && it.canExecute() }
        }
    }
}
