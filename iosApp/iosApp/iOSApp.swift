import SwiftUI

@main
struct iOSApp: App {
    init() {
        registerIosLlamaBridgeIfAvailable()
        registerIosWhisperBridgeIfAvailable()
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}