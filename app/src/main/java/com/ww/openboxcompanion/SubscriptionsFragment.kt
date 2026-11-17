package com.ww.openboxcompanion

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import org.json.JSONObject

class SubscriptionsFragment : Fragment(), Reloadable {

    private var rootView: View? = null
    private lateinit var store: ServerStore
    private lateinit var adapter: SubscriptionAdapter
    private var loading: Boolean = false
    private var loadedOnce: Boolean = false

    private fun <T : View> find(id: Int): T? = rootView?.findViewById(id)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_subscriptions, container, false)
        rootView = view
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        store = ServerStore.getInstance(requireContext())
        adapter = SubscriptionAdapter(emptyList()) { obj -> openEditor(obj) }
        val recycler = find<RecyclerView>(R.id.recycler_sub)
        recycler?.layoutManager = LinearLayoutManager(requireContext())
        recycler?.adapter = adapter

        find<MaterialButton>(R.id.button_sub_add)?.setOnClickListener {
            openEditor(null)
        }
        find<MaterialButton>(R.id.button_sub_refresh)?.setOnClickListener {
            reload()
        }
        reload()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        rootView = null
    }

    override fun onResume() {
        super.onResume()
        if (loadedOnce) {
            reload()
        }
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
            find<TextView>(R.id.text_sub_empty)?.visibility = View.GONE
            return
        }
        loading = true
        loadedOnce = true
        hideError()
        setProgressVisible(true)
        find<TextView>(R.id.text_sub_empty)?.visibility = View.GONE
        Bg.runAsync(
            task = { ApiClient.getSubscriptions(server) },
            onResult = { result ->
                if (!isAdded) return@runAsync
                loading = false
                setProgressVisible(false)
                when (result) {
                    is ApiResult.Success -> {
                        val array = result.data.optJSONArray("subscriptions")
                        if (array == null) {
                            showError(
                                "面板没有返回 subscriptions 字段，原始返回：" +
                                    JsonUtil.truncate(JsonUtil.pretty(result.data), 500)
                            )
                            adapter.submitList(emptyList())
                            return@runAsync
                        }
                        val items = mutableListOf<JSONObject>()
                        for (i in 0 until array.length()) {
                            array.optJSONObject(i)?.let { items.add(it) }
                        }
                        adapter.submitList(items)
                        find<TextView>(R.id.text_sub_empty)?.visibility =
                            if (items.isEmpty()) View.VISIBLE else View.GONE
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
                showError("加载订阅出错：${e.message}")
            }
        )
    }

    private fun openEditor(existing: JSONObject?) {
        val server = store.getSelectedServer() ?: return
        val intent = Intent(requireContext(), SubscriptionEditActivity::class.java)
        intent.putExtra(SubscriptionEditActivity.EXTRA_SERVER_ID, server.id)
        if (existing == null) {
            intent.putExtra(SubscriptionEditActivity.EXTRA_MODE, SubscriptionEditActivity.MODE_ADD)
        } else {
            intent.putExtra(SubscriptionEditActivity.EXTRA_MODE, SubscriptionEditActivity.MODE_EDIT)
            intent.putExtra(SubscriptionEditActivity.EXTRA_SUBSCRIPTION_ID, existing.optString("id"))
            intent.putExtra(SubscriptionEditActivity.EXTRA_SUBSCRIPTION_NAME, existing.optString("name"))
            intent.putExtra(SubscriptionEditActivity.EXTRA_SUBSCRIPTION_CONTENT, existing.optString("content"))
        }
        startActivity(intent)
    }

    private fun setProgressVisible(visible: Boolean) {
        find<ProgressBar>(R.id.progress_sub)?.visibility =
            if (visible) View.VISIBLE else View.GONE
    }

    private fun showError(message: String) {
        val errorView = find<TextView>(R.id.text_sub_error) ?: return
        errorView.text = message
        errorView.visibility = View.VISIBLE
    }

    private fun hideError() {
        find<TextView>(R.id.text_sub_error)?.visibility = View.GONE
    }
}
