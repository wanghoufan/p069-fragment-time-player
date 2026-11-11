package com.wanghoufan.fragmentplayer

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {

    @Test
    fun exportPeekImportRoundTrip() {
        val app = ApplicationProvider.getApplicationContext<FragmentTimeApp>()
        val graph = app.graph
        val file = File(app.getExternalFilesDir(null), "backup-roundtrip.json")
        val uri = Uri.fromFile(file).toString()

        val exported = runBlocking { graph.backups.exportTo(uri).getOrThrow() }
        assertTrue("exported file exists", file.exists() && file.length() > 0)
        assertTrue("courses exported", exported.courses >= 8)

        val peeked = runBlocking { graph.backups.peek(uri).getOrThrow() }
        assertEquals(exported.presets, peeked.presets)
        assertEquals(exported.courses, peeked.courses)

        val imported = runBlocking { graph.backups.importFrom(uri).getOrThrow() }
        assertEquals(exported.presets, imported.presets)
        assertEquals(exported.courses, imported.courses)
    }
}
