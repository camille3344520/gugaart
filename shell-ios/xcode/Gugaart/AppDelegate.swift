// gugaart 的 iOS 宿主（M4-6）：开窗口、把 Kotlin 的整页（GugaartShell 的 MainViewController）放进去、转发「点状态栏」。
// 别的（存储、会话、界面、触感、文件选择器）都在 Kotlin 那边：shell-ios/src/iosMain/kotlin/com/gugaart/ios/。
import GugaartShell
import UIKit

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    // Offline downloads keep going in the background (iOS 26 BGContinuedProcessingTask): the handler must be registered before launch finishes (Kotlin checks the OS version and Info.plist)
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil) -> Bool {
        MainKt.registerBackgroundWork()
        return true
    }
}

final class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?

    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        guard let windowScene = scene as? UIWindowScene else { return }
        let window = UIWindow(windowScene: windowScene)
        // 墨黑的纸（和启动屏同一个颜色）：Compose 第一帧之前不闪白
        window.backgroundColor = UIColor(named: "LaunchBackground") ?? .black
        window.rootViewController = RootViewController()
        window.makeKeyAndVisible()
        self.window = window
    }
}

/// 根：Compose 的整页 + 一块看不见的滚动视图。系统把「点状态栏」只发给窗口里唯一一个 `scrollsToTop` 的滚动视图，
/// 这块就是它：收到了不自己滚，转给 Kotlin（`onStatusBarTap`），由当前页的主滚动区滚回顶（D-lead-27）。
final class RootViewController: UIViewController, UIScrollViewDelegate {
    private let compose = MainKt.MainViewController()
    private let statusBarTap = UIScrollView()

    override func viewDidLoad() {
        super.viewDidLoad()
        view.backgroundColor = UIColor(named: "LaunchBackground") ?? .black
        addChild(compose)
        compose.view.frame = view.bounds
        compose.view.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        view.addSubview(compose.view)
        compose.didMove(toParent: self)

        // 1 × 1 pt、内容比它高一点、停在 y = 1：系统才认为它「能滚回顶」。放在屏幕外，不挡任何触摸
        statusBarTap.frame = CGRect(x: -2, y: -2, width: 1, height: 1)
        statusBarTap.contentSize = CGSize(width: 1, height: 3)
        statusBarTap.contentOffset = CGPoint(x: 0, y: 1)
        statusBarTap.scrollsToTop = true
        statusBarTap.delegate = self
        view.addSubview(statusBarTap)
    }

    func scrollViewShouldScrollToTop(_ scrollView: UIScrollView) -> Bool {
        MainKt.onStatusBarTap()
        return false
    }

    // 竖屏；播放器在前台时横屏（Kotlin 的 PlayerPlatform.playerVisible → IosChrome，M5-1）
    override var supportedInterfaceOrientations: UIInterfaceOrientationMask {
        UIInterfaceOrientationMask(rawValue: UInt(IosPlayerKt.supportedOrientationsMask()))
    }

    // 只有深色：状态栏浅色字；播放器里状态栏和 home 条都藏起来
    override var preferredStatusBarStyle: UIStatusBarStyle { .lightContent }
    override var prefersStatusBarHidden: Bool { IosPlayerKt.hideSystemBars() }
    override var prefersHomeIndicatorAutoHidden: Bool { IosPlayerKt.hideSystemBars() }
    override var childForStatusBarHidden: UIViewController? { nil }
    override var childForHomeIndicatorAutoHidden: UIViewController? { nil }
}
