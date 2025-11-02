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
import org.mozilla.tryfox.data.DefaultUserDataRepository
import org.mozilla.tryfox.data.FenixRepository
import org.mozilla.tryfox.data.GithubRepository
import org.mozilla.tryfox.data.GithubRepositoryImpl
import org.mozilla.tryfox.data.IFenixRepository
import org.mozilla.tryfox.data.MozillaArchiveRepository
import org.mozilla.tryfox.data.MozillaArchiveRepositoryImpl
import org.mozilla.tryfox.data.MozillaPackageManager
import org.mozilla.tryfox.data.UserDataRepository
import org.mozilla.tryfox.data.managers.CacheManager
import org.mozilla.tryfox.data.managers.DefaultCacheManager
import org.mozilla.tryfox.data.managers.DefaultIntentManager
import org.mozilla.tryfox.data.managers.IntentManager
import org.mozilla.tryfox.network.GithubApiService
import org.mozilla.tryfox.network.TreeherderApiService
import org.mozilla.tryfox.ui.screens.HomeViewModel
import org.mozilla.tryfox.ui.screens.ProfileViewModel
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

const val TREEHERDER_BASE_URL = "https://treeherder.mozilla.org/api/"
const val GITHUB_BASE_URL = "https://api.github.com/"

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

    single<TreeherderApiService> {
        val treeherderRetrofit: Retrofit = get(named("treeherderRetrofit"))
        treeherderRetrofit.create(TreeherderApiService::class.java)
    }

    single<GithubApiService> {
        val githubRetrofit: Retrofit = get(named("githubRetrofit"))
        githubRetrofit.create(GithubApiService::class.java)
    }
}

val repositoryModule = module {
    single<IFenixRepository> { FenixRepository(get()) }
    single<UserDataRepository> { DefaultUserDataRepository(androidContext()) }
    single<MozillaArchiveRepository> { MozillaArchiveRepositoryImpl(get()) }
    single<GithubRepository> { GithubRepositoryImpl(get()) }
    single<MozillaPackageManager> { DefaultMozillaPackageManager(androidContext()) }
    single<CacheManager> { DefaultCacheManager(androidContext().cacheDir, get(named("IODispatcher"))) }
    single<IntentManager> { DefaultIntentManager(androidContext()) }
}

val viewModelModule = module {
    viewModel { params -> TryFoxViewModel(get(), get(), params.getOrNull(), params.getOrNull()) }
    viewModel { HomeViewModel(get(), get(), get(), get(), get(), get(), get(named("IODispatcher"))) }
    viewModel { params -> ProfileViewModel(get(), get(), get(), get(),params.getOrNull()) }
}

val appModules = listOf(dispatchersModule, networkModule, repositoryModule, viewModelModule)
