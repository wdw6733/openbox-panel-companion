package com.ww.openboxcompanion

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

class DashboardFragment : Fragment(), Reloadable {

    private var rootView: View? = null
    private lateinit var store: ServerStore
    private var loading: Boolean = false
    private var hasLoaded: Boolean = false
    private var lastRefreshStart: Long = 0L
    private var spinnerReady: Boolean = false
    private var spinnerServers: List<Server> = emptyList()

    private fun <T : View> find(id: Int): T? = rootView?.findViewById(id)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_dashboard, container, false)
        rootView = view
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        store = ServerStore.getInstance(requireContext())

        find<MaterialButton>(R.id.button_manage_servers)?.setOnClickListener {
            (activity as? MainActivity)?.showTab(MainActivity.TAB_TOOLKIT)
        }
        find<MaterialButton>(R.id.button_refresh_all)?.setOnClickListener {
            refreshAll()
        }
        find<MaterialButton>(R.id.button_test_health)?.setOnClickListener {
            refreshHealthOnly()
        }
        find<MaterialButton>(R.id.button_login)?.setOnClickListener {
            doLogin()
        }
        find<MaterialButton>(R.id.button_core_start)?.setOnClickListener {
            confirmCoreAction("start", "启动内核")
        }
        find<MaterialButton>(R.id.button_core_stop)?.setOnClickListener {
            confirmCoreAction("stop", "停止内核")
        }
        find<MaterialButton>(R.id.button_core_restart)?.setOnClickListener {
            confirmCoreAction("restart", "重启内核")
        }
        find<MaterialButton>(R.id.button_goto_manage)?.setOnClickListener {
            (activity as? MainActivity)?.showTab(MainActivity.TAB_MANAGE)
        }
        find<MaterialButton>(R.id.button_goto_panel)?.setOnClickListener {
            (activity as? MainActivity)?.showTab(MainActivity.TAB_PANEL)
        }

        refreshSpinner()
        refreshAll()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        rootView = null
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            reload()
        }
    }

    override fun reload() {
        if (!isAdded || rootView == null) return
        refreshSpinner()
        val now = System.currentTimeMillis()
        if (loading) return
        if (hasLoaded && now - lastRefreshStart < 5000) return
        refreshAll()
    }

    private fun refreshSpinner() {
        val spinner = find<Spinner>(R.id.spinner_server) ?: return
        val servers = store.getServers()
        spinnerServers = servers
        val names = servers.map { "${it.name}（${it.displayUrl()}）" }
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            names.ifEmpty { listOf("还没有服务器，请先添加") }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerReady = false
        spinner.adapter = adapter
        val selected = store.getSelectedServer()
        val selectedIndex = servers.indexOfFirst { it.id == selected?.id }.coerceAtLeast(0)
        if (servers.isNotEmpty()) {
            spinner.setSelection(selectedIndex)
        }
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>,
                view: View?,
                position: Int,
                id: Long
            ) {
                if (!spinnerReady) {
                    spinnerReady = true
                    return
                }
                if (position !in spinnerServers.indices) return
                val picked = spinnerServers[position]
                if (store.getSelectedServerId() != picked.id) {
                    store.setSelectedServerId(picked.id)
                    refreshAll()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
            }
        }
        spinnerReady = false
        // Allow the initial selection callback to pass once without reloading.
        spinner.post { spinnerReady = true }
    }

    private fun selectedServerOrWarn(): Server? {
        val server = store.getSelectedServer()
        if (server == null) {
            showError("还没有服务器。请先到「工具」页添加你的面板服务器。")
            find<TextView>(R.id.text_health_status)?.text = "没有服务器"
            find<TextView>(R.id.text_status_body)?.text = "没有服务器"
            find<TextView>(R.id.text_profile_summary)?.text = "没有服务器"
        }
        return server
    }

    private fun refreshAll() {
        val server = selectedServerOrWarn() ?: return
        if (loading) return
        loading = true
        hasLoaded = true
        lastRefreshStart = System.currentTimeMillis()
        hideError()
        setProgressVisible(true)
        Bg.runAsync(
            task = { ApiClient.healthCheck(server.baseUrl()) },
            onResult = { health ->
                if (!isAdded) return@runAsync
                when (health) {
                    is ApiResult.Success -> {
                        find<TextView>(R.id.text_health_status)?.text =
                            "在线（HTTP ${health.data.httpCode}，${health.data.latencyMs} 毫秒）"
                        find<TextView>(R.id.text_health_detail)?.text =
                            health.data.rawBody.ifEmpty { "（健康检查没有返回内容）" }
                    }
                    is ApiResult.Failure -> {
                        find<TextView>(R.id.text_health_status)?.text = "检查失败"
                        find<TextView>(R.id.text_health_detail)?.text = health.message
                    }
                }
                refreshStatusAndProfile(server)
            },
            onError = { e ->
                if (!isAdded) return@runAsync
                loading = false
                setProgressVisible(false)
                showError("健康检查出错：${e.message}")
            }
        )
    }

    private fun refreshStatusAndProfile(server: Server) {
        Bg.runAsync(
            task = { ApiClient.getStatus(server) },
            onResult = { status ->
                if (!isAdded) return@runAsync
                when (status) {
                    is ApiResult.Success -> {
                        find<TextView>(R.id.text_status_body)?.text =
                            JsonUtil.summarizeStatus(status.data)
                        find<TextView>(R.id.text_status_hint)?.text = ""
                        find<TextView>(R.id.text_login_status)?.text = "已登录，会话有效。"
                    }
                    is ApiResult.Failure -> {
                        find<TextView>(R.id.text_status_body)?.text = "加载失败"
                        find<TextView>(R.id.text_status_hint)?.text = status.message
                        find<TextView>(R.id.text_login_status)?.text =
                            "登录可能已失效，点「登录并同步会话」重试。"
                        showError(status.message)
                    }
                }
                refreshProfileSummary(server)
            },
            onError = { e ->
                if (!isAdded) return@runAsync
                loading = false
                setProgressVisible(false)
                showError("加载状态出错：${e.message}")
            }
        )
    }

    private fun refreshProfileSummary(server: Server) {
        Bg.runAsync(
            task = { ApiClient.getProfile(server) },
            onResult = { profile ->
                if (!isAdded) return@runAsync
                loading = false
                setProgressVisible(false)
                when (profile) {
                    is ApiResult.Success -> {
                        val inner = profile.data.optJSONObject("profile") ?: profile.data
                        find<TextView>(R.id.text_profile_summary)?.text =
                            JsonUtil.summarizeProfile(inner)
                    }
                    is ApiResult.Failure -> {
                        find<TextView>(R.id.text_profile_summary)?.text =
                            "加载失败：" + profile.message
                    }
                }
            },
            onError = { e ->
                if (!isAdded) return@runAsync
                loading = false
                setProgressVisible(false)
                find<TextView>(R.id.text_profile_summary)?.text = "加载失败：${e.message}"
            }
        )
    }

    private fun refreshHealthOnly() {
        val server = selectedServerOrWarn() ?: return
        setProgressVisible(true)
        Bg.runAsync(
            task = { ApiClient.healthCheck(server.baseUrl()) },
            onResult = { health ->
                if (!isAdded) return@runAsync
                setProgressVisible(false)
                when (health) {
                    is ApiResult.Success -> {
                        find<TextView>(R.id.text_health_status)?.text =
                            "在线（HTTP ${health.data.httpCode}，${health.data.latencyMs} 毫秒）"
                        find<TextView>(R.id.text_health_detail)?.text =
                            health.data.rawBody.ifEmpty { "（健康检查没有返回内容）" }
                    }
                    is ApiResult.Failure -> {
                        find<TextView>(R.id.text_health_status)?.text = "检查失败"
                        find<TextView>(R.id.text_health_detail)?.text = health.message
                    }
                }
            },
            onError = { e ->
                if (!isAdded) return@runAsync
                setProgressVisible(false)
                find<TextView>(R.id.text_health_status)?.text = "检查失败：${e.message}"
            }
        )
    }

    private fun doLogin() {
        val server = selectedServerOrWarn() ?: return
        setProgressVisible(true)
        Bg.runAsync(
            task = { ApiClient.login(server) },
            onResult = { result ->
                if (!isAdded) return@runAsync
                setProgressVisible(false)
                when (result) {
                    is ApiResult.Success -> {
                        val synced = ApiClient.syncCookiesToWebView(server)
                        find<TextView>(R.id.text_login_status)?.text =
                            if (synced > 0) {
                                "已登录，已同步 $synced 个会话 Cookie 给面板网页。"
                            } else {
                                "已登录，但没有可同步的会话 Cookie，面板网页里可能还要再登录一次。"
                            }
                        refreshAll()
                    }
                    is ApiResult.Failure -> {
                        find<TextView>(R.id.text_login_status)?.text = "登录失败：" + result.message
                        showError(result.message)
                    }
                }
            },
            onError = { e ->
                if (!isAdded) return@runAsync
                setProgressVisible(false)
                find<TextView>(R.id.text_login_status)?.text = "登录失败：${e.message}"
            }
        )
    }

    private fun confirmCoreAction(action: String, label: String) {
        val server = selectedServerOrWarn() ?: return
        val warning = when (action) {
            "stop" -> "停止内核会断开经由它的分流，确定要停止吗？"
            "restart" -> "重启内核会短暂中断分流，确定要重启吗？"
            else -> "确定要启动内核吗？"
        }
        AlertDialog.Builder(requireContext())
            .setTitle(label)
            .setMessage(warning + "\n\n服务器：" + server.displayUrl())
            .setPositiveButton("确定") { _, _ -> doCoreAction(server, action, label) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun doCoreAction(server: Server, action: String, label: String) {
        setProgressVisible(true)
        find<TextView>(R.id.text_core_result)?.text = "正在执行$label……"
        Bg.runAsync(
            task = { ApiClient.coreAction(server, action) },
            onResult = { result ->
                if (!isAdded) return@runAsync
                setProgressVisible(false)
                when (result) {
                    is ApiResult.Success -> {
                        val ok = if (result.data.has("ok")) {
                            if (result.data.optBoolean("ok", false)) "成功" else "面板返回 ok=false"
                        } else {
                            "已返回：" + JsonUtil.truncate(JsonUtil.pretty(result.data), 300)
                        }
                        find<TextView>(R.id.text_core_result)?.text = "$label：$ok，正在回读状态……"
                        Toast.makeText(requireContext(), "$label：$ok", Toast.LENGTH_SHORT).show()
                        Handler(Looper.getMainLooper()).postDelayed({ refreshAll() }, 1500)
                    }
                    is ApiResult.Failure -> {
                        find<TextView>(R.id.text_core_result)?.text = "$label 失败：" + result.message
                        showError(result.message)
                    }
                }
            },
            onError = { e ->
                if (!isAdded) return@runAsync
                setProgressVisible(false)
                find<TextView>(R.id.text_core_result)?.text = "$label 失败：${e.message}"
            }
        )
    }

    private fun setProgressVisible(visible: Boolean) {
        find<ProgressBar>(R.id.progress_dashboard)?.visibility =
            if (visible) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        val errorView = find<TextView>(R.id.text_dashboard_error) ?: return
        errorView.text = message
        errorView.visibility = View.VISIBLE
    }

    private fun hideError() {
        find<TextView>(R.id.text_dashboard_error)?.visibility = View.GONE
    }
}
