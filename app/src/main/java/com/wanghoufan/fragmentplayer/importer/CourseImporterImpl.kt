package com.wanghoufan.fragmentplayer.importer

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.wanghoufan.fragmentplayer.R
import com.wanghoufan.fragmentplayer.core.importer.CourseImporterApi
import com.wanghoufan.fragmentplayer.core.importer.ImportResult
import com.wanghoufan.fragmentplayer.core.importer.ScanProgress
import com.wanghoufan.fragmentplayer.core.model.Course
import com.wanghoufan.fragmentplayer.core.model.CourseStatus
import com.wanghoufan.fragmentplayer.core.repo.CheckpointRepository
import com.wanghoufan.fragmentplayer.core.repo.CourseRepository
import com.wanghoufan.fragmentplayer.core.repo.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

class CourseImporterImpl(
    context: Context,
    private val courses: CourseRepository,
    private val checkpoints: CheckpointRepository,
    private val settings: SettingsRepository
) : CourseImporterApi {
    private val appContext = context.applicationContext
    private val scanMutex = Mutex()

    private val _scanning = MutableStateFlow(false)
    override val scanning: StateFlow<Boolean> = _scanning

    private val _progress = MutableStateFlow(ScanProgress())
    override val progress: StateFlow<ScanProgress> = _progress

    override suspend fun importFolder(treeUri: String): ImportResult =
        scan(treeUri, persistFolder = true)

    override suspend fun rescan(): ImportResult {
        val folder = settings.currentFolderUri()
        if (folder.isNullOrBlank()) {
            return ImportResult(
                ok = false,
                message = appContext.getString(R.string.data_err_no_folder)
            )
        }
        return scan(folder, persistFolder = false)
    }

    override suspend fun relink(treeUri: String): ImportResult =
        scan(treeUri, persistFolder = true)

    override suspend fun currentFolderUri(): String? = settings.currentFolderUri()

    private suspend fun scan(treeUri: String, persistFolder: Boolean): ImportResult {
        if (!scanMutex.tryLock()) {
            return ImportResult(
                ok = false,
                message = appContext.getString(R.string.data_scan_in_progress)
            )
        }
        _scanning.value = true
        _progress.value = ScanProgress()
        try {
            return withContext(Dispatchers.IO) { runScan(treeUri, persistFolder) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            return ImportResult(
                ok = false,
                message = appContext.getString(R.string.data_err_scan_failed)
            )
        } finally {
            _scanning.value = false
            scanMutex.unlock()
        }
    }

    private suspend fun runScan(treeUri: String, persistFolder: Boolean): ImportResult {
        val tree = Uri.parse(treeUri)
        var permissionWarning = false
        try {
            appContext.contentResolver.takePersistableUriPermission(
                tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            permissionWarning = true
        } catch (_: IllegalArgumentException) {
            permissionWarning = true
        }

        val root = DocumentFile.fromTreeUri(appContext, tree)
            ?: return ImportResult(
                ok = false,
                message = appContext.getString(R.string.data_err_folder_unreadable)
            )

        val collected = ArrayList<ScannedFile>()
        val collectionComplete = collectMediaFiles(root, "", collected)
        if (collected.isEmpty()) {
            val messageRes = if (permissionWarning || !collectionComplete) {
                R.string.data_err_folder_unreadable
            } else {
                R.string.data_err_no_media
            }
            return ImportResult(
                ok = false,
                message = appContext.getString(messageRes)
            )
        }
        // 任一目录读失败：本次扫描是 partial 结果，不得据此标记缺失或降级已有状态。
        val partial = !collectionComplete

        val total = collected.size
        _progress.value = ScanProgress(scanned = 0, total = total, fileName = "")
        val sortedFiles = collected.sortedWith(
            compareBy(NaturalSort) { file: ScannedFile -> file.relativePath }
        )

        val existing = courses.all()
        val byUri = existing.associateBy { it.uri }
        val byFingerprint = existing.associateBy { it.fingerprint }
        val usedIds = HashSet<Long>()
        val rows = ArrayList<Course>(sortedFiles.size + existing.size)
        val seenFingerprints = HashSet<String>()

        var added = 0
        var updated = 0
        var unsupported = 0
        var duplicates = 0

        for ((index, file) in sortedFiles.withIndex()) {
            _progress.value = ScanProgress(
                scanned = index,
                total = total,
                fileName = file.displayName
            )
            if (!seenFingerprints.add(file.fingerprint)) {
                duplicates++
                unsupported++
                _progress.value = ScanProgress(
                    scanned = index + 1,
                    total = total,
                    fileName = file.displayName
                )
                continue
            }

            val match = byUri[file.uri]?.takeIf { it.id !in usedIds }
                ?: byFingerprint[file.fingerprint]?.takeIf { it.id !in usedIds }
            if (match != null) usedIds.add(match.id)

            val probe = MediaProbe.probe(appContext, Uri.parse(file.uri), file.displayName)
            val base = match?.copy(
                uri = file.uri,
                displayName = file.displayName,
                relativePath = file.relativePath,
                sizeBytes = file.sizeBytes,
                lastModified = file.lastModified,
                fingerprint = file.fingerprint
            ) ?: Course(
                uri = file.uri,
                displayName = file.displayName,
                relativePath = file.relativePath,
                durationMs = probe.durationMs,
                type = probe.type,
                hasAudio = probe.hasAudio,
                sizeBytes = file.sizeBytes,
                lastModified = file.lastModified,
                fingerprint = file.fingerprint,
                status = CourseStatus.READY,
                sortIndex = 0
            )

            val keepStatus = match != null && !probe.ok &&
                (partial || probe.failure == ProbeFailure.IO || probe.failure == ProbeFailure.PERMISSION)

            val row = base.copy(
                durationMs = if (probe.durationMs > 0L) probe.durationMs else base.durationMs,
                type = if (probe.ok || !keepStatus) probe.type else base.type,
                hasAudio = if (probe.ok || !keepStatus) probe.hasAudio else base.hasAudio,
                status = when {
                    probe.ok -> CourseStatus.READY
                    match == null -> CourseStatus.UNSUPPORTED
                    // 已有课程：本次读取失败（/部分目录未读到）不降级，保留旧状态，只更新 note。
                    keepStatus -> base.status
                    else -> CourseStatus.UNSUPPORTED
                },
                note = probe.note ?: if (keepStatus) base.note else null
            )

            if (match == null) {
                val newId = courses.insert(row)
                rows.add(row.copy(id = newId))
                added++
            } else {
                rows.add(row.copy(id = match.id))
                updated++
            }
            if (row.status == CourseStatus.UNSUPPORTED) unsupported++

            _progress.value = ScanProgress(
                scanned = index + 1,
                total = total,
                fileName = file.displayName
            )
        }

        val missingNote = appContext.getString(R.string.data_note_missing)
        val missingRows = if (partial) {
            // 部分目录未读取：未见到的课程可能仍存在，不做缺失标记，保留旧 status 与旧 note。
            emptyList()
        } else {
            existing.filter { it.id !in usedIds }
        }
        missingRows.forEach { rows.add(it.copy(status = CourseStatus.MISSING, note = missingNote)) }

        val ordered = rows
            .sortedWith(compareBy(NaturalSort) { course: Course -> course.relativePath })
            .mapIndexed { index, course -> course.copy(sortIndex = index) }
        courses.updateAll(ordered)

        if (persistFolder) settings.setFolderUri(treeUri)

        var message = appContext.getString(
            R.string.data_import_added_updated,
            added,
            updated
        )
        if (missingRows.isNotEmpty()) {
            message += appContext.getString(R.string.data_import_missing_part, missingRows.size)
        }
        val unplayable = unsupported - duplicates
        if (unplayable > 0) {
            message += appContext.getString(R.string.data_import_unsupported_part, unplayable)
        }
        if (duplicates > 0) {
            message += appContext.getString(R.string.data_import_duplicate_part, duplicates)
        }
        if (permissionWarning && persistFolder) {
            message += appContext.getString(R.string.data_import_permission_part)
        }
        if (partial) {
            message += appContext.getString(R.string.data_import_partial_part)
        }

        return ImportResult(
            ok = true,
            added = added,
            updated = updated,
            missing = missingRows.size,
            unsupported = unsupported,
            total = total,
            message = message
        )
    }

    private fun collectMediaFiles(dir: DocumentFile, prefix: String, out: MutableList<ScannedFile>): Boolean {
        // 读取失败返回 false（继续收集其余可读内容），让上层把本次扫描视为 partial。
        val children = try {
            dir.listFiles()
        } catch (_: Exception) {
            return false
        }
        var complete = true
        for (child in children) {
            val name = child.name ?: continue
            if (name.startsWith(".")) continue
            if (child.isDirectory) {
                if (!collectMediaFiles(child, "$prefix$name/", out)) complete = false
                continue
            }
            if (!child.isFile) continue
            if (!MediaProbe.isMediaFile(name)) continue

            val relativePath = "$prefix$name"
            val sizeBytes = child.length()
            val lastModified = child.lastModified()
            out.add(
                ScannedFile(
                    uri = child.uri.toString(),
                    displayName = name,
                    relativePath = relativePath,
                    sizeBytes = sizeBytes,
                    lastModified = lastModified,
                    fingerprint = Fingerprint.compute(relativePath, sizeBytes, lastModified, name)
                )
            )
        }
        return complete
    }

    private data class ScannedFile(
        val uri: String,
        val displayName: String,
        val relativePath: String,
        val sizeBytes: Long,
        val lastModified: Long,
        val fingerprint: String
    )
}