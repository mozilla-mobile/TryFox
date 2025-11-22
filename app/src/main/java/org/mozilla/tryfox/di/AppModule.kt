package org.mozilla.tryfox.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module
import org.mozilla.tryfox.BuildConfig
import org.mozilla.tryfox.TryFoxViewModel
import org.mozilla.tryfox.data.DefaultMozillaPackageManager
import org.mozilla.tryfox.data.MozillaPackageManager
import org.mozilla.tryfox.data.managers.CacheManager
import org.mozilla.tryfox.data.managers.DefaultCacheManager
import org.mozilla.tryfox.data.managers.DefaultIntentManager
import org.mozilla.tryfox.data.managers.IntentManager
import org.mozilla.tryfox.data.repositories.DefaultDownloadFileRepository
import org.mozilla.tryfox.data.repositories.DefaultMozillaArchiveRepository
import org.mozilla.tryfox.data.repositories.DefaultTreeherderRepository
import org.mozilla.tryfox.data.repositories.DefaultUserDataRepository
import org.mozilla.tryfox.data.repositories.DownloadFileRepository
import org.mozilla.tryfox.data.repositories.FenixBetaReleaseRepository
import org.mozilla.tryfox.data.repositories.FenixReleaseReleaseRepository
import org.mozilla.tryfox.data.repositories.FenixReleaseRepository
import org.mozilla.tryfox.data.repositories.FocusReleaseRepository
import org.mozilla.tryfox.data.repositories.MozillaArchiveRepository
import org.mozilla.tryfox.data.repositories.ReferenceBrowserReleaseRepository
import org.mozilla.tryfox.data.repositories.ReleaseRepository
import org.mozilla.tryfox.data.repositories.TreeherderRepository
import org.mozilla.tryfox.data.repositories.TryFoxReleaseRepository
import org.mozilla.tryfox.data.repositories.UserDataRepository
import org.mozilla.tryfox.network.DownloadApiService
import org.mozilla.tryfox.network.GithubApiService
import org.mozilla.tryfox.network.MozillaArchivesApiService
import org.mozilla.tryfox.network.TreeherderApiService
import org.mozilla.tryfox.ui.screens.HomeViewModel
import org.mozilla.tryfox.ui.screens.ProfileViewModel
import org.mozilla.tryfox.util.FENIX
import org.mozilla.tryfox.util.FENIX_BETA
import org.mozilla.tryfox.util.FENIX_RELEASE
import org.mozilla.tryfox.util.FOCUS
import org.mozilla.tryfox.util.REFERENCE_BROWSER
import org.mozilla.tryfox.util.TRYFOX
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

const val TREEHERDER_BASE_URL = "https://treeherder.mozilla.org/api/"
const val GITHUB_BASE_URL = "https://api.github.com/"
const val ARCHIVE_MOZILLA_BASE_URL = "https://archive.mozilla.org/"

val dispatchersModule = module {
    single<CoroutineDispatcher>(named("IODispatcher")) { Dispatchers.IO }
    single<CoroutineDispatcher>(named("DefaultDispatcher")) { Dispatchers.Default }
    single<CoroutineDispatcher>(named("MainDispatcher")) { Dispatchers.Main }
}

val networkModule = module {
    single {
        OkHttpClient.Builder().apply {
            if (BuildConfig.DEBUG) {
                val loggingInterceptor = HttpLoggingInterceptor()
                loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.HEADERS)
                addInterceptor(loggingInterceptor)
            }
        }.build()
    }

    single(named("treeherderRetrofit")) {
        val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }
        Retrofit.Builder()
            .baseUrl(TREEHERDER_BASE_URL)
            .client(get<OkHttpClient>())
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    single(named("githubRetrofit")) {
        val json = Json {
            ignoreUnknownKeys = true
        }
        Retrofit.Builder()
            .baseUrl(GITHUB_BASE_URL)
            .client(get<OkHttpClient>())
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    single(named("mozillaArchiveRetrofit")) {
        Retrofit.Builder()
            .baseUrl(ARCHIVE_MOZILLA_BASE_URL)
            .client(get<OkHttpClient>())
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()
    }

    single<TreeherderApiService> {
        val treeherderRetrofit: Retrofit = get(named("treeherderRetrofit"))
        treeherderRetrofit.create(TreeherderApiService::class.java)
    }

    single<GithubApiService> {
        val githubRetrofit: Retrofit = get(named("githubRetrofit"))
        githubRetrofit.create(GithubApiService::class.java)
    }

    single<DownloadApiService> {
        val treeherderRetrofit: Retrofit =
            get(named("treeherderRetrofit")) // Re-using treeherder retrofit for download as it's a generic download
        treeherderRetrofit.create(DownloadApiService::class.java)
    }

    single<MozillaArchivesApiService> {
        val mozillaArchiveRetrofit: Retrofit = get(named("mozillaArchiveRetrofit"))
        mozillaArchiveRetrofit.create(MozillaArchivesApiService::class.java)
    }
}

val repositoryModule = module {
    single<DownloadFileRepository> {
        DefaultDownloadFileRepository(
            get(),
            get(named("IODispatcher")),
        )
    }
    single<TreeherderRepository> { DefaultTreeherderRepository(get()) }
    single<MozillaArchiveRepository> { DefaultMozillaArchiveRepository(get()) }
    single<UserDataRepository> { DefaultUserDataRepository(androidContext()) }
    single<MozillaPackageManager> { DefaultMozillaPackageManager(androidContext()) }
    single<CacheManager> {
        DefaultCacheManager(
            androidContext().cacheDir,
            get(named("IODispatcher")),
        )
    }
    single<IntentManager> { DefaultIntentManager(androidContext()) }

    single<ReleaseRepository>(named(FENIX)) { FenixReleaseRepository(get()) }
    single<ReleaseRepository>(named(FENIX_RELEASE)) { FenixReleaseReleaseRepository(get()) }
    single<ReleaseRepository>(named(FENIX_BETA)) { FenixBetaReleaseRepository(get()) }
    single<ReleaseRepository>(named(FOCUS)) { FocusReleaseRepository(get()) }
    single<ReleaseRepository>(named(REFERENCE_BROWSER)) { ReferenceBrowserReleaseRepository() }
    single<ReleaseRepository>(named(TRYFOX)) { TryFoxReleaseRepository(get()) }
}

val viewModelModule = module {
    viewModel { params ->
        TryFoxViewModel(
            get(),
            get(),
            get(),
            get(),
            params.getOrNull(),
            params.getOrNull(),
        )
    }
    viewModel {
        val releaseRepositories = listOf(
            get<ReleaseRepository>(named(FENIX)),
            get<ReleaseRepository>(named(FENIX_RELEASE)),
            get<ReleaseRepository>(named(FENIX_BETA)),
            get<ReleaseRepository>(named(FOCUS)),
            get<ReleaseRepository>(named(REFERENCE_BROWSER)),
            get<ReleaseRepository>(named(TRYFOX)),
        )
        HomeViewModel(
            releaseRepositories,
            get(),
            get(),
            get(),
            get(),
            get(named("IODispatcher")),
        )
    }
    viewModel { params -> ProfileViewModel(get(), get(), get(), get(), get(), params.getOrNull()) }
}

val appModules = listOf(dispatchersModule, networkModule, repositoryModule, viewModelModule)
