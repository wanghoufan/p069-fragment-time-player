package com.wanghoufan.fragmentplayer.data.repo

import androidx.room.withTransaction
import com.wanghoufan.fragmentplayer.core.model.Checkpoint
import com.wanghoufan.fragmentplayer.core.repo.CheckpointRepository
import com.wanghoufan.fragmentplayer.data.db.AppDatabase
import com.wanghoufan.fragmentplayer.data.db.CheckpointEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CheckpointRepositoryImpl(private val db: AppDatabase) : CheckpointRepository {
    private val dao = db.checkpointDao()

    override val checkpoint: Flow<Checkpoint?> = dao.observe().map { it?.toModel() }

    override suspend fun get(): Checkpoint? = dao.get()?.toModel()

    override suspend fun save(courseId: Long, positionMs: Long) {
        db.withTransaction {
            dao.clear()
            dao.upsert(
                CheckpointEntity(
                    courseId = courseId,
                    positionMs = positionMs.coerceAtLeast(0L),
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    override suspend fun clear() {
        dao.clear()
    }
}