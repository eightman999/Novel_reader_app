package com.shunlight_library.novel_reader.data.sync

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import java.util.Arrays
import java.util.zip.ZipOutputStream
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** ZIP全体を認証付き暗号化する。ファイル名も暗号文の内側に置く。 */
object EncryptedBackupCrypto {
    private val MAGIC = "NRBAK001".toByteArray(Charsets.US_ASCII)
    private const val SALT_BYTES = 16
    private const val NONCE_BYTES = 12
    private const val PBKDF2_ITERATIONS = 310_000
    private const val GCM_TAG_BITS = 128

    fun hasMagic(header: ByteArray): Boolean =
        header.size >= MAGIC.size && MAGIC.indices.all { header[it] == MAGIC[it] }

    fun writeEncryptedZip(output: OutputStream, password: CharArray, write: (ZipOutputStream) -> Unit) {
        require(password.isNotEmpty()) { "パスワードを入力してください" }
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val nonce = ByteArray(NONCE_BYTES).also { SecureRandom().nextBytes(it) }
        val data = DataOutputStream(output)
        data.write(MAGIC)
        data.write(salt)
        data.write(nonce)

        val cipher = newCipher(Cipher.ENCRYPT_MODE, password, salt, nonce)
        ZipOutputStream(CipherOutputStream(data, cipher)).use(write)
    }

    /** 認証タグの検証は終端まで読んだ時に完了する。呼び出し側は成功前の出力を利用しない。 */
    fun decrypt(input: InputStream, output: OutputStream, password: CharArray, maxBytes: Long): Long {
        val data = DataInputStream(input)
        val magic = ByteArray(MAGIC.size)
        data.readFully(magic)
        require(hasMagic(magic)) { "このアプリの暗号化バックアップではありません" }
        val salt = ByteArray(SALT_BYTES).also { data.readFully(it) }
        val nonce = ByteArray(NONCE_BYTES).also { data.readFully(it) }
        val cipher = newCipher(Cipher.DECRYPT_MODE, password, salt, nonce)
        var total = 0L
        CipherInputStream(data, cipher).use { decrypted ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = decrypted.read(buffer)
                if (count < 0) break
                total += count
                require(total <= maxBytes) { "展開後のバックアップがサイズ上限を超えました" }
                output.write(buffer, 0, count)
            }
        }
        output.flush()
        return total
    }

    private fun newCipher(mode: Int, password: CharArray, salt: ByteArray, nonce: ByteArray): Cipher {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, 256)
        val key = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce))
            }
        } finally {
            Arrays.fill(key, 0)
        }
    }
}
