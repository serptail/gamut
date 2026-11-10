package com.gamut.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MusicBrainzSearchResponse(val recordings: List<MusicBrainzRecordingDto> = emptyList())

@Serializable
data class MusicBrainzRecordingDto(
    val id: String,
    val title: String,
    val length: Long? = null,
    @SerialName("artist-credit") val artistCredit: List<MusicBrainzArtistCreditDto> = emptyList(),
    val releases: List<MusicBrainzReleaseDto> = emptyList(),
    val isrcs: List<String> = emptyList(),
    val score: Int? = null,
)

@Serializable
data class MusicBrainzArtistCreditDto(
    val name: String? = null,
    val artist: MusicBrainzArtistDto? = null,
)

@Serializable
data class MusicBrainzArtistDto(val name: String)

@Serializable
data class MusicBrainzReleaseDto(
    val id: String,
    val title: String? = null,
)

@Serializable
data class CoverArtResponse(val images: List<CoverArtImageDto> = emptyList())

@Serializable
data class CoverArtImageDto(
    val front: Boolean = false,
    val image: String? = null,
    val thumbnails: CoverArtThumbnailsDto? = null,
)

@Serializable
data class CoverArtThumbnailsDto(
    @SerialName("250") val small: String? = null,
    @SerialName("500") val medium: String? = null,
    @SerialName("1200") val large: String? = null,
)

@Serializable
data class ItunesSearchResponse(
    val resultCount: Int = 0,
    val results: List<ItunesTrackDto> = emptyList(),
)

@Serializable
data class ItunesTrackDto(
    val trackId: Long? = null,
    val trackName: String? = null,
    val artistName: String? = null,
    val collectionName: String? = null,
    val trackTimeMillis: Long? = null,
    val artworkUrl100: String? = null,
    val kind: String? = null,
)

@Serializable
data class YoutubeSearchRequest(val query: String)

@Serializable
data class YoutubeSearchResponse(val candidates: List<YoutubeCandidateDto>)

@Serializable
data class YoutubeCandidateDto(
    val videoId: String,
    val title: String,
    val channelName: String,
    val channelIsTopicChannel: Boolean,
    val durationMs: Long,
    val viewCount: Long,
    val category: String = "songs",
    val isMusicCatalog: Boolean = false,
)

@Serializable
data class ResolvedSourceDto(
    val isrc: String,
    val youtubeVideoId: String,
    val resolutionMethod: String,
    val confidenceScore: Float,
    val verifiedCount: Int,
)

@Serializable
data class AudioStreamDto(val url: String, val expiresAtEpochSeconds: Long? = null)

fun YoutubeCandidateDto.toDomain() = YoutubeCandidate(
    videoId,
    title,
    channelName,
    channelIsTopicChannel,
    durationMs,
    viewCount,
    category,
    isMusicCatalog,
)

@Serializable
data class DeezerArtistSearchResponse(val data: List<DeezerArtistDto> = emptyList())

@Serializable
data class DeezerArtistDto(
    val id: Long,
    val name: String,
    @SerialName("picture_xl") val pictureXl: String? = null,
    @SerialName("picture_big") val pictureBig: String? = null,
    @SerialName("nb_fan") val fanCount: Long? = null,
)

@Serializable
data class DeezerTrackResponse(val data: List<DeezerTrackDto> = emptyList())

@Serializable
data class DeezerAlbumResponse(val data: List<DeezerAlbumDto> = emptyList())

@Serializable
data class DeezerTrackDto(
    val id: Long,
    val title: String,
    @SerialName("title_short") val titleShort: String? = null,
    @SerialName("title_version") val titleVersion: String? = null,
    val duration: Long = 0L,
    val rank: Long = 0L,
    val isrc: String? = null,
    val artist: DeezerArtistDto,
    val album: DeezerAlbumDto = DeezerAlbumDto(),
)

@Serializable
data class DeezerAlbumDto(
    val id: Long = 0L,
    val title: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("cover_xl") val coverXl: String? = null,
    @SerialName("cover_big") val coverBig: String? = null,
    @SerialName("record_type") val recordType: String? = null,
    val artist: DeezerArtistDto? = null,
)

@Serializable
data class AppleChartResponse(val feed: AppleChartFeed)

@Serializable
data class AppleChartFeed(val results: List<AppleChartItem> = emptyList())

@Serializable
data class AppleChartItem(
    val id: String,
    val artistName: String,
    val name: String,
    val releaseDate: String? = null,
    val artistId: String? = null,
    val artworkUrl100: String? = null,
    val url: String? = null,
)

@Serializable
data class BillboardChartDto(
    val chart: String = "",
    val date: String? = null,
    val entries: List<BillboardEntryDto> = emptyList(),
)

@Serializable
data class BillboardEntryDto(
    val rank: Int,
    val title: String,
    val artist: String,
    val weeks: Int = 0,
    val image: String? = null,
)

@Serializable
data class AppleLookupResponse(val resultCount: Int = 0, val results: List<AppleLookupItem> = emptyList())

@Serializable
data class AppleLookupItem(
    val wrapperType: String? = null,
    val artistId: Long? = null,
    val artistName: String? = null,
    val collectionId: Long? = null,
    val collectionName: String? = null,
    val trackId: Long? = null,
    val trackName: String? = null,
    val trackTimeMillis: Long? = null,
    val artworkUrl100: String? = null,
    val releaseDate: String? = null,
    val kind: String? = null,
)

@Serializable
data class BillboardHot100Dto(val date: String = "", val data: List<BillboardSongDto> = emptyList())

@Serializable
data class BillboardSongDto(
    val song: String,
    val artist: String,
    @SerialName("this_week") val thisWeek: Int,
    @SerialName("last_week") val lastWeek: Int? = null,
    @SerialName("peak_position") val peak: Int = 0,
    @SerialName("weeks_on_chart") val weeks: Int = 0,
)
