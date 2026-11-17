package com.ww.openboxcompanion

import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * Single-activity shell with four tabs. Fragments are added once and
 * hidden/shown so the embedded panel WebView keeps its page when the
 * user switches tabs.
 */
class MainActivity : AppCompatActivity() {

    companion object {
        const val TAB_DASHBOARD = 0
        const val TAB_PANEL = 1
        const val TAB_MANAGE = 2
        const val TAB_TOOLKIT = 3

        private const val KEY_TAB = "current_tab"
        private const val TAG_DASHBOARD = "dashboard"
        private const val TAG_PANEL = "panel"
        private const val TAG_MANAGE = "manage"
        private const val TAG_TOOLKIT = "toolkit"
    }

    private val titles = arrayOf("总览", "面板", "管理", "工具")
    private val menuIds = intArrayOf(
        R.id.nav_dashboard,
        R.id.nav_panel,
        R.id.nav_manage,
        R.id.nav_toolkit
    )

    private lateinit var fragments: List<Fragment>
    private var currentTab: Int = TAB_DASHBOARD
    private var fragmentsInitialized: Boolean = false
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var toolbar: Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        bottomNav = findViewById(R.id.bottom_nav)

        currentTab = savedInstanceState?.getInt(KEY_TAB, TAB_DASHBOARD) ?: TAB_DASHBOARD
        if (currentTab !in 0..3) currentTab = TAB_DASHBOARD

        val dashboard = supportFragmentManager.findFragmentByTag(TAG_DASHBOARD)
            ?: DashboardFragment()
        val panel = supportFragmentManager.findFragmentByTag(TAG_PANEL)
            ?: PanelFragment()
        val manage = supportFragmentManager.findFragmentByTag(TAG_MANAGE)
            ?: ManageFragment()
        val toolkit = supportFragmentManager.findFragmentByTag(TAG_TOOLKIT)
            ?: ToolkitFragment()
        fragments = listOf(dashboard, panel, manage, toolkit)

        if (savedInstanceState == null) {
            val transaction = supportFragmentManager.beginTransaction()
            transaction.add(R.id.fragment_container, dashboard, TAG_DASHBOARD)
            transaction.add(R.id.fragment_container, panel, TAG_PANEL)
            transaction.add(R.id.fragment_container, manage, TAG_MANAGE)
            transaction.add(R.id.fragment_container, toolkit, TAG_TOOLKIT)
            for ((index, fragment) in fragments.withIndex()) {
                if (index != currentTab) transaction.hide(fragment)
            }
            transaction.commit()
        } else {
            syncVisibility()
        }
        fragmentsInitialized = true
        updateTitleAndNav()

        bottomNav.setOnItemSelectedListener { item ->
            val index = menuIds.indexOf(item.itemId)
            if (index >= 0) {
                showTab(index)
                true
            } else {
                false
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    val panelFragment = fragments[TAB_PANEL] as? PanelFragment
                    if (currentTab == TAB_PANEL && panelFragment?.handleBack() == true) {
                        return
                    }
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        )
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, currentTab)
    }

    fun showTab(index: Int) {
        if (index !in 0..3) return
        if (index == currentTab && fragmentsInitialized) return
        currentTab = index
        syncVisibility()
        updateTitleAndNav()
        (fragments[index] as? Reloadable)?.reload()
    }

    private fun syncVisibility() {
        val transaction = supportFragmentManager.beginTransaction()
        for ((index, fragment) in fragments.withIndex()) {
            if (index == currentTab) {
                transaction.show(fragment)
            } else {
                transaction.hide(fragment)
            }
        }
        transaction.commit()
    }

    private fun updateTitleAndNav() {
        toolbar.title = titles[currentTab]
        val menuId = menuIds[currentTab]
        if (bottomNav.selectedItemId != menuId) {
            bottomNav.selectedItemId = menuId
        }
    }
}
