package com.deavidig.skecth.project.creator.fragments.gradle

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.deavidig.mod.deanielig.fragment.app.applyWindowInsets
import com.deavidig.skecth.project.creator.fragments.ContentFragment
import com.deavidig.sketchprojectpro.databinding.ActivityProjectCreatorFrontGradleBinding
import com.google.android.material.floatingtoolbar.FloatingToolbarLayout
import com.google.android.material.tabs.TabLayout

class GradleFragment : Fragment(), GradleSectionFragmentHost, AndroidSectionFragmentHost {
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