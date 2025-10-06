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
import org.mozilla.tryfox.data.IFenixRepository
import org.mozilla.tryfox.data.MozillaArchiveRepository
import org.mozilla.tryfox.data.MozillaArchiveRepositoryImpl
import org.mozilla.tryfox.data.MozillaPackageManager
import org.mozilla.tryfox.data.UserDataRepository
import org.mozilla.tryfox.data.managers.CacheManager
import org.mozilla.tryfox.data.managers.DefaultCacheManager
import org.mozilla.tryfox.network.ApiService
import org.mozilla.tryfox.ui.screens.HomeViewModel
import org.mozilla.tryfox.ui.screens.ProfileViewModel
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

const val TREEHERDER_BASE_URL = "https://treeherder.mozilla.org/api/"
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

    single {
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

    single { get<Retrofit>().create(ApiService::class.java) }
}

val repositoryModule = module {
    single<IFenixRepository> { FenixRepository(get()) }
    single<UserDataRepository> { DefaultUserDataRepository(androidContext()) }
    single<MozillaArchiveRepository> { MozillaArchiveRepositoryImpl(get()) }
    single<MozillaPackageManager> { DefaultMozillaPackageManager(androidContext()) }
    single<CacheManager> { DefaultCacheManager(androidContext().cacheDir, get(named("IODispatcher"))) }
}

val viewModelModule = module {
    viewModel { TryFoxViewModel(get(), get()) }
    viewModel { HomeViewModel(get(), get(), get(), get(), get(named("IODispatcher"))) }
    viewModel { ProfileViewModel(get(), get(), get()) }
}

val appModules = listOf(dispatchersModule, networkModule, repositoryModule, viewModelModule)
