package org.mozilla.fenixinstaller.di

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import org.mozilla.fenixinstaller.BuildConfig
import org.mozilla.fenixinstaller.FenixInstallerViewModel
import org.mozilla.fenixinstaller.data.DefaultUserDataRepository
import org.mozilla.fenixinstaller.data.FenixRepository
import org.mozilla.fenixinstaller.data.IFenixRepository
import org.mozilla.fenixinstaller.data.MozillaArchiveRepository
import org.mozilla.fenixinstaller.data.MozillaArchiveRepositoryImpl
import org.mozilla.fenixinstaller.data.MozillaPackageManager
import org.mozilla.fenixinstaller.data.UserDataRepository
import org.mozilla.fenixinstaller.network.ApiService
import org.mozilla.fenixinstaller.ui.screens.HomeViewModel
import org.mozilla.fenixinstaller.ui.screens.ProfileViewModel
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

const val TREEHERDER_BASE_URL = "https://treeherder.mozilla.org/api/"
const val ARCHIVE_MOZILLA_BASE_URL = "https://archive.mozilla.org/"

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
    single { MozillaPackageManager(androidContext().packageManager) }
}

val viewModelModule = module {
    viewModel { FenixInstallerViewModel(get()) }
    viewModel { HomeViewModel(get(), get(), get(), Dispatchers.IO) }
    viewModel { ProfileViewModel(get(), get()) }
}

val appModules = listOf(networkModule, repositoryModule, viewModelModule)
