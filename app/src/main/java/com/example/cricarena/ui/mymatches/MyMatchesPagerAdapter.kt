package com.example.cricarena.ui.mymatches

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class MyMatchesPagerAdapter(parent: Fragment) : FragmentStateAdapter(parent) {

    override fun getItemCount(): Int = 3

    override fun createFragment(position: Int): Fragment {
        val tab = when (position) {
            0 -> MyMatchesTab.JOINED
            1 -> MyMatchesTab.CREATED
            else -> MyMatchesTab.COMPLETED
        }
        return MyMatchesListFragment.newInstance(tab)
    }
}
