package com.deavidig.skecth.project.creator.fragment

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.deavidig.sketchprojectpro.databinding.ActivityProjectCreatorGradleBinding

class ActivityManagerCreatorGradleViewPagerFragment() : Fragment() {
	private lateinit var layout_binding: ActivityProjectCreatorGradleBinding

	@RequiresApi(Build.VERSION_CODES.Q)
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		layout_binding = ActivityProjectCreatorGradleBinding.inflate(layoutInflater)

		layout_binding.modulesPagerGradle.isUserInputEnabled = false
		layout_binding.modulesPagerGradle.adapter =
			ActivityManagerCreatorGradleViewPagerFragmentViewPagerFragmentAdapter(this)

		return layout_binding.root
	}

	private class ActivityManagerCreatorGradleViewPagerFragmentViewPagerFragmentAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
		override fun createFragment(position: Int): Fragment =
			if (position == 0) ActivityManagerCreatorGradleViewPagerFragmentHome() else ActivityManagerCreatorGradleViewPagerFragmentProperty()

		override fun getItemCount(): Int = 2
	}
}
