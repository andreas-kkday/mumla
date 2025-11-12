package com.mumble1111.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Parcelize
@Serializable
data class MumbleInfo(
    val targetNode: String = "",
    val channelName: String = "", //UUID
    val timestamp: Long = 0,
    val callerName: String = "",
    val oLogo: String = ""
) : Parcelable {
    companion object {
        fun fromMap(map: Map<String, Any>?): MumbleInfo {
            return MumbleInfo()
        }
    }
}