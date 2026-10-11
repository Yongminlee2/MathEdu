import SwiftUI

// UI 테스트를 돌리려면 테스트 대상 앱이 하나 있어야 해서 둔 빈 앱. 실제로 누르는 것은 삐약수학(com.peep.math)이다.
@main
struct HostApp: App {
    var body: some Scene { WindowGroup { Text("Peep Math review recorder") } }
}
