package com.gamut.core.database

import com.gamut.core.model.DownloadedTrack
import com.gamut.core.model.ResolutionMethod
import com.gamut.core.model.ResolvedSource
import com.gamut.core.model.Track
import com.gamut.core.model.TrackSource

fun TrackEntity.toDomain() = Track(
    isrc = isrc,
    musicBrainzId = musicBrainzId,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    albumArtUrl = albumArtUrl,
    source = runCatching { TrackSource.valueOf(source) }.getOrDefault(TrackSource.MUSICBRAINZ),
    youtubeVideoId = youtubeVideoId,
)

fun Track.toEntity() = TrackEntity(
    isrc = isrc,
    musicBrainzId = musicBrainzId,
    title = title,
    artist = artist,
    album = album,
    durationMs = durationMs,
    albumArtUrl = albumArtUrl,
    source = source.name,
    youtubeVideoId = youtubeVideoId,
)

fun ResolvedSourceEntity.toDomain() = ResolvedSource(isrc, youtubeVideoId, ResolutionMethod.valueOf(resolutionMethod), confidenceScore, verifiedCount)
fun ResolvedSource.toEntity() = ResolvedSourceEntity(isrc, youtubeVideoId, resolutionMethod.name, confidenceScore, verifiedCount)
fun DownloadEntity.toDomain() = DownloadedTrack(isrc, localFilePath, downloadedAtEpochMs, fileSizeBytes)
fun DownloadedTrack.toEntity() = DownloadEntity(isrc, localFilePath, downloadedAtEpochMs, fileSizeBytes)
