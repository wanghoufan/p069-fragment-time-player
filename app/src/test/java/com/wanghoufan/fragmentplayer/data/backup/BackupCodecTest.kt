package com.wanghoufan.fragmentplayer.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCodecTest {

    private fun sample(): BackupFile = BackupFile(
        version = BackupCodec.VERSION,
        exportedAt = 1_700_000_000_000L,
        settings = BackupSettingsDto(
            theme = "DARK",
            fontScale = 1.25f,
            vibration = false,
            alertSound = true,
            folderUri = "content://tree/primary%3AMovies%2FFragmentLearning"
        ),
        presets = listOf(
            BackupPresetDto(id = 1L, name = "刷牙", durationSec = 120, sortOrder = 0),
            BackupPresetDto(id = 2L, name = "滴眼药水", durationSec = 180, sortOrder = 1)
        ),
        courses = listOf(
            BackupCourseDto(
                id = 5L,
                uri = "content://doc/5",
                displayName = "第2节.mp4",
                relativePath = "第2章/第2节.mp4",
                durationMs = 120_000L,
                type = "VIDEO",
                hasAudio = true,
                sizeBytes = 2048L,
                lastModified = 1_700_000_000_000L,
                fingerprint = "abc123",
                status = "READY",
                sortIndex = 0,
                note = null
            )
        ),
        checkpoint = BackupCheckpointDto(courseId = 5L, positionMs = 1000L, updatedAt = 1L),
        sessions = listOf(
            BackupSessionDto(
                id = 1L,
                presetId = 1L,
                presetName = "刷牙",
                targetSec = 120,
                listenedSec = 118L,
                completed = true,
                startedAt = 1L,
                endedAt = 2L
            )
        )
    )

    @Test
    fun dtoRoundTripKeepsAllFields() {
        val decoded = BackupCodec.decode(BackupCodec.encode(sample()))
        assertEquals(sample(), decoded)
        assertEquals(2, decoded.presets.size)
        assertEquals("content://doc/5", decoded.courses.single().uri)
        assertEquals(5L, decoded.checkpoint?.courseId)
        assertEquals(1, decoded.sessions.size)
        assertEquals(1, BackupCodec.summary(decoded).courses)
    }

    @Test
    fun encodedJsonDeclaresVersionOne() {
        val text = BackupCodec.encode(sample())
        assertTrue(text.contains("\"version\": 1"))
    }

    @Test
    fun rejectsUnsupportedVersion() {
        val text = BackupCodec.encode(sample().copy(version = 2))
        val error = assertThrows(BackupFormatException::class.java) {
            BackupCodec.decode(text)
        }
        assertTrue(error.message.orEmpty().contains("版本"))
    }

    @Test
    fun rejectsEmptyCourseUri() {
        val course = sample().courses.single().copy(uri = "")
        val text = BackupCodec.encode(sample().copy(courses = listOf(course)))
        val error = assertThrows(BackupFormatException::class.java) {
            BackupCodec.decode(text)
        }
        assertTrue(error.message.orEmpty().contains("课程"))
    }

    @Test
    fun rejectsBrokenJson() {
        assertThrows(BackupFormatException::class.java) {
            BackupCodec.decode("{ this is not a backup }")
        }
    }

    @Test
    fun legacyBackupWithoutLanguageDefaultsToFollowSystem() {
        val legacy = """
            {
              "version": 1,
              "exportedAt": 0,
              "settings": {
                "theme": "SYSTEM",
                "fontScale": 1.15,
                "vibration": true,
                "alertSound": true,
                "folderUri": null
              },
              "presets": [],
              "courses": [],
              "checkpoint": null,
              "sessions": []
            }
        """.trimIndent()
        val decoded = BackupCodec.decode(legacy)
        assertEquals("FOLLOW_SYSTEM", decoded.settings.language)
    }

    @Test
    fun invalidLanguageFallsBackToFollowSystem() {
        val text = BackupCodec.encode(sample())
            .replace("\"language\": \"FOLLOW_SYSTEM\"", "\"language\": \"FRENCH\"")
        val decoded = BackupCodec.decode(text)
        assertEquals("FOLLOW_SYSTEM", decoded.settings.language)
    }

    @Test
    fun encodedJsonKeepsLanguageField() {
        val decoded = BackupCodec.decode(BackupCodec.encode(sample()))
        assertEquals("FOLLOW_SYSTEM", decoded.settings.language)
    }
}