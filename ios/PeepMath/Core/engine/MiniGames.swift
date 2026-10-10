import Foundation

/// 저학년 버블 선택지를 만드는 도구. (놀이터 화면은 삐약영어에만 있어서 wrongNumbers 만 남겼다)
enum MiniGames {

    /// 정답 주변의 그럴듯한 숫자 n 개 (정답 포함, 섞어서 돌려준다).
    /// 찍어도 맞지 않게 **정답에서 가까운 수부터** 채운다. 음수는 넣지 않는다.
    static func wrongNumbers(_ answer: Int, _ n: Int, _ rnd: KRandom = KRandom.default) -> [Int] {
        var set: [Int] = []   // LinkedHashSet — 넣은 순서를 지킨다
        set.append(answer)
        var d = 1
        while set.count < n && d < 60 {
            for w in [answer + d, answer - d] {
                if w >= 0 && set.count < n && !set.contains(w) { set.append(w) }
            }
            d += 1
        }
        return set.shuffled(random: rnd)
    }
}
