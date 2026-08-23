package com.deavidig.skecth.project.main.adapter

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.deavidig.skecth.project.main.dialogs.ProjectManagerDialogFragment
import com.deavidig.skecth.project.main.fragment.ActivityMainDialogProjectCreatorGradleViewPagerFragment
import com.deavidig.skecth.project.main.fragment.ActivityMainDialogProjectCreatorHomeViewPagerFragment

class MainActivityDialogProjectCreatorViewPagerFragmentAdapter(fragment: ProjectManagerDialogFragment) : FragmentStateAdapter(fragment) {
	override fun createFragment(position: Int): Fragment {
		return when (position) {
			0 -> ActivityMainDialogProjectCreatorHomeViewPagerFragment()
			in 1..2 -> ActivityMainDialogProjectCreatorGradleViewPagerFragment()
			else -> throw RuntimeException("The fragment in position $position not founded!")
		}
	}

	override fun getItemCount(): Int = 3

}