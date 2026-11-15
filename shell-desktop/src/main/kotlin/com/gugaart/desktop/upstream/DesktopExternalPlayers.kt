/*
 * 复制自上游壳（docs/adr/0002-player-wiring-in-repo.md §6，登记在 docs/upstream-sync.md §5.1）：
 *   来源  gugugagaemby/native/desktopApp/src/main/kotlin/com/gugugaga/desktop/DesktopExternalPlayers.kt
 *   提交  1e19741a（upstream.lock）
 *   改动  只改了 package
 * 不要在这里改行为：上游改了来源文件就把同样的改动搬过来（git -C <上游> diff <lock>..HEAD -- <来源文件>）。
 */
package com.gugaart.desktop.upstream

import com.gugugaga.app.player.ExternalPlayerInfo
import com.gugugaga.app.player.ExternalPlayers
import java.io.File

/**
 * 外部播放器 (T06 second batch): the players installed on this Windows machine, and handing one a stream (web:
 * `host/ExternalPlayers.kt`; never part of the automatic engine chain - only the user's explicit choice).
 *
 * Detection is registry + the usual install folders, both cheap and read-only:
 *  - PotPlayer: `HKCU/HKLM SOFTWARE\DAUM\PotPlayer` `ProgramPath`, `%ProgramFiles%\DAUM\PotPlayer\PotPlayerMini64.exe`;
 *  - VLC: `SOFTWARE\VideoLAN\VLC` `InstallDir`, `%ProgramFiles%\VideoLAN\VLC\vlc.exe`;
 *  - mpv (the standalone player, not our bundled core): `%ProgramFiles%\mpv\mpv.exe`, `%ProgramFiles%\mpv.net\mpvnet.exe`,
 *    `where mpv` on the PATH;
 *  - MPC-HC: `%ProgramFiles%\MPC-HC\mpc-hc64.exe`, `%ProgramFiles(x86)%\MPC-HC\mpc-hc.exe`.
 *
 * The stream is passed on the command line (`--start=<s>` / `--input-slave` not used): a URL, the start position where the
 * player supports one, and the title. The Emby access token is already part of the playback URL the page negotiated - it is
 * never logged, echoed or written anywhere; the process is started with a redirected, discarded output so nothing leaks to a
 * console either.
 */
class DesktopExternalPlayers(
    private val env: Map<String, String> = System.getenv(),
    /** Path probes (tests): key -> the executable path, or null. */
    private val registry: (String) -> String? = { readRegistry(it) },
    private val exists: (String) -> Boolean = { File(it).isFile },
    private val onPath: (String) -> String? = { whereOnPath(it) },
    private val rootDrives: () -> List<String> = { defaultDrives() },
) : com.gugugaga.app.player.ExternalPlayers {

    private class Player(val id: String, val name: String, val candidates: List<() -> String?>)

    private fun pf(): String? = env["ProgramFiles"]?.takeIf { it.isNotBlank() }
    private fun pf86(): String? = env["ProgramFiles(x86)"]?.takeIf { it.isNotBlank() }

    /** In preference order: the ones with the best "start at a position" support first. */
    private val players: List<Player> = listOf(
        Player("potplayer", "PotPlayer", listOf(
            { registry("HKLM\\SOFTWARE\\DAUM\\PotPlayer") ?: registry("HKCU\\SOFTWARE\\DAUM\\PotPlayer") },
            { registry("HKLM\\SOFTWARE\\WOW6432Node\\DAUM\\PotPlayer") },
            { registry("HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\PotPlayerMini64.exe") },
            { registry("HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\PotPlayer64.exe") },
            { onPath("PotPlayerMini64.exe") ?: onPath("PotPlayer64.exe") ?: onPath("PotPlayer.exe") },
            { pf()?.let { "$it\\DAUM\\PotPlayer\\PotPlayerMini64.exe" } },
            { pf86()?.let { "$it\\DAUM\\PotPlayer\\PotPlayerMini.exe" } },
        ) + rootDrives().flatMap { d ->
            listOf(
                { "$d\\PotPlayer\\PotPlayerMini64.exe" },
                { "$d\\PotPlayer\\PotPlayer64.exe" },
                { "$d\\PotPlayer\\PotPlayer.exe" },
                { "$d\\Program Files\\DAUM\\PotPlayer\\PotPlayerMini64.exe" },
                { "$d\\Program Files (x86)\\DAUM\\PotPlayer\\PotPlayerMini.exe" },
            )
        }),
        Player("vlc", "VLC", listOf(
            { registry("HKLM\\SOFTWARE\\VideoLAN\\VLC")?.let { "$it\\vlc.exe" } },
            { registry("HKLM\\SOFTWARE\\WOW6432Node\\VideoLAN\\VLC")?.let { "$it\\vlc.exe" } },
            { registry("HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\vlc.exe") },
            { onPath("vlc.exe") },
            { pf()?.let { "$it\\VideoLAN\\VLC\\vlc.exe" } },
            { pf86()?.let { "$it\\VideoLAN\\VLC\\vlc.exe" } },
        ) + rootDrives().flatMap { d ->
            listOf(
                { "$d\\Program Files\\VideoLAN\\VLC\\vlc.exe" },
                { "$d\\Program Files (x86)\\VideoLAN\\VLC\\vlc.exe" },
                { "$d\\VideoLAN\\VLC\\vlc.exe" },
                { "$d\\VLC\\vlc.exe" },
            )
        }),
        Player("mpv", "mpv", listOf(
            { pf()?.let { "$it\\mpv\\mpv.exe" } },
            { pf86()?.let { "$it\\mpv.net\\mpvnet.exe" } },
            { onPath("mpv.exe") },
        ) + rootDrives().flatMap { d ->
            listOf(
                { "$d\\fntv-mpv\\mpv.exe" },
                { "$d\\Program Files\\fntv\\third_party\\fntv-mpv\\mpv.exe" },
                { "$d\\mpv\\mpv.exe" },
                { "$d\\mpv.net\\mpvnet.exe" },
                { "$d\\Program Files\\mpv\\mpv.exe" },
                { "$d\\Program Files\\mpv.net\\mpvnet.exe" },
            )
        }),
        Player("mpc-be", "MPC-BE", listOf(
            { registry("HKLM\\SOFTWARE\\MPC-BE")?.let { "$it\\mpc-be64.exe" } },
            { registry("HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\mpc-be64.exe") },
            { onPath("mpc-be64.exe") ?: onPath("mpc-be.exe") },
            { pf()?.let { "$it\\MPC-BE\\mpc-be64.exe" } },
            { pf86()?.let { "$it\\MPC-BE\\mpc-be.exe" } },
        ) + rootDrives().flatMap { d ->
            listOf(
                { "$d\\Program Files\\MPC-BE\\mpc-be64.exe" },
                { "$d\\Program Files (x86)\\MPC-BE\\mpc-be.exe" },
                { "$d\\MPC-BE\\mpc-be64.exe" },
            )
        }),
        Player("mpc-hc", "MPC-HC", listOf(
            { registry("HKLM\\SOFTWARE\\MPC-HC")?.let { "$it\\mpc-hc64.exe" } },
            { registry("HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\mpc-hc64.exe") },
            { onPath("mpc-hc64.exe") ?: onPath("mpc-hc.exe") },
            { pf()?.let { "$it\\MPC-HC\\mpc-hc64.exe" } },
            { pf86()?.let { "$it\\MPC-HC\\mpc-hc.exe" } },
        ) + rootDrives().flatMap { d ->
            listOf(
                { "$d\\Program Files\\MPC-HC\\mpc-hc64.exe" },
                { "$d\\Program Files (x86)\\MPC-HC\\mpc-hc.exe" },
                { "$d\\MPC-HC\\mpc-hc64.exe" },
            )
        }),
    )

    /** id -> executable; computed once per instance (installing a player while the app runs needs a restart: acceptable). */
    private val found: Map<String, String> by lazy {
        players.mapNotNull { p -> p.candidates.asSequence().mapNotNull { it() }.map { it.trim('"') }.firstOrNull { exists(it) }?.let { p.id to it } }.toMap()
    }

    override suspend fun list(): List<ExternalPlayerInfo> = installed()

    /** The installed players, in preference order (synchronous: the disk / registry probe is cached after the first call). */
    fun installed(): List<ExternalPlayerInfo> = players.filter { it.id in found }.map { ExternalPlayerInfo(it.id, it.name) }

    override suspend fun open(playerId: String, url: String, title: String?, startMs: Long?, headers: Map<String, String>?) {
        val exe = found[playerId] ?: found.values.firstOrNull() ?: throw IllegalStateException("未检测到可用的外部播放器")
        val args = openArgs(playerId, url, title, startMs)
        val pb = ProcessBuilder(listOf(exe) + args)
        pb.redirectErrorStream(true)
        pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
        pb.start()
    }

    companion object {
        /**
         * Command line of [playerId] for [url]: a start position where the player understands one (PotPlayer `--start`,
         * VLC `--start-time`, mpv `--start`), the title as the window title where supported. Pure, so the argument list is
         * unit tested; the URL itself is opaque here.
         */
        fun openArgs(playerId: String, url: String, title: String?, startMs: Long?): List<String> {
            val startSec = ((startMs ?: 0L) / 1000L).coerceAtLeast(0L)
            val name = title?.takeIf { it.isNotBlank() }
            return when (playerId) {
                "potplayer" -> buildList {
                    if (startSec > 0) add("--start=${startSec}s")
                    add(url)
                }
                "vlc" -> buildList {
                    if (startSec > 0) add("--start-time=$startSec")
                    add(url)
                    if (name != null) addAll(listOf("--meta-title", name))
                }
                "mpv" -> buildList {
                    if (startSec > 0) add("--start=$startSec")
                    if (name != null) add("--force-media-title=$name")
                    add(url)
                }
                "mpc-be", "mpc-hc" -> buildList {
                    if (startSec > 0) add("/startpos")
                    if (startSec > 0) add(startSec.toString())
                    add(url)
                }
                else -> listOf(url)
            }
        }

        internal fun defaultDrives(): List<String> = runCatching {
            File.listRoots()?.mapNotNull { it.path.trimEnd('\\', '/') }?.filter { it.isNotBlank() }
        }.getOrNull()?.ifEmpty { null } ?: listOf("C:", "D:", "E:", "F:", "G:")

        /**
         * The raw registry value (`ProgramPath` = an exe, `InstallDir` = a folder), or null when the key is absent (non-Windows, no
         * key, reg.exe missing). The caller knows which shape its player stores, so nothing is normalised here.
         */
        internal fun readRegistry(key: String): String? = runCatching {
            val parts = key.split('\\', limit = 3)
            if (parts.size < 3) return null
            val hive = when (parts[0].uppercase()) {
                "HKLM", "HKEY_LOCAL_MACHINE" -> "HKLM:\\"
                "HKCU", "HKEY_CURRENT_USER" -> "HKCU:\\"
                else -> return null
            }
            val ps = "$hive${parts[1]}\\${parts[2]}"
            registryValue(ps)
        }.getOrNull()

        /** `(Get-ItemProperty -Path <ps>).ProgramPath ?? .InstallDir`: two round trips through reg.exe, ~20 ms each. */
        private fun registryValue(path: String): String? {
            val r = runCatching {
                val p = ProcessBuilder("reg", "query", path).redirectErrorStream(true).start()
                val text = p.inputStream.bufferedReader().readText()
                p.waitFor()
                text
            }.getOrNull() ?: return null
            for (name in listOf("ProgramPath", "InstallDir", "ExePath", "")) {
                val pattern = if (name.isEmpty()) {
                    """^\s*<NO NAME>\s+REG_\w+\s+(.+?)\s*$"""
                } else {
                    """^\s*${Regex.escape(name)}\s+REG_\w+\s+(.+?)\s*$"""
                }
                Regex(pattern, RegexOption.MULTILINE).find(r)?.let { return it.groupValues[1].trim().trimEnd('\\') }
            }
            return null
        }

        /** `where <exe>`: the first match on the PATH (read-only, no console). */
        internal fun whereOnPath(exe: String): String? = runCatching {
            val p = ProcessBuilder("where", exe).redirectErrorStream(true).start()
            val text = p.inputStream.bufferedReader().readText()
            p.waitFor()
            text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && File(it).isFile }
        }.getOrNull()
    }
}