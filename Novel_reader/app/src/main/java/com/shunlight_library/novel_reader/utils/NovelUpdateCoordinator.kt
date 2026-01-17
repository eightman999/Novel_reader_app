package com.shunlight_library.novel_reader.utils

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Coordinates concurrent update and registration operations.
 *
 * Rules:
 * - Max 2 concurrent registrations allowed
 * - No registration during updates (auto or manual)
 * - Auto updates allowed during registration
 * - Single update per ncode
 */
object NovelUpdateCoordinator {
    private const val DEFAULT_TIMEOUT_MS = 30_000L
    private const val DEFAULT_CHECK_INTERVAL_MS = 100L
    private const val MAX_CONCURRENT_REGISTRATIONS = 2

    private val mutex = Mutex()
    private val activeUpdates = ConcurrentHashMap<String, UpdateSession>()
    private val activeRegistrations = ConcurrentHashMap<String, RegistrationSession>()
    private val isAutoUpdateRunning = AtomicBoolean(false)

    /**
     * Attempts to reserve the update slot for the given ncode. Returns null if already taken.
     */
    suspend fun beginUpdate(ncode: String): UpdateSession? {
        if (ncode.isBlank()) return null
        return mutex.withLock {
            if (activeUpdates.containsKey(ncode)) {
                null
            } else {
                UpdateSession(ncode).also { activeUpdates[ncode] = it }
            }
        }
    }

    /**
     * Waits until the update slot becomes available (or timeout) and then reserves it.
     */
    suspend fun awaitUpdateSlot(
        ncode: String,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MS,
        checkIntervalMillis: Long = DEFAULT_CHECK_INTERVAL_MS
    ): UpdateSession? {
        if (ncode.isBlank()) return null

        suspend fun waitForSlot(): UpdateSession {
            while (true) {
                beginUpdate(ncode)?.let { return it }
                delay(checkIntervalMillis)
            }
        }

        return if (timeoutMillis == Long.MAX_VALUE) {
            waitForSlot()
        } else {
            withTimeoutOrNull(timeoutMillis) { waitForSlot() }
        }
    }

    /**
     * Releases the reserved update slot.
     */
    fun finishUpdate(session: UpdateSession?) {
        session ?: return
        session.markFinished()
        activeUpdates.remove(session.ncode, session)
    }

    /**
     * Requests cancellation for the ongoing update with the given ncode.
     */
    fun cancelUpdate(ncode: String) {
        if (ncode.isBlank()) return
        activeUpdates[ncode]?.cancel()
    }

    /**
     * Cancels the ongoing update (if any) and waits for it to finish.
     * Returns false if the slot did not finish within the timeout.
     */
    suspend fun cancelAndWait(
        ncode: String,
        timeoutMillis: Long = DEFAULT_TIMEOUT_MS
    ): Boolean {
        if (ncode.isBlank()) return true
        val session = activeUpdates[ncode] ?: return true
        session.cancel()
        return session.awaitCompletion(timeoutMillis)
    }

    /**
     * Checks whether an update is currently running for the given ncode.
     */
    fun isUpdating(ncode: String): Boolean {
        if (ncode.isBlank()) return false
        return activeUpdates.containsKey(ncode)
    }

    class UpdateSession internal constructor(val ncode: String) {
        private val cancelled = AtomicBoolean(false)
        private val completion = CompletableDeferred<Unit>()

        fun cancel() {
            cancelled.set(true)
        }

        fun isCancelled(): Boolean = cancelled.get()

        internal suspend fun awaitCompletion(timeoutMillis: Long): Boolean {
            return if (timeoutMillis == Long.MAX_VALUE) {
                completion.await()
                true
            } else {
                withTimeoutOrNull(timeoutMillis) {
                    completion.await()
                    true
                } ?: false
            }
        }

        internal fun markFinished() {
            if (!completion.isCompleted) {
                completion.complete(Unit)
            }
        }
    }

    /**
     * Registration session for tracking novel registration operations
     */
    class RegistrationSession internal constructor(val ncode: String) {
        private val cancelled = AtomicBoolean(false)
        private val completion = CompletableDeferred<Unit>()

        fun cancel() {
            cancelled.set(true)
        }

        fun isCancelled(): Boolean = cancelled.get()

        internal suspend fun awaitCompletion(timeoutMillis: Long): Boolean {
            return if (timeoutMillis == Long.MAX_VALUE) {
                completion.await()
                true
            } else {
                withTimeoutOrNull(timeoutMillis) {
                    completion.await()
                    true
                } ?: false
            }
        }

        internal fun markFinished() {
            if (!completion.isCompleted) {
                completion.complete(Unit)
            }
        }
    }

    /**
     * Result of attempting to begin registration
     */
    sealed class RegistrationResult {
        data class Success(val session: RegistrationSession) : RegistrationResult()
        object MaxConcurrentExceeded : RegistrationResult()
        object UpdateInProgress : RegistrationResult()
    }

    /**
     * Attempts to begin a registration. Returns Success with session if allowed, or an error result.
     *
     * Rules:
     * - Maximum 2 concurrent registrations
     * - No registration during manual or auto updates
     */
    suspend fun beginRegistration(ncode: String): RegistrationResult {
        if (ncode.isBlank()) return RegistrationResult.Success(RegistrationSession(ncode))

        return mutex.withLock {
            // Check if any updates are in progress
            if (activeUpdates.isNotEmpty() || isAutoUpdateRunning.get()) {
                return@withLock RegistrationResult.UpdateInProgress
            }

            // Check if max concurrent registrations reached
            if (activeRegistrations.size >= MAX_CONCURRENT_REGISTRATIONS) {
                return@withLock RegistrationResult.MaxConcurrentExceeded
            }

            // Check if this ncode is already being registered
            if (activeRegistrations.containsKey(ncode)) {
                return@withLock RegistrationResult.MaxConcurrentExceeded
            }

            // Allow registration
            val session = RegistrationSession(ncode)
            activeRegistrations[ncode] = session
            RegistrationResult.Success(session)
        }
    }

    /**
     * Releases registration slot
     */
    fun finishRegistration(session: RegistrationSession?) {
        session ?: return
        session.markFinished()
        activeRegistrations.remove(session.ncode, session)
    }

    /**
     * Releases registration slot by ncode
     */
    fun finishRegistrationByNcode(ncode: String) {
        val session = activeRegistrations[ncode]
        session?.let {
            it.markFinished()
            activeRegistrations.remove(ncode, it)
        }
    }

    /**
     * Sets whether auto update is running
     */
    fun setAutoUpdateRunning(running: Boolean) {
        isAutoUpdateRunning.set(running)
    }

    /**
     * Checks if auto update is running
     */
    fun isAutoUpdateInProgress(): Boolean {
        return isAutoUpdateRunning.get()
    }

    /**
     * Checks if any manual updates are in progress
     */
    fun hasActiveUpdates(): Boolean {
        return activeUpdates.isNotEmpty()
    }

    /**
     * Checks if any registrations are in progress
     */
    fun hasActiveRegistrations(): Boolean {
        return activeRegistrations.isNotEmpty()
    }

    /**
     * Gets the count of active registrations
     */
    fun getActiveRegistrationCount(): Int {
        return activeRegistrations.size
    }
}
