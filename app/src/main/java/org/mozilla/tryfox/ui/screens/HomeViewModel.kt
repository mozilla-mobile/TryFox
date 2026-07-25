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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.mozilla.tryfox.data.DownloadState
import org.mozilla.tryfox.data.MozillaPackageManager
import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.data.managers.CacheManager
import org.mozilla.tryfox.data.managers.IntentManager
import org.mozilla.tryfox.data.repositories.DateAwareReleaseRepository
import org.mozilla.tryfox.data.repositories.DownloadFileRepository
import org.mozilla.tryfox.data.repositories.ReleaseRepository
import org.mozilla.tryfox.data.repositories.VersionAwareReleaseRepository
import org.mozilla.tryfox.model.AppState
import org.mozilla.tryfox.model.CacheManagementState
import org.mozilla.tryfox.model.MozillaArchiveApk
import org.mozilla.tryfox.ui.models.AbiUiModel
import org.mozilla.tryfox.ui.models.ApkUiModel
import org.mozilla.tryfox.ui.models.ApksResult
import org.mozilla.tryfox.ui.models.AppUiModel
import org.mozilla.tryfox.ui.models.NightlyBuildOption
import org.mozilla.tryfox.ui.models.newVersionAvailable
import org.mozilla.tryfox.util.FENIX
import org.mozilla.tryfox.util.FENIX_BETA
import org.mozilla.tryfox.util.FENIX_RELEASE
import org.mozilla.tryfox.util.FOCUS
import org.mozilla.tryfox.util.FOCUS_RELEASE
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
                                        installedVersionCode = appState.versionCode,
                                        installedDate = appState.formattedInstallDate,
                                        installingPackageName = appState.installingPackageName,
                                        splitNames = appState.splitNames,
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
            FENIX_RELEASE to mozillaPackageManager.fenixRelease,
            FENIX_BETA to mozillaPackageManager.fenixBeta,
            FOCUS to mozillaPackageManager.focus,
            FOCUS_RELEASE to mozillaPackageManager.focusRelease,
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
                    installedVersionCode = appState.versionCode,
                    installedDate = appState.formattedInstallDate,
                    installingPackageName = appState.installingPackageName,
                    splitNames = appState.splitNames,
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
            repository.appName to buildAppUiModel(repository, appInfoMap[repository.appName])
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

    private fun getLatestApks(apks: List<MozillaArchiveApk>): List<MozillaArchiveApk> {
        if (apks.isEmpty()) {
            return emptyList()
        } else if (apks.none { it.rawDateString != null }) {
            return apks
        }
        val latestDateString = apks.maxOfOrNull { it.rawDateString ?: "" }
        return apks.filter { it.rawDateString == latestDateString }
    }

    private suspend fun buildAppUiModel(
        repository: ReleaseRepository,
        appState: AppState?,
    ): AppUiModel {
        val (apksResult, selectedReleaseVersion, availableReleaseVersions) =
            if (repository is VersionAwareReleaseRepository) {
                when (val versionsResult = repository.getAvailableReleaseVersions()) {
                    is NetworkResult.Success -> {
                        val selectedVersion = versionsResult.data.firstOrNull()
                        val releaseResult = if (selectedVersion != null) {
                            repository.getReleasesForVersion(selectedVersion)
                        } else {
                            NetworkResult.Success(emptyList())
                        }

                        Triple(
                            releaseResult.toApksResult(repository.appName),
                            selectedVersion,
                            versionsResult.data,
                        )
                    }

                    is NetworkResult.Error -> Triple(
                        ApksResult.Error("Error fetching ${repository.appName} builds: ${versionsResult.message}"),
                        null,
                        emptyList(),
                    )
                }
            } else {
                Triple(
                    repository.getLatestReleases().toApksResult(repository.appName),
                    null,
                    emptyList(),
                )
            }

        return AppUiModel(
            name = repository.appName,
            packageName = appState?.packageName ?: "",
            installedVersion = appState?.version,
            installedVersionCode = appState?.versionCode,
            installedDate = appState?.formattedInstallDate,
            installingPackageName = appState?.installingPackageName,
            splitNames = appState?.splitNames ?: emptyList(),
            apks = apksResult,
            selectedReleaseVersion = selectedReleaseVersion,
            availableReleaseVersions = availableReleaseVersions,
        )
    }

    private fun convertParsedApksToUiModels(parsedApks: List<MozillaArchiveApk>): List<ApkUiModel> {
        return parsedApks.map { parsedApk ->
            val date = parsedApk.rawDateString?.formatApkDate()
            val isCompatible = supportedAbis.any { deviceAbi ->
                deviceAbi.equals(
                    parsedApk.abiName,
                    ignoreCase = true,
                )
            }

            // Key the cache dir / unique key by the full build timestamp (yyyy-MM-dd-HH-mm-ss) so
            // two Nightly builds from the same day don't collide on a date-only path. Releases have
            // no timestamp (blank rawDateString) and fall back to the app name.
            val buildKey = parsedApk.rawDateString?.takeIf { it.isNotBlank() }
            val appCacheDir = cacheManager.getCacheDir(parsedApk.appName)
            val apkDir = if (buildKey == null) appCacheDir else File(appCacheDir, buildKey)
            val cacheFile = File(apkDir, parsedApk.fileName)

            val downloadState = if (cacheFile.exists()) {
                DownloadState.Downloaded(cacheFile)
            } else {
                DownloadState.NotDownloaded
            }

            val uniqueKeyPath = if (buildKey == null) {
                parsedApk.appName
            } else {
                "${parsedApk.appName}/$buildKey"
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

    // Converts a raw build timestamp "yyyy-MM-dd-HH-mm-ss" to the display form
    // "yyyy-MM-dd HH:mm:ss". Returns the input unchanged if it isn't in that shape.
    private fun String.formatApkDate(): String {
        val parts = split("-")
        return if (parts.size >= 6) {
            "${parts[0]}-${parts[1]}-${parts[2]} ${parts[3]}:${parts[4]}:${parts[5]}"
        } else {
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

    // Raw build lists for a picked date, kept only while a multi-build picker prompt is pending so a
    // chosen build can be resolved without re-fetching. Keyed by app name.
    private val pendingBuildsByApp = mutableMapOf<String, List<MozillaArchiveApk>>()

    fun onDateSelected(appName: String, date: LocalDate) {
        val repository =
            releaseRepositories.firstOrNull { it.appName == appName } as? DateAwareReleaseRepository
                ?: return

        updateDate(appName, date, allowBuildPrompt = true) {
            repository.getReleases(date)
        }
    }

    fun onClearDate(appName: String) {
        val repository = releaseRepositories.firstOrNull { it.appName == appName } ?: return

        updateDate(appName, null, allowBuildPrompt = false) {
            repository.getLatestReleases()
        }
    }

    /** Applies a build chosen from the multi-build prompt to the card and dismisses the prompt. */
    fun onNightlyBuildSelected(appName: String, buildId: String) {
        val builds = pendingBuildsByApp.remove(appName) ?: return
        val chosen = builds.filter { it.rawDateString == buildId }
        if (chosen.isEmpty()) return

        _homeScreenState.update { state ->
            if (state !is HomeScreenState.Loaded) return@update state
            val app = state.apps[appName] ?: return@update state
            state.copy(
                apps = state.apps + (
                    appName to app.copy(
                        apks = ApksResult.Success(convertParsedApksToUiModels(chosen)),
                        pendingBuildOptions = emptyList(),
                    )
                    ),
            )
        }
    }

    /** Dismisses the multi-build prompt, leaving the latest build (already shown) in place. */
    fun onDismissBuildPicker(appName: String) {
        pendingBuildsByApp.remove(appName)
        _homeScreenState.update { state ->
            if (state !is HomeScreenState.Loaded) return@update state
            val app = state.apps[appName] ?: return@update state
            if (app.pendingBuildOptions.isEmpty()) return@update state
            state.copy(apps = state.apps + (appName to app.copy(pendingBuildOptions = emptyList())))
        }
    }

    private fun buildOptionsFor(builds: List<MozillaArchiveApk>): List<NightlyBuildOption> {
        val timestamps = builds
            .mapNotNull { apk -> apk.rawDateString?.takeIf { it.isNotBlank() } }
            .distinct()
        // rawDateString is "yyyy-MM-dd-HH-mm-ss" (UTC), so lexical descending == newest first.
        return timestamps.sortedDescending().map { rawDate ->
            NightlyBuildOption(id = rawDate, label = rawDate.formatApkDate())
        }
    }

    fun onReleaseVersionSelected(appName: String, version: String) {
        val repository =
            releaseRepositories.firstOrNull { it.appName == appName } as? VersionAwareReleaseRepository
                ?: return

        viewModelScope.launch(ioDispatcher) {
            val currentState = _homeScreenState.value as? HomeScreenState.Loaded ?: return@launch
            val appToUpdate = currentState.apps[appName] ?: return@launch

            val updatedApps = currentState.apps.toMutableMap()
            updatedApps[appName] = appToUpdate.copy(
                apks = ApksResult.Loading,
                selectedReleaseVersion = version,
            )
            _homeScreenState.value = currentState.copy(apps = updatedApps)

            val newApksResult = repository.getReleasesForVersion(version).toApksResult(appName)

            val latestState = _homeScreenState.value as? HomeScreenState.Loaded ?: return@launch
            val latestApp = latestState.apps[appName] ?: return@launch
            val finalUpdatedApps = latestState.apps.toMutableMap()
            finalUpdatedApps[appName] = latestApp.copy(
                apks = newApksResult,
                selectedReleaseVersion = version,
            )

            _homeScreenState.value = latestState.copy(apps = finalUpdatedApps)
        }
    }

    private fun updateDate(
        appName: String,
        date: LocalDate?,
        allowBuildPrompt: Boolean,
        getReleases: suspend (LocalDate?) -> NetworkResult<List<MozillaArchiveApk>>,
    ) {
        viewModelScope.launch(ioDispatcher) {
            val currentState = _homeScreenState.value as? HomeScreenState.Loaded ?: return@launch
            val appToUpdate = currentState.apps[appName] ?: return@launch

            pendingBuildsByApp.remove(appName)
            val updatedApps = currentState.apps.toMutableMap()
            updatedApps[appName] = appToUpdate.copy(
                userPickedDate = date,
                apks = ApksResult.Loading,
                pendingBuildOptions = emptyList(),
            )

            _homeScreenState.value = currentState.copy(apps = updatedApps)

            var buildOptions: List<NightlyBuildOption> = emptyList()
            val newApksResult = when (val result = getReleases(date)) {
                is NetworkResult.Success -> {
                    // Always show the latest build immediately; if the day has more than one build
                    // and prompting is allowed, offer the rest via a one-shot picker prompt.
                    val options = if (allowBuildPrompt) buildOptionsFor(result.data) else emptyList()
                    if (options.size > 1) {
                        pendingBuildsByApp[appName] = result.data
                        buildOptions = options
                    }
                    val latestApks = getLatestApks(result.data)
                    ApksResult.Success(convertParsedApksToUiModels(latestApks))
                }

                is NetworkResult.Error -> ApksResult.Error(
                    "Error fetching $appName nightly builds for $date: ${result.message}",
                )
            }

            val latestState = _homeScreenState.value as? HomeScreenState.Loaded ?: return@launch

            val finalUpdatedApp = latestState.apps[appName]?.copy(
                apks = newApksResult,
                pendingBuildOptions = buildOptions,
            ) ?: return@launch

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

    private fun NetworkResult<List<MozillaArchiveApk>>.toApksResult(appName: String): ApksResult {
        return when (this) {
            is NetworkResult.Success -> {
                val latestApks = getLatestApks(data)
                ApksResult.Success(convertParsedApksToUiModels(latestApks))
            }

            is NetworkResult.Error -> ApksResult.Error(
                "Error fetching $appName builds: $message",
            )
        }
    }

    companion object {
        private const val TAG = "HomeViewModel"
    }
}
