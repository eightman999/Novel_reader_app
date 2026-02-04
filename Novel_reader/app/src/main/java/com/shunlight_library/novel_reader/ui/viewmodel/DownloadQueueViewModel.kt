/*
 * eightman 2005-2025
 * Furin-lab All Rights Reserved.
 * ViewModel for download queue screen.
 */
package com.shunlight_library.novel_reader.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shunlight_library.novel_reader.NovelReaderApplication
import com.shunlight_library.novel_reader.data.entity.RegistrationQueueEntity
import kotlinx.coroutines.launch

class DownloadQueueViewModel : ViewModel() {
    private val repository = NovelReaderApplication.getRepository()

    val queues = repository.getAllRegistrationQueue()

    val processingQueues = repository.getRegistrationQueueByStatus(RegistrationQueueEntity.STATUS_PROCESSING)

    val errorQueues = repository.getRegistrationQueueByStatus(RegistrationQueueEntity.STATUS_ERROR)

    val timeoutQueues = repository.getRegistrationQueueByStatus(RegistrationQueueEntity.STATUS_TIMEOUT)

    val pausedQueues = repository.getRegistrationQueueByStatus(RegistrationQueueEntity.STATUS_PAUSED)

    /**
     * キューを一時停止する（処理中/待機中）
     */
    fun pauseQueue(id: Long) {
        viewModelScope.launch {
            com.shunlight_library.novel_reader.manager.RegistrationQueueManager.pauseQueue(id)
        }
    }

    fun cancelQueue(id: Long) {
        viewModelScope.launch {
            com.shunlight_library.novel_reader.manager.RegistrationQueueManager.cancelQueue(id)
        }
    }

    fun retryQueue(id: Long) {
        viewModelScope.launch {
            com.shunlight_library.novel_reader.manager.RegistrationQueueManager.retryQueue(id)
        }
    }

    fun deleteCompletedQueue(id: Long) {
        viewModelScope.launch {
            repository.deleteRegistrationQueue(id)
        }
    }

    /**
     * エラー/タイムアウトのキューを削除する（一時データも含む）
     */
    fun deleteFailedQueue(id: Long) {
        viewModelScope.launch {
            com.shunlight_library.novel_reader.manager.RegistrationQueueManager.cancelQueue(id)
        }
    }
}
