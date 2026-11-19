// 咕咕嘎嘎 iOS 设计 demo 的模拟器外壳：一个全屏 WKWebView，加载 design/ios/device.html。
// 只为在 iOS 模拟器（或真机）里看设计 demo，不是正式应用。正式应用走 Kotlin Multiplatform（见 design/ios/notes.md「真机落地」）。
//
// 原生桥（device.html 里调用前判空，没有桥照常工作）：
//   window.webkit.messageHandlers.gugu.postMessage({type:'orientation', value:'landscape'|'portrait'})
//   window.webkit.messageHandlers.gugu.postMessage({type:'haptic', style:'light'|'medium'|'heavy'|'success'|'selection'})
//   window.webkit.messageHandlers.gugu.postMessage({type:'chrome', statusBarHidden:Bool, homeIndicatorHidden:Bool})
// 地址：启动参数 -GuguDemoURL <url> 覆盖；默认 http://127.0.0.1:8765/design/ios/device.html?p=home
// 摇一摇（模拟器 ⌃⌘Z）= 重新加载。

import UIKit
import WebKit

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    func application(_ application: UIApplication,
                     configurationForConnecting session: UISceneSession,
                     options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        let c = UISceneConfiguration(name: "Default", sessionRole: session.role)
        c.delegateClass = SceneDelegate.self
        return c
    }
}

@objc(GuguSceneDelegate)
final class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options: UIScene.ConnectionOptions) {
        guard let ws = scene as? UIWindowScene else { return }
        let w = UIWindow(windowScene: ws)
        w.backgroundColor = .black
        w.rootViewController = DemoViewController()
        w.makeKeyAndVisible()
        window = w
    }
}

/// WKUserContentController 会强引用 handler，用弱引用转一手避免循环引用。
private final class WeakHandler: NSObject, WKScriptMessageHandler {
    weak var target: WKScriptMessageHandler?
    init(_ t: WKScriptMessageHandler) { target = t }
    func userContentController(_ c: WKUserContentController, didReceive m: WKScriptMessage) {
        target?.userContentController(c, didReceive: m)
    }
}

final class DemoViewController: UIViewController, WKScriptMessageHandler, WKNavigationDelegate {
    private var web: WKWebView!
    private var mask: UIInterfaceOrientationMask = .portrait
    private var statusHidden = false { didSet { setNeedsStatusBarAppearanceUpdate() } }
    private var homeHidden = false { didSet { setNeedsUpdateOfHomeIndicatorAutoHidden() } }

    override var preferredStatusBarStyle: UIStatusBarStyle { .lightContent }
    override var prefersStatusBarHidden: Bool { statusHidden }
    override var prefersHomeIndicatorAutoHidden: Bool { homeHidden }
    override var supportedInterfaceOrientations: UIInterfaceOrientationMask { mask }
    override var canBecomeFirstResponder: Bool { true }

    private var demoURL: URL {
        let s = UserDefaults.standard.string(forKey: "GuguDemoURL")
            ?? "http://127.0.0.1:8765/design/ios/device.html?p=home"
        return URL(string: s)!
    }

    override func loadView() {
        let cfg = WKWebViewConfiguration()
        cfg.userContentController.add(WeakHandler(self), name: "gugu")
        cfg.allowsInlineMediaPlayback = true
        cfg.allowsPictureInPictureMediaPlayback = true
        cfg.mediaTypesRequiringUserActionForPlayback = []
        let wv = WKWebView(frame: .zero, configuration: cfg)
        wv.isOpaque = false
        wv.backgroundColor = .black
        wv.scrollView.backgroundColor = .black
        wv.scrollView.contentInsetAdjustmentBehavior = .never   // 安全区交给页面的 env(safe-area-inset-*)
        wv.scrollView.bounces = false
        wv.allowsBackForwardNavigationGestures = false          // 左缘右滑返回由页面自己做
        wv.allowsLinkPreview = false
        wv.isInspectable = true                                  // Safari › 开发 › 模拟器 可以调试
        wv.navigationDelegate = self
        web = wv
        view = wv
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        web.load(URLRequest(url: demoURL, cachePolicy: .reloadIgnoringLocalCacheData))
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        becomeFirstResponder()
    }

    override func motionEnded(_ motion: UIEvent.EventSubtype, with event: UIEvent?) {
        if motion == .motionShake { web.reloadFromOrigin() }
    }

    // 安全区变化（首次进窗口、转屏）不会触发网页的 resize；补发一个，让 device.html 重量 env(safe-area-inset-*) 并重排。
    override func viewSafeAreaInsetsDidChange() {
        super.viewSafeAreaInsetsDidChange()
        web?.evaluateJavaScript("dispatchEvent(new Event('resize'))", completionHandler: nil)
    }

    func userContentController(_ c: WKUserContentController, didReceive message: WKScriptMessage) {
        guard let body = message.body as? [String: Any], let type = body["type"] as? String else { return }
        switch type {
        case "orientation":
            let land = (body["value"] as? String) == "landscape"
            mask = land ? .landscape : .portrait
            setNeedsUpdateOfSupportedInterfaceOrientations()
            view.window?.windowScene?.requestGeometryUpdate(
                .iOS(interfaceOrientations: land ? .landscapeRight : .portrait)) { _ in }
        case "haptic":
            switch body["style"] as? String {
            case "success": UINotificationFeedbackGenerator().notificationOccurred(.success)
            case "selection": UISelectionFeedbackGenerator().selectionChanged()
            case "heavy": UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
            case "medium": UIImpactFeedbackGenerator(style: .medium).impactOccurred()
            default: UIImpactFeedbackGenerator(style: .light).impactOccurred()
            }
        case "chrome":
            if let s = body["statusBarHidden"] as? Bool { statusHidden = s }
            if let h = body["homeIndicatorHidden"] as? Bool { homeHidden = h }
        default:
            break
        }
    }

    func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
        let html = """
        <meta name=viewport content="width=device-width,initial-scale=1,viewport-fit=cover">
        <body style="margin:0;background:#100E0B;color:#EFE6D4;font:17px/1.6 -apple-system,'PingFang SC';padding:calc(env(safe-area-inset-top) + 40px) 28px">
        <h2 style="font-family:Georgia,'Songti SC',serif">咕咕嘎嘎 · iOS demo</h2>
        <p>连不上 demo 服务器：<br><code style="color:#EA4E30">\(demoURL.absoluteString)</code></p>
        <p style="opacity:.64">在 Mac 上启动静态服务器（仓库根目录 guganewui/）后，摇一摇（⌃⌘Z）重新加载。</p>
        <p style="opacity:.4;font-size:13px">\(error.localizedDescription)</p>
        </body>
        """
        webView.loadHTMLString(html, baseURL: nil)
    }
}
