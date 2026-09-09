package com.deavidig.mod.deanielig.backdrop.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.customview.view.AbsSavedState
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.deavidig.sketchprojectpro.R
import com.google.android.material.color.MaterialColors
import kotlin.math.roundToInt
import androidx.core.graphics.drawable.toDrawable

/**
 * Material "backdrop" container hosting exactly two layers — a back layer,
 * normally hidden, and a front layer that covers it — and animating between
 * [BackDropState.CONCEALED] (front layer fully covering the back layer) and
 * [BackDropState.REVEALED] (front layer slid down, uncovering the back
 * layer above it while leaving a [getPeekHeight] strip of the front layer
 * visible, typically a header).
 *
 * By default the back layer is filled with the theme's `colorSurface`
 * and the front layer with a slightly darker shade of it (see
 * [getFrontLayerDarkenAmount]), so the two layers read as distinct
 * surfaces without any extra setup. Either can be overridden with a solid
 * color or a drawable through `app:backLayoutBackground` /
 * `app:frontLayoutBackground`, or programmatically via
 * [setBackLayerBackground] / [setFrontLayerBackground].
 *
 * Each layer's content is supplied as a
 * [Fragment][androidx.fragment.app.Fragment] through [adapter], mirroring
 * the `ViewPager2` / `FragmentStateAdapter` pattern already used elsewhere
 * in this library, but without pulling in `RecyclerView`: a backdrop only
 * ever has two fixed, non-recyclable slots, so [ComponentBackdropAdapter]
 * talks directly to a [FragmentManager][androidx.fragment.app.FragmentManager]
 * instead. The two internal containers use fixed resource ids (declared in
 * `ids_component_backdrop.xml`) rather than runtime-generated ones, so
 * fragments attached to them are correctly restored by
 * [FragmentManager][androidx.fragment.app.FragmentManager] across
 * configuration changes without any extra bookkeeping.
 *
 * XML usage:
 * ```xml
 * <com.deavidig.sketchprojectpro.ComponentBackdropLayout
 *     android:id="@+id/backDrop"
 *     android:layout_width="match_parent"
 *     android:layout_height="match_parent"
 *     app:peekHeight="56dp"
 *     app:frontLayerCornerRadius="16dp"
 *     app:frontLayerElevation="8dp" />
 * ```
 *
 * Kotlin usage:
 * ```kotlin
 * backDrop.adapter = object : ComponentBackdropAdapter(this) {
 *     override fun createFragment(position: Int): Fragment =
 *         if (position == ComponentBackdropAdapter.FRONT_LAYER) ContentFragment() else MenuFragment()
 * }
 * backDrop.toggle()
 * ```
 *
 * @constructor Builds the backdrop's two internal layer containers and
 * reads its styleable attributes.
 * @param context View context.
 * @param attrs XML attribute set; may declare `peekHeight`,
 * `frontLayerCornerRadius`, `frontLayerElevation`,
 * `backDropAnimationDuration`, `initialState`, `backLayoutBackground`,
 * `frontLayoutBackground` and `frontLayerDarkenAmount`.
 * @param defStyleAttr Default style attribute, forwarded to [ConstraintLayout].
 * @author DeanielIG, DeavidIG
 */
public class ComponentBackdropLayout @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : ConstraintLayout(context, attrs, defStyleAttr) {

	/** Whether the front layer fully covers the back layer, or is slid down to reveal it. */
	public enum class BackDropState { CONCEALED, REVEALED }

	/** Notified whenever the backdrop finishes transitioning between states. */
	public fun interface OnBackDropStateChangedListener {
		public fun onBackDropStateChanged(state: BackDropState)
	}

	private val backLayerContainer: FrameLayout
	private val frontLayerContainer: FrameLayout

	/** Container id backing the back layer; stable across configuration changes. */
	internal val backLayerContainerId: Int
		get() = backLayerContainer.id

	/** Container id backing the front layer; stable across configuration changes. */
	internal val frontLayerContainerId: Int
		get() = frontLayerContainer.id

	private var peekHeightPx: Int
	private var cornerRadiusPx: Float
	private var animationDurationMs: Long
	private var currentState: BackDropState
	private var frontLayerDarkenAmount: Float

	// Tracks whether the front layer's background is still the auto-derived
	// "darkened colorSurface" shade, so changing the darken amount doesn't
	// clobber a background the caller explicitly set afterwards.
	private var usesAutoFrontLayerBackground: Boolean = true

	private var revealAnimator: ValueAnimator? = null
	private val stateChangedListeners = mutableListOf<OnBackDropStateChangedListener>()

	/**
	 * Supplies the [Fragment][androidx.fragment.app.Fragment] instances for
	 * the back and front layers. Setting this attaches both fragments to
	 * their containers immediately; it only needs to be set once, since a
	 * backdrop's two layers are never recycled or reordered.
	 *
	 * As a Kotlin property this already compiles down to standard
	 * `getAdapter()` / `setAdapter(ComponentBackdropAdapter?)` accessors, so
	 * both `backDrop.adapter = ...` from Kotlin and `backDrop.setAdapter(...)`
	 * from Java work against the same underlying methods.
	 */
	public var adapter: ComponentBackdropAdapter? = null
		set(value) {
			field = value
			value?.attachTo(this)
		}

	init {
		val typedArray = context.obtainStyledAttributes(attrs, R.styleable.ComponentBackdropLayout)
		val density = resources.displayMetrics.density
		peekHeightPx = typedArray.getDimensionPixelSize(
			R.styleable.ComponentBackdropLayout_frontLayoutPeekHeight,
			(DEFAULT_PEEK_HEIGHT_DP * density).roundToInt()
		)
		cornerRadiusPx = typedArray.getDimension(
			R.styleable.ComponentBackdropLayout_frontLayerCornerRadius,
			DEFAULT_CORNER_RADIUS_DP * density
		)
		val frontLayerElevationPx = typedArray.getDimension(
			R.styleable.ComponentBackdropLayout_frontLayerElevation,
			DEFAULT_ELEVATION_DP * density
		)
		animationDurationMs = typedArray.getInteger(
			R.styleable.ComponentBackdropLayout_backDropAnimationDuration,
			DEFAULT_ANIMATION_DURATION_MS
		).toLong()
		currentState = if (typedArray.getInt(R.styleable.ComponentBackdropLayout_initialState, 0) == 1) {
			BackDropState.REVEALED
		} else {
			BackDropState.CONCEALED
		}
		frontLayerDarkenAmount = typedArray.getFloat(
			R.styleable.ComponentBackdropLayout_frontLayerDarkenAmount,
			DEFAULT_FRONT_LAYER_DARKEN_AMOUNT
		).coerceIn(0f, 1f)
		val explicitBackBackground = typedArray.getDrawable(R.styleable.ComponentBackdropLayout_backLayoutBackground)
		val explicitFrontBackground = typedArray.getDrawable(R.styleable.ComponentBackdropLayout_frontLayoutBackground)
		typedArray.recycle()

		backLayerContainer = FrameLayout(context).apply {
			id = R.id.component_backdrop_back_layer_container
			background = explicitBackBackground ?: themeSurfaceColor().toDrawable()
		}
		frontLayerContainer = FrameLayout(context).apply {
			id = R.id.component_backdrop_front_layer_container
			elevation = frontLayerElevationPx
			clipToOutline = true
			outlineProvider = object : ViewOutlineProvider() {
				override fun getOutline(view: View, outline: Outline) {
					outline.setRoundRect(0, 0, view.width, view.height, cornerRadiusPx)
				}
			}
			if (explicitFrontBackground != null) {
				usesAutoFrontLayerBackground = false
				background = explicitFrontBackground
			} else {
				background = darken(themeSurfaceColor(), frontLayerDarkenAmount).toDrawable()
			}
		}

		addView(backLayerContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
		addView(frontLayerContainer, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
	}

	/** Resolves the theme's `?attr/colorSurface`, falling back to a neutral gray if unavailable. */
	private fun themeSurfaceColor(): Int =
		MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurface, DEFAULT_SURFACE_COLOR)

	/** Returns [color] shifted towards black by [amount] (0f = unchanged, 1f = black), preserving alpha. */
	@ColorInt
	private fun darken(@ColorInt color: Int, amount: Float): Int {
		val hsv = FloatArray(3)
		Color.colorToHSV(color, hsv)
		hsv[2] = (hsv[2] * (1f - amount)).coerceIn(0f, 1f)
		return Color.HSVToColor(Color.alpha(color), hsv)
	}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		super.onSizeChanged(w, h, oldw, oldh)
		// Snap to the correct translation for the current state whenever the
		// backdrop's height changes, including the very first layout pass.
		frontLayerContainer.translationY = translationYFor(currentState, h)
	}

	private fun translationYFor(state: BackDropState, heightPx: Int = height): Float =
		if (state == BackDropState.REVEALED) (heightPx - peekHeightPx).toFloat().coerceAtLeast(0f) else 0f

	/** Slides the front layer down, uncovering the back layer; animates unless [animate] is `false`. */
	public fun reveal(animate: Boolean = true) {
		setState(BackDropState.REVEALED, animate)
	}

	/** Slides the front layer back up, covering the back layer; animates unless [animate] is `false`. */
	public fun conceal(animate: Boolean = true) {
		setState(BackDropState.CONCEALED, animate)
	}

	/** Switches between [BackDropState.CONCEALED] and [BackDropState.REVEALED]. */
	public fun toggle(animate: Boolean = true) {
		val target = if (currentState == BackDropState.CONCEALED) BackDropState.REVEALED else BackDropState.CONCEALED
		setState(target, animate)
	}

	/** Returns the backdrop's current [BackDropState]. */
	public fun getState(): BackDropState = currentState

	/** Returns `true` if the back layer is currently uncovered. */
	public fun isRevealed(): Boolean = currentState == BackDropState.REVEALED

	private fun setState(target: BackDropState, animate: Boolean) {
		if (currentState == target && revealAnimator == null) return
		val previousAnimator = revealAnimator
		currentState = target
		previousAnimator?.cancel()

		val targetTranslationY = translationYFor(target)
		if (!animate || height == 0) {
			revealAnimator = null
			frontLayerContainer.translationY = targetTranslationY
			notifyStateChanged(target)
			return
		}

		val animator = ValueAnimator.ofFloat(frontLayerContainer.translationY, targetTranslationY).apply {
			duration = animationDurationMs
			interpolator = FastOutSlowInInterpolator()
			addUpdateListener { frontLayerContainer.translationY = it.animatedValue as Float }
		}
		revealAnimator = animator
		animator.addListener(object : AnimatorListenerAdapter() {
			override fun onAnimationEnd(animation: Animator) {
				if (revealAnimator === animator) {
					revealAnimator = null
				}
				// Only notify if this animator was not superseded by a newer
				// call to setState() while it was cancelling.
				if (currentState == target) {
					notifyStateChanged(target)
				}
			}
		})
		animator.start()
	}

	private fun notifyStateChanged(state: BackDropState) {
		stateChangedListeners.forEach { it.onBackDropStateChanged(state) }
		// Exposed for TalkBack: announces the backdrop's current state when
		// focused, since its own content is just two opaque fragment containers.
		contentDescription = if (state == BackDropState.REVEALED) "Backdrop revealed" else "Backdrop concealed"
	}

	/** Registers a listener notified whenever the backdrop finishes a state transition. */
	public fun addOnBackDropStateChangedListener(listener: OnBackDropStateChangedListener) {
		stateChangedListeners.add(listener)
	}

	/** Removes a previously registered [OnBackDropStateChangedListener]. */
	public fun removeOnBackDropStateChangedListener(listener: OnBackDropStateChangedListener) {
		stateChangedListeners.remove(listener)
	}

	/** Returns the visible height, in pixels, the front layer keeps when [BackDropState.REVEALED]. */
	public fun getFrontLayoutPeekHeight(): Int = peekHeightPx

	/** Sets the visible height, in pixels, the front layer keeps when [BackDropState.REVEALED]. */
	public fun setFrontLayoutPeekHeight(px: Int) {
		peekHeightPx = px
		if (currentState == BackDropState.REVEALED) {
			frontLayerContainer.translationY = translationYFor(BackDropState.REVEALED)
		}
	}

	/** Returns the front layer's corner radius, in pixels. */
	public fun getFrontLayerCornerRadius(): Float = cornerRadiusPx

	/** Sets the front layer's corner radius, in pixels, and refreshes its outline. */
	public fun setFrontLayerCornerRadius(px: Float) {
		cornerRadiusPx = px
		frontLayerContainer.invalidate()
	}

	/** Returns the duration, in milliseconds, of the reveal/conceal animation. */
	public fun getAnimationDuration(): Long = animationDurationMs

	/** Sets the duration, in milliseconds, of the reveal/conceal animation. */
	public fun setAnimationDuration(durationMs: Long) {
		animationDurationMs = durationMs
	}

	/** Returns the back layer's current background. */
	public fun getBackLayerBackground(): Drawable? = backLayerContainer.background

	/** Sets the back layer's background to [drawable], e.g. `app:backLayoutBackground`'s programmatic equivalent. */
	public fun setBackLayerBackground(drawable: Drawable?) {
		backLayerContainer.background = drawable
	}

	/** Sets the back layer's background to a solid [color]. */
	public fun setBackLayerBackgroundColor(@ColorInt color: Int) {
		backLayerContainer.background = ColorDrawable(color)
	}

	/** Returns the front layer's current background. */
	public fun getFrontLayerBackground(): Drawable? = frontLayerContainer.background

	/**
	 * Sets the front layer's background to [drawable], e.g.
	 * `app:frontLayoutBackground`'s programmatic equivalent. This overrides
	 * the automatically darkened `colorSurface` default; future calls to
	 * [setFrontLayerDarkenAmount] will no longer touch the background until
	 * a `null` value or a color is set again.
	 */
	public fun setFrontLayerBackground(drawable: Drawable?) {
		usesAutoFrontLayerBackground = false
		frontLayerContainer.background = drawable
	}

	/**
	 * Sets the front layer's background to a solid [color], also overriding
	 * the auto-darkened default (see [setFrontLayerBackground]).
	 */
	public fun setFrontLayerBackgroundColor(@ColorInt color: Int) {
		usesAutoFrontLayerBackground = false
		frontLayerContainer.background = ColorDrawable(color)
	}

	/**
	 * Restores the front layer's background to the theme-derived default: a
	 * shade of `colorSurface` darkened by [getFrontLayerDarkenAmount].
	 */
	public fun resetFrontLayerBackgroundToTheme() {
		usesAutoFrontLayerBackground = true
		frontLayerContainer.background = ColorDrawable(darken(themeSurfaceColor(), frontLayerDarkenAmount))
	}

	/** Returns how much darker the front layer's default background is than the back layer's, from 0f to 1f. */
	public fun getFrontLayerDarkenAmount(): Float = frontLayerDarkenAmount

	/**
	 * Sets how much darker the front layer's default background is than
	 * `colorSurface`, from `0f` (no change) to `1f` (black). Only takes
	 * visible effect while the front layer is still using its auto-derived
	 * background — i.e. [setFrontLayerBackground] / [setFrontLayerBackgroundColor]
	 * haven't been called since the last theme reset.
	 */
	public fun setFrontLayerDarkenAmount(amount: Float) {
		frontLayerDarkenAmount = amount.coerceIn(0f, 1f)
		if (usesAutoFrontLayerBackground) {
			frontLayerContainer.background = ColorDrawable(darken(themeSurfaceColor(), frontLayerDarkenAmount))
		}
	}

	public fun setFrontLayerElevation(elevation: Float) {
		frontLayerContainer.elevation = elevation
	}

	override fun onSaveInstanceState(): Parcelable {
		val superState = super.onSaveInstanceState()
		return SavedState(superState).also { it.state = currentState }
	}

	override fun onRestoreInstanceState(state: Parcelable?) {
		if (state !is SavedState) {
			super.onRestoreInstanceState(state)
			return
		}
		super.onRestoreInstanceState(state.superState)
		currentState = state.state
		frontLayerContainer.translationY = translationYFor(currentState)
	}

	/** Persists [currentState] across configuration changes. */
	private class SavedState : AbsSavedState {
		var state: BackDropState = BackDropState.CONCEALED

		constructor(superState: Parcelable?) : super(superState ?: EMPTY_STATE)
		constructor(source: Parcel) : super(source) {
			state = BackDropState.values()[source.readInt()]
		}

		override fun writeToParcel(out: Parcel, flags: Int) {
			super.writeToParcel(out, flags)
			out.writeInt(state.ordinal)
		}

		companion object CREATOR : Parcelable.Creator<SavedState> {
			override fun createFromParcel(source: Parcel): SavedState = SavedState(source)
			override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
		}
	}

	private companion object {
		const val DEFAULT_PEEK_HEIGHT_DP = 56f
		const val DEFAULT_CORNER_RADIUS_DP = 16f
		const val DEFAULT_ELEVATION_DP = 8f
		const val DEFAULT_ANIMATION_DURATION_MS = 300

		// ~12% darker than colorSurface, close to the 0xFF303030-vs-lighter-gray
		// contrast between the two layers.
		const val DEFAULT_FRONT_LAYER_DARKEN_AMOUNT = 0.125f

		// Fallback used only if the host theme doesn't resolve colorSurface at all.
		// colorSurface is normally light in a light theme, so the fallback follows suit.
		const val DEFAULT_SURFACE_COLOR = 0xFFFFFBFE.toInt()
	}
}