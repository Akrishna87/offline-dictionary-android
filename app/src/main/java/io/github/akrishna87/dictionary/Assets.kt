package io.github.akrishna87.dictionary

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.pm.PackageInfoCompat
import java.io.File

/**
 * Opens a read-only SQLite database that ships in the APK's assets. SQLite can't read straight
 * from assets, so it is copied to private storage on first launch, and again whenever the app
 * is updated (the data may have changed with it).
 */
internal fun openAssetDatabase(context: Context, name: String): SQLiteDatabase {
    val file = context.getDatabasePath(name)
    val version = PackageInfoCompat.getLongVersionCode(
        context.packageManager.getPackageInfo(context.packageName, 0),
    )
    val stamp = File(file.parentFile, "$name.version")
    if (!file.exists() || stamp.takeIf { it.exists() }?.readText() != version.toString()) {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "$name.part")
        context.assets.open(name).use { input -> tmp.outputStream().use { input.copyTo(it) } }
        tmp.renameTo(file)
        stamp.writeText(version.toString())
    }
    return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
}
