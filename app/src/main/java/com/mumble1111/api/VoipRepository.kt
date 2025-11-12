package com.mumble1111.api

import com.mumble1111.model.CallPayload
import com.mumble1111.model.GetVoipSettingPayload
import com.mumble1111.model.MumbleInfo
import com.mumble1111.model.UpdateTokenBody
import com.mumble1111.model.VoipSetting
import com.mumble1111.model.VoipSettingPayload
import java.util.UUID


class VoipRepository(
    private val voipApiService: VoipApiService,
    private val i18nApiKey: String,
) {
    suspend fun updateFcmToken(
        talentNo: String,
        fcmToken: String,
        deviceId: String
    ): Result<UpdateTokenResponse> {
        return voipApiService.updateFcmToken(
            UpdateTokenBody(
                securityKey = i18nApiKey,
                tNo = talentNo,
                deviceId = deviceId,
                voipToken = fcmToken,
                apnsToken = fcmToken,
                platform = "16"
            )
        )
    }


    suspend fun setVoipSetting(tNo: Int, voipSetting: VoipSetting) : Result<String> {
        return voipApiService.setVoipSetting(
            VoipSettingPayload(
                securityKey = i18nApiKey,
                tNo = tNo,
                voipSetting = voipSetting
            )
        )
    }
    suspend fun getVoipSetting(uNo: Int): Result<String> {
        return voipApiService.getVoipSetting(
            GetVoipSettingPayload(
                securityKey = i18nApiKey,
                uNo = uNo
            )
        )
    }

    suspend fun makeCallToOrganization(talentNo: Int, uNo: Int, talentName: String, talentDeviceId: String, jobId: Int): Result<String> {
        return voipApiService.makeCall(
            CallPayload.CallPayloadToOrganization(
                securityKey = i18nApiKey,
                callerNo = talentNo,
                calleeNo = uNo,
                name = talentName,
                deviceId = talentDeviceId,
                jobId = jobId,
                mumbleInfo = MumbleInfo(
                    targetNode = "5345",
                    channelName = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    oLogo = "https://picsum.photos/seed/$talentNo/128/128"
                )
            )
        )

    }
    suspend fun makeCallToTalent(uNo: Int, talentNo: Int, uName: String, uDeviceId: String, jobId: Int): Result<String> {
        return voipApiService.makeCall(
            CallPayload.CallPayloadToTalent(
                securityKey = i18nApiKey,
                callerNo = uNo,
                calleeNo = talentNo,
                name = uName,
                deviceId = uDeviceId,
                jobId = jobId,
                mumbleInfo = MumbleInfo(
                    targetNode = "5345",
                    channelName = UUID.randomUUID().toString(),
                    timestamp = System.currentTimeMillis(),
                    oLogo = "https://picsum.photos/seed/$uNo/128/128"
                )
            )
        )
    }
}
