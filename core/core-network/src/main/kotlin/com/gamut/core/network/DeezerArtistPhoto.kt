package com.gamut.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeezerPhotoSearchResponse(val data: List<DeezerPhotoArtist> = emptyList())

@Serializable
data class DeezerPhotoArtist(
    val id: Long,
    val name: String = "",
    @SerialName("nb_fan") val nbFan: Long = 0,
    @SerialName("picture_xl") val pictureXl: String? = null,
    @SerialName("picture_big") val pictureBig: String? = null,
)

@Serializable
data class DeezerTitleResponse(val data: List<DeezerTitle> = emptyList())

@Serializable
data class DeezerTitle(val title: String = "")
