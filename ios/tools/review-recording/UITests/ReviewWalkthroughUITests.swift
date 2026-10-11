import XCTest

/// 앱 심사 회신용 녹화(지침 2.1 자료 요청): 앱 실행부터 평소 흐름까지 실기기에서 누른다.
/// 글자가 있는 단추는 글자로, 버블·그림 문제는 화면 좌표로 누른다. 앱은 영어로 띄운다(심사원이 읽게).
/// 앱은 처음 깐 상태여야 한다(record.sh 앞에서 지우고 다시 깐다).
final class ReviewWalkthroughUITests: XCTestCase {
    var app: XCUIApplication!
    var step = 0

    func snap(_ name: String) {
        step += 1
        let shot = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
        shot.name = String(format: "%02d_", step) + name; shot.lifetime = .keepAlways; add(shot)
    }

    /// 글자로 찾기 (정확히 같은 것 우선, 없으면 그 글자로 시작하는 것)
    func text(_ label: String, timeout: Double = 6) -> XCUIElement {
        let exact = app.staticTexts.matching(NSPredicate(format: "label == %@", label)).firstMatch
        if exact.waitForExistence(timeout: timeout) { return exact }
        return app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH %@", label)).firstMatch
    }

    /// 창 안 점 (pt)
    func point(_ x: CGFloat, _ y: CGFloat) -> XCUICoordinate {
        app.windows.firstMatch.coordinate(withNormalizedOffset: .zero).withOffset(CGVector(dx: x, dy: y))
    }

    /// 화면 왼쪽 위의 ◀ 또는 ✕. 그만둘지 묻는 창이 뜨면 그만둔다.
    func back() {
        let b = text("◀", timeout: 2)
        if b.exists { b.tap() } else {
            let x = text("✕", timeout: 2)
            if x.exists { x.tap() } else { point(40, 100).tap() }
        }
        sleep(2)
        let quit = app.staticTexts.matching(NSPredicate(format: "label == %@ OR label == %@", "Quit", "Stop")).firstMatch
        if quit.exists { quit.tap(); sleep(2) }
    }

    /// 지금 문제를 한 번 풀어 본다: 보기 자리를 차례로 눌러 「Check」 → 「Continue」 가 나오면 끝.
    func answerOne(_ name: String) {
        let spots: [(CGFloat, CGFloat)] = [(108, 455), (292, 455), (108, 560), (292, 560), (110, 520), (290, 520)]
        let check = text("Check", timeout: 3)
        for (x, y) in spots {
            point(x, y).tap(); sleep(1)
            if check.exists && check.isHittable { check.tap() }
            sleep(2)
            let cont = text("Continue", timeout: 2)
            if cont.exists {
                snap(name)
                cont.tap(); sleep(2)
                return
            }
        }
        snap(name + "_stuck")
    }

    func testWalkthrough() {
        app = XCUIApplication(bundleIdentifier: "com.peep.math")
        app.launchArguments = ["-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launch()
        sleep(4); snap("home")

        // 홈을 훑어본다: 코스 → 실력·지갑·레벨테스트
        app.swipeUp(); sleep(2); snap("home_scrolled")
        app.swipeDown(); sleep(1); app.swipeDown(); sleep(1)

        // 유치원 코스 → 첫 레슨
        text("Kindergarten").tap(); sleep(3); snap("track")
        point(52, 226).tap(); sleep(4); snap("lesson")

        // 문제 네 개를 풀어 본다(틀리면 정답과 해설이 나온다)
        for i in 1...4 { answerOne("question\(i)") }

        // 연습장: 문제 위에 손으로 써 보고 닫는다
        text("Scratchpad").tap(); sleep(2)
        point(90, 300).press(forDuration: 0.2, thenDragTo: point(200, 380), withVelocity: 300, thenHoldForDuration: 0.2)
        point(220, 300).press(forDuration: 0.2, thenDragTo: point(320, 300), withVelocity: 300, thenHoldForDuration: 0.2)
        sleep(1); snap("scratchpad")
        text("✅ Done").tap(); sleep(2)

        // 레슨 그만두기
        app.otherElements["그만두기"].firstMatch.tap(); sleep(2); snap("quit_dialog")
        text("Quit").tap(); sleep(3)
        back()

        // 레벨테스트 두 문제
        app.swipeUp(); sleep(2)
        text("Find your grade").tap(); sleep(4); snap("placement")
        for i in 1...2 {
            let choice = app.staticTexts.matching(NSPredicate(format: "label MATCHES %@", "^-?[0-9][0-9,./ ]*$")).element(boundBy: 1)
            if choice.exists { choice.tap() } else { point(200, 420).tap() }
            sleep(3); snap("placement\(i)")
        }
        back(); sleep(1)

        // 지갑·상점 → 통계 → 설정(언어 고르기)
        app.swipeUp(); sleep(2)
        text("Shop · Cash out").tap(); sleep(3); snap("wallet")
        app.swipeUp(); sleep(2); snap("shop")
        back()
        app.swipeUp(); sleep(1)
        text("Stats").tap(); sleep(3); snap("stats")
        app.swipeUp(); sleep(2); snap("badges")
        back()
        app.swipeUp(); sleep(1)
        text("Settings").tap(); sleep(3); snap("settings")
        back()
        sleep(2); snap("end")
    }
}
