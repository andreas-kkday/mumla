package com.mumble1111.api

import com.mumble1111.model.CallPayload
import com.mumble1111.model.DeviceStatusPayload
import com.mumble1111.model.GenericError
import com.mumble1111.model.GetVoipSettingPayload
import com.mumble1111.model.UpdateTokenBody
import com.mumble1111.model.VoipSettingPayload
import retrofit2.http.Body
import retrofit2.http.POST

typealias UpdateTokenResponse = GenericError

interface VoipApiService {
    //i18n api service
    @POST("/update-token")
    suspend fun updateFcmToken(@Body updateTokenBody: UpdateTokenBody): Result<UpdateTokenResponse>

    @POST("/set-voip-setting")
    suspend fun setVoipSetting(@Body voipSettings: VoipSettingPayload): Result<String>

    @POST("/get-voip-setting")
    suspend fun getVoipSetting(@Body getSettingPayload: GetVoipSettingPayload): Result<String>

    @POST("/call")
    suspend fun makeCall(@Body callPayload: CallPayload): Result<String>

    @POST("/update-device-status")
    suspend fun logout(
        @Body
        deviceStatusPayload: DeviceStatusPayload.Logout
    ): Result<String>

    @POST("/update-device-status")
    suspend fun answerCall(
        @Body
        deviceStatusPayload: DeviceStatusPayload.CallAnswered
    ): Result<String>
}
