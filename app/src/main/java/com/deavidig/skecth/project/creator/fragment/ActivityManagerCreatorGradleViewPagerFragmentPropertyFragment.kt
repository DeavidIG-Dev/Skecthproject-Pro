package com.deavidig.skecth.project.creator.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.deavidig.sketchprojectpro.databinding.ActivityProjectCreatorGradlePropertyViewpagerFragmentPropertyBinding

class ActivityManagerCreatorGradleViewPagerFragmentPropertyFragment() : Fragment() {
	private lateinit var layout_binding: ActivityProjectCreatorGradlePropertyViewpagerFragmentPropertyBinding

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		layout_binding = ActivityProjectCreatorGradlePropertyViewpagerFragmentPropertyBinding.inflate(layoutInflater)

		return layout_binding.root
	}
}
