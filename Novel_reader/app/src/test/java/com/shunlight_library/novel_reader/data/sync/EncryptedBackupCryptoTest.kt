package com.shunlight_library.novel_reader.data.sync

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class EncryptedBackupCryptoTest {
    private fun createBackup(): ByteArray {
        val output = ByteArrayOutputStream()
        EncryptedBackupCrypto.writeEncryptedZip(output, "long-test-password".toCharArray()) { zip ->
            zip.putNextEntry(ZipEntry("novel_database"))
            zip.write("sample database".toByteArray())
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    @Test fun roundTrip() {
        val encrypted = createBackup()
        assertTrue(EncryptedBackupCrypto.hasMagic(encrypted))
        val plain = ByteArrayOutputStream()
        EncryptedBackupCrypto.decrypt(ByteArrayInputStream(encrypted), plain, "long-test-password".toCharArray(), 1024)
        ZipInputStream(ByteArrayInputStream(plain.toByteArray())).use { zip ->
            assertTrue(zip.nextEntry.name == "novel_database")
            assertArrayEquals("sample database".toByteArray(), zip.readBytes())
        }
    }

    @Test fun wrongPasswordAndTamperingFail() {
        val encrypted = createBackup()
        assertFalse(EncryptedBackupCrypto.hasMagic("PK\u0003\u0004".toByteArray()))
        val tampered = encrypted.copyOf().also { it[it.lastIndex - 2] = (it[it.lastIndex - 2].toInt() xor 1).toByte() }
        for (candidate in listOf(encrypted to "wrong-password", tampered to "long-test-password")) {
            var failed = false
            try {
                EncryptedBackupCrypto.decrypt(ByteArrayInputStream(candidate.first), ByteArrayOutputStream(), candidate.second.toCharArray(), 1024)
            } catch (_: Exception) {
                failed = true
            }
            assertTrue(failed)
        }
    }

    @Test fun decryptedSizeLimitFails() {
        var failed = false
        try {
            EncryptedBackupCrypto.decrypt(ByteArrayInputStream(createBackup()), ByteArrayOutputStream(), "long-test-password".toCharArray(), 1)
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }
}
