package com.wanghoufan.fragmentplayer.data.repo

import com.wanghoufan.fragmentplayer.core.model.TimerSessionRecord
import com.wanghoufan.fragmentplayer.core.repo.SessionRepository
import com.wanghoufan.fragmentplayer.data.db.AppDatabase

class SessionRepositoryImpl(private val db: AppDatabase) : SessionRepository {
    private val dao = db.sessionDao()

    override suspend fun record(record: TimerSessionRecord): Long =
        dao.insert(record.toEntity())

    override suspend fun recent(limit: Int): List<TimerSessionRecord> =
        dao.recent(limit.coerceAtLeast(1)).map { it.toModel() }
}