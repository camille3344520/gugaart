// wkshot：用 macOS 自带的 WebKit（和 iOS 的 WKWebView 同一个引擎）离屏打开一页、等就绪、截图、报告 JS 错误。
// 用来在没有 iOS 模拟器时先验 design/ios/device.html 在 WebKit 里能不能跑。
//
// 编译：swiftc -O wkshot.swift -o "${TMPDIR:-/tmp}/wkshot"
// 用法：wkshot <url> <宽> <高> <输出.png> [--js '<就绪后执行的脚本，可 await>'] [--wait <毫秒>] [--timeout <秒>]
// 输出：PNG（2×）；stdout 打印 JS 异常 / console.error / 原生桥调用 / 诊断（WebGL2、nogl、未解码图片、visibilityState）。
// 退出码：0 = 就绪且没有 JS 异常；1 = 有异常或没就绪。
import AppKit
import WebKit

let argv = CommandLine.arguments
func usage() -> Never {
    FileHandle.standardError.write("用法：wkshot <url> <宽> <高> <输出.png> [--js <脚本>] [--wait <毫秒>] [--timeout <秒>]\n".data(using: .utf8)!)
    exit(2)
}
let url: URL = { if argv.count >= 5, let u = URL(string: argv[1]) { return u }; usage() }()
let W: Double = { if let v = Double(argv[2]) { return v }; usage() }()
let H: Double = { if let v = Double(argv[3]) { return v }; usage() }()
let outPath = argv[4]
func opt(_ k: String) -> String? { if let i = argv.firstIndex(of: k), i + 1 < argv.count { return argv[i + 1] }; return nil }
let actionJS = opt("--js")
let settleMs = Double(opt("--wait") ?? "800") ?? 800
let timeoutS = Double(opt("--timeout") ?? "90") ?? 90

let hookJS = """
(function(){
  const post=(k,m)=>{try{window.webkit.messageHandlers.wklog.postMessage(k+': '+m)}catch(e){}};
  addEventListener('error',e=>post('JS 异常',(e.message||e)+' @ '+(e.filename||'').split('/').pop()+':'+(e.lineno||'')));
  addEventListener('unhandledrejection',e=>post('未处理的 Promise 拒绝',String(e.reason&&(e.reason.stack||e.reason))));
  const ce=console.error;console.error=function(...a){post('console.error',a.map(x=>String(x&&x.stack||x)).join(' '));return ce.apply(console,a)};
})();
"""

final class Runner: NSObject, WKNavigationDelegate, WKScriptMessageHandler {
    let web: WKWebView
    let window: NSWindow
    var errors: [String] = []
    var bridge: [String] = []
    var finished = false

    override init() {
        let cfg = WKWebViewConfiguration()
        cfg.preferences.inactiveSchedulingPolicy = .none          // 离屏也不节流 rAF / 定时器
        let uc = cfg.userContentController
        uc.addUserScript(WKUserScript(source: hookJS, injectionTime: .atDocumentStart, forMainFrameOnly: true))
        web = WKWebView(frame: NSRect(x: 0, y: 0, width: W, height: H), configuration: cfg)
        window = NSWindow(contentRect: NSRect(x: -30000, y: -30000, width: W, height: H),
                          styleMask: .borderless, backing: .buffered, defer: false)
        super.init()
        uc.add(self, name: "wklog")
        uc.add(self, name: "gugu")                                // 冒充 iOS 外壳的原生桥，只记录
        // 离屏窗口默认被当成「被遮挡」→ visibilityState=hidden、rAF 不跑；关掉遮挡检测（WebKit 测试用的 SPI，没有就跳过）
        let sel = NSSelectorFromString("_setWindowOcclusionDetectionEnabled:")
        if let m = class_getInstanceMethod(WKWebView.self, sel) {
            typealias SetBool = @convention(c) (AnyObject, Selector, Bool) -> Void
            unsafeBitCast(method_getImplementation(m), to: SetBool.self)(web, sel, false)
        }
        window.contentView = web
        window.orderFrontRegardless()
        web.navigationDelegate = self
        web.load(URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData))
        Timer.scheduledTimer(withTimeInterval: timeoutS, repeats: false) { [weak self] _ in self?.finish(ready: false) }
    }

    func userContentController(_ c: WKUserContentController, didReceive m: WKScriptMessage) {
        if m.name == "wklog" { errors.append("\(m.body)") }
        else if let d = m.body as? [String: Any] { bridge.append(d.map { "\($0.key)=\($0.value)" }.sorted().joined(separator: " ")) }
    }

    func webView(_ w: WKWebView, didFail n: WKNavigation!, withError e: Error) { errors.append("加载失败: \(e.localizedDescription)"); finish(ready: false) }
    func webView(_ w: WKWebView, didFailProvisionalNavigation n: WKNavigation!, withError e: Error) { errors.append("加载失败: \(e.localizedDescription)"); finish(ready: false) }
    func webView(_ w: WKWebView, didFinish n: WKNavigation!) { poll() }

    func poll() {
        web.evaluateJavaScript("!!(document.body && document.body.dataset.ready === '1')") { [weak self] r, _ in
            guard let self, !self.finished else { return }
            if (r as? Bool) == true { self.afterReady() }
            else { DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) { self.poll() } }
        }
    }

    func afterReady() {
        let go = { DispatchQueue.main.asyncAfter(deadline: .now() + settleMs / 1000) { self.finish(ready: true) } }
        guard let js = actionJS else { go(); return }
        web.callAsyncJavaScript(js, arguments: [:], in: nil, in: .page) { [weak self] r in
            if case .failure(let e) = r { self?.errors.append("--js 失败: \(e)") }
            go()
        }
    }

    func finish(ready: Bool) {
        guard !finished else { return }
        finished = true
        let diag = """
        (() => { const c = document.createElement('canvas');
          const imgs = [...document.images].filter(i => i.src && (!i.complete || !i.naturalWidth)).length;
          return JSON.stringify({ webgl2: !!c.getContext('webgl2'), nogl: document.body && document.body.classList.contains('nogl'),
            undecodedImages: imgs, visibility: document.visibilityState, viewport: innerWidth + 'x' + innerHeight,
            fontsLoaded: document.fonts ? document.fonts.status : 'n/a', atProperty: !!(window.CSS && CSS.registerProperty) }); })()
        """
        web.evaluateJavaScript(diag) { r, _ in
            print("就绪: \(ready ? "是" : "否（超时 \(Int(timeoutS)) 秒）")")
            print("诊断: \(r as? String ?? "-")")
            for b in self.bridge { print("原生桥: \(b)") }
            for e in self.errors { print(e) }
            let snap = WKSnapshotConfiguration()
            snap.rect = NSRect(x: 0, y: 0, width: W, height: H)
            snap.afterScreenUpdates = true
            self.web.takeSnapshot(with: snap) { img, err in
                if let img, let cg = img.cgImage(forProposedRect: nil, context: nil, hints: nil) {
                    let rep = NSBitmapImageRep(cgImage: cg)
                    try? rep.representation(using: .png, properties: [:])?.write(to: URL(fileURLWithPath: outPath))
                    print("截图: \(outPath) (\(cg.width)×\(cg.height))")
                } else { print("截图失败: \(String(describing: err))") }
                exit(ready && !self.errors.contains { $0.hasPrefix("JS 异常") || $0.hasPrefix("未处理") || $0.hasPrefix("加载失败") } ? 0 : 1)
            }
        }
    }
}

let app = NSApplication.shared
app.setActivationPolicy(.prohibited)
let runner = Runner()
app.run()
