package com.wanghoufan.fragmentplayer.data.repo

import androidx.room.withTransaction
import com.wanghoufan.fragmentplayer.core.model.Preset
import com.wanghoufan.fragmentplayer.core.repo.PresetRepository
import com.wanghoufan.fragmentplayer.data.db.AppDatabase
import com.wanghoufan.fragmentplayer.data.db.PresetEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PresetRepositoryImpl(private val db: AppDatabase) : PresetRepository {
    private val dao = db.presetDao()

    override val presets: Flow<List<Preset>> = dao.observeAll().map { rows ->
        rows.map { it.toModel() }
    }

    override suspend fun ensureDefaults() {
        if (dao.count() > 0) return
        db.withTransaction {
            if (dao.count() == 0) {
                dao.insertAll(
                    listOf(
                        PresetEntity(name = "刷牙", durationSec = 120, sortOrder = 0),
                        PresetEntity(name = "滴眼药水", durationSec = 180, sortOrder = 1)
                    )
                )
            }
        }
    }

    override suspend fun all(): List<Preset> = dao.all().map { it.toModel() }

    override suspend fun get(id: Long): Preset? = dao.get(id)?.toModel()

    override suspend fun add(name: String, durationSec: Int): Long =
        dao.insert(
            PresetEntity(
                name = name,
                durationSec = durationSec,
                sortOrder = dao.maxSortOrder() + 1
            )
        )

    override suspend fun update(preset: Preset) {
        dao.update(preset.toEntity())
    }

    override suspend fun delete(id: Long) {
        dao.deleteById(id)
    }
}