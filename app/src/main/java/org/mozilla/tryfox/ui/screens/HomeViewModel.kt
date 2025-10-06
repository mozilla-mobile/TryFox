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
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.todayIn
import org.mozilla.tryfox.data.DownloadState
import org.mozilla.tryfox.data.IFenixRepository
import org.mozilla.tryfox.data.MozillaArchiveRepository
import org.mozilla.tryfox.data.MozillaPackageManager
import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.data.managers.CacheManager
import org.mozilla.tryfox.model.CacheManagementState
import org.mozilla.tryfox.model.ParsedNightlyApk
import org.mozilla.tryfox.ui.models.AbiUiModel
import org.mozilla.tryfox.ui.models.ApkUiModel
import org.mozilla.tryfox.ui.models.ApksResult
import org.mozilla.tryfox.ui.models.AppUiModel
import org.mozilla.tryfox.util.FENIX
import org.mozilla.tryfox.util.FOCUS
import org.mozilla.tryfox.util.REFERENCE_BROWSER
import java.io.File
import kotlin.collections.mapValues

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
                    if (currentState !is HomeScreenState.Loaded) return@update currentState

                    val updatedApps = if (newCacheState is CacheManagementState.IdleEmpty) {
                        currentState.apps.mapValues { (_, app) ->
                            val apksResult = app.apks as? ApksResult.Success ?: return@mapValues app
                            val updatedApks = apksResult.apks.map { it.copy(downloadState = DownloadState.NotDownloaded) }
                            app.copy(apks = ApksResult.Success(updatedApks))
                        }
                    } else {
                        currentState.apps
                    }

                    currentState.copy(
                        apps = updatedApps,
                        cacheManagementState = newCacheState,
                        isDownloadingAnyFile = if (newCacheState is CacheManagementState.IdleEmpty) false else currentState.isDownloadingAnyFile,
                    )
                }
            }
            .launchIn(viewModelScope)

        mozillaPackageManager.appStates
            .onEach { appState ->
                _homeScreenState.update { currentState ->
                    if (currentState is HomeScreenState.Loaded) {
                        currentState.copy(
                            apps = currentState.apps.mapValues { (appName, app) ->
                                if (app.packageName == appState.packageName) {
                                    app.copy(
                                        installedVersion = appState.version,
                                        installedDate = appState.formattedInstallDate,
                                    )
                                } else {
                                    app
                                }
                            },
                        )
                    } else {
                        currentState
                    }
                }
            }.launchIn(viewModelScope)
    }

    fun initialLoad() {
        viewModelScope.launch {
            _homeScreenState.value = HomeScreenState.InitialLoading
            cacheManager.checkCacheStatus() // Initial check

            val appInfoMap = mapOf(
                FENIX to mozillaPackageManager.fenix(),
                FOCUS to mozillaPackageManager.focus(),
                REFERENCE_BROWSER to mozillaPackageManager.referenceBrowser(),
            )

            _homeScreenState.update {
                val currentCacheState = cacheManager.cacheState.value
                val initialApps = appInfoMap.mapValues { (appName, appState) ->
                    AppUiModel(
                        name = appName,
                        packageName = appState.packageName,
                        installedVersion = appState.version,
                        installedDate = appState.formattedInstallDate,
                        apks = ApksResult.Loading,
                    )
                }
                HomeScreenState.Loaded(
                    apps = initialApps,
                    cacheManagementState = currentCacheState,
                    isDownloadingAnyFile = false,
                )
            }

            val results = mapOf(
                FENIX to mozillaArchiveRepository.getFenixNightlyBuilds(),
                FOCUS to mozillaArchiveRepository.getFocusNightlyBuilds(),
                REFERENCE_BROWSER to mozillaArchiveRepository.getReferenceBrowserNightlyBuilds(),
            )

            val newApps = results.mapValues { (appName, result) ->
                val appState = appInfoMap[appName]
                val apksResult = when (result) {
                    is NetworkResult.Success -> {
                        val latestApks = getLatestApks(result.data)
                        ApksResult.Success(convertParsedApksToUiModels(latestApks))
                    }
                    is NetworkResult.Error -> ApksResult.Error("Error fetching $appName nightly builds: ${result.message}")
                }
                AppUiModel(
                    name = appName,
                    packageName = appState?.packageName ?: "",
                    installedVersion = appState?.version,
                    installedDate = appState?.formattedInstallDate,
                    apks = apksResult,
                )
            }

            val isDownloading = newApps.values.any { app ->
                (app.apks as? ApksResult.Success)?.apks?.any { it.downloadState is DownloadState.InProgress } == true
            }

            _homeScreenState.update {
                if (it is HomeScreenState.Loaded) {
                    it.copy(apps = newApps, isDownloadingAnyFile = isDownloading)
                } else {
                    it
                }
            }
        }
    }

    private fun getLatestApks(apks: List<ParsedNightlyApk>): List<ParsedNightlyApk> {
        if (apks.isEmpty()) {
            return emptyList()
        } else if (apks.none { it.rawDateString != null }) {
            return apks
        }
        val latestDateString = apks.maxOfOrNull { it.rawDateString ?: "" }
        return apks.filter { it.rawDateString == latestDateString }
    }

    private fun convertParsedApksToUiModels(parsedApks: List<ParsedNightlyApk>): List<ApkUiModel> {
        return parsedApks.map { parsedApk ->
            val date = parsedApk.rawDateString?.formatApkDate()
            val isCompatible = deviceSupportedAbis.any { deviceAbi ->
                deviceAbi.equals(
                    parsedApk.abiName,
                    ignoreCase = true,
                )
            }

            val datePart = date?.take(10)
            val appCacheDir = cacheManager.getCacheDir(parsedApk.appName)
            val apkDir = if (datePart.isNullOrBlank()) appCacheDir else File(appCacheDir, datePart)
            val cacheFile = File(apkDir, parsedApk.fileName)

            val downloadState = if (cacheFile.exists()) {
                DownloadState.Downloaded(cacheFile)
            } else {
                DownloadState.NotDownloaded
            }

            val uniqueKeyPath = if (datePart.isNullOrBlank()) parsedApk.appName else "${parsedApk.appName}/$datePart"
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
            outputFormat.format(LocalDateTime.parse(this, inputFormat))
        } catch (_: Exception) {
            this
        }
    }

    private fun updateApkDownloadStateInScreenState(
        appName: String,
        uniqueKey: String,
        newDownloadState: DownloadState,
    ) {
        _homeScreenState.update { currentState ->
            if (currentState !is HomeScreenState.Loaded) return@update currentState

            val updatedApps = currentState.apps.toMutableMap()
            val appToUpdate = updatedApps[appName] ?: return@update currentState
            val apksResult = appToUpdate.apks as? ApksResult.Success ?: return@update currentState

            val updatedApks = apksResult.apks.map {
                if (it.uniqueKey == uniqueKey) it.copy(downloadState = newDownloadState) else it
            }

            updatedApps[appName] = appToUpdate.copy(apks = ApksResult.Success(updatedApks))

            val isDownloading = updatedApps.values.any { app ->
                (app.apks as? ApksResult.Success)?.apks?.any { it.downloadState is DownloadState.InProgress } == true
            }

            currentState.copy(apps = updatedApps, isDownloadingAnyFile = isDownloading)
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
                DownloadState.InProgress(0f),
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
                        DownloadState.InProgress(progress),
                    )
                },
            )

            when (result) {
                is NetworkResult.Success -> {
                    updateApkDownloadStateInScreenState(
                        apkInfo.appName,
                        apkInfo.uniqueKey,
                        DownloadState.Downloaded(result.data),
                    )
                    cacheManager.checkCacheStatus() // Notify cache manager about new file
                    onInstallApk?.invoke(result.data)
                }

                is NetworkResult.Error -> {
                    updateApkDownloadStateInScreenState(
                        apkInfo.appName,
                        apkInfo.uniqueKey,
                        DownloadState.DownloadFailed(result.message),
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

    fun onDateSelected(appName: String, date: LocalDate) {
        if (appName == REFERENCE_BROWSER) {
            return
        }

        viewModelScope.launch {
            val currentState = _homeScreenState.value as? HomeScreenState.Loaded ?: return@launch

            val appToUpdate = currentState.apps[appName] ?: return@launch

            val updatedApp = appToUpdate.copy(userPickedDate = date, apks = ApksResult.Loading)

            val updatedApps = currentState.apps.toMutableMap()
            updatedApps[appName] = updatedApp

            _homeScreenState.value = currentState.copy(apps = updatedApps)

            val result = when (appName) {
                FENIX -> mozillaArchiveRepository.getFenixNightlyBuilds(date)
                FOCUS -> mozillaArchiveRepository.getFocusNightlyBuilds(date)
                else -> return@launch
            }

            val newApksResult = when (result) {
                is NetworkResult.Success -> {
                    val latestApks = getLatestApks(result.data)
                    ApksResult.Success(convertParsedApksToUiModels(latestApks))
                }
                is NetworkResult.Error -> ApksResult.Error("Error fetching $appName nightly builds for $date: ${result.message}")
            }

            val latestState = _homeScreenState.value as? HomeScreenState.Loaded ?: return@launch
            val latestAppToUpdate = latestState.apps[appName] ?: return@launch

            val finalUpdatedApp = latestAppToUpdate.copy(apks = newApksResult)

            val finalUpdatedApps = latestState.apps.toMutableMap()
            finalUpdatedApps[appName] = finalUpdatedApp
            _homeScreenState.value = latestState.copy(apps = finalUpdatedApps)
        }
    }

    fun onClearDate(appName: String) {
        viewModelScope.launch {
            val currentState = _homeScreenState.value as? HomeScreenState.Loaded ?: return@launch
            val appToUpdate = currentState.apps[appName] ?: return@launch

            val updatedApp = appToUpdate.copy(userPickedDate = null)
            val updatedApps = currentState.apps.toMutableMap()
            updatedApps[appName] = updatedApp

            _homeScreenState.value = currentState.copy(apps = updatedApps)

            val result = when (appName) {
                FENIX -> mozillaArchiveRepository.getFenixNightlyBuilds()
                FOCUS -> mozillaArchiveRepository.getFocusNightlyBuilds()
                else -> return@launch
            }

            val newApksResult = when (result) {
                is NetworkResult.Success -> {
                    val latestApks = getLatestApks(result.data)
                    ApksResult.Success(convertParsedApksToUiModels(latestApks))
                }
                is NetworkResult.Error -> ApksResult.Error("Error fetching $appName nightly builds: ${result.message}")
            }

            val latestState = _homeScreenState.value as? HomeScreenState.Loaded ?: return@launch
            val latestAppToUpdate = latestState.apps[appName] ?: return@launch

            val finalUpdatedApp = latestAppToUpdate.copy(apks = newApksResult)

            val finalUpdatedApps = latestState.apps.toMutableMap()
            finalUpdatedApps[appName] = finalUpdatedApp
            _homeScreenState.value = latestState.copy(apps = finalUpdatedApps)
        }
    }

    fun getDateValidator(appName: String): (LocalDate) -> Boolean {
        val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
        val fenixMinDate = LocalDate(2021, 12, 21)
        val focusMinDate = LocalDate(2023, 7, 13)

        return { date ->
            if (date > today) {
                false
            } else {
                when (appName) {
                    FENIX -> date >= fenixMinDate
                    FOCUS -> date >= focusMinDate
                    else -> true
                }
            }
        }
    }

    fun openApp(app: String) {
        mozillaPackageManager.launchApp(app)
    }

    companion object {
        private const val TAG = "HomeViewModel"
    }
}
