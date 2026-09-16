package com.deavidig.skecth.project.creator.fragments.gradle

import com.deavidig.sketchprojectpro.R

interface AndroidSectionFragmentHost : GradleFragmentHost {

	fun onCreateAndroidSection() {
		val listAndroidVersionName = getResources().getStringArray(R.array.android_version_name)
		val listAndroidVersionCode = getResources().getStringArray(R.array.android_version_code)

		layout_binding.androidVersion.setLabelFormatter {
			val values = layout_binding.androidVersion.values

			when (it) {
				values[0] -> listAndroidVersionName[it.toInt()]
				values[1] -> listAndroidVersionName[it.toInt()]
				values[2] -> listAndroidVersionName[it.toInt()]
				else -> it.toInt().toString()
			}
		}

		layout_binding.androidVersion.addOnChangeListener { _, _, _ ->
			layout_binding.androidVersionMessage.text =
				"Min SDK: ${listAndroidVersionCode[layout_binding.androidVersion.values[0].toInt()]} - Target SDK: ${listAndroidVersionCode[layout_binding.androidVersion.values[1].toInt()]} - Max SDK: ${listAndroidVersionCode[layout_binding.androidVersion.values[2].toInt()]}"
		}
	}
}