package com.piyak.english

import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * **한국어가 외국 폰으로 새어 나가지 않는지** 검사한다.
 *
 * 규칙은 하나다: 한글이 든 문제문·해설은 **반드시 번역 뼈대 키(tk/ek)** 를 갖고,
 * 그 키의 문자열이 기본 리소스(`values/strings.xml` = 영어)에 있어야 한다.
 *
 * 이 셋 중 하나라도 어긋나면 앱은 한국어 원문을 그대로 띄운다.
 * 안전장치로는 맞지만 영어권 사용자에게는 읽을 수 없는 화면이므로, 여기서 막는다.
 *
 * 새 문제를 추가했다면 다음을 돌려서 고친다:
 *   node tools/i18n/keyify.js --write && node tools/gen_math.js
 *   node tools/i18n/gen_strings.js && node tools/i18n/verify.js
 */
class KoreanLeakTest {

    private val hangul = Regex("[가-힣]")

    /** values/strings.xml (기본 = 영어) 에 정의된 tpl_* 이름들 */
    private fun englishTemplateKeys(): Set<String> {
        val xml = File("src/main/res/values/strings.xml").readText()
        return Regex("<string name=\"tpl_([0-9a-f]{8})\">")
            .findAll(xml).map { it.groupValues[1] }.toSet()
    }

    private fun mathQuestions(): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        fun walk(v: Any?) {
            when (v) {
                is JSONArray -> for (i in 0 until v.length()) walk(v.get(i))
                is JSONObject -> {
                    if (v.optString("type") == "math") out.add(v)
                    for (k in v.keys()) walk(v.get(k))
                }
            }
        }
        File("src/main/assets/packs").listFiles { f -> f.name.endsWith(".json") }
            ?.forEach { walk(JSONObject(it.readText())) }
        return out
    }

    @Test
    fun `한글 문제와 해설에는 번역 키가 붙어 있다`() {
        val bad = ArrayList<String>()
        for (q in mathQuestions()) {
            val id = q.optString("id")
            val prompt = q.optString("prompt")
            if (hangul.containsMatchIn(prompt) && q.optString("tk").isEmpty()) {
                bad.add("[$id] 문제문에 tk 없음: ${prompt.take(60)}")
            }
            val explain = q.optString("explain")
            if (hangul.containsMatchIn(explain) && q.optString("ek").isEmpty()) {
                bad.add("[$id] 해설에 ek 없음: ${explain.take(60)}")
            }
        }
        assertTrue(
            "번역 키 없는 문장 ${bad.size}건 — 이 문장은 어느 나라 폰에서도 한국어로 뜬다\n" +
                bad.take(10).joinToString("\n"),
            bad.isEmpty()
        )
    }

    @Test
    fun `팩이 쓰는 번역 키는 모두 영어 리소스에 있다`() {
        val english = englishTemplateKeys()
        val missing = LinkedHashMap<String, String>()
        for (q in mathQuestions()) {
            for (field in listOf("tk", "ek")) {
                val key = q.optString(field)
                if (key.isNotEmpty() && key !in english) {
                    missing[key] = q.optString("id")
                }
            }
        }
        assertTrue(
            "영어 리소스에 없는 뼈대 ${missing.size}종 — 영어가 없으면 다른 언어도 기댈 곳이 없다\n" +
                missing.entries.take(10).joinToString("\n") { "tpl_${it.key} (예: ${it.value})" },
            missing.isEmpty()
        )
    }

    @Test
    fun `기본 리소스는 영어이고 한국어는 values-ko 에 있다`() {
        val def = File("src/main/res/values/strings.xml").readText()
        val ko = File("src/main/res/values-ko/strings.xml").readText()
        val appName = Regex("<string name=\"app_name\">([^<]*)</string>")
        val defName = appName.find(def)?.groupValues?.get(1) ?: ""
        val koName = appName.find(ko)?.groupValues?.get(1) ?: ""
        assertTrue("기본 리소스(values/)의 app_name 이 한국어다: $defName", !hangul.containsMatchIn(defName))
        assertTrue("values-ko/ 의 app_name 이 한국어가 아니다: $koName", hangul.containsMatchIn(koName))
    }
}
