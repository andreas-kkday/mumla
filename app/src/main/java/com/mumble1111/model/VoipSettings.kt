package com.mumble1111.model

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VoipSettingPayload(
    @SerialName("security_key")
    val securityKey: String,
    //求職, 求才的話要換成 uNo
    @SerialName("tNo")
    val tNo: Int,
    @SerialName("voip_setting")
    val voipSetting: VoipSetting
)

@Serializable
data class GetVoipSettingPayload(
    @SerialName("security_key")
    val securityKey: String,
    //求職, 應該只能查詢廠商的, 所以這裡是 uNo
    @SerialName("uNo")
    val uNo: Int,
)

@Serializable
data class VoipSetting(
    @SerialName("voip_block")
    val voipBlock: Boolean,
    val weekdays: VoipSettingItem? = null,
    val weekend: VoipSettingItem? = null
)

@Serializable
data class VoipSettingItem private constructor(
    val week: List<Int>,
    val time: String
) {
    companion object {
        fun from(
            dayOfWeek: List<DayOfWeek>?,
            startTime: LocalTime?,
            endTime: LocalTime?
        ): VoipSettingItem? {
            val dateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
            if (dayOfWeek == null || startTime == null || endTime == null || dayOfWeek.isEmpty()) {
                return null
            }

            return VoipSettingItem(
                week = dayOfWeek.map { it.value },
                time = "${startTime.format(dateTimeFormatter)}-${endTime.format(dateTimeFormatter)}"
            )
        }
    }
}