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
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
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
                    handleCacheStateChange(currentState, newCacheState)
                }
            }.launchIn(viewModelScope)
    }

    private fun handleCacheStateChange(
        currentState: HomeScreenState,
        newCacheState: CacheManagementState,
    ): HomeScreenState {
        if (currentState !is HomeScreenState.Loaded) {
            return currentState
        }

        return if (newCacheState is CacheManagementState.IdleEmpty) {
            currentState.copy(
                cacheManagementState = newCacheState,
                fenixBuildsState = currentState.fenixBuildsState.resetDownloadState(),
                focusBuildsState = currentState.focusBuildsState.resetDownloadState(),
                referenceBrowserBuildsState =
                    currentState.referenceBrowserBuildsState.resetDownloadState(),
                isDownloadingAnyFile = false,
            )
        } else {
            currentState.copy(cacheManagementState = newCacheState)
        }
    }

    fun initialLoad() {
        viewModelScope.launch {
            _homeScreenState.value = HomeScreenState.InitialLoading
            cacheManager.checkCacheStatus()

            _homeScreenState.update {
                HomeScreenState.Loaded(
                    fenixBuildsState = ApksState.Loading,
                    focusBuildsState = ApksState.Loading,
                    referenceBrowserBuildsState = ApksState.Loading,
                    cacheManagementState = cacheManager.cacheState.value,
                    isDownloadingAnyFile = false,
                )
            }

            loadAllApks()
        }
    }

    private suspend fun loadAllApks() {
        val fenixAppInfo = mozillaPackageManager.fenix
        val focusAppInfo = mozillaPackageManager.focus
        val referenceBrowserAppInfo = mozillaPackageManager.referenceBrowser

        val fenixResult = mozillaArchiveRepository.getFenixNightlyBuilds()
        val focusResult = mozillaArchiveRepository.getFocusNightlyBuilds()
        val referenceBrowserResult = mozillaArchiveRepository.getReferenceBrowserNightlyBuilds()

        val fenixApksState = processRepositoryResult(fenixResult, FENIX, fenixAppInfo)
        val focusApksState = processRepositoryResult(focusResult, FOCUS, focusAppInfo)
        val referenceBrowserApksState =
            processRepositoryResult(
                referenceBrowserResult,
                REFERENCE_BROWSER,
                referenceBrowserAppInfo,
            )

        _homeScreenState.update { currentState ->
            if (currentState is HomeScreenState.Loaded) {
                currentState.copy(
                    fenixBuildsState = fenixApksState,
                    focusBuildsState = focusApksState,
                    referenceBrowserBuildsState = referenceBrowserApksState,
                    isDownloadingAnyFile =
                        checkIsDownloading(
                            fenixApksState,
                            focusApksState,
                            referenceBrowserApksState,
                        ),
                )
            } else {
                currentState
            }
        }
    }

    private fun checkIsDownloading(vararg states: ApksState): Boolean = states.any { it.isDownloading() }

    private fun processRepositoryResult(
        result: NetworkResult<List<ParsedNightlyApk>>,
        appName: String,
        appState: AppState?,
    ): ApksState =
        when (result) {
            is NetworkResult.Success -> {
                val uiModels = result.data.map(::parsedApkToUiModel)
                ApksState.Success(uiModels, appState)
            }
            is NetworkResult.Error -> {
                ApksState.Error("Error fetching $appName nightly builds: ${result.message}", appState)
            }
        }

    private fun parsedApkToUiModel(parsedApk: ParsedNightlyApk): ApkUiModel {
        val date = parsedApk.rawDateString?.formatApkDate()
        val isCompatible = deviceSupportedAbis.any { it.equals(parsedApk.abiName, ignoreCase = true) }

        val datePart = date?.take(10)
        val appCacheDir = cacheManager.getCacheDir(parsedApk.appName)
        val apkDir = if (datePart.isNullOrBlank()) appCacheDir else File(appCacheDir, datePart)
        val cacheFile = File(apkDir, parsedApk.fileName)

        val downloadState =
            if (cacheFile.exists()) {
                DownloadState.Downloaded(cacheFile)
            } else {
                DownloadState.NotDownloaded
            }

        val uniqueKeyPath =
            if (datePart.isNullOrBlank()) {
                parsedApk.appName
            } else {
                "${parsedApk.appName}/$datePart"
            }
        val uniqueKey = "$uniqueKeyPath/${parsedApk.fileName}"

        return ApkUiModel(
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

    private fun String.formatApkDate(): String =
        try {
            val inputFormat = LocalDateTime.Format { byUnicodePattern("yyyy-MM-dd-HH-mm-ss") }
            val outputFormat = LocalDateTime.Format { byUnicodePattern("yyyy-MM-dd HH:mm") }
            LocalDateTime.parse(this, inputFormat).format(outputFormat)
        } catch (_: Exception) {
            this
        }

    private fun updateApkListState(
        apkState: ApksState,
        uniqueKey: String,
        newDownloadState: DownloadState,
    ): ApksState {
        if (apkState !is ApksState.Success) return apkState

        val updatedApks =
            apkState.apks.map {
                if (it.uniqueKey == uniqueKey) it.copy(downloadState = newDownloadState) else it
            }
        return apkState.copy(apks = updatedApks)
    }

    private fun updateApkDownloadStateInScreenState(
        app: String,
        uniqueKey: String,
        newDownloadState: DownloadState,
    ) {
        _homeScreenState.update { currentState ->
            if (currentState !is HomeScreenState.Loaded) return@update currentState

            val newFenixState =
                if (app.equals(FENIX, ignoreCase = true)) {
                    updateApkListState(currentState.fenixBuildsState, uniqueKey, newDownloadState)
                } else {
                    currentState.fenixBuildsState
                }
            val newFocusState =
                if (app.equals(FOCUS, ignoreCase = true)) {
                    updateApkListState(currentState.focusBuildsState, uniqueKey, newDownloadState)
                } else {
                    currentState.focusBuildsState
                }
            val newRefBrowserState =
                if (app.equals(REFERENCE_BROWSER, ignoreCase = true)) {
                    updateApkListState(currentState.referenceBrowserBuildsState, uniqueKey, newDownloadState)
                } else {
                    currentState.referenceBrowserBuildsState
                }

            currentState.copy(
                fenixBuildsState = newFenixState,
                focusBuildsState = newFocusState,
                referenceBrowserBuildsState = newRefBrowserState,
                isDownloadingAnyFile =
                    checkIsDownloading(
                        newFenixState,
                        newFocusState,
                        newRefBrowserState,
                    ),
            )
        }
    }

    fun downloadNightlyApk(apkInfo: ApkUiModel) {
        if (apkInfo.downloadState.isTerminal()) return

        viewModelScope.launch(ioDispatcher) {
            updateApkDownloadStateInScreenState(
                apkInfo.appName,
                apkInfo.uniqueKey,
                DownloadState.InProgress(0f),
            )

            val outputFile =
                File(apkInfo.apkDir, apkInfo.fileName).also {
                    it.parentFile?.mkdirs()
                }

            val result =
                fenixRepository.downloadArtifact(
                    downloadUrl = apkInfo.url,
                    outputFile = outputFile,
                ) { bytesDownloaded, totalBytes ->
                    val progress =
                        if (totalBytes > 0) {
                            bytesDownloaded.toFloat() / totalBytes.toFloat()
                        } else {
                            0f
                        }
                    updateApkDownloadStateInScreenState(
                        app = apkInfo.appName,
                        uniqueKey = apkInfo.uniqueKey,
                        newDownloadState = DownloadState.InProgress(progress),
                    )
                }

            val finalState =
                when (result) {
                    is NetworkResult.Success -> DownloadState.Downloaded(result.data)
                    is NetworkResult.Error -> DownloadState.DownloadFailed(result.message)
                }
            updateApkDownloadStateInScreenState(apkInfo.appName, apkInfo.uniqueKey, finalState)
            cacheManager.checkCacheStatus()

            if (finalState is DownloadState.Downloaded) {
                onInstallApk?.invoke(finalState.file)
            }
        }
    }

    fun clearAppCache() {
        viewModelScope.launch(ioDispatcher) {
            cacheManager.clearCache()
        }
    }

    private fun DownloadState.isTerminal(): Boolean = this is DownloadState.InProgress || this is DownloadState.Downloaded

    private fun ApksState.isDownloading(): Boolean {
        if (this !is ApksState.Success) {
            return false
        }
        return apks.any { it.downloadState is DownloadState.InProgress }
    }

    private fun ApksState.resetDownloadState(): ApksState {
        if (this !is ApksState.Success) {
            return this
        }

        val updatedApks = apks.map { it.copy(downloadState = DownloadState.NotDownloaded) }
        return copy(apks = updatedApks)
    }
}
