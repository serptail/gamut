package com.gamut.feature.search

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class MicRecorder @Inject constructor() {
    @SuppressLint("MissingPermission")
    suspend fun record(onChunk: suspend (ByteArray) -> Boolean) = withContext(Dispatchers.IO) {
        val chunkBytes = SAMPLE_RATE * BYTES_PER_SAMPLE / 10
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) throw IOException("The microphone isn't available")
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, chunkBytes * 4),
        )
        try {
            if (recorder.state != AudioRecord.STATE_INITIALIZED) throw IOException("The microphone isn't available")
            recorder.startRecording()
            val buffer = ByteArray(chunkBytes)
            while (isActive) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read < 0) throw IOException("The microphone stopped working")
                if (read == 0) continue
                if (!onChunk(buffer.copyOf(read))) break
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val BYTES_PER_SAMPLE = 2
        const val BYTES_PER_SECOND = SAMPLE_RATE * BYTES_PER_SAMPLE
    }
}
