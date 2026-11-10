package com.gamut.core.network

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface DeezerChartsApi {
    @GET("chart/{genre}/tracks")
    suspend fun chartTracks(@Path("genre") genre: Int, @Query("limit") limit: Int): DeezerTrackResponse

    @GET("chart/{genre}/albums")
    suspend fun chartAlbums(@Path("genre") genre: Int, @Query("limit") limit: Int): DeezerAlbumResponse

    @GET("chart/{genre}/artists")
    suspend fun chartArtists(@Path("genre") genre: Int, @Query("limit") limit: Int): DeezerArtistSearchResponse

    @GET("editorial/{genre}/releases")
    suspend fun newReleases(@Path("genre") genre: Int, @Query("limit") limit: Int): DeezerAlbumResponse

    @GET("search/artist")
    suspend fun searchArtists(@Query("q") query: String, @Query("limit") limit: Int): DeezerArtistSearchResponse

    @GET("artist/{id}/top")
    suspend fun artistTop(@Path("id") id: Long, @Query("limit") limit: Int): DeezerTrackResponse

    @GET("artist/{id}/related")
    suspend fun relatedArtists(@Path("id") id: Long, @Query("limit") limit: Int): DeezerArtistSearchResponse

    @GET("album/{id}/tracks")
    suspend fun albumTracks(@Path("id") id: Long, @Query("limit") limit: Int): DeezerTrackResponse
}
