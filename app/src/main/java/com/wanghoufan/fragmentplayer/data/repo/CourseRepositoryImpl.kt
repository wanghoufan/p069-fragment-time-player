package com.wanghoufan.fragmentplayer.data.repo

import com.wanghoufan.fragmentplayer.core.model.Course
import com.wanghoufan.fragmentplayer.core.repo.CourseRepository
import com.wanghoufan.fragmentplayer.data.db.AppDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CourseRepositoryImpl(private val db: AppDatabase) : CourseRepository {
    private val dao = db.courseDao()

    override val courses: Flow<List<Course>> = dao.observeAll().map { rows ->
        rows.map { it.toModel() }
    }

    override suspend fun all(): List<Course> = dao.all().map { it.toModel() }

    override suspend fun get(id: Long): Course? = dao.get(id)?.toModel()

    override suspend fun insert(course: Course): Long = dao.insert(course.copy(id = 0L).toEntity())

    override suspend fun update(course: Course) {
        dao.update(course.toEntity())
    }

    override suspend fun updateAll(courses: List<Course>) {
        if (courses.isEmpty()) return
        dao.updateAll(courses.map { it.toEntity() })
    }

    override suspend fun delete(ids: List<Long>) {
        if (ids.isEmpty()) return
        dao.deleteByIds(ids)
    }

    override suspend fun count(): Int = dao.count()
}