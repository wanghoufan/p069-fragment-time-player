package com.wanghoufan.fragmentplayer.importer

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.wanghoufan.fragmentplayer.R
import com.wanghoufan.fragmentplayer.core.model.MediaType
import java.io.IOException
import java.util.Locale

/**
 * 探测失败类型：用于区分“这次读不到（权限/IO）”与“文件本身不支持（格式）”，
 * 前者不应把已有课程降级成 UNSUPPORTED。
 */
enum class ProbeFailure { NONE, IO, PERMISSION, FORMAT }

data class ProbeResult(
    val durationMs: Long,
    val hasAudio: Boolean,
    val type: MediaType,
    val ok: Boolean,
    val note: String? = null,
    val failure: ProbeFailure = ProbeFailure.NONE
)

object MediaProbe {
    private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "mov", "mkv", "webm", "3gp")
    private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "wav", "ogg", "flac")

    fun isMediaFile(name: String): Boolean {
        val extension = extensionOf(name)
        return extension in VIDEO_EXTENSIONS || extension in AUDIO_EXTENSIONS
    }

    fun typeOf(name: String): MediaType =
        if (extensionOf(name) in VIDEO_EXTENSIONS) MediaType.VIDEO else MediaType.AUDIO

    fun probe(context: Context, uri: Uri, displayName: String? = null): ProbeResult {
        val name = displayName ?: uri.lastPathSegment.orEmpty()
        val type = typeOf(name)
        val retriever = MediaMetadataRetriever()
        val extractor = MediaExtractor()

        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?: 0L

            extractor.setDataSource(context, uri, null)
            val trackCount = extractor.trackCount
            var hasAudio = false
            for (index in 0 until trackCount) {
                val mime = extractor.getTrackFormat(index).getString(MediaFormat.KEY_MIME)
                    ?: continue
                if (mime.startsWith("audio/")) {
                    hasAudio = true
                    break
                }
            }

            if (trackCount == 0) {
                return ProbeResult(
                    durationMs = durationMs,
                    hasAudio = false,
                    type = type,
                    ok = false,
                    note = context.getString(R.string.data_probe_no_track),
                    failure = ProbeFailure.FORMAT
                )
            }
            if (type == MediaType.AUDIO && !hasAudio) {
                return ProbeResult(
                    durationMs = durationMs,
                    hasAudio = false,
                    type = type,
                    ok = false,
                    note = context.getString(R.string.data_probe_audio_silent),
                    failure = ProbeFailure.FORMAT
                )
            }
            val note = if (type == MediaType.VIDEO && !hasAudio) {
                context.getString(R.string.data_probe_video_no_audio)
            } else {
                null
            }
            return ProbeResult(
                durationMs = durationMs,
                hasAudio = hasAudio,
                type = type,
                ok = true,
                note = note,
                failure = ProbeFailure.NONE
            )
        } catch (_: SecurityException) {
            return ProbeResult(
                durationMs = 0L,
                hasAudio = false,
                type = type,
                ok = false,
                note = context.getString(R.string.data_probe_no_permission),
                failure = ProbeFailure.PERMISSION
            )
        } catch (_: IOException) {
            return ProbeResult(
                durationMs = 0L,
                hasAudio = false,
                type = type,
                ok = false,
                note = context.getString(R.string.data_probe_unreadable),
                failure = ProbeFailure.IO
            )
        } catch (_: IllegalArgumentException) {
            return ProbeResult(
                durationMs = 0L,
                hasAudio = false,
                type = type,
                ok = false,
                note = context.getString(R.string.data_probe_unreadable),
                failure = ProbeFailure.FORMAT
            )
        } catch (_: UnsupportedOperationException) {
            return ProbeResult(
                durationMs = 0L,
                hasAudio = false,
                type = type,
                ok = false,
                note = context.getString(R.string.data_probe_unreadable),
                failure = ProbeFailure.FORMAT
            )
        } catch (_: Exception) {
            return ProbeResult(
                durationMs = 0L,
                hasAudio = false,
                type = type,
                ok = false,
                note = context.getString(R.string.data_probe_unreadable),
                failure = ProbeFailure.IO
            )
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
            try {
                extractor.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun extensionOf(name: String): String =
        name.substringAfterLast('.', "").lowercase(Locale.ROOT)
}