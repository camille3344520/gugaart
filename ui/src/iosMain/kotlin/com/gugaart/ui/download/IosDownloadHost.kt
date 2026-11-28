package com.gugaart.ui.download

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import platform.BackgroundTasks.BGContinuedProcessingTask
import platform.BackgroundTasks.BGContinuedProcessingTaskRequest
import platform.BackgroundTasks.BGContinuedProcessingTaskRequestSubmissionStrategy
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSBundle
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Network.nw_path_get_status
import platform.Network.nw_path_is_expensive
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_status_satisfied
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid
import platform.UIKit.UIDevice
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_queue_create

/** Pure: one NWPathMonitor path -> [NetState]. `satisfied` = online; unmetered = not `isExpensive` (cellular and personal hotspots are expensive). */
internal fun iosNetState(satisfied: Boolean, expensive: Boolean): NetState = NetState(online = satisfied, unmetered = satisfied && !expensive)

/** Identifier of the system task that keeps downloading in the background (listed in Info.plist's `BGTaskSchedulerPermittedIdentifiers`). */
internal const val CONTINUED_TASK_ID = "com.gugugaga.art.download"

/** Pure: the queued downloads of the account that is downloading now (the system card's progress covers them; other accounts' queue does not run now). */
internal fun continuedBatch(rows: List<DownloadRow>): List<String> {
    val r = rows.firstOrNull { it.running } ?: return emptyList()
    return rows.filter { it.entry.status == DlStatus.Queued && it.entry.serverId == r.entry.serverId && it.entry.userId == r.entry.userId }.map { it.key }
}

/**
 * Pure: progress (0..1000) on the system's continued-processing card. [batch] = the downloads (keys) seen during this task: finished ones
 * count as full, deleted ones drop out; one with an unknown size is left out of the total.
 */
internal fun continuedProgress(rows: List<DownloadRow>, batch: Set<String>): Long {
    var done = 0L
    var total = 0L
    for (r in rows) {
        if (r.key !in batch || r.entry.total <= 0) continue
        total += r.entry.total
        done += if (r.entry.status == DlStatus.Done) r.entry.total else r.entry.done.coerceIn(0, r.entry.total)
    }
    return if (total <= 0) 0 else (done * 1000 / total).coerceIn(0, 1000)
}

/**
 * Offline downloads on iPhone (1.6 batch F3; the counterpart of Android's `AndroidDownloadHost`):
 * - files in [dir] (the shell passes `Application Support/GugaArt/downloads`, excluded from backups, [IosDownloadStorage]);
 * - network: `NWPathMonitor` ([iosNetState]);
 * - transfer: the shared `runTransfer` (ktor's Darwin engine = `URLSession`; Range resume, retries and no retry on 403 are shared code);
 * - background: from iOS 26, when the user starts a download (app in the foreground) we ask the system for a continued-processing task
 *   (`BGContinuedProcessingTask`): downloads go on in the background and on the lock screen, the system shows title and progress and the
 *   user can stop it there; handed back when the queue is idle. Older systems (or when the system declines): the short background time
 *   of `beginBackgroundTask` (about 30 s). After that the app is suspended with the download on disk as `.part`; back in the foreground
 *   (or relaunched after being killed) the shared queue resumes with `Range`. There is no URLSession background session: one transfer,
 *   one state (the shared one).
 * One per process (set by the shell in `iosDeps`).
 */
@OptIn(ExperimentalForeignApi::class)
class IosDownloadHost(dir: String) : DownloadHost {
    override val storage: DownloadStorage = IosDownloadStorage(dir)

    /** The system's path (the first one arrives asynchronously right after `start`; online until then). */
    private var real = NetState.UNKNOWN_ONLINE
    private val net = MutableStateFlow(NetState.UNKNOWN_ONLINE)
    override val network: StateFlow<NetState> = net

    /**
     * Acceptance seam (the Debug simulator runner): non-null = pretend the system reports this network (a simulator shares the Mac's
     * network and cannot go offline); null = the system's. Nothing sets it in a release build.
     */
    var simulated: NetState? = null
        set(v) {
            field = v
            net.value = v ?: real
        }

    private val monitor = nw_path_monitor_create()

    // main thread only
    private var alive = false
    private var task: UIBackgroundTaskIdentifier = UIBackgroundTaskInvalid
    private var observers: List<Any> = emptyList()
    private var submitted = false
    private var continued: BGContinuedProcessingTask? = null
    private var continuedJob: Job? = null
    private val main = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Acceptance: state of the continued-processing task (`none` / `submitted` / `running` / `refused: …` / `expired` / `done`). */
    var continuedState: String = "none"
        private set

    init {
        current = this
        nw_path_monitor_set_update_handler(monitor) { path ->
            val n = iosNetState(nw_path_get_status(path) == nw_path_status_satisfied, nw_path_is_expensive(path))
            real = n
            if (simulated == null) net.value = n
        }
        nw_path_monitor_set_queue(monitor, dispatch_queue_create("com.gugugaga.art.download-net", null))
        nw_path_monitor_start(monitor)
        onMain {
            val center = NSNotificationCenter.defaultCenter
            val queue = NSOperationQueue.mainQueue
            observers = listOf(
                center.addObserverForName(UIApplicationDidEnterBackgroundNotification, null, queue) { _ -> if (alive) beginTask() },
                center.addObserverForName(UIApplicationWillEnterForegroundNotification, null, queue) { _ -> endTask() },
            )
        }
    }

    override fun keepAlive(on: Boolean) = onMain {
        alive = on
        if (!on) {
            endTask()
            finishContinued(success = true)
        } else if (UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateBackground) {
            beginTask()
        } else {
            requestContinued()
        }
    }

    /** The user tapped 下载 (in the foreground): the moment to ask the system for background time. */
    override fun onUserStart() = onMain { requestContinued() }

    private fun requestContinued() {
        if (submitted || continued != null) return
        if (!registered) {
            if (continuedState == "none") continuedState = "refused: not registered"
            return
        }
        if (UIApplication.sharedApplication.applicationState != UIApplicationState.UIApplicationStateActive) return
        val scheduler = BGTaskScheduler.sharedScheduler
        val rows = Downloads.rows.value
        val (title, subtitle) = notificationText(rows) ?: rows.firstOrNull { it.entry.status == DlStatus.Queued }?.let { it.entry.displayName to "排队" } ?: return
        val request = BGContinuedProcessingTaskRequest(CONTINUED_TASK_ID, title, subtitle)
        // Not now = not at all (no queueing: by the time the system would run it the user is usually back in the app)
        request.strategy = BGContinuedProcessingTaskRequestSubmissionStrategy.BGContinuedProcessingTaskRequestSubmissionStrategyFail
        val error = memScoped {
            val err = alloc<ObjCObjectVar<NSError?>>()
            if (scheduler.submitTaskRequest(request, err.ptr)) null else (err.value?.localizedDescription ?: "?")
        }
        submitted = error == null
        continuedState = if (error == null) "submitted" else "refused: $error"
        if (error != null) Downloads.log("后台接着下：系统没给（$error）")
    }

    /** Granted: keep the card's title and progress in step with the queue; on expiry (the system or the user stopped it) hand it back - only the short background time is left then. */
    private fun adopt(t: BGTask) {
        val task = t as? BGContinuedProcessingTask
        if (task == null || !alive || continued != null) {
            submitted = false
            t.setTaskCompletedWithSuccess(true)
            return
        }
        continued = task
        continuedState = "running"
        task.expirationHandler = {
            onMain {
                if (continued === task) {
                    continuedState = "expired"
                    finishContinued(success = false)
                }
            }
        }
        task.progress.totalUnitCount = 1000
        val batch = HashSet<String>()
        continuedJob = main.launch {
            Downloads.rows.collect { rows ->
                batch += continuedBatch(rows)
                notificationText(rows)?.let { (title, subtitle) -> task.updateTitle(title, subtitle) }
                task.progress.completedUnitCount = continuedProgress(rows, batch)
            }
        }
    }

    private fun finishContinued(success: Boolean) {
        continuedJob?.cancel()
        continuedJob = null
        submitted = false
        val t = continued ?: return
        continued = null
        if (continuedState == "running") continuedState = "done"
        t.setTaskCompletedWithSuccess(success)
    }

    /** Work going on when the app leaves the foreground: ask for background time; on expiry hand it back (the app is then suspended, the download stays on disk as `.part`). */
    private fun beginTask() {
        if (task != UIBackgroundTaskInvalid) return
        task = UIApplication.sharedApplication.beginBackgroundTaskWithName("gugaart-download") {
            Downloads.log("后台时间到了，下载暂停在盘上")
            endTask()
        }
    }

    private fun endTask() {
        val t = task
        if (t == UIBackgroundTaskInvalid) return
        task = UIBackgroundTaskInvalid
        UIApplication.sharedApplication.endBackgroundTask(t)
    }

    private fun onMain(block: () -> Unit) {
        dispatch_async(dispatch_get_main_queue(), block)
    }

    companion object {
        /** The process's host (the system hands the continued-processing task to it). */
        private var current: IosDownloadHost? = null
        private var registered = false

        /**
         * Called once at launch (`AppDelegate`'s didFinishLaunching via the shell's `registerBackgroundWork()`): register the handler of the
         * continued-processing task - the system wants it registered before launch finishes. Skipped before iOS 26 or when Info.plist does not
         * list the identifier (registering would raise then); downloads only get the short background time in that case.
         */
        fun registerBackgroundTask() {
            if (registered || !continuedSupported()) return
            registered = BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(CONTINUED_TASK_ID, usingQueue = null) { t ->
                if (t != null) dispatch_async(dispatch_get_main_queue()) { current?.adopt(t) ?: t.setTaskCompletedWithSuccess(true) }
            }
        }

        private fun continuedSupported(): Boolean {
            val major = UIDevice.currentDevice.systemVersion.substringBefore('.').toIntOrNull() ?: return false
            if (major < 26) return false
            val ids = NSBundle.mainBundle.objectForInfoDictionaryKey("BGTaskSchedulerPermittedIdentifiers") as? List<*> ?: return false
            return CONTINUED_TASK_ID in ids
        }
    }
}
