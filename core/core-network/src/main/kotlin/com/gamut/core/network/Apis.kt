package com.gamut.core.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface MusicBrainzApi {
    @GET("recording")
    suspend fun searchRecordings(
        @Query("query") query: String,
        @Query("fmt") format: String = "json",
        @Query("limit") limit: Int = 25,
        @Query("inc") includes: String = "isrcs",
    ): MusicBrainzSearchResponse
}

interface CoverArtApi {
    @GET("release/{mbid}")
    suspend fun getCoverArt(@Path("mbid") releaseMbid: String): Response<CoverArtResponse>
}

interface ItunesApi {
    @GET("search")
    suspend fun search(
        @Query("term") term: String,
        @Query("media") media: String = "music",
        @Query("entity") entity: String = "song",
        @Query("limit") limit: Int = 25,
        @Query("country") country: String = "US",
    ): Response<ItunesSearchResponse>

    @GET("lookup")
    suspend fun lookupAll(
        @Query("id") id: String,
        @Query("entity") entity: String? = null,
        @Query("limit") limit: Int = 200,
    ): AppleLookupResponse

    @GET("search")
    suspend fun searchArtists(
        @Query("term") term: String,
        @Query("entity") entity: String = "musicArtist",
        @Query("limit") limit: Int = 10,
    ): AppleLookupResponse

    @GET("lookup")
    suspend fun lookup(
        @Query("id") id: String,
        @Query("entity") entity: String = "song",
    ): Response<ItunesSearchResponse>
}

interface DeezerApi {
    @GET("search/artist")
    suspend fun searchArtists(@Query("q") query: String, @Query("limit") limit: Int = 5): DeezerArtistSearchResponse

    @GET("artist/{artistId}/top")
    suspend fun topTracks(@Path("artistId") artistId: Long, @Query("limit") limit: Int = 100): DeezerTrackResponse

    @GET("search")
    suspend fun searchTracks(@Query("q") query: String, @Query("limit") limit: Int = 25): DeezerTrackResponse

    @GET("chart/0/tracks")
    suspend fun chartTracks(@Query("limit") limit: Int = 25): DeezerTrackResponse

    @GET("chart/0/albums")
    suspend fun chartAlbums(@Query("limit") limit: Int = 12): DeezerAlbumResponse

    @GET("chart/0/artists")
    suspend fun chartArtists(@Query("limit") limit: Int = 12): DeezerArtistSearchResponse

    @GET("artist/{artistId}/albums")
    suspend fun artistAlbums(
        @Path("artistId") artistId: Long,
        @Query("limit") limit: Int = 100,
        @Query("index") index: Int = 0,
    ): DeezerAlbumResponse

    @GET("artist/{artistId}/related")
    suspend fun relatedArtists(@Path("artistId") artistId: Long, @Query("limit") limit: Int = 20): DeezerArtistSearchResponse

    @GET("artist/{artistId}")
    suspend fun artist(@Path("artistId") artistId: Long): DeezerArtistDto

    @GET("search/artist")
    suspend fun searchArtistPhotos(@Query("q") query: String, @Query("limit") limit: Int = 10): DeezerPhotoSearchResponse

    @GET("artist/{artistId}/albums")
    suspend fun artistAlbumTitles(@Path("artistId") artistId: Long, @Query("limit") limit: Int = 100): DeezerTitleResponse

    @GET("album/{albumId}/tracks")
    suspend fun albumTracks(@Path("albumId") albumId: Long, @Query("limit") limit: Int = 100): DeezerTrackResponse
}

interface AppleChartsApi {
    @GET("music/most-played/{limit}/songs.json")
    suspend fun topSongs(@Path("limit") limit: Int = 10): AppleChartResponse

    @GET("music/most-played/{limit}/albums.json")
    suspend fun topAlbums(@Path("limit") limit: Int = 10): AppleChartResponse
}

interface YoutubeMusicApi {
    @POST("search")
    suspend fun search(@Body request: YoutubeSearchRequest): YoutubeSearchResponse
}

interface ResolverSyncApi {
    @GET("resolve/{isrc}")
    suspend fun get(@Path("isrc") isrc: String): Response<ResolvedSourceDto>

    @POST("resolve")
    suspend fun save(@Body source: ResolvedSourceDto): Response<Unit>

    @GET("charts/{name}")
    suspend fun chart(@Path("name") name: String, @Query("limit") limit: Int = 50): Response<BillboardChartDto>

    @GET("youtube/search")
    suspend fun searchYoutube(@Query("query") query: String): YoutubeSearchResponse

    @GET("youtube/stream/{videoId}")
    suspend fun youtubeStream(@Path("videoId") videoId: String): Response<AudioStreamDto>
}

interface BillboardApi {
    @GET("mhollingshead/billboard-hot-100/main/recent.json")
    suspend fun recent(): BillboardHot100Dto
}
