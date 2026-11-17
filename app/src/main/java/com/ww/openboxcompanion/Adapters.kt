package com.ww.openboxcompanion

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import org.json.JSONObject

class ServerAdapter(
    private var servers: List<Server>,
    private var selectedId: String?,
    private val onSelect: (Server) -> Unit,
    private val onEdit: (Server) -> Unit
) : RecyclerView.Adapter<ServerAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.text_server_name)
        val url: TextView = view.findViewById(R.id.text_server_url)
        val badge: TextView = view.findViewById(R.id.text_server_badge)
        val editButton: MaterialButton = view.findViewById(R.id.button_server_edit)
    }

    fun submitList(newServers: List<Server>, newSelectedId: String?) {
        servers = newServers
        selectedId = newSelectedId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_server, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val server = servers[position]
        holder.name.text = server.name
        holder.url.text = server.displayUrl()
        if (server.id == selectedId) {
            holder.badge.text = "当前服务器"
            holder.badge.visibility = View.VISIBLE
        } else {
            holder.badge.text = ""
            holder.badge.visibility = View.GONE
        }
        holder.itemView.setOnClickListener { onSelect(server) }
        holder.editButton.setOnClickListener { onEdit(server) }
    }

    override fun getItemCount(): Int = servers.size
}

class SubscriptionAdapter(
    private var items: List<JSONObject>,
    private val onOpen: (JSONObject) -> Unit
) : RecyclerView.Adapter<SubscriptionAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.text_sub_name)
        val detail: TextView = view.findViewById(R.id.text_sub_detail)
        val editButton: MaterialButton = view.findViewById(R.id.button_sub_edit)
    }

    fun submitList(newItems: List<JSONObject>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_subscription, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val obj = items[position]
        holder.name.text = obj.optString("name").ifEmpty { "(未命名订阅)" }
        holder.detail.text = JsonUtil.subscriptionDetail(obj)
        holder.itemView.setOnClickListener { onOpen(obj) }
        holder.editButton.setOnClickListener { onOpen(obj) }
    }

    override fun getItemCount(): Int = items.size
}

class GroupAdapter(
    private var items: List<JSONObject>,
    private val onToggle: (JSONObject, Boolean) -> Unit
) : RecyclerView.Adapter<GroupAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.text_group_name)
        val detail: TextView = view.findViewById(R.id.text_group_detail)
        val enabledSwitch: SwitchMaterial = view.findViewById(R.id.switch_group_enabled)
    }

    fun submitList(newItems: List<JSONObject>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun refresh() {
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_group, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val obj = items[position]
        holder.name.text = obj.optString("name").ifEmpty { "(未命名节点组)" }
        holder.detail.text = JsonUtil.groupDetail(obj)
        val hasId = obj.optString("id").isNotEmpty()
        holder.enabledSwitch.setOnCheckedChangeListener(null)
        holder.enabledSwitch.isChecked = obj.optBoolean("enabled", false)
        holder.enabledSwitch.isEnabled = hasId
        holder.enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
            onToggle(obj, isChecked)
        }
    }

    override fun getItemCount(): Int = items.size
}

class CommandAdapter(
    private val items: List<CommandItem>,
    private val onCopy: (CommandItem) -> Unit
) : RecyclerView.Adapter<CommandAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.text_command_title)
        val body: TextView = view.findViewById(R.id.text_command_body)
        val copyButton: MaterialButton = view.findViewById(R.id.button_command_copy)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_command, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.title.text = item.title
        holder.body.text = item.body
        holder.copyButton.setOnClickListener { onCopy(item) }
        holder.itemView.setOnClickListener { onCopy(item) }
    }

    override fun getItemCount(): Int = items.size
}
