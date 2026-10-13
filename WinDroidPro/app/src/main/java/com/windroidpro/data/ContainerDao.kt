package com.windroidpro.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ContainerDao {
    @Query("SELECT * FROM containers ORDER BY lastUsed DESC")
    fun getAllContainers(): Flow<List<Container>>

    @Query("SELECT * FROM containers ORDER BY lastUsed DESC")
    suspend fun getAllContainersOnce(): List<Container>

    @Query("SELECT * FROM containers WHERE id = :id")
    suspend fun getContainerById(id: String): Container?

    @Transaction
    @Query("SELECT * FROM containers WHERE id = :id")
    fun getContainerWithUsbDevices(id: String): Flow<ContainerWithUsbDevices>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContainer(container: Container)

    @Update
    suspend fun updateContainer(container: Container)

    @Delete
    suspend fun deleteContainer(container: Container)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsbDevice(device: ContainerUsbDevice)

    @Query("DELETE FROM container_usb_devices WHERE containerId = :containerId")
    suspend fun clearUsbDevices(containerId: String)
}
