package org.mozilla.tryfox.ui.screens

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import org.mozilla.tryfox.data.DownloadState
import org.mozilla.tryfox.data.IFenixRepository
import org.mozilla.tryfox.data.MozillaArchiveRepository
import org.mozilla.tryfox.data.MozillaPackageManager
import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.data.managers.CacheManager
import org.mozilla.tryfox.model.AppState
import org.mozilla.tryfox.model.CacheManagementState
import org.mozilla.tryfox.model.ParsedNightlyApk
import org.mozilla.tryfox.ui.models.AbiUiModel
import org.mozilla.tryfox.ui.models.ApkUiModel
import org.mozilla.tryfox.ui.models.ApksState
import org.mozilla.tryfox.util.FENIX
import org.mozilla.tryfox.util.FOCUS
import org.mozilla.tryfox.util.REFERENCE_BROWSER
import java.io.File

@OptIn(FormatStringsInDatetimeFormats::class)
class HomeViewModel(
    private val mozillaArchiveRepository: MozillaArchiveRepository,
    private val fenixRepository: IFenixRepository,
    private val mozillaPackageManager: MozillaPackageManager,
    private val cacheManager: CacheManager,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    internal var deviceSupportedAbisForTesting: List<String>? = null

    private val _homeScreenState = MutableStateFlow<HomeScreenState>(HomeScreenState.InitialLoading)
    val homeScreenState: StateFlow<HomeScreenState> = _homeScreenState.asStateFlow()

    private val deviceSupportedAbis: List<String> by lazy {
        deviceSupportedAbisForTesting ?: Build.SUPPORTED_ABIS?.toList() ?: emptyList()
    }

    var onInstallApk: ((File) -> Unit)? = null

    init {
        cacheManager.cacheState
            .onEach { newCacheState ->
                _homeScreenState.update { currentState ->
                    var nextState = if (currentState is HomeScreenState.Loaded) {
                        currentState.copy(cacheManagementState = newCacheState)
                    } else {
                        currentState
                    }

                    if (newCacheState is CacheManagementState.IdleEmpty && nextState is HomeScreenState.Loaded) {
                        val currentLoadedState = nextState

                        val updatedFenixApks =
                            (currentLoadedState.fenixBuildsState as? ApksState.Success)?.apks?.map {
                                it.copy(downloadState = DownloadState.NotDownloaded)
                            }
                        val newFenixState =
                            if (updatedFenixApks != null) {
                                currentLoadedState.fenixBuildsState.copy(apks = updatedFenixApks)
                            } else currentLoadedState.fenixBuildsState

                        val updatedFocusApks =
                            (currentLoadedState.focusBuildsState as? ApksState.Success)?.apks?.map {
                                it.copy(downloadState = DownloadState.NotDownloaded)
                            }
                        val newFocusState =
                            if (updatedFocusApks != null) {
                                currentLoadedState.focusBuildsState.copy(apks = updatedFocusApks)
                            } else currentLoadedState.focusBuildsState

                        val updatedReferenceBrowserApks =
                            (currentLoadedState.referenceBrowserBuildsState as? ApksState.Success)?.apks?.map {
                                it.copy(downloadState = DownloadState.NotDownloaded)
                            }
                        val newReferenceBrowserState =
                            if (updatedReferenceBrowserApks != null) {
                                currentLoadedState.referenceBrowserBuildsState.copy(apks = updatedReferenceBrowserApks)
                            } else currentLoadedState.referenceBrowserBuildsState

                        nextState = currentLoadedState.copy(
                            fenixBuildsState = newFenixState,
                            focusBuildsState = newFocusState,
                            referenceBrowserBuildsState = newReferenceBrowserState,
                            isDownloadingAnyFile = false
                        )
                    }
                    nextState
                }
            }
            .launchIn(viewModelScope)
    }

    fun initialLoad() {
        viewModelScope.launch {
            _homeScreenState.value = HomeScreenState.InitialLoading
            cacheManager.checkCacheStatus() // Initial check

            val fenixAppInfo = mozillaPackageManager.fenix
            val focusAppInfo = mozillaPackageManager.focus
            val referenceBrowserAppInfo = mozillaPackageManager.referenceBrowser

            _homeScreenState.update {
                val currentCacheState = cacheManager.cacheState.value
                HomeScreenState.Loaded(
                    fenixBuildsState = ApksState.Loading,
                    focusBuildsState = ApksState.Loading,
                    referenceBrowserBuildsState = ApksState.Loading,
                    cacheManagementState = currentCacheState,
                    isDownloadingAnyFile = false
                )
            }

            val fenixResult = mozillaArchiveRepository.getFenixNightlyBuilds()
            val focusResult = mozillaArchiveRepository.getFocusNightlyBuilds()
            val referenceBrowserResult = mozillaArchiveRepository.getReferenceBrowserNightlyBuilds()

            val fenixApksState = processRepositoryResult(fenixResult, FENIX, fenixAppInfo)
            val focusApksState = processRepositoryResult(focusResult, FOCUS, focusAppInfo)
            val referenceBrowserApksState = processRepositoryResult(
                referenceBrowserResult,
                REFERENCE_BROWSER,
                referenceBrowserAppInfo
            )

            val isDownloading =
                checkIsDownloading(fenixApksState, focusApksState, referenceBrowserApksState)

            _homeScreenState.update {
                if (it is HomeScreenState.Loaded) {
                    it.copy(
                        fenixBuildsState = fenixApksState,
                        focusBuildsState = focusApksState,
                        referenceBrowserBuildsState = referenceBrowserApksState,
                        isDownloadingAnyFile = isDownloading
                    )
                } else {
                    it
                }
            }
        }
    }

    private fun checkIsDownloading(
        fenixState: ApksState,
        focusState: ApksState,
        referenceBrowserState: ApksState
    ): Boolean {
        val fenixDownloading =
            (fenixState as? ApksState.Success)?.apks?.any { it.downloadState is DownloadState.InProgress } == true
        val focusDownloading =
            (focusState as? ApksState.Success)?.apks?.any { it.downloadState is DownloadState.InProgress } == true
        val referenceBrowserDownloading =
            (referenceBrowserState as? ApksState.Success)?.apks?.any { it.downloadState is DownloadState.InProgress } == true
        return fenixDownloading || focusDownloading || referenceBrowserDownloading
    }

    private fun processRepositoryResult(
        result: NetworkResult<List<ParsedNightlyApk>>,
        appName: String,
        appState: AppState?
    ): ApksState {
        return when (result) {
            is NetworkResult.Success -> {
                val uiModels = convertParsedApksToUiModels(result.data)
                ApksState.Success(uiModels, appState)
            }

            is NetworkResult.Error -> {
                ApksState.Error(
                    "Error fetching $appName nightly builds: ${result.message}",
                    appState
                )
            }
        }
    }

    private fun convertParsedApksToUiModels(parsedApks: List<ParsedNightlyApk>): List<ApkUiModel> {
        return parsedApks.map { parsedApk ->
            val date = parsedApk.rawDateString?.formatApkDate()
            val isCompatible = deviceSupportedAbis.any { deviceAbi ->
                deviceAbi.equals(
                    parsedApk.abiName,
                    ignoreCase = true
                )
            }

            val datePart = date?.take(10)
            val appCacheDir = cacheManager.getCacheDir(parsedApk.appName)
            val apkDir = if (datePart.isNullOrBlank()) {
                appCacheDir
            } else {
                File(appCacheDir, datePart)
            }
            val cacheFile = File(apkDir, parsedApk.fileName)

            val downloadState = if (cacheFile.exists()) {
                DownloadState.Downloaded(cacheFile)
            } else {
                DownloadState.NotDownloaded
            }

            val uniqueKeyPath = if (datePart.isNullOrBlank()) {
                parsedApk.appName
            } else {
                "${parsedApk.appName}/$datePart"
            }
            val uniqueKey = "$uniqueKeyPath/${parsedApk.fileName}"

            ApkUiModel(
                originalString = parsedApk.originalString,
                date = date ?: "",
                appName = parsedApk.appName,
                version = parsedApk.version,
                abi = AbiUiModel(parsedApk.abiName, isCompatible),
                url = parsedApk.fullUrl,
                fileName = parsedApk.fileName,
                downloadState = downloadState,
                uniqueKey = uniqueKey,
                apkDir = apkDir,
            )
        }
    }

    private fun String.formatApkDate(): String {
        return try {
            val inputFormat = LocalDateTime.Format { byUnicodePattern("yyyy-MM-dd-HH-mm-ss") }
            val outputFormat = LocalDateTime.Format { byUnicodePattern("yyyy-MM-dd HH:mm") }
            LocalDateTime.parse(this, inputFormat).format(outputFormat)
        } catch (_: Exception) {
            this
        }
    }

    private fun updateApkListState(
        apkState: ApksState,
        uniqueKey: String,
        newDownloadState: DownloadState
    ): ApksState {
        val state = apkState as? ApksState.Success ?: return apkState

        val updatedApks = state.apks.map {
            if (it.uniqueKey == uniqueKey) it.copy(downloadState = newDownloadState) else it
        }
        return apkState.copy(apks = updatedApks)
    }

    private fun updateApkDownloadStateInScreenState(
        app: String,
        uniqueKey: String,
        newDownloadState: DownloadState
    ) {
        _homeScreenState.update { currentState ->
            if (currentState !is HomeScreenState.Loaded) return@update currentState

            val updatedFenixState = if (app.equals(FENIX, ignoreCase = true)) {
                updateApkListState(currentState.fenixBuildsState, uniqueKey, newDownloadState)
            } else {
                currentState.fenixBuildsState
            }

            val updatedFocusState = if (app.equals(FOCUS, ignoreCase = true)) {
                updateApkListState(currentState.focusBuildsState, uniqueKey, newDownloadState)
            } else {
                currentState.focusBuildsState
            }

            val updatedReferenceBrowserState =
                if (app.equals(REFERENCE_BROWSER, ignoreCase = true)) {
                    updateApkListState(
                        currentState.referenceBrowserBuildsState,
                        uniqueKey,
                        newDownloadState
                    )
                } else {
                    currentState.referenceBrowserBuildsState
                }

            currentState.copy(
                fenixBuildsState = updatedFenixState,
                focusBuildsState = updatedFocusState,
                referenceBrowserBuildsState = updatedReferenceBrowserState,
                isDownloadingAnyFile = checkIsDownloading(
                    updatedFenixState,
                    updatedFocusState,
                    updatedReferenceBrowserState
                )
            )
        }
    }

    fun downloadNightlyApk(apkInfo: ApkUiModel) {
        if (apkInfo.downloadState is DownloadState.InProgress || apkInfo.downloadState is DownloadState.Downloaded) {
            return
        }

        viewModelScope.launch {
            updateApkDownloadStateInScreenState(
                apkInfo.appName,
                apkInfo.uniqueKey,
                DownloadState.InProgress(0f)
            )

            val outputDir = apkInfo.apkDir
            if (!outputDir.exists()) outputDir.mkdirs()
            val outputFile = File(outputDir, apkInfo.fileName)

            val result = fenixRepository.downloadArtifact(
                downloadUrl = apkInfo.url,
                outputFile = outputFile,
                onProgress = { bytesDownloaded, totalBytes ->
                    val progress =
                        if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes.toFloat() else 0f
                    updateApkDownloadStateInScreenState(
                        apkInfo.appName,
                        apkInfo.uniqueKey,
                        DownloadState.InProgress(progress)
                    )
                }
            )

            when (result) {
                is NetworkResult.Success -> {
                    updateApkDownloadStateInScreenState(
                        apkInfo.appName,
                        apkInfo.uniqueKey,
                        DownloadState.Downloaded(result.data)
                    )
                    cacheManager.checkCacheStatus() // Notify cache manager about new file
                    onInstallApk?.invoke(result.data)
                }

                is NetworkResult.Error -> {
                    updateApkDownloadStateInScreenState(
                        apkInfo.appName,
                        apkInfo.uniqueKey,
                        DownloadState.DownloadFailed(result.message)
                    )
                    cacheManager.checkCacheStatus() // Check cache even on error
                }
            }
        }
    }

    fun clearAppCache() {
        viewModelScope.launch {
            cacheManager.clearCache()
        }
    }

    companion object {
        private const val TAG = "HomeViewModel"
    }
}
