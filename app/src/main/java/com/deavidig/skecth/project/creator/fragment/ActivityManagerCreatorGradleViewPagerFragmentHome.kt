package com.deavidig.skecth.project.creator.fragment

import android.R
import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.deavidig.sketchprojectpro.databinding.ActivityProjectCreatorGradleHomeBinding
import com.google.android.material.textview.MaterialTextView

class ActivityManagerCreatorGradleViewPagerFragmentHome() : Fragment() {
	private lateinit var layout_binding: ActivityProjectCreatorGradleHomeBinding
	val listAndroidVersionName: ArrayList<String> = arrayListOf(
		"Android 1.0",
		"Android 1.1 Petit Four",
		"Android 1.5 Cupcake",
		"Android 1.6 Donut",

		"Android 2.0 Eclair",
		"Android 2.0.1 Eclair",
		"Android 2.1 Eclair",

		"Android 2.2 Froyo",

		"Android 2.3 Gingerbread",
		"Android 2.3.3 Gingerbread",

		"Android 3.0 Honeycomb",
		"Android 3.1 Honeycomb",
		"Android 3.2 Honeycomb",

		"Android 4.0 Ice Cream Sandwich",
		"Android 4.0.3 Ice Cream Sandwich",
		"Android 4.1 Jelly Bean",
		"Android 4.2 Jelly Bean",
		"Android 4.3 Jelly Bean",
		"Android 4.4 KitKat",

		"Android 5.0 Lollipop",
		"Android 5.1 Lollipop",

		"Android 6.0 Marshmallow",

		"Android 7.0 Nougat",
		"Android 7.1 Nougat",

		"Android 8.0 Oreo",
		"Android 8.1 Oreo",

		"Android 9 Pie",

		"Android 10 Quince Tart",
		"Android 11 Red Velvet Cake",
		"Android 12 Snow Cone",
		"Android 12L Snow Cone V2",
		"Android 13 Tiramisu",
		"Android 14 Upside Down Cake",
		"Android 15 Vanilla Ice Cream",
		"Android 16 Baklava",
		"Android 17 Cinnamon Bun"
	)

	val listAndroidVersionCode: ArrayList<String> = arrayListOf(
		"API 1",
		"API 2",
		"API 3",
		"API 4",
		"API 5",
		"API 6",
		"API 7",
		"API 8",
		"API 9",

		"API 10",
		"API 11",
		"API 12",
		"API 13",
		"API 14",
		"API 15",
		"API 16",
		"API 17",
		"API 18",
		"API 19",

		"API 21",
		"API 22",
		"API 23",
		"API 24",
		"API 25",
		"API 26",
		"API 27",
		"API 28",
		"API 29",

		"API 30",
		"API 31",
		"API 32",
		"API 33",
		"API 34",
		"API 35",
		"API 36",
		"API 37"
	)

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		layout_binding = ActivityProjectCreatorGradleHomeBinding.inflate(layoutInflater)

		/*layout_binding.languageVersionOption.selectTab(layout_binding.languageVersionOption.getTabAt(2))



		layout_binding.androidOptionVersion.setLabelFormatter {
			val values = layout_binding.androidOptionVersion.values

			when (it) {
				values[0] -> listAndroidVersionName[it.toInt()]
				values[1] -> listAndroidVersionName[it.toInt()]
				else -> it.toInt().toString()
			}
		}

		layout_binding.androidOptionVersion.addOnChangeListener { _, _, _ ->
			layout_binding.androidVersionOptions.text = "Min SDK: ${listAndroidVersionCode[layout_binding.androidOptionVersion.values[0].toInt()]} - Target SDK: ${listAndroidVersionCode[layout_binding.androidOptionVersion.values[1].toInt()]}"
		}*/

		layout_binding.numberPicker.minValue = 0
		layout_binding.numberPicker.maxValue = 100
		layout_binding.numberPicker.value = 10

		layout_binding.numberPicker.setOnValueChangedListener { _, oldValue, newValue ->
			println("Cambió de $oldValue a $newValue")
		}

		layout_binding.numberPicker.wrapSelectorWheel = false

		return layout_binding.root
	}
}