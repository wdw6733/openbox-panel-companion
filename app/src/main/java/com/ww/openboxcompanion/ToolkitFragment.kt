package com.ww.openboxcompanion

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class ToolkitFragment : Fragment(), Reloadable {

    private var rootView: View? = null
    private lateinit var store: ServerStore
    private lateinit var serverAdapter: ServerAdapter
    private var checkingRelease: Boolean = false

    private fun <T : View> find(id: Int): T? = rootView?.findViewById(id)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_toolkit, container, false)
        rootView = view
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        store = ServerStore.getInstance(requireContext())

        serverAdapter = ServerAdapter(
            emptyList(),
            null,
            onSelect = { server ->
                store.setSelectedServerId(server.id)
                refreshServerList()
                Toast.makeText(requireContext(), "已切换到 ${server.name}", Toast.LENGTH_SHORT).show()
            },
            onEdit = { server ->
                val intent = Intent(requireContext(), ServerEditActivity::class.java)
                intent.putExtra(ServerEditActivity.EXTRA_SERVER_ID, server.id)
                startActivity(intent)
            }
        )
        val serverRecycler = find<RecyclerView>(R.id.recycler_servers)
        serverRecycler?.layoutManager = LinearLayoutManager(requireContext())
        serverRecycler?.adapter = serverAdapter

        val commandAdapter = CommandAdapter(CommandsData.commands) { item ->
            copyText(item.title, item.body)
        }
        val commandRecycler = find<RecyclerView>(R.id.recycler_commands)
        commandRecycler?.layoutManager = LinearLayoutManager(requireContext())
        commandRecycler?.adapter = commandAdapter

        find<MaterialButton>(R.id.button_server_add)?.setOnClickListener {
            val intent = Intent(requireContext(), ServerEditActivity::class.java)
            startActivity(intent)
        }
        find<MaterialButton>(R.id.button_release_check)?.setOnClickListener {
            checkReleases()
        }
        find<MaterialButton>(R.id.button_release_open)?.setOnClickListener {
            openReleasesPage()
        }
        find<MaterialButton>(R.id.button_clear_webview)?.setOnClickListener {
            confirmClearWebView()
        }
        find<MaterialButton>(R.id.button_clear_all)?.setOnClickListener {
            confirmClearAll()
        }

        find<TextView>(R.id.text_about_body)?.text =
            "面板伴侣 0.1.0，非官方原创工具，与 Open-Box 作者无关，不含 Open-Box 自有程序。\n\n" +
                "原生界面只用公开仓库里出现的接口：健康检查、密码登录、服务状态、内核启停、订阅、节点组、配置。连接、流量、策略等功能在面板网页里完成。\n\n" +
                "密码加密存在本机，只发给你自己的面板；App 不备份、不上传数据。面板是 HTTP，只能在局域网或你自己的隧道里用。"

        reload()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        rootView = null
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            reload()
        }
    }

    override fun reload() {
        if (!isAdded || rootView == null) return
        refreshServerList()
        val warning = find<TextView>(R.id.text_storage_warning)
        if (warning != null) {
            if (store.isSecureStorage()) {
                warning.visibility = View.GONE
            } else {
                warning.text = "注意：这台设备的加密存储不可用，密码已退回普通本地存储（仍不离开手机）。如介意，请不要保存密码。"
                warning.visibility = View.VISIBLE
            }
        }
    }

    private fun refreshServerList() {
        serverAdapter.submitList(store.getServers(), store.getSelectedServerId())
    }

    private fun checkReleases() {
        if (checkingRelease) return
        checkingRelease = true
        find<ProgressBar>(R.id.progress_release)?.visibility = View.VISIBLE
        Bg.runAsync(
            task = { ApiClient.fetchReleases() },
            onResult = { result ->
                if (!isAdded) return@runAsync
                checkingRelease = false
                find<ProgressBar>(R.id.progress_release)?.visibility = View.GONE
                when (result) {
                    is ApiResult.Success -> {
                        find<TextView>(R.id.text_release_body)?.text = formatReleases(result.data)
                    }
                    is ApiResult.Failure -> {
                        find<TextView>(R.id.text_release_body)?.text = "检查失败：" + result.message
                    }
                }
            },
            onError = { e ->
                if (!isAdded) return@runAsync
                checkingRelease = false
                find<ProgressBar>(R.id.progress_release)?.visibility = View.GONE
                find<TextView>(R.id.text_release_body)?.text = "检查失败：${e.message}"
            }
        )
    }

    private fun formatReleases(releases: List<ReleaseInfo>): String {
        if (releases.isEmpty()) return "没有找到 Release。"
        val lines = mutableListOf<String>()
        for ((index, release) in releases.withIndex()) {
            val label = if (index == 0) "最新版本" else "历史版本"
            lines.add("$label：${release.tag} ${release.name}")
            if (release.publishedAt.isNotEmpty()) {
                lines.add("发布时间：${release.publishedAt}")
            }
            val androidApk = release.assets.firstOrNull { it.name.startsWith("open-box-android-") }
            if (androidApk != null) {
                lines.add("官方 Android APK：${androidApk.name}（${formatSize(androidApk.sizeBytes)}），请在浏览器中打开 Releases 页下载。")
            }
            lines.add("")
        }
        lines.add("本 App 不转存官方安装包，下载请走官方 Releases 页。")
        return lines.joinToString("\n").trim()
    }

    private fun formatSize(bytes: Long): String {
        return if (bytes >= 1024 * 1024) {
            "${bytes / 1024 / 1024} MB"
        } else if (bytes >= 1024) {
            "${bytes / 1024} KB"
        } else {
            "$bytes 字节"
        }
    }

    private fun openReleasesPage() {
        try {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://github.com/liandu2024/Open-Box/releases")
            )
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            if (isAdded) {
                Toast.makeText(requireContext(), "没有可用的浏览器。", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copyText(label: String, text: String) {
        val clipboard = requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) {
            Toast.makeText(requireContext(), "无法访问剪贴板。", Toast.LENGTH_SHORT).show()
            return
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(requireContext(), "已复制：$label", Toast.LENGTH_SHORT).show()
    }

    private fun confirmClearWebView() {
        AlertDialog.Builder(requireContext())
            .setTitle("清除网页数据")
            .setMessage("将清除面板网页的 Cookie 和本地存储，网页里需要重新登录。服务器和密码不受影响。确定吗？")
            .setPositiveButton("确定") { _, _ ->
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                WebStorage.getInstance().deleteAllData()
                ApiClient.clearCookies()
                Toast.makeText(requireContext(), "网页数据已清除。", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun confirmClearAll() {
        AlertDialog.Builder(requireContext())
            .setTitle("清除全部服务器")
            .setMessage("将删除全部服务器和保存的密码，且无法恢复。确定吗？")
            .setPositiveButton("确定") { _, _ ->
                store.clearAll()
                ApiClient.clearCookies()
                refreshServerList()
                Toast.makeText(requireContext(), "已清除全部服务器。", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
