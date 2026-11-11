package com.wanghoufan.fragmentplayer.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FingerprintTest {

    @Test
    fun sameMetadataProducesSameFingerprint() {
        val first = Fingerprint.compute("第2章/02.mp4", 1024L, 1_700_000_000_000L, "02.mp4")
        val second = Fingerprint.compute("第2章/02.mp4", 1024L, 1_700_000_000_000L, "02.mp4")
        assertEquals(first, second)
        assertEquals(64, first.length)
        assertEquals(
            "c3ca9ff9037651f33507b88885da20dcef192738f64fa931939ecaa0abcad6c8",
            first
        )
    }

    @Test
    fun differentSizeProducesDifferentFingerprint() {
        val base = Fingerprint.compute("第2章/02.mp4", 1024L, 1_700_000_000_000L, "02.mp4")
        val changed = Fingerprint.compute("第2章/02.mp4", 2048L, 1_700_000_000_000L, "02.mp4")
        assertNotEquals(base, changed)
    }

    @Test
    fun differentPathProducesDifferentFingerprint() {
        val base = Fingerprint.compute("第2章/02.mp4", 1024L, 1_700_000_000_000L, "02.mp4")
        val moved = Fingerprint.compute("第3章/02.mp4", 1024L, 1_700_000_000_000L, "02.mp4")
        assertNotEquals(base, moved)
    }

    @Test
    fun differentModifiedTimeProducesDifferentFingerprint() {
        val base = Fingerprint.compute("第2章/02.mp4", 1024L, 1_700_000_000_000L, "02.mp4")
        val replaced = Fingerprint.compute("第2章/02.mp4", 1024L, 1_700_000_001_000L, "02.mp4")
        assertNotEquals(base, replaced)
    }
}