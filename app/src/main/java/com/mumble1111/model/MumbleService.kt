package com.mumble1111.model

import timber.log.Timber

object MumbleService {
    private var _isTestEnv: Boolean? = null
    private var _apiSecurityKey: String? = null

    fun init(isTestEnv: Boolean, apiSecurityKey: String) {
        _isTestEnv = isTestEnv
        _apiSecurityKey = apiSecurityKey
        Timber.d("Mumble Init $_isTestEnv $_apiSecurityKey")
    }

    fun isTestEnv(): Boolean {
        assert(_isTestEnv != null, { "MumbleService.init() must be called first" })
        Timber.d("Mumble isTest $_isTestEnv")
        return _isTestEnv!!
    }

    fun getApiSecurityKey(): String {
        assert(_apiSecurityKey != null, { "MumbleService.init() must be called first" })
        return _apiSecurityKey!!
    }

}