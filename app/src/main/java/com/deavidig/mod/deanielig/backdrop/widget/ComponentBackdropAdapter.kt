package com.deavidig.mod.deanielig.backdrop.widget

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager

/**
 * Supplies the two [Fragment] instances hosted by a [ComponentBackDropLayout]:
 * the back layer, at [BACK_LAYER], and the front layer, at [FRONT_LAYER].
 *
 * A backdrop only ever hosts exactly two fixed slots that are never
 * recycled, reordered, or paged through by the user, so this adapter talks
 * directly to a [FragmentManager] instead of going through `RecyclerView` /
 * `ViewPager2`'s `FragmentStateAdapter`. This avoids pulling in a
 * scrolling-and-recycling machinery that a fixed two-slot layout never
 * exercises, while still giving each layer proper [Fragment] lifecycle
 * ownership — state saving and restoration across configuration changes is
 * handled by [FragmentManager] itself, exactly as it would for any other
 * statically-added fragment, because [ComponentBackDropLayout]'s two
 * containers use fixed resource ids rather than runtime-generated ones.
 *
 * Usage:
 * ```kotlin
 * backDrop.adapter = object : ComponentBackDropAdapter(this) {
 *     override fun createFragment(position: Int): Fragment =
 *         if (position == FRONT_LAYER) ContentFragment() else MenuFragment()
 * }
 * ```
 *
 * @constructor Creates an adapter bound to a [FragmentManager]. Prefer the
 * public [FragmentActivity] or [Fragment] constructors below.
 * @param fragmentManager Manager used to add and look up the layer fragments.
 * @author DeanielIG, DeavidIG
 */
public abstract class ComponentBackdropAdapter private constructor(
	private val fragmentManager: FragmentManager
) {

	/**
	 * Creates an adapter backed by [activity]'s
	 * [FragmentActivity.getSupportFragmentManager].
	 *
	 * @param activity Host activity for the backdrop.
	 */
	public constructor(activity: FragmentActivity) : this(activity.supportFragmentManager)

	/**
	 * Creates an adapter backed by [fragment]'s
	 * [Fragment.getChildFragmentManager], for hosting a backdrop nested
	 * inside another fragment.
	 *
	 * @param fragment Host fragment for the backdrop.
	 */
	public constructor(fragment: Fragment) : this(fragment.childFragmentManager)

	/**
	 * Returns the [Fragment] to display at [position].
	 *
	 * @param position Either [BACK_LAYER] or [FRONT_LAYER].
	 */
	public abstract fun createFragment(position: Int): Fragment

	/**
	 * Adds the back and front layer fragments to their containers in
	 * [layout], skipping any layer that already has a fragment attached —
	 * for example after a configuration change, where [FragmentManager] has
	 * already restored it into the (stable) container id on its own.
	 */
	internal fun attachTo(layout: ComponentBackdropLayout) {
		val backFragment = fragmentManager.findFragmentByTag(BACK_LAYER_TAG)
		val frontFragment = fragmentManager.findFragmentByTag(FRONT_LAYER_TAG)

		if (backFragment == null || frontFragment == null) {
			fragmentManager.beginTransaction()
				.setReorderingAllowed(true)
				.apply {
					if (backFragment == null) {
						add(
							layout.backLayerContainerId,
							createFragment(BACK_LAYER),
							BACK_LAYER_TAG
						)
					}

					if (frontFragment == null) {
						add(
							layout.frontLayerContainerId,
							createFragment(FRONT_LAYER),
							FRONT_LAYER_TAG
						)
					}
				}
				.commitNow()
		}
	}

	public companion object {
		/** Position of the back layer fragment, normally hidden behind the front layer. */
		public const val BACK_LAYER: Int = 0

		/** Position of the front layer fragment, normally covering the back layer. */
		public const val FRONT_LAYER: Int = 1

		private const val BACK_LAYER_TAG = "com.deavidig.mod.deaniel.backdrop.widget.ComponentBackdropLayout.BACKDROP_BACK_LAYER"
		private const val FRONT_LAYER_TAG = "com.deavidig.mod.deaniel.backdrop.widget.ComponentBackdropLayout.BACKDROP_FRONT_LAYER"

		abstract class ComponentBaseBackdropAdapter private constructor(
			private val fragmentManager: FragmentManager
		) : ComponentBackdropAdapter(fragmentManager) {
			/**
			 * Creates an adapter backed by [activity]'s
			 * [FragmentActivity.getSupportFragmentManager].
			 *
			 * @param activity Host activity for the backdrop.
			 */
			public constructor(activity: FragmentActivity) : this(activity.supportFragmentManager)

			/**
			 * Creates an adapter backed by [fragment]'s
			 * [Fragment.getChildFragmentManager], for hosting a backdrop nested
			 * inside another fragment.
			 *
			 * @param fragment Host fragment for the backdrop.
			 */
			public constructor(fragment: Fragment) : this(fragment.childFragmentManager)

			/**
			 * Returns the [Fragment] to display at [position].
			 *
			 * @param position Either [BACK_LAYER] or [FRONT_LAYER].
			 */
			final override fun createFragment(position: Int): Fragment = if (position == FRONT_LAYER) createFrontFragment() else createBackFragment()

			abstract fun createBackFragment(): Fragment

			abstract fun createFrontFragment(): Fragment

		}

		abstract class ComponentLayoutBackdropAdapter private constructor(
			private val fragmentManager: FragmentManager
		) : ComponentBackdropAdapter(fragmentManager) {
			/**
			 * Creates an adapter backed by [activity]'s
			 * [FragmentActivity.getSupportFragmentManager].
			 *
			 * @param activity Host activity for the backdrop.
			 */
			public constructor(activity: FragmentActivity) : this(activity.supportFragmentManager)

			/**
			 * Creates an adapter backed by [fragment]'s
			 * [Fragment.getChildFragmentManager], for hosting a backdrop nested
			 * inside another fragment.
			 *
			 * @param fragment Host fragment for the backdrop.
			 */
			public constructor(fragment: Fragment) : this(fragment.childFragmentManager)

			/**
			 * Returns the [Fragment] to display at [position].
			 *
			 * @param position Either [BACK_LAYER] or [FRONT_LAYER].
			 */
			final override fun createFragment(position: Int): Fragment = if (position == FRONT_LAYER) ViewFragment(createFrontFragment()) else ViewFragment(createBackFragment())

			private class ViewFragment(private val view: View) : Fragment() {
				override fun onCreateView(
					inflater: LayoutInflater,
					container: ViewGroup?,
					savedInstanceState: Bundle?
				): View {
					return view
				}
			}

			abstract fun createBackFragment(): View

			abstract fun createFrontFragment(): View

		}
	}
}