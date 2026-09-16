package com.deavidig.skecth.project.creator.fragments.gradle

import android.content.Context
import android.widget.ArrayAdapter
import androidx.core.view.get
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.FragmentActivity
import com.deavidig.skecth.project.creator.activities.ProjectCreatorActivity
import com.deavidig.sketchprojectpro.R

interface GradleSectionFragmentHost : GradleFragmentHost {
	fun requireContext(): Context // fake-base

	fun requireActivity(): FragmentActivity // fake-base

	val VERSION_CODE_REGEX: Regex
		get() = "[1-9][0-9]*".toRegex()

	fun onCreateGradleSection() {
		val array = getResources().getStringArray(R.array.android_application_prefix)

		layout_binding.nameApplicationSuffix.setAdapter(
			ArrayAdapter(
				requireContext(),
				android.R.layout.simple_list_item_1,
				array
			)
		)
		layout_binding.versionCode.doOnTextChanged { text, _, _, _ ->
			text!!
			if (!text.matches(VERSION_CODE_REGEX)) {
				layout_binding.versionCodeBox.setErrorEnabled(true)
				layout_binding.versionCodeBox.setErrorText("The Version Code only can contains numerics.")
				(requireActivity() as ProjectCreatorActivity).layout_binding.bar.getRightMenu()!![0].isEnabled =
					false
				return@doOnTextChanged
			}
			(requireActivity() as ProjectCreatorActivity).layout_binding.bar.getRightMenu()!![0].isEnabled =
				true
		}
	}
}