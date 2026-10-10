import UIKit

/// 세고 끌어 옮기는 이모지 → 일러스트. 그림이 있으면 일러스트를, 없으면 nil(이모지 글자를 쓴다).
/// (MathVisualView 와 GroupDragView 가 같이 쓴다)
enum EmojiArt {
    private static let names: [String: String] = [
        // 과일
        "🍎": "word_apple", "🍓": "word_strawberry", "🍌": "word_banana",
        "🍊": "word_orange", "🍇": "word_grape", "🍑": "word_peach",
        "🍉": "word_watermelon", "🥝": "word_kiwi",
        // 동물
        "🐥": "ck_idle", "🐶": "word_dog", "🐱": "word_cat", "🐰": "word_rabbit",
        "🐧": "word_penguin", "🐸": "word_frog", "🐼": "word_panda", "🦊": "word_fox",
        // 사물
        "⭐": "word_star", "🎈": "word_balloon", "🍪": "word_cookie",
        "🚗": "word_car", "✏️": "word_pencil", "🌸": "word_flower",
        "🧸": "word_toy", "⚽": "word_soccer",
    ]

    private static var cache: [String: Drawable?] = [:]

    static func of(_ ctx: Context, _ emoji: String) -> Drawable? {
        if let hit = cache[emoji] { return hit }
        let d: Drawable?
        if let name = names[emoji] {
            let id = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
            d = id == 0 ? nil : ctx.getDrawable(id)
        } else {
            d = nil
        }
        cache[emoji] = .some(d)
        return d
    }
}
