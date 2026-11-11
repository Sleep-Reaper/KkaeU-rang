package com.sleepreaper.kkaeurang.voice

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlin.math.PI
import kotlin.math.sin

/**
 * "지금 말하세요" 신호음(띵).
 *
 * 소리 파일 없이 코드로 짧은 사인파를 만들어 재생한다.
 * 차량 오디오는 용도(usage)별로 출력 경로를 나누므로, 알림음 용도(ASSISTANCE_SONIFICATION)로 재생한다.
 */
internal class ListeningBeep {

    private val track: AudioTrack? = createTrack()

    /** 신호음을 처음부터 재생한다. 재생 중에 다시 부르면 처음부터 다시 울린다. 메인 스레드에서 불러도 막히지 않는다. */
    fun play() {
        val audioTrack = track ?: return
        try {
            // MODE_STATIC은 한 번 재생 후 위치가 끝에 있으므로, 멈추고 데이터를 다시 올려서 처음부터 재생한다
            audioTrack.stop()
            audioTrack.reloadStaticData()
            audioTrack.play()
        } catch (e: IllegalStateException) {
            Log.e(TAG, "신호음 재생 실패", e)
        }
    }

    fun release() {
        track?.release()
    }

    private fun createTrack(): AudioTrack? {
        val sampleCount = SAMPLE_RATE * DURATION_MS / 1000
        val fadeCount = SAMPLE_RATE * FADE_MS / 1000
        // 시작·끝을 부드럽게 줄여서 '틱' 하는 클릭음을 없앤다
        val pcm = ShortArray(sampleCount) { i ->
            val envelope = minOf(1.0, i / fadeCount.toDouble(), (sampleCount - 1 - i) / fadeCount.toDouble())
            val sample = sin(2 * PI * FREQUENCY_HZ * i / SAMPLE_RATE) * envelope * AMPLITUDE
            (sample * Short.MAX_VALUE).toInt().toShort()
        }

        return try {
            AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 2)
                .build()
                .apply { write(pcm, 0, pcm.size) }
        } catch (e: Exception) {
            // 신호음이 안 나도 듣기 기능 자체는 동작해야 하므로 앱을 죽이지 않는다
            Log.e(TAG, "신호음 준비 실패, 신호음 없이 동작", e)
            null
        }
    }

    private companion object {
        const val TAG = "KkaeuVoice"
        const val SAMPLE_RATE = 48_000
        const val DURATION_MS = 150
        const val FADE_MS = 10
        const val FREQUENCY_HZ = 880.0
        const val AMPLITUDE = 0.5 // 0.0~1.0
    }
}
