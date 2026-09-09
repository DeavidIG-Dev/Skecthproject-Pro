package com.deavidig.mod.deanielig.badge.widget

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.annotation.Px
import com.deavidig.mod.deanielig.badge.widget.ComponentBadge.Companion.DEFAULT_BACKGROUND_COLOR
import com.deavidig.mod.deanielig.badge.widget.ComponentBadge.Companion.NO_MAX_LENGTH
import java.lang.ref.WeakReference

/**
 * M3-style, XML-free badge that attaches itself to any [View] and floats a
 * small rounded pill over one of its corners — the classic "unread /ünread
 * count / status" indicator.
 *
 * Pure Kotlin: there is no companion `attrs_component_badge.xml` and no
 * `<ComponentBadge>` tag to place in a layout. Every knob is exposed as a
 * chainable setter, so the whole thing reads as a small DSL:
 *
 * ```kotlin
 * ComponentBadge(requireContext())
 *     .setText("Experimental")
 *     .show(layout_binding.languageOptionScala)
 * ```
 *
 * ### Why a View instead of a PopupWindow
 * The badge is rendered by an internal [BadgeDrawView] added directly into
 * the anchor's window (its `decorView`, when available), not into a
 * separate `PopupWindow`. This keeps the badge free of window-focus / IME
 * side effects, lets it participate in normal `elevation` and property
 * animation, and — most importantly — keeps it positioned correctly even
 * when the anchor lives inside a scrolling container such as
 * `RecyclerView` or `ListView`, matching the approach already used for
 * `ComponentSearchDockedPanel`.
 *
 * ### Lifecycle
 * A single [ComponentBadge] instance is meant to track a single anchor at a
 * time. Calling [show] again with a different anchor moves the same pill.
 * [hide] keeps the pill attached but invisible (cheap to bring back),
 * while [remove] fully detaches it and tears down all listeners — call it
 * from `onDestroyView`/`onDetachedFromWindow` if the anchor's lifecycle can
 * outlive the badge's usefulness.
 *
 * @param context Any context; the Activity window is resolved lazily from
 * it (or from the anchor's `rootView` as a fallback) when [show] is called.
 */
class ComponentBadge(private val context: Context) {

	companion object {
		/** Default pill background: Material red, chosen to read as an "alert"/"new" marker. */
		@ColorInt
		val DEFAULT_BACKGROUND_COLOR: Int = Color.parseColor("#D32F2F")

		/** Default suffix appended when the label is truncated. */
		const val DEFAULT_TRUNCATION_SUFFIX: String = "+"

		/** No truncation applied unless [setMaxLength] is called explicitly. */
		const val NO_MAX_LENGTH: Int = Int.MAX_VALUE
	}

	// ---- Configuration ----------------------------------------------------

	@ColorInt
	private var backgroundColor: Int = DEFAULT_BACKGROUND_COLOR

	@ColorInt
	private var textColor: Int = Color.WHITE

	private var textSizeSp: Float = 10f
	private var minSizeDp: Float = 16f

	private var rawText: CharSequence = ""
	private var maxLength: Int = NO_MAX_LENGTH
	private var truncationSuffix: String = DEFAULT_TRUNCATION_SUFFIX

	/** Corner of the anchor the pill's center is pinned to. Defaults to top-end. */
	private var gravity: Int = Gravity.TOP or Gravity.END

	@Px
	private var offsetX: Float = 0f

	@Px
	private var offsetY: Float = 0f

	@Px
	private var elevationPx: Float = 6f * context.resources.displayMetrics.density

	// ---- Runtime state ------------------------------------------------------

	private var badgeView: BadgeDrawView? = null
	private var anchorRef: WeakReference<View>? = null
	private var overlayRoot: ViewGroup? = null

	private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
	private var scrollListener: ViewTreeObserver.OnScrollChangedListener? = null
	private var attachStateListener: View.OnAttachStateChangeListener? = null

	// ---- Public fluent API ----------------------------------------------

	/** Sets the pill's background color. Red ([DEFAULT_BACKGROUND_COLOR]) by default. */
	fun setBackgroundColor(@ColorInt color: Int): ComponentBadge = apply {
		backgroundColor = color
		badgeView?.setBadgeColor(color)
	}

	/** Sets the label text. Subject to truncation per [setMaxLength]/[setTruncationSuffix]. */
	fun setText(text: CharSequence): ComponentBadge = apply {
		rawText = text
		applyLabel()
	}

	/** Clears the label, collapsing the badge into a small dot. */
	fun clearText(): ComponentBadge = apply {
		rawText = ""
		applyLabel()
	}

	/**
	 * Caps the label at [length] characters before appending
	 * [setTruncationSuffix]. Unset (i.e. [NO_MAX_LENGTH]) by default, so
	 * `setText("Experimental")` renders in full unless you opt in here —
	 * e.g. `setMaxLength(2)` turns `"127"` into `"12+"`.
	 */
	fun setMaxLength(length: Int): ComponentBadge = apply {
		maxLength = length
		applyLabel()
	}

	/** Removes any length cap, restoring the full [setText] value. */
	fun clearMaxLength(): ComponentBadge = apply {
		maxLength = NO_MAX_LENGTH
		applyLabel()
	}

	/** Suffix appended when the label exceeds [setMaxLength]. `"+"` by default, customizable. */
	fun setTruncationSuffix(suffix: String): ComponentBadge = apply {
		truncationSuffix = suffix
		applyLabel()
	}

	fun setTextColor(@ColorInt color: Int): ComponentBadge = apply {
		textColor = color
		badgeView?.setTextColor(color)
	}

	fun setTextSize(sp: Float): ComponentBadge = apply {
		textSizeSp = sp
		badgeView?.setTextSizeSp(sp)
		repositionIfPossible()
	}

	/** Minimum pill diameter (dot size / pill height floor), in dp. 16dp by default. */
	fun setMinSize(dp: Float): ComponentBadge = apply {
		minSizeDp = dp
		badgeView?.setMinSizeDp(dp)
		repositionIfPossible()
	}

	/**
	 * Which corner of the anchor the pill centers itself on. Use
	 * [Gravity.TOP]/[Gravity.BOTTOM] combined with [Gravity.START]/[Gravity.END].
	 * Defaults to `TOP or END`.
	 */
	fun setAnchorGravity(gravity: Int): ComponentBadge = apply {
		this.gravity = gravity
		repositionIfPossible()
	}

	/** Fine-tunes the pill's resting position relative to the anchored corner, in px. */
	fun setOffset(@Px x: Float, @Px y: Float): ComponentBadge = apply {
		offsetX = x
		offsetY = y
		repositionIfPossible()
	}

	/** Elevation applied to the pill so it visually floats above the anchor. */
	fun setElevation(@Px elevation: Float): ComponentBadge = apply {
		elevationPx = elevation
		badgeView?.elevation = elevation
	}

	/**
	 * Attaches (or moves) the badge pill so it floats over [anchor]'s
	 * configured corner, and starts tracking [anchor]'s position across
	 * layout passes, scrolls, and re-parenting.
	 */
	fun show(anchor: View): ComponentBadge = apply {
		detachWatchers()

		anchorRef = WeakReference(anchor)
		val root = resolveOverlayRoot(anchor)
		overlayRoot = root

		val view = badgeView ?: BadgeDrawView(context).also { badgeView = it }
		view.setBadgeColor(backgroundColor)
		view.setTextColor(textColor)
		view.setTextSizeSp(textSizeSp)
		view.setMinSizeDp(minSizeDp)
		view.elevation = elevationPx
		view.setLabel(resolveDisplayText())
		view.visibility = View.VISIBLE

		when {
			view.parent == null ->
				root.addView(
					view,
					FrameLayout.LayoutParams(
						ViewGroup.LayoutParams.WRAP_CONTENT,
						ViewGroup.LayoutParams.WRAP_CONTENT
					)
				)

			view.parent !== root -> {
				(view.parent as? ViewGroup)?.removeView(view)
				root.addView(
					view,
					FrameLayout.LayoutParams(
						ViewGroup.LayoutParams.WRAP_CONTENT,
						ViewGroup.LayoutParams.WRAP_CONTENT
					)
				)
			}
		}

		attachWatchers(anchor, root)
		repositionBadge(anchor, root)
	}

	/** Hides the pill without tearing down its listeners; cheap to [show] again. */
	fun hide() {
		badgeView?.visibility = View.GONE
	}

	/** Fully detaches the pill from the window and stops tracking the anchor. */
	fun remove() {
		detachWatchers()
		val view = badgeView ?: return
		(view.parent as? ViewGroup)?.removeView(view)
		badgeView = null
		overlayRoot = null
		anchorRef = null
	}

	/** Whether the pill is currently attached to the window and visible. */
	fun isShowing(): Boolean {
		val view = badgeView ?: return false
		return view.isAttachedToWindow && view.visibility == View.VISIBLE
	}

	// ---- Internals ---------------------------------------------------------

	private fun applyLabel() {
		badgeView?.setLabel(resolveDisplayText())
		repositionIfPossible()
	}

	private fun resolveDisplayText(): CharSequence {
		val text = rawText
		return if (text.length > maxLength) text.take(maxLength)
			.toString() + truncationSuffix else text
	}

	private fun repositionIfPossible() {
		val anchor = anchorRef?.get() ?: return
		val root = overlayRoot ?: return
		repositionBadge(anchor, root)
	}

	private fun resolveOverlayRoot(anchor: View): ViewGroup {
		val decorView = context.findActivity()?.window?.decorView as? ViewGroup
		return decorView ?: anchor.rootView as? ViewGroup
		?: throw IllegalStateException("ComponentBadge: anchor has no ViewGroup root to attach to.")
	}

	private fun attachWatchers(anchor: View, root: ViewGroup) {
		val observer = anchor.viewTreeObserver

		val onLayout = ViewTreeObserver.OnGlobalLayoutListener { repositionBadge(anchor, root) }
		observer.addOnGlobalLayoutListener(onLayout)
		globalLayoutListener = onLayout

		val onScroll = ViewTreeObserver.OnScrollChangedListener { repositionBadge(anchor, root) }
		observer.addOnScrollChangedListener(onScroll)
		scrollListener = onScroll

		val onAttachState = object : View.OnAttachStateChangeListener {
			override fun onViewAttachedToWindow(v: View) = repositionBadge(anchor, root)
			override fun onViewDetachedFromWindow(v: View) = remove()
		}
		anchor.addOnAttachStateChangeListener(onAttachState)
		attachStateListener = onAttachState
	}

	private fun detachWatchers() {
		val anchor = anchorRef?.get()
		if (anchor != null) {
			globalLayoutListener?.let {
				anchor.viewTreeObserver.takeIf { vto -> vto.isAlive }
					?.removeOnGlobalLayoutListener(it)
			}
			scrollListener?.let {
				anchor.viewTreeObserver.takeIf { vto -> vto.isAlive }
					?.removeOnScrollChangedListener(it)
			}
			attachStateListener?.let { anchor.removeOnAttachStateChangeListener(it) }
		}
		globalLayoutListener = null
		scrollListener = null
		attachStateListener = null
	}

	private fun repositionBadge(anchor: View, root: ViewGroup) {
		val badge = badgeView ?: return
		if (!anchor.isAttachedToWindow) return
		if (anchor.width == 0 && anchor.height == 0) return

		if (badge.measuredWidth == 0) {
			badge.measure(
				View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
				View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
			)
		}
		val badgeWidth = badge.measuredWidth
		val badgeHeight = badge.measuredHeight

		val anchorLoc = IntArray(2)
		val rootLoc = IntArray(2)
		anchor.getLocationInWindow(anchorLoc)
		root.getLocationInWindow(rootLoc)

		val anchorLeftInRoot = (anchorLoc[0] - rootLoc[0]).toFloat()
		val anchorTopInRoot = (anchorLoc[1] - rootLoc[1]).toFloat()

		val isEnd =
			gravity and Gravity.END == Gravity.END || gravity and Gravity.RIGHT == Gravity.RIGHT
		val isBottom = gravity and Gravity.BOTTOM == Gravity.BOTTOM

		val cornerX = anchorLeftInRoot + if (isEnd) anchor.width.toFloat() else 0f
		val cornerY = anchorTopInRoot + if (isBottom) anchor.height.toFloat() else 0f

		val lp = badge.layoutParams as? FrameLayout.LayoutParams
			?: FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT,
				ViewGroup.LayoutParams.WRAP_CONTENT
			)
		lp.leftMargin = (cornerX - badgeWidth / 2f + offsetX).toInt()
		lp.topMargin = (cornerY - badgeHeight / 2f + offsetY).toInt()
		badge.layoutParams = lp
	}

	private fun Context.findActivity(): Activity? {
		var ctx = this
		while (ctx is ContextWrapper) {
			if (ctx is Activity) return ctx
			ctx = ctx.baseContext
		}
		return null
	}
}

/**
 * Internal M3-flavoured surface responsible **only** for measuring and
 * drawing the badge pill (background + label).
 *
 * [ComponentBadge] owns the lifecycle — attaching, positioning and removing
 * this view relative to an anchor — while [BadgeDrawView] knows nothing
 * about anchors, windows, or overlays. It just renders a rounded pill sized
 * to its label, or a plain dot when the label is empty.
 *
 * Kept as a plain [View] (not a `Drawable` or `PopupWindow`) so it can
 * participate in normal elevation, invalidation, and property-animation
 * APIs while staying light enough to live inside a decor-view overlay.
 * This mirrors the "plain elevated View" approach already used for
 * `ComponentSearchDockedPanel`, favoring compatibility with scrolling
 * containers (`RecyclerView`, `ListView`) over the window semantics of a
 * `PopupWindow`.
 */
internal class BadgeDrawView(context: Context) : View(context) {

	private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		style = Paint.Style.FILL
	}

	private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.WHITE
		textAlign = Paint.Align.CENTER
	}

	private val bgRect = RectF()

	/** Diameter used when there is no label (dot mode), and the floor for the pill height. */
	private var minSizePx: Float = dp(16f)

	/** Horizontal breathing room added on each side of the label inside the pill. */
	private var horizontalPaddingPx: Float = dp(5f)

	/** Current label already resolved by [ComponentBadge] (truncation applied upstream). */
	var label: CharSequence = ""
		private set

	init {
		textPaint.textSize = sp(10f)
	}

	/** Sets the label to render. Triggers a re-measure since pill width depends on text width. */
	fun setLabel(text: CharSequence) {
		if (label == text) return
		label = text
		requestLayout()
		invalidate()
	}

	/** Sets the pill's fill color. This is the hook that makes the background customizable. */
	fun setBadgeColor(@ColorInt color: Int) {
		if (backgroundPaint.color == color) return
		backgroundPaint.color = color
		invalidate()
	}

	fun setTextColor(@ColorInt color: Int) {
		if (textPaint.color == color) return
		textPaint.color = color
		invalidate()
	}

	fun setTextSizeSp(sizeSp: Float) {
		textPaint.textSize = sp(sizeSp)
		requestLayout()
		invalidate()
	}

	fun setMinSizeDp(sizeDp: Float) {
		minSizePx = dp(sizeDp)
		requestLayout()
	}

	override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
		super.onSizeChanged(w, h, oldw, oldh)

		(parent as? ViewGroup)?.clipChildren = false
		(parent as? ViewGroup)?.clipToPadding = false

		post {
			val location = IntArray(2)
			getLocationOnScreen(location)
			val screenWidth = resources.displayMetrics.widthPixels

			val originalRight = location[0] + w - translationX
			val overflow = originalRight - screenWidth

			translationX = if (overflow > 0) -overflow else 0f
		}
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val textWidth = if (label.isEmpty()) 0f else textPaint.measureText(label.toString())
		val contentWidth = if (label.isEmpty()) minSizePx else textWidth + horizontalPaddingPx * 2
		val width = maxOf(minSizePx, contentWidth)
		val height = minSizePx
		setMeasuredDimension(width.toInt(), height.toInt())
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)

		val location = IntArray(2)
		getLocationOnScreen(location)
		val viewLeftOnScreen = location[0]
		val screenWidth = resources.displayMetrics.widthPixels

		val overflowX = (viewLeftOnScreen + width) - screenWidth
		val shiftX = if (overflowX > 0) overflowX.toFloat() else 0f

		canvas.save()
		canvas.translate(-shiftX, 0f)

		bgRect.set(0f, 0f, width.toFloat(), height.toFloat())
		val radius = height / 2f
		canvas.drawRoundRect(bgRect, radius, radius, backgroundPaint)

		if (label.isNotEmpty()) {
			val fm = textPaint.fontMetrics
			val textY = height / 2f - (fm.ascent + fm.descent) / 2f
			canvas.drawText(label.toString(), width / 2f, textY, textPaint)
		}

		canvas.restore()
	}

	@Px
	private fun dp(value: Float): Float =
		TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

	@Px
	private fun sp(value: Float): Float =
		TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
}