package com.mumble1111.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class CallPayload {
    @Serializable
    data class CallPayloadToOrganization(
        @SerialName("security_key")
        val securityKey: String,
        @SerialName("caller_tNo")
        val callerNo: Int,
        @SerialName("callee_uNo")
        val calleeNo: Int,
        val name: String,
        @SerialName("device_id")
        val deviceId: String,
        @SerialName("job_id")
        val jobId: Int,
        @SerialName("mumble_info")
        val mumbleInfo: MumbleInfo
    ) : CallPayload()

    @Serializable
    data class CallPayloadToTalent(
        @SerialName("security_key")
        val securityKey: String,
        @SerialName("caller_uNo")
        val callerNo: Int,
        @SerialName("callee_tNo")
        val calleeNo: Int,
        val name: String,
        @SerialName("device_id")
        val deviceId: String,
        @SerialName("job_id")
        val jobId: Int,
        @SerialName("mumble_info")
        val mumbleInfo: MumbleInfo
    ) : CallPayload()

}


