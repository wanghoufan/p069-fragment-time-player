package com.wanghoufan.fragmentplayer.core.importer

import kotlinx.coroutines.flow.StateFlow

data class ScanProgress(
    val scanned: Int = 0,
    val total: Int = 0,
    val fileName: String = ""
)

data class ImportResult(
    val ok: Boolean,
    val added: Int = 0,
    val updated: Int = 0,
    val missing: Int = 0,
    val unsupported: Int = 0,
    val total: Int = 0,
    val message: String? = null
)

interface CourseImporterApi {
    val scanning: StateFlow<Boolean>
    val progress: StateFlow<ScanProgress>

    suspend fun importFolder(treeUri: String): ImportResult

    suspend fun rescan(): ImportResult

    suspend fun relink(treeUri: String): ImportResult

    suspend fun currentFolderUri(): String?
}