package com.wanghoufan.fragmentplayer.importer

import java.security.MessageDigest

object Fingerprint {
    fun compute(
        relativePath: String,
        sizeBytes: Long,
        lastModified: Long,
        displayName: String
    ): String = sha256("$relativePath|$sizeBytes|$lastModified|$displayName")

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}