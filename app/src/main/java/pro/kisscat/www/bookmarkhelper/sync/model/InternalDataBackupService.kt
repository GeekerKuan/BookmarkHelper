package pro.kisscat.www.bookmarkhelper.sync.model

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/** Portable, user-selected backup and restore for the app-private managed data store. */
object InternalDataBackupService {
    private const val MAX_BACKUP_BYTES = 128L * 1024L * 1024L
    private const val CURRENT_DATABASE_VERSION = 2
    private val REQUIRED_TABLES = setOf(
        "datasets",
        "records",
        "dataset_records",
        "record_sources",
        "bookmark_folders",
    )

    @Synchronized
    @Throws(IOException::class)
    fun backup(context: Context, destination: Uri) {
        val appContext = context.applicationContext
        IntermediateDataRepository.withClosedDatabaseFile { databaseFile ->
            if (!databaseFile.isFile) throw IOException("数据管理数据库不存在")
            if (databaseFile.length() > MAX_BACKUP_BYTES) throw IOException("数据备份超过安全大小上限")
            val output = appContext.contentResolver.openOutputStream(destination, "w")
                ?: throw IOException("无法打开备份目标文件")
            output.use { target ->
                FileInputStream(databaseFile).use { source -> source.copyTo(target, 128 * 1024) }
                target.flush()
            }
        }
    }

    @Synchronized
    @Throws(IOException::class)
    fun restore(context: Context, source: Uri) {
        val appContext = context.applicationContext
        val incoming = File(appContext.cacheDir, "browser-data-restore-${System.nanoTime()}.sqlite")
        try {
            copyIncoming(appContext, source, incoming)
            validateDatabase(incoming)
            var rollback: File? = null
            IntermediateDataRepository.withClosedDatabaseFile { databaseFile ->
                val directory = databaseFile.parentFile
                    ?: throw IOException("数据管理目录不存在")
                val staged = File(directory, "${databaseFile.name}.incoming")
                rollback = File(directory, "${databaseFile.name}.before-restore")
                deleteIfPresent(staged)
                deleteIfPresent(requireNotNull(rollback))
                copyAndSync(incoming, staged)
                validateDatabase(staged)

                if (databaseFile.isFile && !databaseFile.renameTo(requireNotNull(rollback))) {
                    deleteIfPresent(staged)
                    throw IOException("无法创建恢复前回滚副本")
                }
                if (!staged.renameTo(databaseFile)) {
                    requireNotNull(rollback).renameTo(databaseFile)
                    throw IOException("无法原子替换数据管理数据库")
                }
                deleteIfPresent(File(databaseFile.absolutePath + "-wal"))
                deleteIfPresent(File(databaseFile.absolutePath + "-shm"))
                deleteIfPresent(File(databaseFile.absolutePath + "-journal"))
            }
            rollback?.let(::deleteIfPresent)
        } finally {
            deleteIfPresent(incoming)
        }
    }

    private fun copyIncoming(context: Context, source: Uri, destination: File) {
        val input = context.contentResolver.openInputStream(source)
            ?: throw IOException("无法读取备份文件")
        input.use { stream ->
            FileOutputStream(destination, false).use { output ->
                val buffer = ByteArray(128 * 1024)
                var total = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_BACKUP_BYTES) throw IOException("备份文件超过安全大小上限")
                    output.write(buffer, 0, read)
                }
                output.fd.sync()
            }
        }
    }

    private fun copyAndSync(source: File, destination: File) {
        FileInputStream(source).use { input ->
            FileOutputStream(destination, false).use { output ->
                input.copyTo(output, 128 * 1024)
                output.fd.sync()
            }
        }
    }

    private fun validateDatabase(file: File) {
        if (!file.isFile || file.length() < 100L) throw IOException("备份文件为空或格式无效")
        val database = try {
            SQLiteDatabase.openDatabase(file.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        } catch (error: RuntimeException) {
            throw IOException("备份文件不是有效的数据管理数据库", error)
        }
        try {
            val version = database.rawQuery("PRAGMA user_version", null).use { cursor ->
                if (!cursor.moveToFirst()) -1 else cursor.getInt(0)
            }
            if (version !in 1..CURRENT_DATABASE_VERSION) {
                throw IOException("备份数据库版本不受支持：$version")
            }
            val tables = database.rawQuery(
                "SELECT name FROM sqlite_master WHERE type='table'", null,
            ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
            if (!tables.containsAll(REQUIRED_TABLES)) throw IOException("备份缺少必要的数据表")
            val integrity = database.rawQuery("PRAGMA quick_check", null).use { cursor ->
                cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
            }
            if (!integrity) throw IOException("备份数据库完整性检查失败")
        } finally {
            database.close()
        }
    }

    private fun deleteIfPresent(file: File) {
        if (file.exists() && !file.delete()) file.deleteOnExit()
    }
}
