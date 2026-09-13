package com.deavidig.skecth.project.creator.fragments

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.deavidig.mod.deanielig.fragment.app.applyWindowInsets
import com.deavidig.skecth.project.creator.activities.ProjectCreatorActivity
import com.deavidig.sketchprojectpro.R
import com.deavidig.sketchprojectpro.databinding.ActivityProjectCreatorFrontGradleBinding
import com.google.android.material.floatingtoolbar.FloatingToolbarLayout
import com.google.android.material.tabs.TabLayout

interface IGradleSectionHost {
	var layout_binding: ActivityProjectCreatorFrontGradleBinding /* fake-base */
}

interface IGradleGradleSectionHostSection : IGradleSectionHost {
	fun requireContext(): Context // fake-base

	fun getResources(): Resources // fake-base

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
				(requireActivity() as ProjectCreatorActivity).layout_binding.bar.getRightMenu()!!
					.getItem(0).isEnabled = false
				return@doOnTextChanged
			}
			(requireActivity() as ProjectCreatorActivity).layout_binding.bar.getRightMenu()!!
				.getItem(0).isEnabled = true
		}
	}
}

interface IGradleSectionHostAndroidSection : IGradleSectionHost {
	val listAndroidVersionName: ArrayList<String>
		get() = arrayListOf(
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
	val listAndroidVersionCode: ArrayList<String>
		get() = arrayListOf(
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

	fun onCreateAndroidSection() {
		layout_binding.androidVersion.setLabelFormatter {
			val values = layout_binding.androidVersion.values

			when (it) {
				values[0] -> listAndroidVersionName[it.toInt()]
				values[1] -> listAndroidVersionName[it.toInt()]
				else -> it.toInt().toString()
			}
		}

		layout_binding.androidVersion.addOnChangeListener { _, _, _ ->
			layout_binding.androidVersionMessage.text =
				"Min SDK: ${listAndroidVersionCode[layout_binding.androidVersion.values[0].toInt()]} - Target SDK: ${listAndroidVersionCode[layout_binding.androidVersion.values[1].toInt()]}"
		}
	}
}

class GradleFragment : Fragment(), IGradleSectionHost, IGradleGradleSectionHostSection,
	IGradleSectionHostAndroidSection {
	override lateinit var layout_binding: ActivityProjectCreatorFrontGradleBinding

	var animation: ValueAnimator? = null

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View = layout_binding.root

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		applyWindowInsets()
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		layout_binding = ActivityProjectCreatorFrontGradleBinding.inflate(layoutInflater)

		layout_binding.root.setOnScrollChangeListener { _, _, scrollY, _, _ ->
			animation?.cancel()
			val startTranslationY =
				((requireParentFragment() as ContentFragment).layout_binding.tabFilter.parent as FloatingToolbarLayout).translationY
			var targetTranslationY =
				(((requireParentFragment() as ContentFragment).layout_binding.tabFilter.parent as FloatingToolbarLayout).width * 1.5).toInt()

			if (scrollY == 0) {
				targetTranslationY = 0
			}

			animation = ValueAnimator.ofFloat(0f, 1f).apply {
				duration = 500
				interpolator = FastOutSlowInInterpolator()
				addUpdateListener { animator ->
					val fraction = animator.animatedValue as Float
					((requireParentFragment() as ContentFragment).layout_binding.tabFilter.parent as FloatingToolbarLayout).translationY =
						startTranslationY + fraction * (targetTranslationY - startTranslationY)
				}
				start()
			}
		}

		layout_binding.tabFilter.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
			override fun onTabSelected(tab: TabLayout.Tab) {
				layout_binding.pagerFilter.displayedChild = tab.position
			}

			override fun onTabUnselected(tab: TabLayout.Tab) {}

			override fun onTabReselected(tab: TabLayout.Tab) {}
		})

		onCreateGradleSection()
		onCreateAndroidSection()
	}
}