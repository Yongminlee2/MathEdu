package com.piyak.english.ui

import android.content.Context
import android.graphics.drawable.Drawable

/**
 * 세고 끌어 옮기는 이모지 → codex 일러스트.
 * 그림이 있으면 일러스트를, 없으면 이모지 글자를 그대로 쓴다.
 * (MathVisualView 와 GroupDragView 가 같이 쓴다 — 발주서 #05·#07로 전 사물 커버)
 */
object EmojiArt {
    private val names = mapOf(
        // 과일
        "🍎" to "word_apple", "🍓" to "word_strawberry", "🍌" to "word_banana",
        "🍊" to "word_orange", "🍇" to "word_grape", "🍑" to "word_peach",
        "🍉" to "word_watermelon", "🥝" to "word_kiwi",
        // 동물
        "🐥" to "ck_idle", "🐶" to "word_dog", "🐱" to "word_cat", "🐰" to "word_rabbit",
        "🐧" to "word_penguin", "🐸" to "word_frog", "🐼" to "word_panda", "🦊" to "word_fox",
        // 사물
        "⭐" to "word_star", "🎈" to "word_balloon", "🍪" to "word_cookie",
        "🚗" to "word_car", "✏️" to "word_pencil", "🌸" to "word_flower",
        "🧸" to "word_toy", "⚽" to "word_soccer",
    )

    private val cache = HashMap<String, Drawable?>()

    fun of(ctx: Context, emoji: String): Drawable? = cache.getOrPut(emoji) {
        val name = names[emoji] ?: return@getOrPut null
        val id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
        if (id == 0) null else ctx.getDrawable(id)
    }
}
