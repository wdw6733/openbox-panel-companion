package com.ww.openboxcompanion

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/** Fragments that can reload their data when they become visible again. */
interface Reloadable {
    fun reload()
}

/** Minimal background threading: no coroutines dependency needed. */
object Bg {
    private val executor = Executors.newCachedThreadPool()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun <T> runAsync(
        task: () -> T,
        onResult: (T) -> Unit,
        onError: (Exception) -> Unit = {}
    ) {
        executor.execute {
            try {
                val result = task()
                mainHandler.post { onResult(result) }
            } catch (e: Exception) {
                mainHandler.post { onError(e) }
            }
        }
    }

    fun postToMain(task: () -> Unit) {
        mainHandler.post { task() }
    }
}

/**
 * Defensive JSON helpers. Panel responses are parsed with opt* accessors
 * everywhere: a missing or renamed field shows as "未返回", never a crash.
 */
object JsonUtil {

    fun pretty(value: Any?): String {
        return try {
            when (value) {
                is JSONObject -> value.toString(2)
                is JSONArray -> value.toString(2)
                null -> "空"
                else -> value.toString()
            }
        } catch (e: Exception) {
            value?.toString() ?: "空"
        }
    }

    fun truncate(text: String, max: Int): String {
        if (text.length <= max) return text
        return text.take(max) + "\n……（已截断）"
    }

    fun summarizeStatus(root: JSONObject): String {
        val lines = mutableListOf<String>()
        lines.add("平台：" + root.optString("platform").ifEmpty { "未返回" })
        val core = root.optJSONObject("core")
        if (core != null) {
            lines.add("内核运行：" + describeBool(core, "running"))
            lines.add("内核自启：" + describeBool(core, "autostart"))
        } else {
            lines.add("内核状态：未返回")
        }
        val container = root.optJSONObject("container")
        if (container != null) {
            lines.add("容器模式：" + container.optString("mode").ifEmpty { "未返回" })
            lines.add("容器路由：" + container.optString("routing").ifEmpty { "未返回" })
        }
        lines.add("")
        lines.add("原始返回：")
        lines.add(truncate(pretty(root), 1500))
        return lines.joinToString("\n")
    }

    fun summarizeProfile(profile: JSONObject): String {
        val lines = mutableListOf<String>()
        lines.add("IPv6：" + describeBool(profile, "ipv6"))
        lines.add("屏蔽 QUIC：" + describeBool(profile, "rejectQuic"))
        lines.add("直连不进内核：" + describeBool(profile, "directBypass"))
        val dns = profile.optJSONObject("dns")
        if (dns != null) {
            lines.add("DNS 区域：" + dns.optString("region").ifEmpty { "未返回" })
            lines.add("DNS 模式：" + dns.optString("mode").ifEmpty { "未返回" })
        }
        val tun = profile.optJSONObject("tun")
        if (tun != null) {
            lines.add("TUN 自动重定向：" + describeBool(tun, "autoRedirect"))
        }
        val routing = profile.optJSONObject("routing")
        if (routing != null) {
            lines.add("兜底出口：" + routing.optString("fallbackDefault").ifEmpty { "未返回" })
            val policies = routing.optJSONArray("policies")
            lines.add("站点集数量：" + (policies?.length()?.toString() ?: "未返回"))
        }
        return lines.joinToString("\n")
    }

    fun subscriptionDetail(obj: JSONObject): String {
        val lines = mutableListOf<String>()
        lines.add("ID：" + obj.optString("id").ifEmpty { "未返回" })
        val content = obj.optString("content")
        if (content.isNotEmpty()) {
            lines.add("内容长度：${content.length} 字符（点查看与编辑内容可见全文）")
        }
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key == "id" || key == "name" || key == "content") continue
            val value = obj.opt(key) ?: continue
            if (value is JSONObject || value is JSONArray) continue
            lines.add("$key：" + truncate(value.toString(), 80))
        }
        return lines.joinToString("\n")
    }

    fun groupDetail(obj: JSONObject): String {
        val lines = mutableListOf<String>()
        val type = obj.optString("type").ifEmpty { obj.optString("kind") }
        if (type.isNotEmpty()) lines.add("类型：$type")
        val mode = obj.optString("mode")
        if (mode.isNotEmpty()) lines.add("模式：$mode")
        val members = obj.optJSONArray("members")
        if (members != null) {
            val names = mutableListOf<String>()
            for (i in 0 until minOf(members.length(), 5)) {
                names.add(members.optString(i))
            }
            val suffix = if (members.length() > 5) " 等共 ${members.length()} 个" else ""
            lines.add("成员：" + names.joinToString("、") + suffix)
        }
        val keywords = obj.optString("keywords")
        if (keywords.isNotEmpty()) lines.add("关键词：$keywords")
        return lines.joinToString("\n").ifEmpty { "无更多信息" }
    }

    private fun describeBool(obj: JSONObject, key: String): String {
        if (!obj.has(key)) return "未返回"
        return if (obj.optBoolean(key, false)) "开" else "关"
    }
}

/** Copyable commands taken from the public Open-Box docs. */
object CommandsData {
    val commands: List<CommandItem> = listOf(
        CommandItem(
            "安装（OpenWrt / Debian / Ubuntu，SSH 以 root 执行）",
            "curl -fsSL https://raw.githubusercontent.com/liandu2024/Open-Box/main/scripts/install.sh | sh"
        ),
        CommandItem(
            "安装（GitHub 不畅时走镜像）",
            "curl -fsSL https://gh-proxy.com/raw.githubusercontent.com/liandu2024/Open-Box/main/scripts/install.sh | sh -s -- --mirror"
        ),
        CommandItem(
            "升级",
            "curl -fsSL https://raw.githubusercontent.com/liandu2024/Open-Box/main/scripts/update.sh | sh"
        ),
        CommandItem(
            "回退到上一个版本",
            "curl -fsSL https://raw.githubusercontent.com/liandu2024/Open-Box/main/scripts/update.sh | sh -s -- --rollback --direct"
        ),
        CommandItem(
            "卸载（保留数据）",
            "curl -fsSL https://raw.githubusercontent.com/liandu2024/Open-Box/main/scripts/uninstall.sh | sh"
        ),
        CommandItem(
            "卸载（连数据一起删）",
            "curl -fsSL https://raw.githubusercontent.com/liandu2024/Open-Box/main/scripts/uninstall.sh | sh -s -- --purge"
        ),
        CommandItem(
            "查看面板密码（SSH）",
            "open-box password"
        ),
        CommandItem(
            "查看当前面板端口（SSH）",
            "open-box port"
        ),
        CommandItem(
            "修改面板端口为 3080（SSH）",
            "open-box port 3080"
        ),
        CommandItem(
            "检查升级（SSH）",
            "open-box check"
        ),
        CommandItem(
            "重启内核和面板（SSH）",
            "open-box restart"
        ),
        CommandItem(
            "查看服务日志（Debian / Ubuntu）",
            "journalctl -u openbox -u openbox-panel"
        ),
        CommandItem(
            "紧急恢复直连（Debian / Ubuntu）",
            "systemctl stop openbox"
        ),
        CommandItem(
            "面板健康检查（在本机上执行）",
            "curl -fsS \"http://127.0.0.1:3036/api/health\""
        ),
        CommandItem(
            "查看面板密码（Docker）",
            "docker exec open-box open-box password"
        ),
        CommandItem(
            "查看容器日志（Docker Compose）",
            "docker compose logs -f"
        ),
        CommandItem(
            "Docker 一键安装（替换为你的容器 IP）",
            "sudo sh docker/install.sh --ip 192.168.1.2"
        )
    )
}
