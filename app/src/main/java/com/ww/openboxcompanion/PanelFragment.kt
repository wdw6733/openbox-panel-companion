package com.ww.openboxcompanion

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.CookieManager
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton

/**
 * Embedded full panel. The panel page itself does everything the
 * native screens do not: policies, routing, connections, traffic,
 * DNS, rule testing, backup and restore.
 */
class PanelFragment : Fragment(), Reloadable {

    private var rootView: View? = null
    private var webView: WebView? = null
    private var loadedBaseUrl: String? = null
    private lateinit var store: ServerStore

    private fun <T : View> find(id: Int): T? = rootView?.findViewById(id)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_panel, container, false)
        rootView = view
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        store = ServerStore.getInstance(requireContext())
        setupWebView()

        find<MaterialButton>(R.id.button_panel_back)?.setOnClickListener {
            webView?.let { if (it.canGoBack()) it.goBack() }
        }
        find<MaterialButton>(R.id.button_panel_forward)?.setOnClickListener {
            webView?.let { if (it.canGoForward()) it.goForward() }
        }
        find<MaterialButton>(R.id.button_panel_reload)?.setOnClickListener {
            loadPanel(forceReload = true)
        }
        find<MaterialButton>(R.id.button_panel_browser)?.setOnClickListener {
            openInBrowser()
        }
        loadPanel(forceReload = false)
    }

    override fun onDestroyView() {
        val current = webView
        if (current != null) {
            (current.parent as? ViewGroup)?.removeView(current)
            current.destroy()
        }
        webView = null
        rootView = null
        super.onDestroyView()
    }

    override fun onPause() {
        super.onPause()
        webView?.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView?.onResume()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            reload()
        }
    }

    override fun reload() {
        if (!isAdded || rootView == null) return
        loadPanel(forceReload = false)
    }

    /** Returns true if the back press was consumed by WebView history. */
    fun handleBack(): Boolean {
        val current = webView ?: return false
        return if (current.canGoBack()) {
            current.goBack()
            true
        } else {
            false
        }
    }

    private fun setupWebView() {
        val current = find<WebView>(R.id.webview_panel) ?: return
        webView = current
        val settings = current.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.loadsImagesAutomatically = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(current, true)

        current.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                return if (url.startsWith("http://") || url.startsWith("https://")) {
                    false
                } else {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        if (isAdded) {
                            Toast.makeText(requireContext(), "无法打开链接：$url", Toast.LENGTH_SHORT).show()
                        }
                    }
                    true
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                find<TextView>(R.id.text_panel_url)?.text = url
                find<ProgressBar>(R.id.progress_panel)?.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                find<TextView>(R.id.text_panel_url)?.text = url
                find<ProgressBar>(R.id.progress_panel)?.visibility = View.GONE
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame && isAdded) {
                    Toast.makeText(
                        requireContext(),
                        "网页加载失败：${error.description}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        current.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                val progress = find<ProgressBar>(R.id.progress_panel) ?: return
                progress.progress = newProgress
                progress.visibility = if (newProgress >= 100) View.GONE else View.VISIBLE
            }
        }
    }

    private fun loadPanel(forceReload: Boolean) {
        val current = webView ?: return
        val server = store.getSelectedServer()
        if (server == null) {
            loadedBaseUrl = null
            find<TextView>(R.id.text_panel_url)?.text = ""
            current.loadData(
                "<html><body><p>还没有服务器。请先到「工具」页添加你的面板服务器。</p></body></html>",
                "text/html",
                "utf-8"
            )
            return
        }
        val baseUrl = server.baseUrl()
        if (!forceReload && baseUrl == loadedBaseUrl && current.url != null) {
            return
        }
        val previousBaseUrl = loadedBaseUrl
        ApiClient.syncCookiesToWebView(server)
        loadedBaseUrl = baseUrl
        find<TextView>(R.id.text_panel_url)?.text = baseUrl
        if (forceReload && current.url != null && previousBaseUrl == baseUrl) {
            current.reload()
        } else {
            current.loadUrl(baseUrl)
        }
    }

    private fun openInBrowser() {
        val url = webView?.url ?: store.getSelectedServer()?.baseUrl() ?: return
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            if (isAdded) {
                Toast.makeText(requireContext(), "没有可用的浏览器。", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
