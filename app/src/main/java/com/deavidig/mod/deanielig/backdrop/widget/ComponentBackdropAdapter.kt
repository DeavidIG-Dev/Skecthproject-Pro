package com.deavidig.mod.deanielig.backdrop.widget

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.deavidig.mod.deanielig.backdrop.widget.ComponentBackdropAdapter.Companion.BACK_LAYER
import com.deavidig.mod.deanielig.backdrop.widget.ComponentBackdropAdapter.Companion.ComponentLayoutBackdropAdapter.ViewFragment.Companion.newInstance
import com.deavidig.mod.deanielig.backdrop.widget.ComponentBackdropAdapter.Companion.FRONT_LAYER

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

		private const val BACK_LAYER_TAG =
			"com.deavidig.mod.deaniel.backdrop.widget.ComponentBackdropLayout.BACKDROP_BACK_LAYER"
		private const val FRONT_LAYER_TAG =
			"com.deavidig.mod.deaniel.backdrop.widget.ComponentBackdropLayout.BACKDROP_FRONT_LAYER"

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
			final override fun createFragment(position: Int): Fragment =
				if (position == FRONT_LAYER) createFrontFragment() else createBackFragment()

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
			final override fun createFragment(position: Int): Fragment =
				ViewFragment.newInstance(
					position,
					if (position == FRONT_LAYER) createFrontFragment() else createBackFragment()
				)

			/**
			 * Hosts a plain [View] (rather than a nested [Fragment]) as a
			 * backdrop layer.
			 *
			 * The [View] handed to [newInstance] is only usable for the
			 * *first* creation: a [View] can't be put in a [Bundle], so it is
			 * never part of saved instance state. Whenever [FragmentManager]
			 * itself recreates this fragment — on a configuration change or a
			 * process restart, via its own no-arg-constructor reflection,
			 * bypassing [createFragment] entirely — [providedView] is `null`
			 * and [onCreateView] instead re-derives the view by asking the
			 * currently attached [ComponentLayoutBackdropAdapter] to build it
			 * again from [position]. This requires the host to have already
			 * reassigned [ComponentBackdropLayout.adapter] by the time
			 * [FragmentManager] restores this fragment's view (i.e. set it in
			 * `onCreate()`, as shown in [ComponentBackdropAdapter]'s class
			 * doc) — otherwise [onCreateView] fails fast with an
			 * [IllegalStateException] instead of silently showing nothing.
			 */
			public class ViewFragment : Fragment() {

				private var providedView: View? = null

				override fun onCreateView(
					inflater: LayoutInflater,
					container: ViewGroup?,
					savedInstanceState: Bundle?
				): View {
					providedView?.let { return it }

					val position = requireArguments().getInt(ARG_POSITION)
					val backdrop = container?.parent as? ComponentBackdropLayout
					val adapter = backdrop?.adapter as? ComponentLayoutBackdropAdapter
					checkNotNull(adapter) {
						"ViewFragment was recreated by FragmentManager (e.g. after a " +
								"configuration change) but found no ComponentLayoutBackdropAdapter " +
								"attached to its ComponentBackdropLayout yet. Re-set " +
								"ComponentBackdropLayout.adapter before FragmentManager restores " +
								"fragment state, e.g. in onCreate()."
					}
					return if (position == FRONT_LAYER) adapter.createFrontFragment() else adapter.createBackFragment()
				}

				internal companion object {
					private const val ARG_POSITION =
						"com.deavidig.mod.deaniel.backdrop.widget.ComponentBackdropLayout.ARG_VIEW_FRAGMENT_POSITION"

					internal fun newInstance(position: Int, view: View): ViewFragment =
						ViewFragment().apply {
							providedView = view
							arguments = Bundle().apply { putInt(ARG_POSITION, position) }
						}
				}
			}

			abstract fun createBackFragment(): View

			abstract fun createFrontFragment(): View

		}
	}
}