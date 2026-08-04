package com.piyak.english.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** 영어 TTS 래퍼. 대화문은 화자별 피치를 바꿔 2인 연출. */
class Tts(ctx: Context, private val onReady: (Boolean) -> Unit = {}) {

    private var tts: TextToSpeech? = null
    var ready = false
        private set
    var rate = 1.0f // 사용자 설정 속도

    private val main = Handler(Looper.getMainLooper())
    private var seq = 0
    private var pending: List<Pair<Float, String>> = emptyList()
    private var pendingIdx = 0
    private var onQueueDone: (() -> Unit)? = null

    init {
        tts = TextToSpeech(ctx.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                val r = tts?.setLanguage(Locale.US)
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) ready = false
            }
            if (ready) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(id: String?) {}
                    override fun onError(id: String?) { main.post { nextInQueue() } }
                    override fun onDone(id: String?) { main.post { nextInQueue() } }
                })
            }
            main.post { onReady(ready) }
        }
    }

    /**
     * 문제를 **화면에 뜬 언어 그대로** 읽어 준다.
     *
     * 문제문이 폰 언어로 번역돼 나오는데 음성만 한국어로 고정하면,
     * 영어 문장을 한국어 발음으로 뭉개 읽는다. 폰 언어를 따라가는 게 맞다.
     * 그 언어의 음성이 폰에 없으면 영어로 떨어진다 (읽어주기를 통째로 잃지 않게).
     *
     * 언어는 재생 직전에 매번 지정한다 — 끝나고 되돌리는 방식은 타이밍이 어긋나기 쉽다.
     */
    fun speakQuestion(text: String) {
        val t = tts ?: return
        pending = emptyList(); onQueueDone = null
        t.stop()
        val r = t.setLanguage(Locale.getDefault())
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            t.setLanguage(Locale.US)
        }
        t.setPitch(1.05f)
        t.setSpeechRate(rate * 0.95f)
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "q${seq++}")
    }

    /** 단문 재생. slow=true 면 절반 속도(거북이 버튼). */
    fun speak(text: String, slow: Boolean = false, pitch: Float = 1.0f) {
        val t = tts ?: return
        pending = emptyList(); onQueueDone = null
        t.stop()
        t.setLanguage(Locale.US)
        t.setPitch(pitch)
        t.setSpeechRate(if (slow) rate * 0.55f else rate)
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "u${seq++}")
    }

    /** (피치, 문장) 목록을 순서대로 재생 — 대화문용 */
    fun speakLines(lines: List<Pair<Float, String>>, onDone: (() -> Unit)? = null) {
        val t = tts ?: return
        t.stop()
        pending = lines
        pendingIdx = 0
        onQueueDone = onDone
        playPending()
    }

    private fun playPending() {
        val t = tts ?: return
        t.setLanguage(Locale.US)
        if (pendingIdx >= pending.size) {
            pending = emptyList()
            onQueueDone?.invoke(); onQueueDone = null
            return
        }
        val (pitch, text) = pending[pendingIdx]
        t.setPitch(pitch)
        t.setSpeechRate(rate)
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "u${seq++}")
    }

    private fun nextInQueue() {
        if (pending.isEmpty()) return
        pendingIdx++
        playPending()
    }

    fun stop() {
        pending = emptyList(); onQueueDone = null
        tts?.stop()
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
    }
}
