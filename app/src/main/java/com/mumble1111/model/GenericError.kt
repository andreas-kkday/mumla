package com.mumble1111.model

import com.google.gson.annotations.SerializedName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class GenericError(
    @SerializedName("message", alternate = arrayOf("error"))
    @JsonNames("message", "error")
    val message: String
)