package org.mozilla.fenixinstaller.ui.screens

import android.content.Context
import android.os.Build
import android.util.Log // Added import
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.fenixinstaller.data.DownloadState
import org.mozilla.fenixinstaller.data.FenixRepository // Keep for potential companion object usage, consistent with ProfileViewModel
import org.mozilla.fenixinstaller.data.IFenixRepository
import org.mozilla.fenixinstaller.data.MozillaArchiveRepository
import org.mozilla.fenixinstaller.data.MozillaPackageManager
import org.mozilla.fenixinstaller.data.NetworkResult
import org.mozilla.fenixinstaller.model.AppState
import org.mozilla.fenixinstaller.model.CacheManagementState
import org.mozilla.fenixinstaller.model.ParsedNightlyApk
import org.mozilla.fenixinstaller.ui.models.AbiUiModel
import org.mozilla.fenixinstaller.ui.models.ApkUiModel
import org.mozilla.fenixinstaller.ui.models.FocusApksState
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import logcat.LogPriority
import logcat.logcat

class HomeViewModel constructor(
    private val mozillaArchiveRepository: MozillaArchiveRepository,
    private val fenixRepository: IFenixRepository,
    private val mozillaPackageManager: MozillaPackageManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    internal var deviceSupportedAbisForTesting: List<String>? = null

    private val _homeScreenState = MutableStateFlow<HomeScreenState>(HomeScreenState.InitialLoading)
    val homeScreenState: StateFlow<HomeScreenState> = _homeScreenState.asStateFlow()

    private val deviceSupportedAbis: List<String> by lazy {
        deviceSupportedAbisForTesting ?: Build.SUPPORTED_ABIS?.toList() ?: emptyList()
    }

    var onInstallApk: ((File) -> Unit)? = null

    fun initialLoad(context: Context) {
        viewModelScope.launch {
            if (_homeScreenState.value !is HomeScreenState.Loaded) {
                _homeScreenState.value = HomeScreenState.InitialLoading
            }

            val fenixAppInfo = mozillaPackageManager.fenix
            val focusAppInfo = mozillaPackageManager.focus
            val initialCacheState = determineCacheState(context)

            _homeScreenState.value = HomeScreenState.Loaded(
                fenixBuildsState = FocusApksState.Loading,
                focusBuildsState = FocusApksState.Loading,
                fenixAppInfo = fenixAppInfo,
                focusAppInfo = focusAppInfo,
                cacheManagementState = initialCacheState,
                isDownloadingAnyFile = false
            )

            val fenixResult = mozillaArchiveRepository.getFenixNightlyBuilds()
            val focusResult = mozillaArchiveRepository.getFocusNightlyBuilds()

            val fenixApksState = processRepositoryResult(fenixResult, "fenix", context)
            val focusApksState = processRepositoryResult(focusResult, "focus", context)

            val isDownloading = checkIsDownloading(fenixApksState, focusApksState)
            val finalCacheState = determineCacheState(context)

            _homeScreenState.update {
                if (it is HomeScreenState.Loaded) {
                    it.copy(
                        fenixBuildsState = fenixApksState,
                        focusBuildsState = focusApksState,
                        cacheManagementState = finalCacheState,
                        isDownloadingAnyFile = isDownloading
                    )
                } else {
                    HomeScreenState.Loaded(
                        fenixBuildsState = fenixApksState,
                        focusBuildsState = focusApksState,
                        fenixAppInfo = fenixAppInfo,
                        focusAppInfo = focusAppInfo,
                        cacheManagementState = finalCacheState,
                        isDownloadingAnyFile = isDownloading
                    )
                }
            }
        }
    }

    private fun checkIsDownloading(fenixState: FocusApksState, focusState: FocusApksState): Boolean {
        val fenixDownloading = (fenixState as? FocusApksState.Success)?.apks?.any { it.downloadState is DownloadState.InProgress } == true
        val focusDownloading = (focusState as? FocusApksState.Success)?.apks?.any { it.downloadState is DownloadState.InProgress } == true
        return fenixDownloading || focusDownloading
    }

    private fun processRepositoryResult(result: NetworkResult<List<ParsedNightlyApk>>, appName: String, context: Context): FocusApksState {
        return when (result) {
            is NetworkResult.Success -> {
                val uiModels = convertParsedApksToUiModels(result.data, context)
                if (uiModels.isEmpty()) {
                    FocusApksState.Error("No $appName nightly builds found for the current month.")
                } else {
                    FocusApksState.Success(uiModels)
                }
            }
            is NetworkResult.Error -> {
                FocusApksState.Error("Error fetching $appName nightly builds: ${result.message}")
            }
        }
    }

    private fun convertParsedApksToUiModels(parsedApks: List<ParsedNightlyApk>, context: Context): List<ApkUiModel> {
        return parsedApks.map { parsedApk ->
            val date = parsedApk.rawDateString.formatApkDate()
            val isCompatible = deviceSupportedAbis.any { deviceAbi -> deviceAbi.equals(parsedApk.abiName, ignoreCase = true) }

            val cacheDir = File(context.cacheDir, "${parsedApk.appName}/${date.substring(0, 10)}")
            val cacheFile = File(cacheDir, parsedApk.fileName)
            val downloadState = if (cacheFile.exists()) {
                DownloadState.Downloaded(cacheFile)
            } else {
                DownloadState.NotDownloaded
            }

            ApkUiModel(
                originalString = parsedApk.originalString,
                date = date,
                appName = parsedApk.appName,
                version = parsedApk.version,
                abi = AbiUiModel(parsedApk.abiName, isCompatible),
                url = parsedApk.fullUrl,
                fileName = parsedApk.fileName,
                downloadState = downloadState,
                uniqueKey = "${parsedApk.appName}/${date.substring(0, 10)}/${parsedApk.fileName}"
            )
        }
    }

    private fun isAppCachePopulated(appSpecificCacheDir: File): Boolean {
        if (!appSpecificCacheDir.exists() || !appSpecificCacheDir.isDirectory) return false
        appSpecificCacheDir.listFiles()?.forEach { item ->
            if (item.isFile) return true
            if (item.isDirectory) {
                item.listFiles()?.any { it.isFile }?.let { if (it) return true }
            }
        }
        return false
    }

    private fun determineCacheState(context: Context): CacheManagementState {
        val cacheRoot = context.cacheDir
        val fenixDir = File(cacheRoot, "fenix")
        val focusDir = File(cacheRoot, "focus")
        val isFenixPopulated = isAppCachePopulated(fenixDir)
        val isFocusPopulated = isAppCachePopulated(focusDir)
        return if (!isFenixPopulated && !isFocusPopulated) CacheManagementState.IdleEmpty else CacheManagementState.IdleNonEmpty
    }

    private fun checkCacheStatusAndUpdateState(context: Context) {
        viewModelScope.launch(ioDispatcher) {
            val newCacheState = determineCacheState(context)
            _homeScreenState.update {
                if (it is HomeScreenState.Loaded) {
                    it.copy(cacheManagementState = newCacheState)
                } else {
                    it
                }
            }
            logcat(TAG) { "Cache status checked. State: $newCacheState" }
        }
    }

    fun fetchLatestFenixNightlyBuilds(context: Context) {
        viewModelScope.launch {
            _homeScreenState.update {
                if (it is HomeScreenState.Loaded) {
                    it.copy(fenixBuildsState = FocusApksState.Loading, fenixAppInfo = mozillaPackageManager.fenix)
                } else HomeScreenState.InitialLoading
            }

            when (val result = mozillaArchiveRepository.getFenixNightlyBuilds()) {
                is NetworkResult.Success -> {
                    val apks = convertParsedApksToUiModels(result.data, context)
                    val newState = if (apks.isEmpty()) FocusApksState.Error("No Fenix nightly builds found.") else FocusApksState.Success(apks)
                    _homeScreenState.update {
                        if (it is HomeScreenState.Loaded) {
                            it.copy(fenixBuildsState = newState, isDownloadingAnyFile = checkIsDownloading(newState, it.focusBuildsState))
                        } else it
                    }
                }
                is NetworkResult.Error -> {
                    val errorState = FocusApksState.Error("Error fetching Fenix nightly builds: ${result.message}")
                     _homeScreenState.update {
                        if (it is HomeScreenState.Loaded) {
                            it.copy(fenixBuildsState = errorState, isDownloadingAnyFile = checkIsDownloading(errorState, it.focusBuildsState))
                        } else it
                    }
                }
            }
            checkCacheStatusAndUpdateState(context)
        }
    }

    fun fetchLatestFocusNightlyBuilds(context: Context) {
        viewModelScope.launch {
            _homeScreenState.update {
                if (it is HomeScreenState.Loaded) {
                    it.copy(focusBuildsState = FocusApksState.Loading, focusAppInfo = mozillaPackageManager.focus)
                } else HomeScreenState.InitialLoading
            }

            when (val result = mozillaArchiveRepository.getFocusNightlyBuilds()) {
                is NetworkResult.Success -> {
                    val apks = convertParsedApksToUiModels(result.data, context)
                    val newState = if (apks.isEmpty()) FocusApksState.Error("No Focus nightly builds found.") else FocusApksState.Success(apks)
                    _homeScreenState.update {
                        if (it is HomeScreenState.Loaded) {
                            it.copy(focusBuildsState = newState, isDownloadingAnyFile = checkIsDownloading(it.fenixBuildsState, newState))
                        } else it
                    }
                }
                is NetworkResult.Error -> {
                    val errorState = FocusApksState.Error("Error fetching Focus nightly builds: ${result.message}")
                    _homeScreenState.update {
                        if (it is HomeScreenState.Loaded) {
                            it.copy(focusBuildsState = errorState, isDownloadingAnyFile = checkIsDownloading(it.fenixBuildsState, errorState))
                        } else it
                    }
                }
            }
            checkCacheStatusAndUpdateState(context)
        }
    }

    private fun String.formatApkDate(): String {
        return try {
            val inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss")
            val outputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            LocalDateTime.parse(this, inputFormatter).format(outputFormatter)
        } catch (_: Exception) {
            this
        }
    }

    private fun updateApkDownloadStateInScreenState(app: String, uniqueKey: String, newState: DownloadState) {
        _homeScreenState.update { currentState ->
            if (currentState !is HomeScreenState.Loaded) return@update currentState

            val updatedFenixBuildsState = if (app.contains("fenix", ignoreCase = true) && currentState.fenixBuildsState is FocusApksState.Success) {
                val updatedApks = currentState.fenixBuildsState.apks.map {
                    if (it.uniqueKey == uniqueKey) it.copy(downloadState = newState) else it
                }
                currentState.fenixBuildsState.copy(apks = updatedApks)
            } else {
                currentState.fenixBuildsState
            }

            val updatedFocusBuildsState = if (app.contains("focus", ignoreCase = true) && currentState.focusBuildsState is FocusApksState.Success) {
                val updatedApks = currentState.focusBuildsState.apks.map {
                    if (it.uniqueKey == uniqueKey) it.copy(downloadState = newState) else it
                }
                currentState.focusBuildsState.copy(apks = updatedApks)
            } else {
                currentState.focusBuildsState
            }

            currentState.copy(
                fenixBuildsState = updatedFenixBuildsState,
                focusBuildsState = updatedFocusBuildsState,
                isDownloadingAnyFile = checkIsDownloading(updatedFenixBuildsState, updatedFocusBuildsState)
            )
        }
    }

    fun downloadNightlyApk(apkInfo: ApkUiModel, context: Context) {
        if (apkInfo.downloadState is DownloadState.InProgress || apkInfo.downloadState is DownloadState.Downloaded) {
            return
        }

        viewModelScope.launch {
            updateApkDownloadStateInScreenState(apkInfo.appName, apkInfo.uniqueKey, DownloadState.InProgress(0f))

            val outputDir = File(context.cacheDir, "${apkInfo.appName}/${apkInfo.date.substring(0, 10)}")
            if (!outputDir.exists()) outputDir.mkdirs()
            val outputFile = File(outputDir, apkInfo.fileName)

            val result = fenixRepository.downloadArtifact(
                downloadUrl = apkInfo.url,
                outputFile = outputFile,
                onProgress = { bytesDownloaded, totalBytes ->
                    val progress = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes.toFloat() else 0f
                    updateApkDownloadStateInScreenState(apkInfo.appName, apkInfo.uniqueKey, DownloadState.InProgress(progress))
                }
            )

            when (result) {
                is NetworkResult.Success -> {
                    updateApkDownloadStateInScreenState(apkInfo.appName, apkInfo.uniqueKey, DownloadState.Downloaded(result.data))
                    checkCacheStatusAndUpdateState(context)
                    onInstallApk?.invoke(result.data)
                }
                is NetworkResult.Error -> {
                    updateApkDownloadStateInScreenState(apkInfo.appName, apkInfo.uniqueKey, DownloadState.DownloadFailed(result.message))
                    checkCacheStatusAndUpdateState(context)
                }
            }
        }
    }

    fun clearAppCache(context: Context) {
        viewModelScope.launch {
            _homeScreenState.update {
                if (it is HomeScreenState.Loaded) it.copy(cacheManagementState = CacheManagementState.Clearing)
                else it
            }

            try {
                withContext(ioDispatcher) {
                    File(context.cacheDir, "fenix").deleteRecursively()
                    File(context.cacheDir, "focus").deleteRecursively()
                }

                _homeScreenState.update { currentState ->
                    if (currentState !is HomeScreenState.Loaded) return@update currentState

                    val updatedFenixApks = (currentState.fenixBuildsState as? FocusApksState.Success)?.apks?.map {
                        it.copy(downloadState = DownloadState.NotDownloaded)
                    }
                    val newFenixState = if (updatedFenixApks != null && currentState.fenixBuildsState is FocusApksState.Success) {
                        currentState.fenixBuildsState.copy(apks = updatedFenixApks)
                    } else currentState.fenixBuildsState

                    val updatedFocusApks = (currentState.focusBuildsState as? FocusApksState.Success)?.apks?.map {
                        it.copy(downloadState = DownloadState.NotDownloaded)
                    }
                     val newFocusState = if (updatedFocusApks != null && currentState.focusBuildsState is FocusApksState.Success) {
                        currentState.focusBuildsState.copy(apks = updatedFocusApks)
                    } else currentState.focusBuildsState

                    currentState.copy(
                        fenixBuildsState = newFenixState,
                        focusBuildsState = newFocusState,
                        isDownloadingAnyFile = false
                    )
                }
            } catch (e: Exception) {
                logcat(LogPriority.ERROR, TAG) { "Error clearing cache: ${e.message}\n${Log.getStackTraceString(e)}" }
            } finally {
                checkCacheStatusAndUpdateState(context)
            }
        }
    }

    companion object {
        private const val TAG = "HomeViewModel"
    }
}
