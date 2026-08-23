package com.deavidig.skecth.project.main.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.deavidig.sketchprojectpro.databinding.ActivityMainDialogProjectCreatorGradleBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ActivityMainDialogProjectCreatorGradleViewPagerFragment() : Fragment() {
	private lateinit var layoutBinding: ActivityMainDialogProjectCreatorGradleBinding

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		layoutBinding = ActivityMainDialogProjectCreatorGradleBinding.inflate(inflater)

		layoutBinding.modulesGradle.headerView!!.setOnClickListener {
			MaterialAlertDialogBuilder(requireContext())
				.setTitle("Create new Gradle Module.")
				.setMessage("Create a new Module for you project using Gradle.")
				.setPositiveButton("Create") { _, _ -> }
				.setNegativeButton("Cancel", null)
				.show()
		}

		return layoutBinding.root
	}
}
