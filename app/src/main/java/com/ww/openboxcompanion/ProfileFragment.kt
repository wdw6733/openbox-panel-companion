package com.ww.openboxcompanion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import org.json.JSONObject

class ProfileFragment : Fragment(), Reloadable {

    private var rootView: View? = null
    private lateinit var store: ServerStore
    private var profileObj: JSONObject? = null
    private var loading: Boolean = false
    private var saving: Boolean = false
    private var applyingSwitches: Boolean = false

    private fun <T : View> find(id: Int): T? = rootView?.findViewById(id)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_profile, container, false)
        rootView = view
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        store = ServerStore.getInstance(requireContext())

        find<MaterialButton>(R.id.button_profile_refresh)?.setOnClickListener {
            reload()
        }
        find<MaterialButton>(R.id.button_profile_open_panel)?.setOnClickListener {
            (activity as? MainActivity)?.showTab(MainActivity.TAB_PANEL)
        }

        setupSwitch(R.id.switch_ipv6, "IPv6") { checked ->
            JSONObject().put("ipv6", checked)
        }
        setupSwitch(R.id.switch_reject_quic, "屏蔽 QUIC") { checked ->
            JSONObject().put("rejectQuic", checked)
        }
        setupSwitch(R.id.switch_direct_bypass, "直连不进内核") { checked ->
            JSONObject().put("directBypass", checked)
        }
        setupSwitch(R.id.switch_tun_auto_redirect, "TUN 自动重定向") { checked ->
            JSONObject().put("tun", JSONObject().put("autoRedirect", checked))
        }

        reload()
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
        if (!isAdded || rootView == null || loading) return
        val server = store.getSelectedServer()
        if (server == null) {
            showError("还没有服务器。请先到「工具」页添加你的面板服务器。")
            find<TextView>(R.id.text_profile_body)?.text = "没有服务器"
            return
        }
        loading = true
        hideError()
        setProgressVisible(true)
        Bg.runAsync(
            task = { ApiClient.getProfile(server) },
            onResult = { result ->
                if (!isAdded) return@runAsync
                loading = false
                setProgressVisible(false)
                when (result) {
                    is ApiResult.Success -> {
                        val inner = result.data.optJSONObject("profile") ?: result.data
                        profileObj = inner
                        find<TextView>(R.id.text_profile_body)?.text =
                            JsonUtil.summarizeProfile(inner) +
                                "\n\n原始返回（截断）：\n" +
                                JsonUtil.truncate(JsonUtil.pretty(result.data), 2000)
                        applySwitchStates(inner)
                    }
                    is ApiResult.Failure -> {
                        showError(result.message)
                        find<TextView>(R.id.text_profile_body)?.text = "加载失败"
                    }
                }
            },
            onError = { e ->
                if (!isAdded) return@runAsync
                loading = false
                setProgressVisible(false)
                showError("加载配置出错：${e.message}")
            }
        )
    }

    private fun setupSwitch(
        switchId: Int,
        label: String,
        buildPartial: (Boolean) -> JSONObject
    ) {
        val switch = find<SwitchMaterial>(switchId) ?: return
        switch.setOnCheckedChangeListener { _, isChecked ->
            if (applyingSwitches) return@setOnCheckedChangeListener
            if (saving) {
                revertSwitch(switch, isChecked)
                return@setOnCheckedChangeListener
            }
            confirmSwitchChange(switch, label, isChecked, buildPartial(isChecked))
        }
    }

    private fun confirmSwitchChange(
        switch: SwitchMaterial,
        label: String,
        checked: Boolean,
        partial: JSONObject
    ) {
        val state = if (checked) "开" else "关"
        AlertDialog.Builder(requireContext())
            .setTitle("确认修改")
            .setMessage("将“$label”设为$state？只发这一项的部分更新，保存后会回读确认。")
            .setPositiveButton("确定") { _, _ -> doSwitchChange(label, partial) }
            .setNegativeButton("取消") { _, _ -> revertSwitch(switch, checked) }
            .setOnCancelListener { revertSwitch(switch, checked) }
            .show()
    }

    private fun revertSwitch(switch: SwitchMaterial, checked: Boolean) {
        applyingSwitches = true
        switch.isChecked = !checked
        applyingSwitches = false
    }

    private fun doSwitchChange(label: String, partial: JSONObject) {
        val server = store.getSelectedServer() ?: return
        saving = true
        setProgressVisible(true)
        Bg.runAsync(
            task = { ApiClient.updateProfile(server, partial) },
            onResult = { result ->
                if (!isAdded) return@runAsync
                saving = false
                setProgressVisible(false)
                when (result) {
                    is ApiResult.Success -> {
                        Toast.makeText(requireContext(), "“$label”已保存，正在回读确认。", Toast.LENGTH_SHORT).show()
                        loading = false
                        reload()
                    }
                    is ApiResult.Failure -> {
                        showError("保存失败：" + result.message)
                        loading = false
                        reload()
                    }
                }
            },
            onError = { e ->
                if (!isAdded) return@runAsync
                saving = false
                setProgressVisible(false)
                showError("保存出错：${e.message}")
                loading = false
                reload()
            }
        )
    }

    private fun applySwitchStates(profile: JSONObject) {
        applyingSwitches = true
        val unavailable = mutableListOf<String>()

        val ipv6Switch = find<SwitchMaterial>(R.id.switch_ipv6)
        if (ipv6Switch != null) {
            if (profile.has("ipv6")) {
                ipv6Switch.isEnabled = true
                ipv6Switch.isChecked = profile.optBoolean("ipv6", false)
            } else {
                ipv6Switch.isEnabled = false
                ipv6Switch.isChecked = false
                unavailable.add("IPv6")
            }
        }

        val quicSwitch = find<SwitchMaterial>(R.id.switch_reject_quic)
        if (quicSwitch != null) {
            if (profile.has("rejectQuic")) {
                quicSwitch.isEnabled = true
                quicSwitch.isChecked = profile.optBoolean("rejectQuic", false)
            } else {
                quicSwitch.isEnabled = false
                quicSwitch.isChecked = false
                unavailable.add("屏蔽 QUIC")
            }
        }

        val bypassSwitch = find<SwitchMaterial>(R.id.switch_direct_bypass)
        if (bypassSwitch != null) {
            if (profile.has("directBypass")) {
                bypassSwitch.isEnabled = true
                bypassSwitch.isChecked = profile.optBoolean("directBypass", false)
            } else {
                bypassSwitch.isEnabled = false
                bypassSwitch.isChecked = false
                unavailable.add("直连不进内核")
            }
        }

        val tunSwitch = find<SwitchMaterial>(R.id.switch_tun_auto_redirect)
        if (tunSwitch != null) {
            val tun = profile.optJSONObject("tun")
            if (tun != null && tun.has("autoRedirect")) {
                tunSwitch.isEnabled = true
                tunSwitch.isChecked = tun.optBoolean("autoRedirect", false)
            } else {
                tunSwitch.isEnabled = false
                tunSwitch.isChecked = false
                unavailable.add("TUN 自动重定向")
            }
        }

        find<TextView>(R.id.text_profile_warning)?.text =
            if (unavailable.isEmpty()) {
                ""
            } else {
                "以下开关因面板未返回对应字段而不可用：" + unavailable.joinToString("、") + "。请在面板网页里改。"
            }
        applyingSwitches = false
    }

    private fun setProgressVisible(visible: Boolean) {
        find<ProgressBar>(R.id.progress_profile)?.visibility =
            if (visible) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        val errorView = find<TextView>(R.id.text_profile_error) ?: return
        errorView.text = message
        errorView.visibility = View.VISIBLE
    }

    private fun hideError() {
        find<TextView>(R.id.text_profile_error)?.visibility = View.GONE
    }
}
