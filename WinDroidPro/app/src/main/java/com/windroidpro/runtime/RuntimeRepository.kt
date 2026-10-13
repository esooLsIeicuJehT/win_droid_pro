package com.windroidpro.runtime

import android.content.Context
import com.windroidpro.data.Container
import com.windroidpro.data.ContainerDao
import com.winlator.box64.Box64Preset
import com.winlator.container.ContainerManager
import com.winlator.core.WineInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.winlator.container.Container as EngineContainer

@Singleton
class RuntimeRepository @Inject constructor(@ApplicationContext private val context: Context,
    private val dao: ContainerDao, private val installer: RuntimeInstaller) {
    private val mutex = Mutex()
    val gamesDirectory: File get() = File(context.filesDir, "games").apply { mkdirs() }

    private suspend fun createEngine(name: String, id: String, description: String,
        profile: RuntimeProfile): EngineContainer = withContext(Dispatchers.Main) {
        check(installer.state.value.ready) { "Install the Windows runtime first." }
        val data = JSONObject().apply {
            put("name", name)
            put("screenSize", "${profile.width}x${profile.height}")
            put("graphicsDriver", profile.drivers)
            put("graphicsDriverConfig", profile.driverConfig)
            put("dxwrapper", profile.wrapper)
            put("box64Preset", Box64Preset.CONSERVATIVE)
            put("envVars", "MESA_SHADER_CACHE_DISABLE=false MESA_SHADER_CACHE_MAX_SIZE=128MB WINEESYNC=1")
            put("drives", "D:${gamesDirectory.absolutePath}")
            put("wineVersion", WineInfo.MAIN_WINE_INFO.identifier())
            put("extraData", JSONObject().put("windroidId", id).put("windroidDescription", description))
        }
        suspendCancellableCoroutine { continuation ->
            ContainerManager(context).createContainerAsync(data) { engine ->
                if (continuation.isActive) {
                    if (engine != null) continuation.resume(engine)
                    else continuation.resumeWithException(IllegalStateException("Could not create Windows container. Check free storage."))
                }
            }
        }
    }

    suspend fun create(name: String, description: String, profile: RuntimeProfile): Container = mutex.withLock {
        val id = UUID.randomUUID().toString()
        val engine = createEngine(name.trim(), id, description.trim(), profile)
        val container = Container(id = id, name = engine.name, description = description.trim(),
            runtimeId = engine.id, prefixPath = File(engine.rootDir, ".wine").absolutePath,
            wineVersion = WineInfo.MAIN_WINE_VERSION, screenWidth = profile.width, screenHeight = profile.height,
            enableVKD3D = false, box64Preset = "conservative")
        dao.insertContainer(container)
        container
    }

    suspend fun resolve(container: Container): Int = mutex.withLock {
        check(installer.state.value.ready) { "Install the Windows runtime first." }
        val manager = withContext(Dispatchers.IO) { ContainerManager(context) }
        val existing = manager.getContainerById(container.runtimeId)
            ?: manager.containers.firstOrNull { it.getExtra("windroidId") == container.id }
        check(existing != null || container.runtimeId == 0) { "This Windows container was deleted. Refresh the container list." }
        val engine = existing ?: createEngine(container.name, container.id, container.description, RuntimeProfile.MALI)
        ensureGameDrive(engine)
        dao.updateContainer(container.copy(runtimeId = engine.id,
            prefixPath = File(engine.rootDir, ".wine").absolutePath,
            wineVersion = WineInfo.MAIN_WINE_VERSION, lastUsed = System.currentTimeMillis()))
        engine.id
    }

    private fun ensureGameDrive(engine: EngineContainer) {
        val drives = engine.drivesIterator().toList()
        if (drives.any { File(it.path).canonicalFile == gamesDirectory.canonicalFile }) return
        // Advanced/imported containers may already use D:. Preserve custom
        // mappings and choose an available drive rather than launch an empty path.
        val letter = ('D'..'Z').firstOrNull { candidate -> drives.none { it.letter == candidate.toString() } }
            ?: error("Free a drive letter in this container to use the game library.")
        engine.drives = engine.drives + "$letter:${gamesDirectory.absolutePath}"
        engine.saveData()
    }

    suspend fun synchronize() = mutex.withLock {
        if (!installer.state.value.ready) return@withLock
        withContext(Dispatchers.IO) {
            val originals = dao.getAllContainersOnce().associateBy { it.id }
            val found = HashSet<String>()
            for (engine in ContainerManager(context).containers) {
                var id = engine.getExtra("windroidId")
                if (id.isBlank()) {
                    id = UUID.randomUUID().toString()
                    engine.putExtra("windroidId", id)
                    engine.saveData()
                }
                val original = originals[id]
                found.add(id)
                val value = original ?: Container(id = id, name = engine.name,
                    prefixPath = File(engine.rootDir, ".wine").absolutePath)
                dao.insertContainer(value.copy(name = engine.name, runtimeId = engine.id,
                    wineVersion = WineInfo.fromIdentifier(context, engine.wineVersion).fullVersion(),
                    prefixPath = File(engine.rootDir, ".wine").absolutePath,
                    description = engine.getExtra("windroidDescription", value.description)))
            }
            // Keep unconverted scaffold rows, but reflect deletion from the
            // advanced runtime UI rather than silently recreating a prefix.
            originals.values.filter { it.runtimeId != 0 && it.id !in found }
                .forEach { dao.deleteContainer(it) }
        }
    }

    suspend fun delete(container: Container) = mutex.withLock {
        withContext(Dispatchers.Main) {
            val manager = ContainerManager(context)
            val engine = manager.getContainerById(container.runtimeId)
                ?: manager.containers.firstOrNull { it.getExtra("windroidId") == container.id }
            if (engine != null) suspendCancellableCoroutine<Unit> { continuation ->
                manager.removeContainerAsync(engine) {
                    if (continuation.isActive) {
                        if (engine.rootDir.exists()) continuation.resumeWithException(IllegalStateException("Could not delete Windows container."))
                        else continuation.resume(Unit)
                    }
                }
            }
        }
        dao.deleteContainer(container)
    }
}
