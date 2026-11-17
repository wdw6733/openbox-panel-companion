package com.ww.openboxcompanion

import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class ServerEditActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SERVER_ID = "server_id"
        private val CONFLICT_PORTS = setOf(53, 7853, 7854, 7891, 9095)
    }

    private lateinit var store: ServerStore
    private var editingId: String? = null
    private var testing: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_server_edit)
        store = ServerStore.getInstance(this)
        editingId = intent.getStringExtra(EXTRA_SERVER_ID)

        val schemeSpinner = findViewById<Spinner>(R.id.spinner_server_scheme)
        val schemeAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            listOf("http", "https")
        )
        schemeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        schemeSpinner.adapter = schemeAdapter

        val existing = editingId?.let { store.getServer(it) }
        if (existing != null) {
            findViewById<TextInputEditText>(R.id.edit_server_name).setText(existing.name)
            schemeSpinner.setSelection(if (existing.scheme == "https") 1 else 0)
            findViewById<TextInputEditText>(R.id.edit_server_host).setText(existing.host)
            findViewById<TextInputEditText>(R.id.edit_server_port).setText(existing.port.toString())
            findViewById<TextInputEditText>(R.id.edit_server_password).setText(existing.password)
            findViewById<MaterialButton>(R.id.button_server_delete).visibility = View.VISIBLE
        } else {
            findViewById<TextInputEditText>(R.id.edit_server_port).setText("3036")
        }

        findViewById<MaterialButton>(R.id.button_server_test).setOnClickListener {
            testConnection()
        }
        findViewById<MaterialButton>(R.id.button_server_save).setOnClickListener {
            saveWithPortCheck()
        }
        findViewById<MaterialButton>(R.id.button_server_delete).setOnClickListener {
            confirmDelete()
        }
    }

    private data class ParsedInput(
        val name: String,
        val scheme: String,
        val host: String,
        val port: Int,
        val password: String
    )

    private fun parseInput(): ParsedInput? {
        var hostInput = findViewById<TextInputEditText>(R.id.edit_server_host).text
            ?.toString()?.trim() ?: ""
        var scheme = findViewById<Spinner>(R.id.spinner_server_scheme)
            .selectedItem?.toString() ?: "http"
        var portInput = findViewById<TextInputEditText>(R.id.edit_server_port).text
            ?.toString()?.trim() ?: ""

        // Accept a pasted full URL like http://192.168.1.1:3036/
        if (hostInput.contains("://")) {
            val parts = hostInput.split("://", limit = 2)
            if (parts[0] == "http" || parts[0] == "https") {
                scheme = parts[0]
            }
            hostInput = parts.getOrNull(1) ?: ""
        }
        hostInput = hostInput.substringBefore("/").trim()
        if (!hostInput.startsWith("[") && hostInput.contains(":")) {
            val hostPart = hostInput.substringBeforeLast(":")
            val portPart = hostInput.substringAfterLast(":")
            if (portPart.all { it.isDigit() }) {
                hostInput = hostPart
                if (portInput.isEmpty()) {
                    portInput = portPart
                }
            }
        }
        if (hostInput.isEmpty()) {
            Toast.makeText(this, "请填写主机地址。", Toast.LENGTH_SHORT).show()
            return null
        }
        val port = portInput.toIntOrNull()
        if (port == null || port < 1 || port > 65535) {
            Toast.makeText(this, "端口必须是 1 到 65535 的数字。", Toast.LENGTH_SHORT).show()
            return null
        }
        val nameInput = findViewById<TextInputEditText>(R.id.edit_server_name).text
            ?.toString()?.trim() ?: ""
        val password = findViewById<TextInputEditText>(R.id.edit_server_password).text
            ?.toString() ?: ""
        return ParsedInput(
            name = nameInput.ifEmpty { hostInput },
            scheme = scheme,
            host = hostInput,
            port = port,
            password = password
        )
    }

    private fun saveWithPortCheck() {
        val parsed = parseInput() ?: return
        if (parsed.port in CONFLICT_PORTS) {
            AlertDialog.Builder(this)
                .setTitle("端口可能冲突")
                .setMessage("端口 ${parsed.port} 与内核端口冲突（53、7853、7854、7891、9095），面板可能无法正常工作。仍要保存吗？")
                .setPositiveButton("仍要保存") { _, _ -> doSave(parsed) }
                .setNegativeButton("取消", null)
                .show()
        } else {
            doSave(parsed)
        }
    }

    private fun doSave(parsed: ParsedInput) {
        val id = editingId ?: ServerStore.newId()
        val server = Server(
            id = id,
            name = parsed.name,
            scheme = parsed.scheme,
            host = parsed.host,
            port = parsed.port,
            password = parsed.password
        )
        store.saveServer(server)
        store.setSelectedServerId(server.id)
        Toast.makeText(this, "已保存，并设为当前服务器。", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK)
        finish()
    }

    private fun testConnection() {
        if (testing) return
        val parsed = parseInput() ?: return
        val server = Server(
            id = editingId ?: "test",
            name = parsed.name,
            scheme = parsed.scheme,
            host = parsed.host,
            port = parsed.port,
            password = parsed.password
        )
        testing = true
        findViewById<ProgressBar>(R.id.progress_server_test).visibility = View.VISIBLE
        findViewById<TextView>(R.id.text_server_test_result).text = "正在测试……"
        Bg.runAsync(
            task = {
                val health = ApiClient.healthCheck(server.baseUrl())
                val login = ApiClient.login(server)
                Pair(health, login)
            },
            onResult = { (health, login) ->
                testing = false
                findViewById<ProgressBar>(R.id.progress_server_test).visibility = View.GONE
                val healthText = when (health) {
                    is ApiResult.Success -> "健康检查：在线（HTTP ${health.data.httpCode}，${health.data.latencyMs} 毫秒）"
                    is ApiResult.Failure -> "健康检查失败：" + health.message
                }
                val loginText = when (login) {
                    is ApiResult.Success -> "登录：成功"
                    is ApiResult.Failure -> "登录失败：" + login.message
                }
                findViewById<TextView>(R.id.text_server_test_result).text =
                    "$healthText\n$loginText"
            },
            onError = { e ->
                testing = false
                findViewById<ProgressBar>(R.id.progress_server_test).visibility = View.GONE
                findViewById<TextView>(R.id.text_server_test_result).text =
                    "测试出错：${e.message}"
            }
        )
    }

    private fun confirmDelete() {
        val id = editingId ?: return
        AlertDialog.Builder(this)
            .setTitle("删除服务器")
            .setMessage("将删除这台服务器和保存的密码，且无法恢复。确定吗？")
            .setPositiveButton("确定") { _, _ ->
                store.deleteServer(id)
                ApiClient.clearCookies()
                Toast.makeText(this, "已删除。", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
