package com.deavidig.skecth.project.creator.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.deavidig.mod.deanielig.tablayout.widget.selectTabAt
import com.deavidig.sketchprojectpro.databinding.ActivityProjectCreatorFrontBinding
import com.google.android.material.tabs.TabLayout

class ContentFragment : Fragment() {
	lateinit var layout_binding: ActivityProjectCreatorFrontBinding
	private var position = 0

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putInt("Position of Tab", position)
	}

	override fun onViewStateRestored(savedInstanceState: Bundle?) {
		super.onViewStateRestored(savedInstanceState)
		position = savedInstanceState?.getInt("Position of Tab") ?: 0
		layout_binding.tabFilter.selectTabAt(position)
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View = layout_binding.root

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		layout_binding = ActivityProjectCreatorFrontBinding.inflate(layoutInflater)

		layout_binding.pagerFilter.adapter = ViewPager2Fragment(this)
		layout_binding.pagerFilter.isUserInputEnabled = false

		layout_binding.tabFilter.addOnTabSelectedListener(object :
			TabLayout.OnTabSelectedListener {
			override fun onTabSelected(tab: TabLayout.Tab) {
				position = tab.position
				layout_binding.pagerFilter.setCurrentItem(position, false)
			}

			override fun onTabUnselected(tab: TabLayout.Tab) {}

			override fun onTabReselected(tab: TabLayout.Tab) {}
		})
	}

	class ViewPager2Fragment(fragment: Fragment) : FragmentStateAdapter(fragment) {
		override fun createFragment(position: Int): Fragment =
			if (position == 0) HomeFragment() else GradleFragment()

		override fun getItemCount(): Int = 2
	}
}