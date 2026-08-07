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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import org.mozilla.tryfox.data.DownloadState
import org.mozilla.tryfox.data.InstalledTryBuild
import org.mozilla.tryfox.data.MozillaPackageManager
import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.data.managers.CacheManager
import org.mozilla.tryfox.data.managers.IntentManager
import org.mozilla.tryfox.data.repositories.CachedHomeApk
import org.mozilla.tryfox.data.repositories.CachedHomeApp
import org.mozilla.tryfox.data.repositories.DateAwareReleaseRepository
import org.mozilla.tryfox.data.repositories.EmptyHomeDataCacheRepository
import org.mozilla.tryfox.data.repositories.EmptyInstalledTryBuildRepository
import org.mozilla.tryfox.data.repositories.HomeDataCacheRepository
import org.mozilla.tryfox.data.repositories.HomeDataSnapshot
import org.mozilla.tryfox.data.repositories.InstalledTryBuildRepository
import org.mozilla.tryfox.data.repositories.ReleaseRepository
import org.mozilla.tryfox.data.repositories.UserDataRepository
import org.mozilla.tryfox.data.repositories.VersionAwareReleaseRepository
import org.mozilla.tryfox.download.ApkDownloadCoordinator
import org.mozilla.tryfox.download.ApkDownloadRequest
import org.mozilla.tryfox.download.model.DownloadStatus
import org.mozilla.tryfox.download.model.PersistedDownloadState
import org.mozilla.tryfox.install.ApkInstallCoordinator
import org.mozilla.tryfox.install.InstallState
import org.mozilla.tryfox.model.AppState
import org.mozilla.tryfox.model.HomeScreenLayout
import org.mozilla.tryfox.model.MozillaArchiveApk
import org.mozilla.tryfox.ui.models.AbiUiModel
import org.mozilla.tryfox.ui.models.ApkUiModel
import org.mozilla.tryfox.ui.models.ApksResult
import org.mozilla.tryfox.ui.models.AppUiModel
import org.mozilla.tryfox.ui.models.NightlyBuildOption
import org.mozilla.tryfox.ui.models.newVersionAvailable
import org.mozilla.tryfox.util.FENIX
import org.mozilla.tryfox.util.FENIX_BETA
import org.mozilla.tryfox.util.FENIX_DEBUG
import org.mozilla.tryfox.util.FENIX_RELEASE
import org.mozilla.tryfox.util.FOCUS
import org.mozilla.tryfox.util.FOCUS_BETA
import org.mozilla.tryfox.util.FOCUS_DEBUG
import org.mozilla.tryfox.util.FOCUS_RELEASE
import org.mozilla.tryfox.util.REFERENCE_BROWSER
import org.mozilla.tryfox.util.TRYFOX
import java.io.File

/**
 * ViewModel for the Home screen, responsible for fetching and displaying nightly builds of different Mozilla apps.
 *
 * @param releaseRepositories A list of release repositories.
 * @param downloadCoordinator Coordinator for WorkManager-backed APK downloads.
 * @param mozillaPackageManager Manager for interacting with installed Mozilla apps.
 * @param cacheManager Manager for handling application cache.
 * @param intentManager Manager for handling intents, such as APK installation.
 * @param ioDispatcher The coroutine dispatcher for background operations.
 */
class HomeViewModel(
    private val releaseRepositories: List<ReleaseRepository>,
    private val downloadCoordinator: ApkDownloadCoordinator,
    private val mozillaPackageManager: MozillaPackageManager,
    private val cacheManager: CacheManager,
    private val intentManager: IntentManager,
    private val installCoordinator: ApkInstallCoordinator,
    private val ioDispatcher: CoroutineDispatcher,
    private val userDataRepository: UserDataRepository? = null,
    private val homeDataCacheRepository: HomeDataCacheRepository = EmptyHomeDataCacheRepository,
    private val installedTryBuildRepository: InstalledTryBuildRepository = EmptyInstalledTryBuildRepository,
    private val supportedAbis: List<String> = Build.SUPPORTED_ABIS.toList(),
) : ViewModel() {

    private val _homeScreenState = MutableStateFlow<HomeScreenState>(HomeScreenState.InitialLoading)
    val homeScreenState: StateFlow<HomeScreenState> = _homeScreenState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()
    val installStates: StateFlow<Map<String, InstallState>> = installCoordinator.states
    private val downloadStates = MutableStateFlow<Map<String, PersistedDownloadState>>(emptyMap())
    private var currentAppsByName: Map<String, AppUiModel> = emptyMap()
    private var cachedAppsByName: Map<String, AppUiModel> = emptyMap()
    private var installedTryBuild: InstalledTryBuild? = null
    private var initialLoadStarted = false
    private var tryFoxCardDismissed = false
    private var homeScreenLayout = HomeScreenLayout.OneCardPerApp

    @Volatile
    private var selectedHomeAppNames = HomeAppFamily.entries.associateWith { it.defaultAppName }
    private val appMutationVersions = mutableMapOf<String, Long>()
    private val appsLock = Any()
    private val refreshMutex = Mutex()
    private val refreshStateMutex = Mutex()
    private var activeRefreshes = 0

    init {
        downloadCoordinator.downloads
            .onEach { persistedDownloads ->
                downloadStates.value = persistedDownloads
                syncLoadedStateDownloadStates()
            }
            .launchIn(viewModelScope)

        cacheManager.cacheState
            .onEach { newCacheState ->
                _homeScreenState.update { currentState ->
                    if (currentState !is HomeScreenState.Loaded) return@update currentState
                    currentState.copy(cacheManagementState = newCacheState)
                }
                syncLoadedStateDownloadStates()
            }
            .launchIn(viewModelScope)

        userDataRepository?.homeScreenLayoutFlow
            ?.onEach { layout ->
                homeScreenLayout = layout
                _homeScreenState.update { currentState ->
                    if (currentState is HomeScreenState.Loaded) {
                        currentState.copy(homeScreenLayout = layout)
                    } else {
                        currentState
                    }
                }
            }
            ?.launchIn(viewModelScope)

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
                                        installedTryBuild = app.name.takeIf { it == FENIX_DEBUG }
                                            ?.let { matchingInstalledTryBuild(appState) },
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

        installedTryBuildRepository.installedTryBuild
            .onEach { build ->
                installedTryBuild = build
                _homeScreenState.update { currentState ->
                    if (currentState !is HomeScreenState.Loaded) return@update currentState
                    currentState.copy(
                        apps = currentState.apps.mapValues { (_, app) ->
                            if (app.name == FENIX_DEBUG) {
                                app.copy(
                                    installedTryBuild = matchingInstalledTryBuild(
                                        app.packageName,
                                        app.installedVersion,
                                        app.installedVersionCode,
                                    ),
                                )
                            } else {
                                app
                            }
                        },
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun initialLoad() {
        if (initialLoadStarted) return
        initialLoadStarted = true
        launchRefresh(hydrateCache = true)
    }

    fun refreshData() {
        launchRefresh(hydrateCache = false)
    }

    fun selectHomeAppFlavor(family: HomeAppFamily, appName: String) {
        if (appName !in family.appNames) return
        selectedHomeAppNames = selectedHomeAppNames + (family to appName)
        _homeScreenState.update { state ->
            if (state !is HomeScreenState.Loaded) state
            else state.copy(selectedAppNames = selectedHomeAppNames)
        }
    }

    private fun launchRefresh(hydrateCache: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            markRefreshStarted()
            try {
                refreshMutex.withLock {
                    if (hydrateCache) {
                        cacheManager.checkCacheStatus()
                        hydrateCachedData()
                    }
                    fetchData()
                }
            } finally {
                markRefreshFinished()
            }
        }
    }

    private suspend fun markRefreshStarted() = refreshStateMutex.withLock {
        activeRefreshes += 1
        _isRefreshing.value = true
    }

    private suspend fun markRefreshFinished() = refreshStateMutex.withLock {
        activeRefreshes -= 1
        _isRefreshing.value = activeRefreshes > 0
    }

    private suspend fun hydrateCachedData() {
        val snapshot = homeDataCacheRepository.read() ?: return
        val appInfoMap = appInfoMap()
        val cachedApps = snapshot.apps.associate { cachedApp ->
            cachedApp.appName to cachedApp.toAppUiModel(appInfoMap[cachedApp.appName])
        }
        if (cachedApps.isEmpty()) return

        synchronized(appsLock) {
            cachedAppsByName = cachedApps
            currentAppsByName = initialApps(appInfoMap) + cachedApps
        }
        publishCurrentApps()
    }

    private suspend fun fetchData() {
        val appInfoMap = appInfoMap()
        val mutationVersionsAtStart = synchronized(appsLock) { appMutationVersions.toMap() }
        if (_homeScreenState.value !is HomeScreenState.Loaded) {
            synchronized(appsLock) {
                currentAppsByName = initialApps(appInfoMap)
            }
            publishCurrentApps()
        }

        val fetchedApps = releaseRepositories.associate { repository ->
            repository.appName to buildAppUiModel(repository, appInfoMap[repository.appName])
        }
        applyFetchedApps(fetchedApps, mutationVersionsAtStart)
        publishCurrentApps()
        persistSuccessfulApps()
    }

    private fun appInfoMap(): Map<String, AppState> {
        return mapOf(
            FENIX to mozillaPackageManager.fenix,
            FENIX_RELEASE to mozillaPackageManager.fenixRelease,
            FENIX_BETA to mozillaPackageManager.fenixBeta,
            FENIX_DEBUG to mozillaPackageManager.fenixDebug,
            FOCUS to mozillaPackageManager.focus,
            FOCUS_RELEASE to mozillaPackageManager.focusRelease,
            FOCUS_BETA to mozillaPackageManager.focusBeta,
            FOCUS_DEBUG to mozillaPackageManager.fenixDebug,
            REFERENCE_BROWSER to mozillaPackageManager.referenceBrowser,
            TRYFOX to mozillaPackageManager.tryfox,
        )
    }

    private fun initialApps(appInfoMap: Map<String, AppState>): Map<String, AppUiModel> =
        appInfoMap.mapValues { (appName, appState) ->
            AppUiModel(
                name = appName,
                packageName = appState.packageName,
                installedVersion = appState.version,
                installedVersionCode = appState.versionCode,
                installedDate = appState.formattedInstallDate,
                installingPackageName = appState.installingPackageName,
                splitNames = appState.splitNames,
                installedTryBuild = appName.takeIf { it == FENIX_DEBUG }
                    ?.let { matchingInstalledTryBuild(appState) },
                apks = ApksResult.Loading,
            )
        }

    private fun publishCurrentApps() {
        val apps = synchronized(appsLock) { currentAppsByName }
        val currentCacheState = cacheManager.cacheState.value
        val tryFoxApp = apps[TRYFOX]
            ?.takeIf { !tryFoxCardDismissed && it.newVersionAvailable }
        _homeScreenState.value = HomeScreenState.Loaded(
            apps = apps.filterNot { (key, _) -> key == TRYFOX },
            tryfoxApp = tryFoxApp,
            cacheManagementState = currentCacheState,
            isDownloadingAnyFile = false,
            selectedAppNames = selectedHomeAppNames,
            homeScreenLayout = homeScreenLayout,
        ).applyDownloadStates(downloadStates.value)
    }

    private fun updateCurrentApp(appName: String, update: (AppUiModel) -> AppUiModel) {
        synchronized(appsLock) {
            val currentApp = currentAppsByName[appName] ?: return
            currentAppsByName = currentAppsByName + (appName to update(currentApp))
            appMutationVersions[appName] = (appMutationVersions[appName] ?: 0) + 1
        }
    }

    private fun applyFetchedApps(
        fetchedApps: Map<String, AppUiModel>,
        mutationVersionsAtStart: Map<String, Long>,
    ) {
        synchronized(appsLock) {
            val mergedApps = fetchedApps.mapValues { (appName, fetchedApp) ->
                val cachedApp = currentAppsByName[appName]
                val changedWhileRefreshing = appMutationVersions[appName] != mutationVersionsAtStart[appName]
                if (changedWhileRefreshing && cachedApp != null) {
                    cachedApp
                } else if (fetchedApp.apks is ApksResult.Error && cachedApp?.apks is ApksResult.Success) {
                    cachedApp
                } else {
                    fetchedApp
                }
            }
            currentAppsByName = currentAppsByName + mergedApps
            cachedAppsByName = cachedAppsByName + fetchedApps.filterValues { it.apks is ApksResult.Success }
        }
    }

    private suspend fun persistSuccessfulApps() {
        val cachedApps = synchronized(appsLock) { cachedAppsByName }
        val successfulApps = cachedApps.values.mapNotNull { app ->
            val successfulApks = app.apks as? ApksResult.Success ?: return@mapNotNull null
            CachedHomeApp(
                appName = app.name,
                apks = successfulApks.apks.map(::toCachedHomeApk),
                selectedReleaseVersion = app.selectedReleaseVersion,
                availableReleaseVersions = app.availableReleaseVersions,
            )
        }
        if (successfulApps.isNotEmpty()) {
            homeDataCacheRepository.write(
                HomeDataSnapshot(version = HomeDataSnapshot.CURRENT_VERSION, apps = successfulApps),
            )
        }
    }

    private fun CachedHomeApp.toAppUiModel(appState: AppState?): AppUiModel = AppUiModel(
        name = appName,
        packageName = appState?.packageName.orEmpty(),
        installedVersion = appState?.version,
        installedVersionCode = appState?.versionCode,
        installedDate = appState?.formattedInstallDate,
        installingPackageName = appState?.installingPackageName,
        splitNames = appState?.splitNames.orEmpty(),
        installedTryBuild = appState?.takeIf { appName == FENIX_DEBUG }?.let(::matchingInstalledTryBuild),
        apks = ApksResult.Success(apks.map { it.toUiModel() }),
        selectedReleaseVersion = selectedReleaseVersion,
        availableReleaseVersions = availableReleaseVersions,
    )

    private fun CachedHomeApk.toUiModel(): ApkUiModel {
        val parsed = MozillaArchiveApk(originalString, rawDateString, appName, version, abiName, fullUrl, fileName)
        return convertParsedApksToUiModels(listOf(parsed)).single()
    }

    private fun toCachedHomeApk(apk: ApkUiModel): CachedHomeApk = CachedHomeApk(
        originalString = apk.originalString,
        rawDateString = apk.uniqueKey.split('/').let { parts -> parts.getOrNull(1)?.takeIf { parts.size > 2 } },
        appName = apk.appName,
        version = apk.version,
        abiName = apk.abi.name.orEmpty(),
        fullUrl = apk.url,
        fileName = apk.fileName,
    )

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
            installedTryBuild = appState?.takeIf { repository.appName == FENIX_DEBUG }?.let(::matchingInstalledTryBuild),
            apks = apksResult,
            selectedReleaseVersion = selectedReleaseVersion,
            availableReleaseVersions = availableReleaseVersions,
        )
    }

    private fun convertParsedApksToUiModels(parsedApks: List<MozillaArchiveApk>): List<ApkUiModel> {
        return parsedApks.map { parsedApk ->
            val date = parsedApk.rawDateString?.formatNightlyBuildDate()
            val buildDate = parsedApk.rawDateString?.rawNightlyBuildDate()
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
                buildDate = buildDate,
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

    fun downloadNightlyApk(apkInfo: ApkUiModel) {
        if (apkInfo.downloadState is DownloadState.InProgress || apkInfo.downloadState is DownloadState.Downloaded) {
            return
        }

        val outputFile = File(apkInfo.apkDir, apkInfo.fileName)
        outputFile.parentFile?.mkdirs()
        downloadCoordinator.enqueue(
            ApkDownloadRequest(
                uniqueKey = apkInfo.uniqueKey,
                downloadUrl = apkInfo.url,
                outputFile = outputFile,
                appName = apkInfo.appName,
                fileName = apkInfo.fileName,
                cacheRelativePath = cacheRelativePathFor(apkInfo),
            ),
        )
    }

    fun installApk(file: File) {
        installCoordinator.install(file.absolutePath, file)
    }

    fun installHomeApk(apkInfo: ApkUiModel) {
        val file = File(apkInfo.apkDir, apkInfo.fileName)
        installCoordinator.install(apkInfo.uniqueKey, file)
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
        val chosenApks = ApksResult.Success(convertParsedApksToUiModels(chosen))

        updateCurrentApp(appName) {
            it.copy(apks = chosenApks, pendingBuildOptions = emptyList())
        }

        _homeScreenState.update { state ->
            if (state !is HomeScreenState.Loaded) return@update state
            val app = state.apps[appName] ?: return@update state
            state.copy(
                apps = state.apps + (
                    appName to app.copy(
                        apks = chosenApks,
                        pendingBuildOptions = emptyList(),
                    )
                    ),
            )
        }
    }

    /** Dismisses the multi-build prompt, leaving the latest build (already shown) in place. */
    fun onDismissBuildPicker(appName: String) {
        pendingBuildsByApp.remove(appName)
        updateCurrentApp(appName) { app -> app.copy(pendingBuildOptions = emptyList()) }
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
            NightlyBuildOption(id = rawDate, label = rawDate.formatNightlyBuildTimestamp())
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
            updateCurrentApp(appName) {
                it.copy(apks = ApksResult.Loading, selectedReleaseVersion = version)
            }
            _homeScreenState.value = currentState.copy(apps = updatedApps)

            val newApksResult = repository.getReleasesForVersion(version).toApksResult(appName)

            val latestState = _homeScreenState.value as? HomeScreenState.Loaded ?: return@launch
            val latestApp = latestState.apps[appName] ?: return@launch
            val finalUpdatedApps = latestState.apps.toMutableMap()
            finalUpdatedApps[appName] = latestApp.copy(
                apks = newApksResult,
                selectedReleaseVersion = version,
            )
            updateCurrentApp(appName) {
                it.copy(apks = newApksResult, selectedReleaseVersion = version)
            }

            _homeScreenState.value = latestState.copy(apps = finalUpdatedApps)
            syncLoadedStateDownloadStates()
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
            updateCurrentApp(appName) {
                it.copy(
                    userPickedDate = date,
                    apks = ApksResult.Loading,
                    pendingBuildOptions = emptyList(),
                )
            }

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
            updateCurrentApp(appName) {
                it.copy(
                    userPickedDate = date,
                    apks = newApksResult,
                    pendingBuildOptions = buildOptions,
                )
            }
            _homeScreenState.value = latestState.copy(apps = finalUpdatedApps)
            syncLoadedStateDownloadStates()
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

    fun openInstalledApp(packageName: String) {
        installCoordinator?.openInstalledApp(packageName) ?: mozillaPackageManager.launchApp(packageName)
    }

    private fun matchingInstalledTryBuild(appState: AppState): InstalledTryBuild? =
        matchingInstalledTryBuild(appState.packageName, appState.version, appState.versionCode)

    private fun matchingInstalledTryBuild(
        packageName: String,
        versionName: String?,
        versionCode: Long?,
    ): InstalledTryBuild? {
        val build = installedTryBuild ?: return null
        if (versionName == null || build.versionName == null) return null
        return build.takeIf {
            packageName == it.packageName &&
                versionName == it.versionName &&
                versionCode == it.versionCode
        }
    }

    fun dismissTryFoxCard() {
        tryFoxCardDismissed = true
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

    private fun syncLoadedStateDownloadStates() {
        val persistedDownloads = downloadStates.value
        _homeScreenState.update { currentState ->
            if (currentState !is HomeScreenState.Loaded) return@update currentState
            currentState.applyDownloadStates(persistedDownloads)
        }
    }

    private fun HomeScreenState.Loaded.applyDownloadStates(
        persistedDownloads: Map<String, PersistedDownloadState>,
    ): HomeScreenState.Loaded {
        val updatedApps = apps.mapValues { (_, app) -> app.withDownloadStates(persistedDownloads) }
        val updatedTryFoxApp = tryfoxApp?.withDownloadStates(persistedDownloads)
        val isDownloading = updatedApps.values.any { app -> app.containsActiveDownload() } ||
            updatedTryFoxApp?.containsActiveDownload() == true

        return copy(
            apps = updatedApps,
            tryfoxApp = updatedTryFoxApp,
            isDownloadingAnyFile = isDownloading,
        )
    }

    private fun AppUiModel.withDownloadStates(
        persistedDownloads: Map<String, PersistedDownloadState>,
    ): AppUiModel {
        val apksResult = apks as? ApksResult.Success ?: return this
        val updatedApks = apksResult.apks.map { apk ->
            apk.copy(downloadState = resolveDownloadState(apk, persistedDownloads))
        }
        return copy(apks = ApksResult.Success(updatedApks))
    }

    private fun AppUiModel.containsActiveDownload(): Boolean =
        (apks as? ApksResult.Success)?.apks?.any { it.downloadState is DownloadState.InProgress } == true

    private fun resolveDownloadState(
        apk: ApkUiModel,
        persistedDownloads: Map<String, PersistedDownloadState>,
    ): DownloadState {
        val resolvedFile = File(apk.apkDir, apk.fileName)
        return persistedDownloads[apk.uniqueKey]?.toDownloadState(resolvedFile)
            ?: if (resolvedFile.exists()) {
                DownloadState.Downloaded(resolvedFile)
            } else {
                DownloadState.NotDownloaded
            }
    }

    private fun PersistedDownloadState.toDownloadState(file: File): DownloadState =
        when (status) {
            DownloadStatus.QUEUED,
            DownloadStatus.RUNNING,
                -> DownloadState.InProgress(
                progress = progress ?: 0f,
                isIndeterminate = totalBytes <= 0L,
            )
            DownloadStatus.SUCCEEDED -> if (file.exists()) {
                DownloadState.Downloaded(file)
            } else {
                DownloadState.NotDownloaded
            }
            DownloadStatus.FAILED -> DownloadState.DownloadFailed(errorMessage)
            DownloadStatus.CANCELED -> DownloadState.NotDownloaded
        }

    private fun cacheRelativePathFor(apkInfo: ApkUiModel): String? {
        val cacheRoot = cacheManager.getCacheDir(apkInfo.appName).parentFile ?: return null
        return apkInfo.apkDir.relativeToOrNull(cacheRoot)?.path
    }

    companion object {
        private const val TAG = "HomeViewModel"
    }
}
