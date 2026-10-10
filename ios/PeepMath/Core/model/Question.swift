import Foundation

// sealed class → 클래스 상속: `Question` 밑에 `Question.Mcq` 처럼 같은 이름의 final class. 검사는 `q as? Question.Math`. Pair 는 (first:, second:) 튜플.

/// 문제 한 개. 팩 JSON 의 type 필드로 구분한다.
class Question {
    let id: String
    let explain: String?

    /// 팩 JSON 의 skill 값(있으면 우선). 유형만으로 구분 못 하는 경우에 쓴다.
    var skillTag: String? = nil

    init(_ id: String, _ explain: String?) {
        self.id = id
        self.explain = explain
    }

    /// 실력 영역: vocab / listening / speaking / writing / grammar / reading / m_calc
    var skill: String { skillTag ?? defaultSkill() }

    private func defaultSkill() -> String {
        switch self {
        case let m as Mcq: return m.passage != nil ? "reading" : "vocab"
        case is ListenMcq, is ListenDialog: return "listening"
        case is Dictation: return "listening"
        case is Order, is TypeTranslate: return "writing"
        case is Speak: return "speaking"
        case is Match: return "vocab"
        case is Math: return "m_calc"
        default: return "m_calc"
        }
    }

    // MARK: - JSON

    /// 모르는 type 이면 던진다 (코틀린 IllegalArgumentException 자리)
    static func fromJson(_ o: JSONObject) throws -> Question {
        let q = try build(o)
        q.skillTag = o.optString("skill").ifEmpty { nil }
        return q
    }

    private static func build(_ o: JSONObject) throws -> Question {
        let id = o.getString("id")
        let explain = o.optString("explain").ifEmpty { nil }
        let t = o.getString("type")
        switch t {
        case "mcq", "reading":
            return Mcq(
                id, o.getString("prompt"), strList(o.getJSONArray("choices")),
                o.getInt("answer"), passage: o.optString("passage").ifEmpty { nil }, explain: explain,
                bigEmoji: o.optString("bigEmoji").ifEmpty { nil }
            )
        case "listen_mcq":
            return ListenMcq(
                id, o.getString("tts"), o.optString("prompt", "무엇을 들었나요?"),
                strList(o.getJSONArray("choices")), o.getInt("answer"), explain: explain
            )
        case "dictation":
            return Dictation(
                id, o.getString("tts"), o.getString("answer"),
                alts: strListOpt(o.optJSONArray("alts")), hintKo: o.optString("hintKo").ifEmpty { nil }, explain: explain
            )
        case "order":
            return Order(
                id, o.getString("ko"), o.getString("en"),
                extras: strListOpt(o.optJSONArray("extras")), explain: explain
            )
        case "type_translate":
            return TypeTranslate(
                id, o.getString("ko"), o.getString("answer"),
                alts: strListOpt(o.optJSONArray("alts")), explain: explain
            )
        case "match":
            let arr = o.getJSONArray("pairs")
            let pairs: [(first: String, second: String)] = (0..<arr.length()).map {
                let p = arr.getJSONArray($0)
                return (p.getString(0), p.getString(1))
            }
            return Match(id, pairs, explain: explain)
        case "speak":
            return Speak(id, o.getString("en"), ko: o.optString("ko").ifEmpty { nil }, explain: explain)
        case "math":
            let choices = strListOpt(o.optJSONArray("choices"))
            let koPrompt = o.getString("prompt")
            let T = Tpl.self
            return Math(
                id,
                // 팩의 뼈대 키 + 값으로 폰 언어 문장을 만든다 (한국어 폰이면 원문 그대로)
                T.sentence(o.optString("tk").ifEmpty { nil }, strListOpt(o.optJSONArray("ta")), koPrompt),
                visual: MathVisual.fromJson(o.optJSONObject("visual")),
                input: o.optString("input", choices.isEmpty ? "number" : "choice"),
                answer: o.optString("answer"),
                alts: strListOpt(o.optJSONArray("alts")),
                // 선택지는 채점에 answerIndex 를 쓰므로 글자만 바꿔도 안전하다
                choices: T.words(choices),
                answerIndex: o.optInt("answerIndex", -1),
                unit: T.unit(o.optString("unit")),
                explain: T.sentence(
                    o.optString("ek").ifEmpty { nil }, strListOpt(o.optJSONArray("ea")),
                    explain ?? ""
                ).ifEmpty { nil },
                promptKo: koPrompt
            )
        case "listen_dialog":
            let arr = o.getJSONArray("lines")
            let lines: [(first: String, second: String)] = (0..<arr.length()).map {
                let p = arr.getJSONArray($0)
                return (p.getString(0), p.getString(1))
            }
            return ListenDialog(
                id, lines, o.getString("prompt"),
                strList(o.getJSONArray("choices")), o.getInt("answer"), explain: explain
            )
        default:
            throw JSONException("unknown question type: \(t)")
        }
    }

    private static func strList(_ a: JSONArray) -> [String] { (0..<a.length()).map { a.getString($0) } }
    private static func strListOpt(_ a: JSONArray?) -> [String] {
        guard let a = a else { return [] }
        return (0..<a.length()).map { a.getString($0) }
    }
}

private extension String {
    /// 코틀린 `ifEmpty { null }`
    func ifEmpty(_ f: () -> String?) -> String? { isEmpty ? f() : self }
}

extension Question {

    /// 4지선다. passage 가 있으면 독해(지문) 문제.
    final class Mcq: Question {
        let prompt: String
        let choices: [String]
        let answer: Int
        let passage: String?
        /// 문제 위에 크게 띄울 그림 이모지
        let bigEmoji: String?

        init(_ id: String, _ prompt: String, _ choices: [String], _ answer: Int,
             passage: String? = nil, explain: String? = nil, bigEmoji: String? = nil) {
            self.prompt = prompt
            self.choices = choices
            self.answer = answer
            self.passage = passage
            self.bigEmoji = bigEmoji
            super.init(id, explain)
        }
    }

    /// TTS 로 tts 를 들려준 뒤 4지선다.
    final class ListenMcq: Question {
        let tts: String
        let prompt: String
        let choices: [String]
        let answer: Int

        init(_ id: String, _ tts: String, _ prompt: String, _ choices: [String], _ answer: Int, explain: String? = nil) {
            self.tts = tts
            self.prompt = prompt
            self.choices = choices
            self.answer = answer
            super.init(id, explain)
        }
    }

    /// TTS 를 듣고 받아쓰기.
    final class Dictation: Question {
        let tts: String
        let answer: String
        let alts: [String]
        let hintKo: String?

        init(_ id: String, _ tts: String, _ answer: String,
             alts: [String] = [], hintKo: String? = nil, explain: String? = nil) {
            self.tts = tts
            self.answer = answer
            self.alts = alts
            self.hintKo = hintKo
            super.init(id, explain)
        }
    }

    /// 한국어 문장을 보고 영어 단어 타일을 순서대로 배열.
    final class Order: Question {
        let ko: String
        let en: String
        let extras: [String]

        init(_ id: String, _ ko: String, _ en: String, extras: [String] = [], explain: String? = nil) {
            self.ko = ko
            self.en = en
            self.extras = extras
            super.init(id, explain)
        }

        var tokens: [String] { en.split(" ").filter { $0.isNotBlank() } }
    }

    /// 한국어 문장을 영어로 타이핑.
    final class TypeTranslate: Question {
        let ko: String
        let answer: String
        let alts: [String]

        init(_ id: String, _ ko: String, _ answer: String, alts: [String] = [], explain: String? = nil) {
            self.ko = ko
            self.answer = answer
            self.alts = alts
            super.init(id, explain)
        }
    }

    /// 단어↔뜻 5쌍 매칭.
    final class Match: Question {
        let pairs: [(first: String, second: String)]

        init(_ id: String, _ pairs: [(first: String, second: String)], explain: String? = nil) {
            self.pairs = pairs
            super.init(id, explain)
        }
    }

    /// 영어 문장을 소리 내어 읽기(STT 채점).
    final class Speak: Question {
        let en: String
        let ko: String?

        init(_ id: String, _ en: String, ko: String? = nil, explain: String? = nil) {
            self.en = en
            self.ko = ko
            super.init(id, explain)
        }
    }

    /// 수학 문제. 그림(visual)과 입력 방식(input)을 조합해 모든 수학 유형을 표현한다.
    /// input = number(숫자 키패드) / choice(4지선다) / text(식·문자 답) / visual(그림 조작)
    final class Math: Question {
        let prompt: String
        let visual: MathVisual?
        let input: String
        let answer: String
        let alts: [String]
        let choices: [String]
        let answerIndex: Int
        let unit: String
        /// 번역 전 한국어 원문. 그림 고르기·이름 뽑기가 폰 언어와 상관없이 같게 나오도록 남긴다.
        let promptKo: String

        /// promptKo 기본값(= prompt)은 nil 로 받는다
        init(_ id: String, _ prompt: String,
             visual: MathVisual? = nil, input: String = "number", answer: String = "",
             alts: [String] = [], choices: [String] = [], answerIndex: Int = -1, unit: String = "",
             explain: String? = nil, promptKo: String? = nil) {
            self.prompt = prompt
            self.visual = visual
            self.input = input
            self.answer = answer
            self.alts = alts
            self.choices = choices
            self.answerIndex = answerIndex
            self.unit = unit
            self.promptKo = promptKo ?? prompt
            super.init(id, explain)
        }
    }

    /// 2인 대화를 듣고 4지선다 (토익 LC 스타일).
    final class ListenDialog: Question {
        /// (화자 A/B, 대사)
        let lines: [(first: String, second: String)]
        let prompt: String
        let choices: [String]
        let answer: Int

        init(_ id: String, _ lines: [(first: String, second: String)], _ prompt: String,
             _ choices: [String], _ answer: Int, explain: String? = nil) {
            self.lines = lines
            self.prompt = prompt
            self.choices = choices
            self.answer = answer
            super.init(id, explain)
        }
    }
}
