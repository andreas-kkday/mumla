package com.mumble1111.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class DeviceStatusPayload {

    @Serializable
    data class Logout(
        @SerialName("security_key")
        val securityKey: String,
        @SerialName("device_id")
        val deviceId: String,
        @SerialName("tNo")
        val tNo: Int? = null,
        @SerialName("uNo")
        val uNo: Int? = null,
        @SerialName("type")
        @EncodeDefault
        val type: String = "logout"
    ) : DeviceStatusPayload()

    @Serializable
    data class CallAnswered(
        @SerialName("security_key")
        val securityKey: String,
        @SerialName("device_id")
        val deviceId: String,
        @SerialName("log_id")
        val logId: Int?,
        @SerialName("tNo")
        val tNo: Int? = null,
        @SerialName("uNo")
        val uNo: Int? = null,
        @SerialName("type")
        @EncodeDefault
        val type: String = "call_answered"
    ) : DeviceStatusPayload()

    @Serializable
    data class CallRejected(
        @SerialName("security_key")
        val securityKey: String,
        @SerialName("tNo")
        val tNo: Int? = null,
        @SerialName("uNo")
        val uNo: Int? = null,
        @SerialName("type")
        @EncodeDefault
        val type: String = "call_rejected"
    ) : DeviceStatusPayload()

    //TODO 這個參數很多種狀況
    @Serializable
    data class CallHangUp(
        @SerialName("security_key")
        val securityKey: String,
        @SerialName("tNo")
        val tNo: Int? = null,
        @SerialName("uNo")
        val uNo: Int? = null,
        @SerialName("type")
        @EncodeDefault
        val type: String = "call_hangup"
    ) : DeviceStatusPayload()

    @Serializable
    data class CallCancelled(
        @SerialName("security_key")
        val securityKey: String,
//        @SerialName("device_id")
//        val deviceId: String,
        @SerialName("log_id")
        val logId: Int?,
        @SerialName("tNo")
        val tNo: Int? = null,
        @SerialName("uNo")
        val uNo: Int? = null,
        @SerialName("type")
        @EncodeDefault
        val type: String = "call_cancelled"
    ) : DeviceStatusPayload()

    @Serializable
    data class CallMissedTalentToOrg(
        @SerialName("security_key")
        val securityKey: String,
        @SerialName("tNo")
        val tNo: Int?,
        @SerialName("callee_uNo")
        val calleeUNo: Int?,
        @SerialName("log_id")
        val logId: Int?,
        @SerialName("type")
        @EncodeDefault
        val type: String = "call_missed"
    ) : DeviceStatusPayload()

    @Serializable
    data class CallMissedOrgToTalent(
        @SerialName("security_key")
        val securityKey: String,
        @SerialName("uNo")
        val uNo: Int?,
        @SerialName("callee_tNo")
        val calleeTNo: Int?,
        @SerialName("log_id")
        val logId: Int?,
        @SerialName("type")
        @EncodeDefault
        val type: String = "call_missed"
    ) : DeviceStatusPayload()

    @Serializable
    data class CallDisconnectedOrg(
        @SerialName("security_key")
        val securityKey: String,
        @SerialName("uNo")
        val uNo: Int?,
        @SerialName("device_id")
        val deviceId: String,
        @SerialName("log_id")
        val logId: Int?,
        @SerialName("type")
        @EncodeDefault
        val type: String = "call_disconnected"
    ) : DeviceStatusPayload()

    @Serializable
    data class CallDisconnectedTalent(
        @SerialName("security_key")
        val securityKey: String,
        @SerialName("tNo")
        val tNo: Int?,
        @SerialName("device_id")
        val deviceId: String,
        @SerialName("log_id")
        val logId: Int?,
        @SerialName("type")
        @EncodeDefault
        val type: String = "call_disconnected"
    ) : DeviceStatusPayload()
}