package com.sleepreaper.kkaeurang.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.math.PI
import kotlin.math.sin

/**
 * 음성 경고 아래에 깔리는 반복 신호음("삐- 삐-").
 *
 * - res/raw/alert_beep 파일이 있으면 그 파일을 반복 재생하고,
 *   없으면 880Hz 사인파로 "300ms 삐 → 300ms 쉼" 패턴을 만들어 끝없이 반복한다.
 * - 음성이 잘 들리도록 음성보다 작은 볼륨([BEEP_VOLUME])으로 재생한다.
 * - 재생 전에 오디오 포커스를 요청하고 (음성과 함께 울려야 하므로 GAIN_TRANSIENT_MAY_DUCK),
 *   stop() 때 반납한다.
 * - 포커스를 잃으면(LOSS / LOSS_TRANSIENT) 즉시 멈추고 포커스를 반납한 뒤 [onFocusLost]로 알린다.
 *   멈춘 뒤 다시 울릴지는 부른 쪽(VoiceAlarm)이 정한다.
 *
 * 메인 스레드에서만 호출한다. 포커스 변경 이벤트도 메인 스레드로 받는다.
 */
internal class BeepLayer(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(BEEP_USAGE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** 포커스를 잃어 신호음이 멈췄을 때 호출된다 (메인 스레드). 인자는 AudioManager.AUDIOFOCUS_LOSS* 값 */
    var onFocusLost: ((focusChange: Int) -> Unit)? = null

    /** 지금 신호음이 울리고 있는지 */
    var isPlaying = false
        private set

    private var hasFocus = false
    private var ducked = false
    private var released = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change -> onFocusChange(change) }

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(audioAttributes)
        // 바로 받지 못하면 늦게 받아도 소용없다 (그 사이 문구 사이 구간이 지나감). 거절로 보고 fallback 한다
        .setAcceptsDelayedFocusGain(false)
        // 다른 소리와 함께 울리는 게 목적이므로 ducking 때 멈추지 않는다 (true면 동시 재생이 깨질 수 있음)
        .setWillPauseWhenDucked(false)
        .setOnAudioFocusChangeListener(focusListener, mainHandler)
        .build()

    private var player: LoopPlayer? = createPlayer()

    /**
     * 포커스를 요청하고 신호음을 반복 재생한다.
     * @return 실제로 울리기 시작했으면 true. 포커스 거절·플레이어 없음이면 false (신호음 없이 진행하면 된다)
     */
    fun start(): Boolean {
        if (released) return false
        if (isPlaying) return true
        val loopPlayer = player ?: run {
            Log.w(TAG, "[신호음] 플레이어가 없어 신호음 없이 진행")
            return false
        }

        val result = audioManager.requestAudioFocus(focusRequest)
        Log.i(TAG, "[신호음] 포커스 요청 (usage=${usageName(BEEP_USAGE)}, gain=GAIN_TRANSIENT_MAY_DUCK) → ${requestResultName(result)}")
        if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
        hasFocus = true
        ducked = false

        return try {
            loopPlayer.setVolume(BEEP_VOLUME)
            loopPlayer.play()
            isPlaying = true
            Log.i(TAG, "[신호음] 재생 시작 (${loopPlayer.description}, 볼륨 $BEEP_VOLUME)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "[신호음] 재생 실패 → 포커스 반납", e)
            abandonFocus()
            false
        }
    }

    /** 신호음을 멈추고 포커스를 반납한다. 울리고 있지 않으면 아무것도 하지 않는다. */
    fun stop() {
        if (!isPlaying && !hasFocus) return
        haltPlayer()
        abandonFocus()
        Log.i(TAG, "[신호음] 정지, 포커스 반납")
    }

    fun release() {
        if (released) return
        stop()
        released = true
        onFocusLost = null
        player?.release()
        player = null
    }

    private fun onFocusChange(change: Int) {
        if (!isPlaying) {
            Log.d(TAG, "[신호음] 포커스 변경 ${focusChangeName(change)} (재생 중 아님, 무시)")
            return
        }
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.i(TAG, "[신호음] 포커스 변경 GAIN")
                if (ducked) {
                    ducked = false
                    player?.setVolume(BEEP_VOLUME)
                    Log.i(TAG, "[신호음] ducking 해제 → 볼륨 $BEEP_VOLUME 복원")
                }
            }

            // 다른 소리와 함께 울리되 볼륨을 낮추라는 뜻 (동시 재생 유지)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                ducked = true
                val volume = BEEP_VOLUME * DUCK_VOLUME_RATIO
                player?.setVolume(volume)
                Log.i(TAG, "[신호음] 포커스 변경 LOSS_TRANSIENT_CAN_DUCK → 볼륨 $volume 로 낮추고 계속 재생")
            }

            AudioManager.AUDIOFOCUS_LOSS, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                haltPlayer()
                abandonFocus()
                Log.w(TAG, "[신호음] 포커스 변경 ${focusChangeName(change)} → 즉시 중지, 포커스 반납")
                onFocusLost?.invoke(change)
            }

            else -> Log.d(TAG, "[신호음] 포커스 변경 ${focusChangeName(change)}")
        }
    }

    private fun haltPlayer() {
        if (!isPlaying) return
        isPlaying = false
        try {
            player?.halt()
        } catch (e: Exception) {
            Log.e(TAG, "[신호음] 정지 중 오류", e)
        }
    }

    private fun abandonFocus() {
        if (!hasFocus) return
        hasFocus = false
        ducked = false
        audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private fun createPlayer(): LoopPlayer? {
        val rawId = findRawBeepId()
        if (rawId != 0) {
            try {
                return RawFilePlayer(appContext, rawId, audioAttributes, audioManager)
                    .also { Log.i(TAG, "[신호음] 소스: ${it.description}") }
            } catch (e: Exception) {
                Log.w(TAG, "[신호음] res/raw/$RAW_BEEP_NAME 준비 실패 → 합성음 사용", e)
            }
        }
        return try {
            ToneLoopPlayer(audioAttributes).also { Log.i(TAG, "[신호음] 소스: ${it.description}") }
        } catch (e: Exception) {
            // 신호음이 안 나도 음성 경고 자체는 동작해야 하므로 앱을 죽이지 않는다
            Log.e(TAG, "[신호음] 준비 실패 → 신호음 없이 동작", e)
            null
        }
    }

    // 파일이 없을 수도 있어 R.raw.alert_beep를 직접 참조하면 빌드가 깨진다. 그래서 이름으로 찾는다
    @SuppressLint("DiscouragedApi")
    private fun findRawBeepId(): Int =
        appContext.resources.getIdentifier(RAW_BEEP_NAME, "raw", appContext.packageName)

    /** 반복 재생기. 파일 재생(MediaPlayer)과 합성음(AudioTrack)을 같은 방식으로 다루기 위한 것 */
    private interface LoopPlayer {
        val description: String
        fun play()
        fun halt()
        fun setVolume(volume: Float)
        fun release()
    }

    /** res/raw/alert_beep 파일을 끝없이 반복 재생한다. 삐·쉼 패턴은 파일 안에 들어 있어야 한다. */
    private class RawFilePlayer(
        context: Context,
        resId: Int,
        attributes: AudioAttributes,
        audioManager: AudioManager
    ) : LoopPlayer {

        private val mediaPlayer: MediaPlayer =
            MediaPlayer.create(context, resId, attributes, audioManager.generateAudioSessionId())
                ?: throw IllegalStateException("MediaPlayer.create 실패")

        init {
            mediaPlayer.isLooping = true
        }

        override val description = "파일 res/raw/$RAW_BEEP_NAME (반복)"

        override fun play() {
            mediaPlayer.seekTo(0)
            mediaPlayer.start()
        }

        override fun halt() {
            if (mediaPlayer.isPlaying) mediaPlayer.pause()
            mediaPlayer.seekTo(0)
        }

        override fun setVolume(volume: Float) = mediaPlayer.setVolume(volume, volume)

        override fun release() = mediaPlayer.release()
    }

    /**
     * 한 주기(삐 + 쉼) PCM을 만들어 AudioTrack 정적 버퍼에 올리고, 루프 구간을 무한 반복으로 걸어 재생한다.
     * 별도 스레드 없이 오디오 시스템이 알아서 반복한다.
     */
    private class ToneLoopPlayer(attributes: AudioAttributes) : LoopPlayer {

        private val frameCount = SAMPLE_RATE * (TONE_ON_MS + TONE_OFF_MS) / 1000

        private val track: AudioTrack = run {
            val onCount = SAMPLE_RATE * TONE_ON_MS / 1000
            val fadeCount = SAMPLE_RATE * TONE_FADE_MS / 1000
            // 삐 구간의 시작·끝을 부드럽게 줄여서 '틱' 하는 클릭음을 없앤다. 나머지는 무음
            val pcm = ShortArray(frameCount) { i ->
                if (i >= onCount) {
                    0
                } else {
                    val envelope = minOf(1.0, i / fadeCount.toDouble(), (onCount - 1 - i) / fadeCount.toDouble())
                    val sample = sin(2 * PI * TONE_FREQUENCY_HZ * i / SAMPLE_RATE) * envelope * TONE_AMPLITUDE
                    (sample * Short.MAX_VALUE).toInt().toShort()
                }
            }
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
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
                .apply {
                    val written = write(pcm, 0, pcm.size)
                    check(written == pcm.size) { "PCM 쓰기 실패 (written=$written)" }
                }
        }

        override val description =
            "합성음 ${TONE_FREQUENCY_HZ.toInt()}Hz ${TONE_ON_MS}ms 삐 → ${TONE_OFF_MS}ms 쉼 반복"

        override fun play() {
            // 정적 버퍼는 멈춘 상태에서만 처음으로 되감고 루프 구간을 걸 수 있다
            track.stop()
            track.reloadStaticData()
            val result = track.setLoopPoints(0, frameCount, LOOP_FOREVER)
            if (result != AudioTrack.SUCCESS) Log.w(TAG, "[신호음] 반복 구간 설정 실패 ($result), 한 번만 울릴 수 있음")
            track.play()
        }

        // pause는 즉시 멈추고, 이어서 stop으로 루프 상태를 정리한다
        override fun halt() {
            track.pause()
            track.stop()
        }

        override fun setVolume(volume: Float) {
            track.setVolume(volume)
        }

        override fun release() = track.release()
    }

    companion object {
        /**
         * 신호음의 AudioAttributes usage. 차량은 usage마다 CarAudioContext(출력 경로·포커스 정책)가 다르다.
         * - 기본: USAGE_ALARM (CarAudioContext ALARM)
         * - 대안: USAGE_ASSISTANCE_SONIFICATION (CarAudioContext SYSTEM_SOUND).
         *   ALARM이 거절되거나 음성과 함께 울리지 못하면 이 값으로 바꿔서 시험해 본다.
         */
        const val BEEP_USAGE = AudioAttributes.USAGE_ALARM

        /** 신호음 볼륨 (0.0~1.0). 음성이 잘 들리도록 음성(1.0)보다 작게 둔다 */
        const val BEEP_VOLUME = 0.35f

        /** LOSS_TRANSIENT_CAN_DUCK을 받았을 때 [BEEP_VOLUME]에 곱하는 비율 */
        private const val DUCK_VOLUME_RATIO = 0.5f

        /** 이 이름의 파일이 res/raw에 있으면 합성음 대신 사용한다 (확장자 제외) */
        private const val RAW_BEEP_NAME = "alert_beep"

        private const val TAG = "KkaeuAlert"
        private const val SAMPLE_RATE = 48_000
        private const val TONE_FREQUENCY_HZ = 880.0
        private const val TONE_ON_MS = 300
        private const val TONE_OFF_MS = 300
        private const val TONE_FADE_MS = 10
        private const val TONE_AMPLITUDE = 0.8 // PCM 자체 크기. 실제 크기는 BEEP_VOLUME으로 조절
        private const val LOOP_FOREVER = -1

        fun focusChangeName(change: Int): String = when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> "GAIN"
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> "GAIN_TRANSIENT"
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> "GAIN_TRANSIENT_MAY_DUCK"
            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE -> "GAIN_TRANSIENT_EXCLUSIVE"
            AudioManager.AUDIOFOCUS_LOSS -> "LOSS"
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "LOSS_TRANSIENT"
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "LOSS_TRANSIENT_CAN_DUCK"
            else -> "UNKNOWN($change)"
        }

        private fun requestResultName(result: Int): String = when (result) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> "GRANTED"
            AudioManager.AUDIOFOCUS_REQUEST_FAILED -> "FAILED(거절)"
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> "DELAYED"
            else -> "UNKNOWN($result)"
        }

        private fun usageName(usage: Int): String = when (usage) {
            AudioAttributes.USAGE_ALARM -> "ALARM"
            AudioAttributes.USAGE_ASSISTANCE_SONIFICATION -> "ASSISTANCE_SONIFICATION"
            else -> usage.toString()
        }
    }
}
