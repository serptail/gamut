package com.gamut.smoke

import java.util.concurrent.TimeUnit
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request as OkHttpRequest
import okhttp3.RequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

private val VIDEOS = listOf("dQw4w9WgXcQ", "9bZkp7q19f0", "kJQP7kiw5Fk", "JGwWNGJdvx8", "fJ9rUzIMcZQ")

fun main() {
    val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()
    NewPipe.init(SmokeDownloader(client))

    var passed = 0
    var total = 0
    for (id in VIDEOS) {
        total++
        if (attempt("stream $id") { streamPlays(client, id) }) passed++
    }
    total++
    if (attempt("song search") { searchWorks() }) passed++

    println("SMOKE_RESULT passed=$passed total=$total")
}

private fun attempt(label: String, block: () -> Boolean): Boolean {
    repeat(2) { n ->
        try {
            if (block()) {
                println("ok    $label")
                return true
            }
            println("fail  $label (try ${n + 1})")
        } catch (e: Exception) {
            println("fail  $label (try ${n + 1}): ${e.javaClass.simpleName}: ${e.message?.take(160)}")
        }
        Thread.sleep(2_000)
    }
    return false
}

private fun streamPlays(client: OkHttpClient, videoId: String): Boolean {
    val info = StreamInfo.getInfo("https://www.youtube.com/watch?v=$videoId")
    val stream = info.audioStreams
        .filter { it.isUrl && it.content.isNotBlank() }
        .maxByOrNull { it.averageBitrate } ?: return false
    val request = OkHttpRequest.Builder().url(stream.content).header("Range", "bytes=0-2047").build()
    client.newCall(request).execute().use { response ->
        if (response.code != 200 && response.code != 206) return false
        return (response.body?.bytes()?.size ?: 0) >= 1024
    }
}

private fun searchWorks(): Boolean {
    val service = NewPipe.getService("YouTube")
    val handler = service.getSearchQHFactory().fromQuery(
        "Rick Astley Never Gonna Give You Up",
        listOf(YoutubeSearchQueryHandlerFactory.MUSIC_SONGS),
        "",
    )
    return SearchInfo.getInfo(service, handler).relatedItems.filterIsInstance<StreamInfoItem>().isNotEmpty()
}

private class SmokeDownloader(private val client: OkHttpClient) : Downloader() {
    override fun execute(request: Request): Response {
        val headers = Headers.Builder().apply {
            request.headers().forEach { (name, values) -> values.forEach { value -> add(name, value) } }
        }.build()
        val body = request.dataToSend()?.let { RequestBody.create(null, it) }
        val call = OkHttpRequest.Builder()
            .url(request.url())
            .headers(headers)
            .method(request.httpMethod(), body)
            .build()
        client.newCall(call).execute().use { response ->
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string().orEmpty(),
                response.request.url.toString(),
            )
        }
    }
}
