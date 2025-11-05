package org.mozilla.tryfox.ui.screens

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
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
import org.mozilla.tryfox.data.DateAwareReleaseRepository
import org.mozilla.tryfox.data.DownloadFileRepository
import org.mozilla.tryfox.data.DownloadState
import org.mozilla.tryfox.data.MozillaPackageManager
import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.data.ReleaseRepository
import org.mozilla.tryfox.data.managers.CacheManager
import org.mozilla.tryfox.data.managers.IntentManager
import org.mozilla.tryfox.model.CacheManagementState
import org.mozilla.tryfox.model.ParsedNightlyApk
import org.mozilla.tryfox.ui.models.AbiUiModel
import org.mozilla.tryfox.ui.models.ApkUiModel
import org.mozilla.tryfox.ui.models.ApksResult
import org.mozilla.tryfox.ui.models.AppUiModel
import org.mozilla.tryfox.ui.models.newVersionAvailable
import org.mozilla.tryfox.util.FENIX
import org.mozilla.tryfox.util.FOCUS
import org.mozilla.tryfox.util.REFERENCE_BROWSER
import org.mozilla.tryfox.util.TRYFOX
import java.io.File

/**
 * ViewModel for the Home screen, responsible for fetching and displaying nightly builds of different Mozilla apps.
 *
 * @param releaseRepositories A list of release repositories.
 * @param downloadFileRepository Repository for downloading files.
 * @param mozillaPackageManager Manager for interacting with installed Mozilla apps.
 * @param cacheManager Manager for handling application cache.
 * @param intentManager Manager for handling intents, such as APK installation.
 * @param ioDispatcher The coroutine dispatcher for background operations.
 */
@OptIn(FormatStringsInDatetimeFormats::class)
class HomeViewModel(
    private val releaseRepositories: List<ReleaseRepository>,
    private val downloadFileRepository: DownloadFileRepository,
    private val mozillaPackageManager: MozillaPackageManager,
    private val cacheManager: CacheManager,
    private val intentManager: IntentManager,
    private val ioDispatcher: CoroutineDispatcher,
    private val supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList(),
) : ViewModel() {

    private val _homeScreenState = MutableStateFlow<HomeScreenState>(HomeScreenState.InitialLoading)
    val homeScreenState: StateFlow<HomeScreenState> = _homeScreenState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        cacheManager.cacheState
            .onEach { newCacheState ->
                _homeScreenState.update { currentState ->
                    if (currentState !is HomeScreenState.Loaded) return@update currentState

                    val updatedApps = if (newCacheState is CacheManagementState.IdleEmpty) {
                        currentState.apps.mapValues { (_, app) ->
                            val apksResult = app.apks as? ApksResult.Success ?: return@mapValues app
                            val updatedApks = apksResult.apks.map {
                                it.copy(downloadState = DownloadState.NotDownloaded)
                            }
                            app.copy(apks = ApksResult.Success(updatedApks))
                        }
                    } else {
                        currentState.apps
                    }

                    val updatedTryFoxApp = if (newCacheState is CacheManagementState.IdleEmpty) {
                        currentState.tryfoxApp?.let { app ->
                            val apksResult = app.apks as? ApksResult.Success ?: return@let app
                            val updatedApks = apksResult.apks.map {
                                it.copy(downloadState = DownloadState.NotDownloaded)
                            }
                            app.copy(apks = ApksResult.Success(updatedApks))
                        }
                    } else {
                        currentState.tryfoxApp
                    }

                    currentState.copy(
                        apps = updatedApps,
                        tryfoxApp = updatedTryFoxApp,
                        cacheManagementState = newCacheState,
                        isDownloadingAnyFile = if (newCacheState is CacheManagementState.IdleEmpty) {
                            false
                        } else {
                            currentState.isDownloadingAnyFile
                        },
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
        viewModelScope.launch(ioDispatcher) {
            _homeScreenState.value = HomeScreenState.InitialLoading
            _isRefreshing.value = true
            cacheManager.checkCacheStatus() // Initial check
            fetchData()
            _isRefreshing.value = false
        }
    }

    fun refreshData() {
        viewModelScope.launch(ioDispatcher) {
            _isRefreshing.value = true
            fetchData()
            _isRefreshing.value = false
        }
    }

    private suspend fun fetchData() {
        val appInfoMap = mapOf(
            FENIX to mozillaPackageManager.fenix,
            FOCUS to mozillaPackageManager.focus,
            REFERENCE_BROWSER to mozillaPackageManager.referenceBrowser,
            TRYFOX to mozillaPackageManager.tryfox,
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
                apps = initialApps.filterNot { (key, _) -> key == TRYFOX },
                tryfoxApp = initialApps[TRYFOX],
                cacheManagementState = currentCacheState,
                isDownloadingAnyFile = false,
            )
        }

        val newApps = releaseRepositories.associate { repository ->
            repository.appName to repository.getLatestReleases()
        }.mapValues { (appName, result) ->
            val appState = appInfoMap[appName]
            val apksResult = when (result) {
                is NetworkResult.Success -> {
                    val latestApks = getLatestApks(result.data)
                    ApksResult.Success(convertParsedApksToUiModels(latestApks))
                }

                is NetworkResult.Error -> ApksResult.Error(
                    "Error fetching $appName nightly builds: ${result.message}",
                )
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

        val tryFoxApp = newApps[TRYFOX]?.takeIf { it.newVersionAvailable }

        _homeScreenState.update {
            if (it is HomeScreenState.Loaded) {
                it.copy(
                    apps = newApps.filterNot { (key, _) -> key == TRYFOX },
                    tryfoxApp = tryFoxApp,
                    isDownloadingAnyFile = isDownloading,
                )
            } else {
                it
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
            val isCompatible = supportedAbis.any { deviceAbi ->
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
            var updatedTryFoxApp = currentState.tryfoxApp

            if (appName == TRYFOX) {
                updatedTryFoxApp = updatedTryFoxApp?.let { appToUpdate ->
                    val apksResult =
                        appToUpdate.apks as? ApksResult.Success ?: return@let appToUpdate
                    val updatedApks = apksResult.apks.map {
                        if (it.uniqueKey == uniqueKey) it.copy(downloadState = newDownloadState) else it
                    }
                    appToUpdate.copy(apks = ApksResult.Success(updatedApks))
                }
            } else {
                val appToUpdate = updatedApps[appName] ?: return@update currentState
                val apksResult =
                    appToUpdate.apks as? ApksResult.Success ?: return@update currentState

                val updatedApks = apksResult.apks.map {
                    if (it.uniqueKey == uniqueKey) it.copy(downloadState = newDownloadState) else it
                }
                updatedApps[appName] = appToUpdate.copy(apks = ApksResult.Success(updatedApks))
            }

            val isDownloading = updatedApps.values.any { app ->
                (app.apks as? ApksResult.Success)?.apks?.any { it.downloadState is DownloadState.InProgress } == true
            } || (updatedTryFoxApp?.apks as? ApksResult.Success)?.apks?.any { it.downloadState is DownloadState.InProgress } == true

            currentState.copy(
                apps = updatedApps,
                tryfoxApp = updatedTryFoxApp,
                isDownloadingAnyFile = isDownloading,
            )
        }
    }

    fun downloadNightlyApk(apkInfo: ApkUiModel) {
        if (apkInfo.downloadState is DownloadState.InProgress || apkInfo.downloadState is DownloadState.Downloaded) {
            return
        }

        viewModelScope.launch(ioDispatcher) {
            updateApkDownloadStateInScreenState(
                apkInfo.appName,
                apkInfo.uniqueKey,
                DownloadState.InProgress(0f, isIndeterminate = true),
            )

            val outputDir = apkInfo.apkDir
            if (!outputDir.exists()) outputDir.mkdirs()
            val outputFile = File(outputDir, apkInfo.fileName)

            val result = downloadFileRepository.downloadFile(
                downloadUrl = apkInfo.url,
                outputFile = outputFile,
                onProgress = { bytesDownloaded, totalBytes ->
                    val isIndeterminate = totalBytes <= 0
                    val progress =
                        if (isIndeterminate) 0f else bytesDownloaded.toFloat() / totalBytes.toFloat()
                    updateApkDownloadStateInScreenState(
                        apkInfo.appName,
                        apkInfo.uniqueKey,
                        DownloadState.InProgress(progress, isIndeterminate),
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
                    installApk(result.data)
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

    fun installApk(file: File) {
        intentManager.installApk(file)
    }

    fun uninstallApp(packageName: String) {
        intentManager.uninstallApk(packageName)
    }

    fun clearAppCache() {
        viewModelScope.launch(ioDispatcher) {
            cacheManager.clearCache()
        }
    }

    fun onDateSelected(appName: String, date: LocalDate) {
        val repository =
            releaseRepositories.firstOrNull { it.appName == appName } as? DateAwareReleaseRepository
                ?: return

        updateDate(appName, date) {
            repository.getReleases(date)
        }
    }

    fun onClearDate(appName: String) {
        val repository = releaseRepositories.firstOrNull { it.appName == appName } ?: return

        updateDate(appName, null) {
            repository.getLatestReleases()
        }
    }

    private fun updateDate(
        appName: String,
        date: LocalDate?,
        getReleases: suspend (LocalDate?) -> NetworkResult<List<ParsedNightlyApk>>,
    ) {
        viewModelScope.launch(ioDispatcher) {
            val currentState = _homeScreenState.value as? HomeScreenState.Loaded ?: return@launch
            val appToUpdate = currentState.apps[appName] ?: return@launch

            val updatedApps = currentState.apps.toMutableMap()
            updatedApps[appName] =
                appToUpdate.copy(userPickedDate = date, apks = ApksResult.Loading)

            _homeScreenState.value = currentState.copy(apps = updatedApps)

            val newApksResult = when (val result = getReleases(date)) {
                is NetworkResult.Success -> {
                    val latestApks = getLatestApks(result.data)
                    ApksResult.Success(convertParsedApksToUiModels(latestApks))
                }

                is NetworkResult.Error -> ApksResult.Error(
                    "Error fetching $appName nightly builds for $date: ${result.message}",
                )
            }

            val latestState = _homeScreenState.value as? HomeScreenState.Loaded ?: return@launch

            val finalUpdatedApp =
                latestState.apps[appName]?.copy(apks = newApksResult) ?: return@launch

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

    fun dismissTryFoxCard() {
        _homeScreenState.update { currentState ->
            if (currentState !is HomeScreenState.Loaded) return@update currentState
            currentState.copy(tryfoxApp = null)
        }
    }

    companion object {
        private const val TAG = "HomeViewModel"
    }
}
