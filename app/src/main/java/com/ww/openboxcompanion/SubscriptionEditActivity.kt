package com.ww.openboxcompanion

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class SubscriptionEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODE = "mode"
        const val EXTRA_SERVER_ID = "server_id"
        const val EXTRA_SUBSCRIPTION_ID = "subscription_id"
        const val EXTRA_SUBSCRIPTION_NAME = "subscription_name"
        const val EXTRA_SUBSCRIPTION_CONTENT = "subscription_content"
        const val MODE_ADD = "add"
        const val MODE_EDIT = "edit"
    }

    private lateinit var store: ServerStore
    private var server: Server? = null
    private var mode: String = MODE_ADD
    private var subscriptionId: String = ""
    private var saving: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_subscription_edit)
        store = ServerStore.getInstance(this)

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_ADD
        val serverId = intent.getStringExtra(EXTRA_SERVER_ID)
        server = serverId?.let { store.getServer(it) } ?: store.getSelectedServer()
        if (server == null) {
            Toast.makeText(this, "没有可用的服务器。", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        subscriptionId = intent.getStringExtra(EXTRA_SUBSCRIPTION_ID) ?: ""
        val nameInput = findViewById<TextInputEditText>(R.id.edit_sub_name)
        val contentInput = findViewById<TextInputEditText>(R.id.edit_sub_content)

        if (mode == MODE_EDIT) {
            findViewById<TextView>(R.id.text_sub_edit_title).text = "编辑订阅"
            findViewById<TextView>(R.id.text_sub_edit_warning).text =
                "修改内容会整体替换该订阅的内容，保存前请确认。订阅名称在这里不可改，如需改名请在面板网页里操作。"
            nameInput.setText(intent.getStringExtra(EXTRA_SUBSCRIPTION_NAME) ?: "")
            nameInput.isEnabled = false
            contentInput.setText(intent.getStringExtra(EXTRA_SUBSCRIPTION_CONTENT) ?: "")
        } else {
            findViewById<TextView>(R.id.text_sub_edit_title).text = "新增订阅"
            findViewById<TextView>(R.id.text_sub_edit_warning).text =
                "新增后请在面板网页里确认订阅已正确解析。内容可以是链接、Clash YAML 或节点分享链接。"
        }

        findViewById<MaterialButton>(R.id.button_sub_save).setOnClickListener {
            save()
        }
        findViewById<MaterialButton>(R.id.button_sub_cancel).setOnClickListener {
            finish()
        }
    }

    private fun save() {
        if (saving) return
        val currentServer = server ?: return
        val name = findViewById<TextInputEditText>(R.id.edit_sub_name).text
            ?.toString()?.trim() ?: ""
        val content = findViewById<TextInputEditText>(R.id.edit_sub_content).text
            ?.toString() ?: ""
        if (mode == MODE_ADD && name.isEmpty()) {
            Toast.makeText(this, "请填写订阅名称。", Toast.LENGTH_SHORT).show()
            return
        }
        if (content.isBlank()) {
            Toast.makeText(this, "请填写订阅内容。", Toast.LENGTH_SHORT).show()
            return
        }
        if (mode == MODE_EDIT && subscriptionId.isEmpty()) {
            Toast.makeText(this, "这个订阅没有 ID，无法保存。", Toast.LENGTH_SHORT).show()
            return
        }
        saving = true
        findViewById<ProgressBar>(R.id.progress_sub_save).visibility = View.VISIBLE
        findViewById<TextView>(R.id.text_sub_save_result).text = "正在保存……"
        Bg.runAsync(
            task = {
                if (mode == MODE_ADD) {
                    ApiClient.addSubscription(currentServer, name, content)
                } else {
                    ApiClient.updateSubscriptionContent(currentServer, subscriptionId, content)
                }
            },
            onResult = { result ->
                saving = false
                findViewById<ProgressBar>(R.id.progress_sub_save).visibility = View.GONE
                when (result) {
                    is ApiResult.Success -> {
                        Toast.makeText(this, "已保存。", Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    }
                    is ApiResult.Failure -> {
                        findViewById<TextView>(R.id.text_sub_save_result).text =
                            "保存失败：" + result.message
                    }
                }
            },
            onError = { e ->
                saving = false
                findViewById<ProgressBar>(R.id.progress_sub_save).visibility = View.GONE
                findViewById<TextView>(R.id.text_sub_save_result).text = "保存出错：${e.message}"
            }
        )
    }
}
