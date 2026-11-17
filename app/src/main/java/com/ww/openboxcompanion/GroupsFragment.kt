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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import org.json.JSONObject

class GroupsFragment : Fragment(), Reloadable {

    private var rootView: View? = null
    private lateinit var store: ServerStore
    private lateinit var adapter: GroupAdapter
    private var items: List<JSONObject> = emptyList()
    private var loading: Boolean = false
    private var saving: Boolean = false
    private var loadedOnce: Boolean = false

    private fun <T : View> find(id: Int): T? = rootView?.findViewById(id)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_groups, container, false)
        rootView = view
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        store = ServerStore.getInstance(requireContext())
        adapter = GroupAdapter(emptyList()) { obj, checked -> confirmToggle(obj, checked) }
        val recycler = find<RecyclerView>(R.id.recycler_group)
        recycler?.layoutManager = LinearLayoutManager(requireContext())
        recycler?.adapter = adapter
        find<MaterialButton>(R.id.button_group_refresh)?.setOnClickListener {
            reload()
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
            adapter.submitList(emptyList())
            find<TextView>(R.id.text_group_empty)?.visibility = View.GONE
            return
        }
        loading = true
        loadedOnce = true
        hideError()
        setProgressVisible(true)
        find<TextView>(R.id.text_group_empty)?.visibility = View.GONE
        Bg.runAsync(
            task = { ApiClient.getGroups(server) },
            onResult = { result ->
                if (!isAdded) return@runAsync
                loading = false
                setProgressVisible(false)
                when (result) {
                    is ApiResult.Success -> {
                        val array = result.data.optJSONArray("groups")
                        if (array == null) {
                            showError(
                                "面板没有返回 groups 字段，原始返回：" +
                                    JsonUtil.truncate(JsonUtil.pretty(result.data), 500)
                            )
                            items = emptyList()
                            adapter.submitList(emptyList())
                            return@runAsync
                        }
                        val loaded = mutableListOf<JSONObject>()
                        for (i in 0 until array.length()) {
                            array.optJSONObject(i)?.let { loaded.add(it) }
                        }
                        items = loaded
                        adapter.submitList(loaded)
                        find<TextView>(R.id.text_group_empty)?.visibility =
                            if (loaded.isEmpty()) View.VISIBLE else View.GONE
                    }
                    is ApiResult.Failure -> {
                        showError(result.message)
                    }
                }
            },
            onError = { e ->
                if (!isAdded) return@runAsync
                loading = false
                setProgressVisible(false)
                showError("加载节点组出错：${e.message}")
            }
        )
    }

    private fun confirmToggle(obj: JSONObject, checked: Boolean) {
        if (saving) {
            adapter.refresh()
            return
        }
        val name = obj.optString("name").ifEmpty { "(未命名节点组)" }
        val label = if (checked) "启用" else "停用"
        AlertDialog.Builder(requireContext())
            .setTitle("确认修改")
            .setMessage("将节点组“$name”设为$label？保存时会把整份节点组写回面板。")
            .setPositiveButton("确定") { _, _ -> doToggle(obj, checked) }
            .setNegativeButton("取消") { _, _ -> adapter.refresh() }
            .setOnCancelListener { adapter.refresh() }
            .show()
    }

    private fun doToggle(obj: JSONObject, checked: Boolean) {
        val server = store.getSelectedServer()
        if (server == null) {
            adapter.refresh()
            return
        }
        val targetId = obj.optString("id")
        if (targetId.isEmpty()) {
            adapter.refresh()
            return
        }
        saving = true
        setProgressVisible(true)
        Bg.runAsync(
            task = {
                val copy = JSONArray()
                for (item in items) {
                    val cloned = JSONObject(item.toString())
                    if (cloned.optString("id") == targetId) {
                        cloned.put("enabled", checked)
                    }
                    copy.put(cloned)
                }
                ApiClient.updateGroups(server, copy)
            },
            onResult = { result ->
                if (!isAdded) return@runAsync
                saving = false
                setProgressVisible(false)
                when (result) {
                    is ApiResult.Success -> {
                        Toast.makeText(requireContext(), "已保存，正在重新加载。", Toast.LENGTH_SHORT).show()
                        loading = false
                        reload()
                    }
                    is ApiResult.Failure -> {
                        showError("保存失败：" + result.message)
                        adapter.refresh()
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
                adapter.refresh()
            }
        )
    }

    private fun setProgressVisible(visible: Boolean) {
        find<ProgressBar>(R.id.progress_group)?.visibility =
            if (visible) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        val errorView = find<TextView>(R.id.text_group_error) ?: return
        errorView.text = message
        errorView.visibility = View.VISIBLE
    }

    private fun hideError() {
        find<TextView>(R.id.text_group_error)?.visibility = View.GONE
    }
}
