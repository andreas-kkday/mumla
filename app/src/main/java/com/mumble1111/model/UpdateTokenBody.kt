package com.mumble1111.model

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Created by DDPlay123 on 2025/10/3.
 */
@Serializable
data class UpdateTokenBody(
    @SerializedName("security_key")
    @SerialName("security_key")
    val securityKey: String,
    //求職, 求才的話要換成 uNo
    @SerializedName("tNo")
    @SerialName("tNo")
    val tNo: String,
    @SerializedName("device_id")
    @SerialName("device_id")
    val deviceId: String,
    @SerializedName("voip_token")
    @SerialName("voip_token")
    val voipToken: String,
    @SerializedName("APNs_token")
    @SerialName("APNs_token")
    val apnsToken: String,
    val platform: String
)