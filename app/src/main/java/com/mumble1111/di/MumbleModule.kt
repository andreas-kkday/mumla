package com.mumble1111.di

import com.mumble1111.api.VoipApiService
import com.mumble1111.api.VoipRepository
import com.mumble1111.model.MumbleService
import com.skydoves.retrofit.adapters.result.ResultCallAdapterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

private const val MUMBLE_RETROFIT = "MumbleRetrofit"
private const val MUMBLE_TEST_RETROFIT = "MumbleTestRetrofit"

private const val BASE_URL_I18N = "https://api.1111job.app/"
private const val BASE_URL_I18N_TEST = "https://uat-api.1111job.app/"
private const val I18N_OKHTTP = "I18NOkHttp"
val mumbleModule = module {
    single(named(MUMBLE_TEST_RETROFIT)) {
        Retrofit.Builder()
            .baseUrl(BASE_URL_I18N_TEST)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(
                Json.asConverterFactory(
                    "application/json; charset=UTF8".toMediaType()
                )
            )
            .addCallAdapterFactory(ResultCallAdapterFactory.create())
            .client(get(named(I18N_OKHTTP)))
            .build()
    }

    single(named(MUMBLE_RETROFIT)) {
        Retrofit.Builder()
            .baseUrl(BASE_URL_I18N)
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(
                Json.asConverterFactory(
                    "application/json; charset=UTF8".toMediaType()
                )
            )
            .addCallAdapterFactory(ResultCallAdapterFactory.create())
            .client(get(named(I18N_OKHTTP)))
            .build()
    }

    single {
        val retrofitName = if (MumbleService.isTestEnv()) {
            MUMBLE_TEST_RETROFIT
        } else {
            MUMBLE_RETROFIT
        }
        get<Retrofit>(named(retrofitName)).create(VoipApiService::class.java)
    }

    single {
        VoipRepository(
            voipApiService = get(),
            MumbleService.getApiSecurityKey()
        )
    }
}