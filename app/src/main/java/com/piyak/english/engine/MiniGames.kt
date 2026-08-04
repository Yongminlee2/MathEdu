package com.piyak.english.engine

import kotlin.random.Random

/**
 * 저학년 버블 선택지를 만드는 도구.
 *
 * 원래는 "삐약놀이터"(풍선 터뜨리기·바구니 담기 등)의 문제 은행이었는데,
 * 놀이터 화면은 **삐약영어 앱에만 있다.** 수학 앱에는 그 화면이 없어서
 * 게임 목록·영어 단어 은행은 아무도 부르지 않는 죽은 코드로 남아 있었다
 * (거기 묶인 한국어 79개가 번역 대상처럼 보여서 발견했다).
 *
 * 실제로 쓰이는 것은 [wrongNumbers] 하나뿐이라 그것만 남긴다.
 * 놀이터가 수학에도 필요해지면 삐약영어 쪽에서 가져오면 된다.
 */
object MiniGames {

    /**
     * 정답 주변의 그럴듯한 숫자 n 개 (정답 포함, 섞어서 돌려준다).
     *
     * 저학년은 키패드로 치는 대신 버블을 톡 눌러 답한다. 오답이 정답과
     * 너무 멀면 찍어도 맞으므로 **정답에서 가까운 수부터** 채운다.
     * 음수는 넣지 않는다 — 아직 배우지 않은 수다.
     */
    fun wrongNumbers(answer: Int, n: Int, rnd: Random = Random.Default): List<Int> {
        val set = LinkedHashSet<Int>()
        set.add(answer)
        var d = 1
        while (set.size < n && d < 60) {
            for (w in listOf(answer + d, answer - d)) {
                if (w >= 0 && set.size < n) set.add(w)
            }
            d++
        }
        return set.toList().shuffled(rnd)
    }
}
