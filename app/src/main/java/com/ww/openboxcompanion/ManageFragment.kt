package com.ww.openboxcompanion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class ManageFragment : Fragment(), Reloadable {

    private var rootView: View? = null
    private var mediator: TabLayoutMediator? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_manage, container, false)
        rootView = view
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val tabLayout = view.findViewById<TabLayout>(R.id.tablayout_manage)
        val viewPager = view.findViewById<ViewPager2>(R.id.viewpager_manage)
        viewPager.adapter = ManagePagerAdapter(this)
        viewPager.offscreenPageLimit = 1
        val titles = arrayOf("订阅", "节点组", "配置")
        mediator = TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = titles[position]
        }
        mediator?.attach()
    }

    override fun onDestroyView() {
        mediator?.detach()
        mediator = null
        rootView = null
        super.onDestroyView()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            reload()
        }
    }

    override fun reload() {
        if (!isAdded) return
        for (fragment in childFragmentManager.fragments) {
            (fragment as? Reloadable)?.reload()
        }
    }

    private class ManagePagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = 3

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> SubscriptionsFragment()
                1 -> GroupsFragment()
                else -> ProfileFragment()
            }
        }
    }
}
