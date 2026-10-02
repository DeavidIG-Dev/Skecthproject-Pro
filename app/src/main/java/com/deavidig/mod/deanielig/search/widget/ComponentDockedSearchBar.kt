package com.deavidig.mod.deanielig.search.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.MenuRes
import androidx.annotation.Px
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.deavidig.sketchprojectpro.R
import com.google.android.material.color.MaterialColors
import kotlin.math.max
import kotlin.math.min
import com.google.android.material.R as MR

/**
 * A Material Design 3 **DockedSearchBar** (the Android-View counterpart of Jetpack Compose's
 * `DockedSearchBar`) built on top of [ComponentSearchBar].
 *
 * It does NOT re-implement the search bar: it hosts a real [ComponentSearchBar] -reachable through
 * [getSearchBar]- so everything that bar already offers (title/subtitle, outer `navigationIcon`,
 * inner `searchNavigationIcon`, clear icon, the four menus `leftMenu`/`rightMenu`/`pillMenuLeft`/
 * `pillMenuRight` with overflow, every listener, pill styling...) is available unchanged, and every
 * `ComponentSearchBar` XML attribute is accepted directly on this view.
 *
 * What this class adds is the **docked panel**: the children declared inside this view (results,
 * suggestions, history...) are shown under the pill, aligned to its edges, while the search is
 * active ([ComponentSearchBar.setSearchActive]). The panel opens with a reveal animation.
 *
 * ### Styles: joined / unjoined
 * - `Widget.ComponentMaterial.ComponentDockedSearchBar.Unjoined` (default): the panel is its own
 *   rounded surface, separated from the pill by a small gap.
 * - `Widget.ComponentMaterial.ComponentDockedSearchBar.Joined`: the panel is glued to the pill and
 *   both become ONE rounded surface (like Compose's expanded docked bar).
 *
 * Apply one with `style="@style/..."`, with `app:dockedJoined`, or with [setJoined].
 *
 * ### `forceSpaceDocked`
 * - `true` ("space" mode): the panel is part of the layout, so it takes room and pushes the
 *   siblings below this view down while the search is active.
 * - `false` (default, "dialog" mode): on activation a scrim fades in over the whole screen and the
 *   panel opens, while the bar and the panel are lifted into a full-screen [Dialog] so only they
 *   stay visible. A placeholder keeps the original space, so nothing in the layout jumps. Tapping
 *   the scrim, the back button or the search navigation icon closes everything with the reverse
 *   animation.
 *
 * ### Usage (XML)
 * ```xml
 * <com.deavidig.mod.deanielig.search.widget.ComponentDockedSearchBar
 *     android:id="@+id/dockedSearchBar"
 *     style="@style/Widget.ComponentMaterial.ComponentDockedSearchBar.Joined"
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:title="Inbox"
 *     android:hint="Search"
 *     app:navigationIcon="@drawable/ic_menu_hamburger_24"
 *     app:forceSpaceDocked="false">
 *
 *     <androidx.recyclerview.widget.RecyclerView
 *         android:layout_width="match_parent"
 *         android:layout_height="wrap_content" />
 * </com.deavidig.mod.deanielig.search.widget.ComponentDockedSearchBar>
 * ```
 *
 * ### Usage (Kotlin)
 * ```kotlin
 * docked.getSearchBar().setOnQueryTextChangeListener { text -> adapter.filter(text) }
 * docked.setOnSearchActiveChangeListener { active -> /* ... */ }   // NOT on getSearchBar()
 * docked.setJoined(true)
 * docked.setForceSpaceDocked(true)
 * ```
 *
 * Notes:
 * - This view owns the inner bar's `OnSearchActiveChangeListener`; use
 *   [setOnSearchActiveChangeListener] on this class instead of setting one on [getSearchBar].
 * - Scroll flags (`app:layout_scrollFlags`) go on THIS view when it lives inside an `AppBarLayout`.
 * - The dialog mode needs an Activity-backed [Context] (the usual case when inflating from XML).
 * - In the joined style the pill's own shadow is turned off and the shared surface casts it instead.
 * - In the joined style a divider separates the pill from the panel (`dockedDividerEnabled`,
 *   `dockedDividerColor`, `dockedDividerHeight`; `colorOutlineVariant` and 1dp by default).
 *
 * @author DeanielIG, DeavidIG
 */
class ComponentDockedSearchBar @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

	companion object {
		private const val PANEL_GAP_DP = 4
		private const val DEFAULT_CONTENT_MAX_HEIGHT_DP = 240
		private const val DEFAULT_ELEVATION_DP = 2
		private const val DEFAULT_SCRIM_COLOR = 0x52000000 // black @ 32%, the M3 scrim
		private const val ANIM_DURATION = 250L
	}

	// region State (everything private; public API through getters/setters) ----------------------

	private var initialized = false
	private var internalChange = false

	private val searchBar: ComponentSearchBar = ComponentSearchBar(context)
	private val panel: DockedPanel = DockedPanel(context)
	private val placeholder: View = View(context)
	private val surface = JoinedSurface()

	private val barParams = LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT,
		ViewGroup.LayoutParams.WRAP_CONTENT
	)
	private val panelParams = LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT,
		ViewGroup.LayoutParams.WRAP_CONTENT
	)

	private var joined: Boolean = false
	private var forceSpaceDocked: Boolean = false
	private var contentMaxHeightPx: Int = dpToPx(DEFAULT_CONTENT_MAX_HEIGHT_DP)

	private var scrimEnabled: Boolean = true
	@ColorInt
	private var scrimColor: Int = DEFAULT_SCRIM_COLOR

	@ColorInt
	private var pillColor: Int? = null
	@ColorInt
	private var panelColor: Int? = null
	private var panelCornerRadius: Float? = null
	private var dividerEnabled: Boolean = true
	@ColorInt
	private var dividerColor: Int? = null
	private var dividerHeightPx: Int = dpToPx(1)
	private var pillElevation: Float = dpToPx(DEFAULT_ELEVATION_DP).toFloat()
	private var panelElevation: Float = dpToPx(DEFAULT_ELEVATION_DP).toFloat()

	private var overlay: Overlay? = null
	private var overlayRestorePending = false
	private var revealAnimator: ValueAnimator? = null

	private var searchActiveListener: ComponentSearchBar.OnSearchActiveChangeListener? = null

	// endregion


	// region Internal types ---------------------------------------------------------------------

	/**
	 * Surface under the pill. It caps its own height at [maxHeightPx] (0 = unbounded) and opens with
	 * a reveal: [reveal] 0..1 scales the measured height (children stay clipped at the top) and fades it.
	 */
	private class DockedPanel(context: Context) : FrameLayout(context) {
		var maxHeightPx: Int = 0

		var reveal: Float = 1f
			set(value) {
				val v = value.coerceIn(0f, 1f)
				if (field == v) return
				field = v
				alpha = v
				requestLayout()
			}

		/** Line drawn over the top edge of the panel (joined style: it separates the pill from the content). */
		var dividerVisible: Boolean = false
			set(value) {
				if (field != value) {
					field = value; invalidate()
				}
			}
		var dividerHeightPx: Int = 1
			set(value) {
				if (field != value) {
					field = value; invalidate()
				}
			}
		private val dividerPaint = Paint()

		fun setDividerColor(@ColorInt color: Int) {
			dividerPaint.color = color
			invalidate()
		}

		override fun dispatchDraw(canvas: Canvas) {
			super.dispatchDraw(canvas)
			if (dividerVisible && dividerHeightPx > 0) {
				canvas.drawRect(0f, 0f, width.toFloat(), dividerHeightPx.toFloat(), dividerPaint)
			}
		}

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val spec = if (maxHeightPx > 0) {
				when (MeasureSpec.getMode(heightMeasureSpec)) {
					MeasureSpec.EXACTLY -> heightMeasureSpec
					MeasureSpec.AT_MOST -> MeasureSpec.makeMeasureSpec(
						min(
							MeasureSpec.getSize(
								heightMeasureSpec
							), maxHeightPx
						), MeasureSpec.AT_MOST
					)

					else -> MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST)
				}
			} else heightMeasureSpec
			super.onMeasure(widthMeasureSpec, spec)
			if (reveal < 1f) setMeasuredDimension(measuredWidth, (measuredHeight * reveal).toInt())
		}
	}

	/** Single rounded rect (pill + panel) drawn behind the bar in the joined style; the host's outline follows it. */
	private class JoinedSurface : Drawable() {
		val rect = RectF()
		var radius = 0f
		private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

		fun setFillColor(@ColorInt color: Int) {
			paint.color = color
			invalidateSelf()
		}

		override fun draw(canvas: Canvas) {
			if (!rect.isEmpty) canvas.drawRoundRect(rect, radius, radius, paint)
		}

		override fun setAlpha(alpha: Int) {
			paint.alpha = alpha; invalidateSelf()
		}

		override fun setColorFilter(colorFilter: ColorFilter?) {
			paint.colorFilter = colorFilter; invalidateSelf()
		}

		@Deprecated("Deprecated in Java")
		override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
	}

	/** Everything the dialog mode needs while it is showing. */
	private class Overlay(
		val dialog: Dialog,
		val root: FrameLayout,
		val scrim: View,
		val stack: LinearLayout
	) {
		var panelTop: Int = 0
		var bottomInset: Int = 0
		var ready: Boolean = false
		var closing: Boolean = false
	}

	private val surfaceOutline = object : ViewOutlineProvider() {
		override fun getOutline(view: View, outline: Outline) {
			val r = surface.rect
			if (r.isEmpty) outline.setEmpty()
			else outline.setRoundRect(
				r.left.toInt(),
				r.top.toInt(),
				r.right.toInt(),
				r.bottom.toInt(),
				surface.radius
			)
		}
	}

	/** Joined panel: only the bottom corners are rounded (the top ones sit off-screen), so content is clipped like the surface. */
	private val joinedPanelOutline = object : ViewOutlineProvider() {
		override fun getOutline(view: View, outline: Outline) {
			val r = surface.radius
			outline.setRoundRect(0, -r.toInt(), view.width, view.height, r)
		}
	}

	// endregion

	init {
		orientation = VERTICAL
		clipChildren = false
		clipToPadding = false

		panel.visibility = GONE
		panel.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
			override fun onChildViewAdded(parent: View?, child: View?) =
				updatePanelVisibility(animate = false)

			override fun onChildViewRemoved(parent: View?, child: View?) =
				updatePanelVisibility(animate = false)
		})
		panel.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> updateJoinedSurface() }
		placeholder.visibility = GONE

		searchBar.setOnSearchActiveChangeListener { active -> onSearchActiveChanged(active) }
		searchBar.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
			syncPanelBounds()
			updateJoinedSurface()
		}

		internalChange = true
		super.addView(searchBar, barParams)
		super.addView(panel, panelParams)
		super.addView(
			placeholder,
			LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)
		)
		internalChange = false

		if (attrs != null) {
			forwardSearchBarAttributes(attrs, defStyleAttr)
			readOwnAttributes(attrs, defStyleAttr)
		}
		initialized = true
		applyAppearance()
	}


	// region XML attributes -----------------------------------------------------------------------

	/** Applies every `ComponentSearchBar` attribute found on this view to the inner bar (same mapping as the bar itself). */
	private fun forwardSearchBarAttributes(attrs: AttributeSet, defStyleAttr: Int) {
		val a =
			context.obtainStyledAttributes(attrs, R.styleable.ComponentSearchBar, defStyleAttr, 0)
		try {
			val bar = searchBar
			a.getString(R.styleable.ComponentSearchBar_title)?.let { bar.setTitle(it) }
			a.getString(R.styleable.ComponentSearchBar_subtitle)?.let { bar.setSubtitle(it) }

			if (a.hasValue(R.styleable.ComponentSearchBar_titleTextColor)) {
				bar.setTitleTextColor(
					a.getColor(
						R.styleable.ComponentSearchBar_titleTextColor,
						Color.BLACK
					)
				)
			}
			if (a.hasValue(R.styleable.ComponentSearchBar_titleTextSize)) {
				bar.setTitleTextSize(
					a.getDimension(
						R.styleable.ComponentSearchBar_titleTextSize,
						0f
					) / resources.displayMetrics.scaledDensity
				)
			}
			a.getResourceId(R.styleable.ComponentSearchBar_titleTextAppearance, 0)
				.takeIf { it != 0 }?.let { bar.setTitleTextAppearance(it) }

			if (a.hasValue(R.styleable.ComponentSearchBar_subtitleTextColor)) {
				bar.setSubtitleTextColor(
					a.getColor(
						R.styleable.ComponentSearchBar_subtitleTextColor,
						Color.BLACK
					)
				)
			}
			if (a.hasValue(R.styleable.ComponentSearchBar_subtitleTextSize)) {
				bar.setSubtitleTextSize(
					a.getDimension(
						R.styleable.ComponentSearchBar_subtitleTextSize,
						0f
					) / resources.displayMetrics.scaledDensity
				)
			}
			a.getResourceId(R.styleable.ComponentSearchBar_subtitleTextAppearance, 0)
				.takeIf { it != 0 }?.let { bar.setSubtitleTextAppearance(it) }

			if (a.hasValue(R.styleable.ComponentSearchBar_titleAlignment)) {
				bar.setTitleAlignment(
					ComponentSearchBar.HorizontalAlignment.values()[a.getInt(
						R.styleable.ComponentSearchBar_titleAlignment,
						0
					)]
				)
			}
			if (a.hasValue(R.styleable.ComponentSearchBar_subtitleAlignment)) {
				bar.setSubtitleAlignment(
					ComponentSearchBar.HorizontalAlignment.values()[a.getInt(
						R.styleable.ComponentSearchBar_subtitleAlignment,
						0
					)]
				)
			}

			a.getDrawable(R.styleable.ComponentSearchBar_navigationIcon)
				?.let { bar.setNavigationIcon(it) }
			if (a.hasValue(R.styleable.ComponentSearchBar_navigationIconTint)) {
				bar.setNavigationIconTint(
					a.getColor(
						R.styleable.ComponentSearchBar_navigationIconTint,
						Color.BLACK
					)
				)
			}
			a.getString(R.styleable.ComponentSearchBar_navigationContentDescription)
				?.let { bar.setNavigationContentDescription(it) }

			a.getDrawable(R.styleable.ComponentSearchBar_searchNavigationIcon)
				?.let { bar.setSearchNavigationIcon(it) }
			if (a.hasValue(R.styleable.ComponentSearchBar_searchNavigationIconTint)) {
				bar.setSearchNavigationIconTint(
					a.getColor(
						R.styleable.ComponentSearchBar_searchNavigationIconTint,
						Color.BLACK
					)
				)
			}
			a.getString(R.styleable.ComponentSearchBar_searchNavigationContentDescription)
				?.let { bar.setSearchNavigationContentDescription(it) }

			a.getString(R.styleable.ComponentSearchBar_android_hint)?.let { bar.setQueryHint(it) }
			a.getString(R.styleable.ComponentSearchBar_android_text)?.let { bar.setQuery(it) }
			if (a.hasValue(R.styleable.ComponentSearchBar_clearIconEnable)) {
				bar.setClearIconEnabled(
					a.getBoolean(
						R.styleable.ComponentSearchBar_clearIconEnable,
						true
					)
				)
			}
			if (a.hasValue(R.styleable.ComponentSearchBar_clearIconTint)) {
				bar.setClearIconTint(
					a.getColor(
						R.styleable.ComponentSearchBar_clearIconTint,
						Color.BLACK
					)
				)
			}

			a.getResourceId(R.styleable.ComponentSearchBar_leftMenu, 0).takeIf { it != 0 }
				?.let { bar.inflateLeftMenu(it) }
			a.getResourceId(R.styleable.ComponentSearchBar_rightMenu, 0).takeIf { it != 0 }
				?.let { bar.inflateRightMenu(it) }
			a.getResourceId(R.styleable.ComponentSearchBar_pillMenuLeft, 0).takeIf { it != 0 }
				?.let { bar.inflatePillLeftMenu(it) }
			a.getResourceId(R.styleable.ComponentSearchBar_pillMenuRight, 0).takeIf { it != 0 }
				?.let { bar.inflatePillRightMenu(it) }
			a.getDrawable(R.styleable.ComponentSearchBar_leftOverflowIcon)
				?.let { bar.setLeftOverflowIcon(it) }
			a.getDrawable(R.styleable.ComponentSearchBar_rightOverflowIcon)
				?.let { bar.setRightOverflowIcon(it) }
			a.getDrawable(R.styleable.ComponentSearchBar_pillMenuLeftOverflowIcon)
				?.let { bar.setPillLeftOverflowIcon(it) }
			a.getDrawable(R.styleable.ComponentSearchBar_pillMenuRightOverflowIcon)
				?.let { bar.setPillRightOverflowIcon(it) }

			if (a.hasValue(R.styleable.ComponentSearchBar_pillBackgroundColor)) {
				val color =
					a.getColor(R.styleable.ComponentSearchBar_pillBackgroundColor, Color.LTGRAY)
				bar.setPillBackgroundColor(color)
				pillColor = color
			}
			if (a.hasValue(R.styleable.ComponentSearchBar_pillElevation)) {
				val elevation =
					a.getDimension(R.styleable.ComponentSearchBar_pillElevation, pillElevation)
				pillElevation = elevation
				panelElevation = elevation
			}

			if (a.hasValue(R.styleable.ComponentSearchBar_contentInsetStartWithNavigation)) {
				bar.setContentInsetStartWithNavigation(
					a.getDimensionPixelSize(
						R.styleable.ComponentSearchBar_contentInsetStartWithNavigation,
						bar.getContentInsetStart()
					)
				)
			}
			if (a.hasValue(R.styleable.ComponentSearchBar_contentInsetEnd)) {
				bar.setContentInsetEnd(
					a.getDimensionPixelSize(
						R.styleable.ComponentSearchBar_contentInsetEnd,
						bar.getContentInsetEnd()
					)
				)
			}
		} finally {
			a.recycle()
		}
	}

	private fun readOwnAttributes(attrs: AttributeSet, defStyleAttr: Int) {
		val a = context.obtainStyledAttributes(
			attrs,
			R.styleable.ComponentDockedSearchBar,
			defStyleAttr,
			0
		)
		try {
			joined = a.getBoolean(R.styleable.ComponentDockedSearchBar_dockedJoined, joined)
			forceSpaceDocked = a.getBoolean(
				R.styleable.ComponentDockedSearchBar_forceSpaceDocked,
				forceSpaceDocked
			)
			contentMaxHeightPx = a.getDimensionPixelSize(
				R.styleable.ComponentDockedSearchBar_dockedContentMaxHeight,
				contentMaxHeightPx
			)
			scrimEnabled =
				a.getBoolean(R.styleable.ComponentDockedSearchBar_dockedScrimEnabled, scrimEnabled)
			scrimColor =
				a.getColor(R.styleable.ComponentDockedSearchBar_dockedScrimColor, scrimColor)
			if (a.hasValue(R.styleable.ComponentDockedSearchBar_dockedPanelBackgroundColor)) {
				panelColor = a.getColor(
					R.styleable.ComponentDockedSearchBar_dockedPanelBackgroundColor,
					Color.LTGRAY
				)
			}
			if (a.hasValue(R.styleable.ComponentDockedSearchBar_dockedPanelCornerRadius)) {
				panelCornerRadius =
					a.getDimension(R.styleable.ComponentDockedSearchBar_dockedPanelCornerRadius, 0f)
			}
			dividerEnabled = a.getBoolean(
				R.styleable.ComponentDockedSearchBar_dockedDividerEnabled,
				dividerEnabled
			)
			if (a.hasValue(R.styleable.ComponentDockedSearchBar_dockedDividerColor)) {
				dividerColor = a.getColor(
					R.styleable.ComponentDockedSearchBar_dockedDividerColor,
					Color.LTGRAY
				)
			}
			dividerHeightPx = a.getDimensionPixelSize(
				R.styleable.ComponentDockedSearchBar_dockedDividerHeight,
				dividerHeightPx
			)
			if (a.hasValue(R.styleable.ComponentDockedSearchBar_dockedPanelElevation)) {
				panelElevation = a.getDimension(
					R.styleable.ComponentDockedSearchBar_dockedPanelElevation,
					panelElevation
				)
			}
		} finally {
			a.recycle()
		}
	}

	// endregion


	// region Public API: the inner bar ------------------------------------------------------------

	/** The real [ComponentSearchBar]: use it for the whole bar API (menus, listeners, icons, tints...). */
	fun getSearchBar(): ComponentSearchBar = searchBar

	fun setTitle(title: CharSequence?) = searchBar.setTitle(title)
	fun setTitle(@StringRes resId: Int) = searchBar.setTitle(resId)
	fun getTitle(): CharSequence? = searchBar.getTitle()

	fun setSubtitle(subtitle: CharSequence?) = searchBar.setSubtitle(subtitle)
	fun setSubtitle(@StringRes resId: Int) = searchBar.setSubtitle(resId)
	fun getSubtitle(): CharSequence? = searchBar.getSubtitle()

	fun setNavigationIcon(icon: Drawable?) = searchBar.setNavigationIcon(icon)
	fun setNavigationIcon(@DrawableRes iconRes: Int) = searchBar.setNavigationIcon(iconRes)
	fun getNavigationIcon(): Drawable? = searchBar.getNavigationIcon()

	fun setSearchNavigationIcon(icon: Drawable?) = searchBar.setSearchNavigationIcon(icon)
	fun setSearchNavigationIcon(@DrawableRes iconRes: Int) =
		searchBar.setSearchNavigationIcon(iconRes)

	fun getSearchNavigationIcon(): Drawable? = searchBar.getSearchNavigationIcon()

	fun setQuery(text: CharSequence?, submit: Boolean = false) = searchBar.setQuery(text, submit)
	fun getQuery(): CharSequence = searchBar.getQuery()
	fun clearQuery() = searchBar.clearQuery()
	fun setQueryHint(hint: CharSequence?) = searchBar.setQueryHint(hint)
	fun getQueryHint(): CharSequence? = searchBar.getQueryHint()

	fun inflateMenu(side: ComponentSearchBar.MenuSide, @MenuRes menuRes: Int) =
		searchBar.inflateMenu(side, menuRes)

	fun setSearchActive(active: Boolean) = searchBar.setSearchActive(active)
	fun isSearchActive(): Boolean = searchBar.isSearchActive()

	/** Use this instead of setting the listener on [getSearchBar] (this view owns that one). */
	fun setOnSearchActiveChangeListener(listener: ComponentSearchBar.OnSearchActiveChangeListener?) {
		searchActiveListener = listener
	}

	/** Also recolors the docked surface, unless [setPanelBackgroundColor] was given an explicit color (unjoined only). */
	fun setPillBackgroundColor(@ColorInt color: Int?) {
		pillColor = color
		searchBar.setPillBackgroundColor(color)
		applyAppearance()
	}

	/** Also applies the same elevation to the docked panel. */
	fun setPillElevation(@Px elevation: Float) {
		pillElevation = elevation
		panelElevation = elevation
		applyAppearance()
	}

	// endregion


	// region Public API: style, panel and mode ---------------------------------------------------

	fun isJoined(): Boolean = joined

	/**
	 * `true`: the panel is glued to the pill and both form one rounded surface (style `...Joined`).
	 * `false`: the panel is a separate surface with a small gap (style `...Unjoined`). Safe while active.
	 */
	fun setJoined(joined: Boolean) {
		if (this.joined == joined) return
		this.joined = joined
		applyAppearance()
		syncPanelBounds()
		updateJoinedSurface()
	}

	fun isDividerEnabled(): Boolean = dividerEnabled

	/** Joined style only: shows/hides the divider between the pill and the panel (on by default). */
	fun setDividerEnabled(enabled: Boolean) {
		dividerEnabled = enabled; applyAppearance()
	}

	/** `null` uses the theme's `colorOutlineVariant`. */
	fun setDividerColor(@ColorInt color: Int?) {
		dividerColor = color; applyAppearance()
	}

	fun getDividerHeight(): Int = dividerHeightPx
	fun setDividerHeight(@Px height: Int) {
		dividerHeightPx = height; applyAppearance()
	}

	fun isForceSpaceDocked(): Boolean = forceSpaceDocked

	/**
	 * `true`: the panel takes layout space and pushes the siblings below. `false`: bar + panel are
	 * shown in a [Dialog] over a scrim -see the class KDoc. Safe to call while the search is active.
	 */
	fun setForceSpaceDocked(force: Boolean) {
		if (forceSpaceDocked == force) return
		forceSpaceDocked = force
		if (force) {
			closeOverlayNow()
			updatePanelVisibility(animate = false)
		} else if (searchBar.isSearchActive()) {
			panel.visibility = GONE
			openOverlay()
		}
	}

	/** The panel itself: add results/suggestions here programmatically (XML children already land here). */
	fun getContentContainer(): ViewGroup = panel

	fun getContentMaxHeight(): Int = contentMaxHeightPx

	/** Max height of the panel in pixels (240dp by default); `0` removes the cap. */
	fun setContentMaxHeight(@Px maxHeight: Int) {
		contentMaxHeightPx = maxHeight
		applyPanelMaxHeight()
	}

	fun isScrimEnabled(): Boolean = scrimEnabled

	/** Dialog mode only: `false` keeps the dialog (and its tap-outside-to-close) but with no dimming. */
	fun setScrimEnabled(enabled: Boolean) {
		scrimEnabled = enabled
	}

	@ColorInt
	fun getScrimColor(): Int = scrimColor
	fun setScrimColor(@ColorInt color: Int) {
		scrimColor = color
	}

	/** Unjoined only. `null` follows the pill color (or the theme default, `colorSurfaceContainerHigh`). */
	fun setPanelBackgroundColor(@ColorInt color: Int?) {
		panelColor = color; applyAppearance()
	}

	/** Unjoined only. `null` follows the pill's corner radius. */
	fun setPanelCornerRadius(@Px radius: Float?) {
		panelCornerRadius = radius; applyAppearance()
	}

	fun getPanelCornerRadius(): Float? = panelCornerRadius

	/** Unjoined only (the joined surface uses the pill elevation). */
	fun setPanelElevation(@Px elevation: Float) {
		panelElevation = elevation; applyAppearance()
	}

	// endregion


	// region Children declared in XML go to the panel -------------------------------------------

	override fun addView(child: View?, index: Int, params: ViewGroup.LayoutParams?) {
		if (!initialized || internalChange) {
			super.addView(child, index, params)
			return
		}
		val lp = when (params) {
			null -> FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.WRAP_CONTENT,
				ViewGroup.LayoutParams.WRAP_CONTENT
			)

			is FrameLayout.LayoutParams -> params
			is ViewGroup.MarginLayoutParams -> FrameLayout.LayoutParams(params)
			else -> FrameLayout.LayoutParams(params)
		}
		panel.addView(child, index, lp)
	}

	// endregion


	// region Active state / panel reveal ---------------------------------------------------------

	private fun onSearchActiveChanged(active: Boolean) {
		if (forceSpaceDocked) {
			updatePanelVisibility(animate = true)
		} else {
			// Posted: moving views while the tapped icon is still dispatching its own click is asking for trouble.
			post { if (searchBar.isSearchActive()) openOverlay() else closeOverlay() }
		}
		searchActiveListener?.onSearchActiveChange(active)
	}

	private fun shouldShowPanel(): Boolean {
		if (!searchBar.isSearchActive() || panel.childCount == 0) return false
		val o = overlay
		return if (forceSpaceDocked) o == null else o != null && o.ready && !o.closing
	}

	/** Opens/closes the panel with the reveal animation (or instantly when [animate] is false). */
	private fun updatePanelVisibility(animate: Boolean) {
		val show = shouldShowPanel()
		if (show) {
			if (panel.visibility != VISIBLE) {
				revealAnimator?.cancel()
				panel.reveal = if (animate && isLaidOut) 0f else 1f
				panel.visibility = VISIBLE
				applyPanelMaxHeight()
				syncPanelBounds()
			}
			if (animate && panel.reveal < 1f) animateReveal(panel.reveal, 1f, null)
			else if (!animate) {
				revealAnimator?.cancel(); panel.reveal = 1f
			}
		} else if (panel.visibility == VISIBLE) {
			revealAnimator?.cancel()
			if (animate && isLaidOut && overlay == null) {
				animateReveal(panel.reveal, 0f) { panel.visibility = GONE; panel.reveal = 1f }
			} else {
				panel.visibility = GONE
				panel.reveal = 1f
			}
		}
	}

	private fun animateReveal(from: Float, to: Float, onEnd: (() -> Unit)?) {
		revealAnimator?.cancel()
		val animator = ValueAnimator.ofFloat(from, to).apply {
			duration = ANIM_DURATION
			interpolator = FastOutSlowInInterpolator()
			addUpdateListener { panel.reveal = it.animatedValue as Float }
			addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					if (revealAnimator === animation) revealAnimator = null
					onEnd?.invoke()
				}
			})
		}
		revealAnimator = animator
		animator.start()
	}

	private fun applyPanelMaxHeight() {
		val cap = if (contentMaxHeightPx > 0) contentMaxHeightPx else Int.MAX_VALUE
		val o = overlay
		val resolved = if (o == null) cap else {
			val available = o.root.height - o.panelTop - o.bottomInset - dpToPx(8)
			if (available > 0) min(cap, available) else cap
		}
		panel.maxHeightPx = if (resolved == Int.MAX_VALUE) 0 else resolved
		panel.requestLayout()
	}

	/** Pill bounds relative to the bar: `[left, right, bottom, top]`. The pill is the widest visible `LinearLayout` child of the bar. */
	private fun pillBounds(): IntArray? {
		var best: View? = null
		for (i in 0 until searchBar.childCount) {
			val child = searchBar.getChildAt(i)
			if (child is LinearLayout && child.visibility != GONE && (best == null || child.width > best.width)) best =
				child
		}
		return best?.takeIf { it.width > 0 }
			?.let { intArrayOf(it.left, it.right, it.bottom, it.top) }
	}

	/** Aligns the panel with the pill's edges, right under it (no gap when joined). */
	private fun syncPanelBounds() {
		val pill = pillBounds() ?: return
		val lp = panel.layoutParams as? LinearLayout.LayoutParams ?: return
		val left = pill[0]
		val right = max(0, searchBar.width - pill[1])
		val gap = if (joined) 0 else dpToPx(PANEL_GAP_DP)
		val top = gap - max(0, searchBar.height - pill[2])
		if (lp.leftMargin != left || lp.rightMargin != right || lp.topMargin != top) {
			lp.leftMargin = left
			lp.rightMargin = right
			lp.topMargin = top
			panel.layoutParams = lp
		}
	}

	// endregion


	// region Dialog mode ------------------------------------------------------------------------

	private fun openOverlay() {
		if (overlay != null || forceSpaceDocked || !searchBar.isSearchActive() || !isAttachedToWindow) return
		runWhenLaidOut {
			if (overlay == null && !forceSpaceDocked && searchBar.isSearchActive()) showOverlay()
		}
	}

	private fun showOverlay() {
		val dialog = object : Dialog(context, android.R.style.Theme_Translucent_NoTitleBar) {
			@Suppress("OVERRIDE_DEPRECATION")
			override fun onBackPressed() {
				// Goes through the search state so the closing animation always runs.
				searchBar.setSearchActive(false)
			}
		}
		val root = FrameLayout(context)
		val scrim = View(context).apply {
			setBackgroundColor(if (scrimEnabled) scrimColor else Color.TRANSPARENT)
			alpha = 0f
			setOnClickListener { searchBar.setSearchActive(false) }
		}
		root.addView(
			scrim,
			FrameLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT
			)
		)
		val stack = LinearLayout(context).apply {
			orientation = VERTICAL
			clipChildren = false
			clipToPadding = false
		}

		val holder = Overlay(dialog, root, scrim, stack)
		overlay = holder

		dialog.setContentView(root)
		dialog.setOnDismissListener {
			// Dismissed by someone else (not through closeOverlay): bring everything home.
			if (overlay === holder) {
				overlay = null
				cancelAnimations(holder)
				restoreChildren()
				searchBar.setSearchActive(false)
			}
		}
		dialog.window?.let { w ->
			w.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
			w.setLayout(
				WindowManager.LayoutParams.MATCH_PARENT,
				WindowManager.LayoutParams.MATCH_PARENT
			)
			w.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
			w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
			w.statusBarColor = Color.TRANSPARENT
			w.navigationBarColor = Color.TRANSPARENT
			w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
			WindowCompat.setDecorFitsSystemWindows(w, false)
		}
		ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
			holder.bottomInset = max(
				insets.getInsets(WindowInsetsCompat.Type.ime()).bottom,
				insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
			)
			applyPanelMaxHeight()
			insets
		}

		dialog.show()

		// The views hop into the dialog right before its FIRST frame: that frame is cancelled and redrawn with
		// the bar already in place, so there is no flash and no frame where the bar is missing.
		root.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
			override fun onPreDraw(): Boolean {
				if (root.width == 0) return true
				root.viewTreeObserver.removeOnPreDrawListener(this)
				if (overlay !== holder) return true
				moveChildrenToOverlay(holder)
				return false
			}
		})
	}

	private fun moveChildrenToOverlay(o: Overlay) {
		val barLoc = IntArray(2).also { searchBar.getLocationOnScreen(it) }
		val rootLoc = IntArray(2).also { o.root.getLocationOnScreen(it) }
		val barWidth = searchBar.width
		val barHeight = searchBar.height
		val pill = pillBounds()
		val barLeft = barLoc[0] - rootLoc[0]
		val barTop = barLoc[1] - rootLoc[1]
		val pillBottom = pill?.get(2) ?: barHeight

		// Leave a placeholder of the same height so the host layout does not jump.
		super.removeView(searchBar)
		super.removeView(panel)
		placeholder.layoutParams =
			LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, barHeight)
		placeholder.visibility = VISIBLE

		o.stack.addView(
			searchBar,
			LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT
			)
		)
		o.stack.addView(panel, panelParams)
		o.root.addView(
			o.stack,
			FrameLayout.LayoutParams(barWidth, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
				leftMargin = barLeft
				topMargin = barTop
			})
		o.panelTop = barTop + pillBottom + if (joined) 0 else dpToPx(PANEL_GAP_DP)
		o.ready = true

		applyAppearance()
		updatePanelVisibility(animate = true)
		o.scrim.animate().alpha(1f).setDuration(ANIM_DURATION)
			.setInterpolator(FastOutSlowInInterpolator()).start()
		o.root.post { searchBar.requestSearchFocus() }
	}

	/** Reverse animation: the panel closes while the scrim fades; then everything goes back home. */
	private fun closeOverlay() {
		val o = overlay ?: return
		if (o.closing) return
		o.closing = true
		o.dialog.setOnDismissListener(null)

		revealAnimator?.cancel()
		if (panel.visibility == VISIBLE) {
			animateReveal(panel.reveal, 0f) { panel.visibility = GONE }
		}
		o.scrim.animate().cancel()
		o.scrim.animate()
			.alpha(0f)
			.setDuration(ANIM_DURATION)
			.setInterpolator(FastOutSlowInInterpolator())
			.withEndAction {
				if (overlay !== o) return@withEndAction
				overlay = null
				cancelAnimations(o)
				panel.visibility = GONE
				panel.reveal = 1f
				restoreChildren()
				safeDismiss(o.dialog)
			}
			.start()
	}

	/** No animation: used when switching to space mode or when the view leaves the window. */
	private fun closeOverlayNow() {
		val o = overlay ?: return
		overlay = null
		o.dialog.setOnDismissListener(null)
		cancelAnimations(o)
		restoreChildren()
		safeDismiss(o.dialog)
	}

	private fun cancelAnimations(o: Overlay) {
		o.scrim.animate().cancel()
		revealAnimator?.cancel()
		revealAnimator = null
	}

	/** Puts the bar and the panel back in their original slots; the placeholder goes away. */
	private fun restoreChildren() {
		(searchBar.parent as? ViewGroup)?.takeIf { it !== this }?.removeView(searchBar)
		(panel.parent as? ViewGroup)?.takeIf { it !== this }?.removeView(panel)
		internalChange = true
		if (searchBar.parent == null) super.addView(searchBar, 0, barParams)
		if (panel.parent == null) super.addView(panel, 1, panelParams)
		internalChange = false
		placeholder.visibility = GONE
		placeholder.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0)
		applyAppearance()
		syncPanelBounds()
		updatePanelVisibility(animate = false)
	}

	private fun safeDismiss(dialog: Dialog) {
		try {
			if (dialog.isShowing) dialog.dismiss()
		} catch (_: IllegalArgumentException) {
			// The window is already gone (e.g. the Activity was destroyed).
		}
	}

	private fun runWhenLaidOut(block: () -> Unit) {
		if (isLaidOut && searchBar.width > 0) {
			block()
			return
		}
		addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
			override fun onLayoutChange(
				v: View?,
				l: Int,
				t: Int,
				r: Int,
				b: Int,
				oldL: Int,
				oldT: Int,
				oldR: Int,
				oldB: Int
			) {
				if (searchBar.width > 0) {
					removeOnLayoutChangeListener(this)
					block()
				}
			}
		})
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		if (overlayRestorePending) {
			overlayRestorePending = false
			restoreChildren()
		}
		if (forceSpaceDocked) updatePanelVisibility(animate = false) else if (searchBar.isSearchActive()) openOverlay()
	}

	override fun onDetachedFromWindow() {
		val o = overlay
		if (o != null) {
			// The dialog must not outlive this view. The children are brought back on the next attach
			// (adding views while detaching would leave them half-attached).
			overlay = null
			overlayRestorePending = true
			o.dialog.setOnDismissListener(null)
			cancelAnimations(o)
			safeDismiss(o.dialog)
			searchBar.setSearchActive(false)
		}
		super.onDetachedFromWindow()
	}

	// endregion


	// region Appearance --------------------------------------------------------------------------

	private fun themeSurfaceColor(): Int = MaterialColors.getColor(
		context,
		MR.attr.colorSurfaceContainerHigh,
		MaterialColors.getColor(context, MR.attr.colorSurfaceVariant, Color.LTGRAY)
	)

	private fun surfaceRadius(): Float = searchBar.getPillCornerRadius() ?: (dpToPx(56) / 2f)

	/** Applies joined/unjoined: panel look, pill shadow and which view carries the shared surface. */
	private fun applyAppearance() {
		if (!initialized) return
		val radius = surfaceRadius()
		val fill = pillColor ?: themeSurfaceColor()
		surface.radius = radius
		surface.setFillColor(fill)

		if (joined) {
			panel.background = null
			ViewCompat.setElevation(panel, 0f)
			panel.outlineProvider = joinedPanelOutline
			searchBar.setPillElevation(0f) // the shared surface casts the shadow instead
		} else {
			panel.background = GradientDrawable().apply {
				shape = GradientDrawable.RECTANGLE
				cornerRadius = panelCornerRadius ?: radius
				setColor(panelColor ?: fill)
			}
			panel.outlineProvider = ViewOutlineProvider.BACKGROUND
			ViewCompat.setElevation(panel, panelElevation)
			searchBar.setPillElevation(pillElevation)
		}
		panel.clipToOutline = true
		panel.invalidateOutline()

		panel.dividerVisible = joined && dividerEnabled
		panel.dividerHeightPx = dividerHeightPx
		panel.setDividerColor(
			dividerColor ?: MaterialColors.getColor(
				context,
				MR.attr.colorOutlineVariant,
				Color.LTGRAY
			)
		)

		val active = overlay?.stack
		styleHost(this, joined && active == null)
		active?.let { styleHost(it, joined) }
		updateJoinedSurface()
	}

	/** In the joined style the host (this view, or the dialog's stack) draws the shared surface and casts its shadow. */
	private fun styleHost(host: ViewGroup, withSurface: Boolean) {
		host.clipChildren = false
		host.clipToPadding = false
		if (withSurface) {
			host.background = surface
			host.outlineProvider = surfaceOutline
			host.clipToOutline = false
			ViewCompat.setElevation(host, pillElevation)
		} else {
			if (host.background === surface) host.background = null
			host.outlineProvider = ViewOutlineProvider.BACKGROUND
			ViewCompat.setElevation(host, 0f)
		}
	}

	/** Recomputes the shared surface: from the pill's top to the pill's bottom, or to the panel's bottom while it is open. */
	private fun updateJoinedSurface() {
		if (!joined || !initialized) return
		val host: ViewGroup = overlay?.stack ?: this
		val pill = pillBounds()
		if (pill == null || searchBar.parent !== host) {
			surface.rect.setEmpty()
		} else {
			val left = searchBar.left + pill[0]
			val right = searchBar.left + pill[1]
			val top = searchBar.top + pill[3]
			var bottom = searchBar.top + pill[2]
			if (panel.visibility == VISIBLE && panel.parent === host) bottom =
				max(bottom, panel.bottom)
			surface.rect.set(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
		}
		surface.invalidateSelf()
		host.invalidateOutline()
		host.invalidate()
	}

	private fun dpToPx(dp: Int): Int =
		TypedValue.applyDimension(
			TypedValue.COMPLEX_UNIT_DIP,
			dp.toFloat(),
			resources.displayMetrics
		).toInt()

	// endregion


	// region State saving ------------------------------------------------------------------------
	// The inner bar has no id, so this view saves what matters itself (query + active state).

	override fun onSaveInstanceState(): Parcelable {
		val state = SavedState(super.onSaveInstanceState())
		state.query = searchBar.getQuery().toString()
		state.isSearchActive = searchBar.isSearchActive()
		return state
	}

	override fun onRestoreInstanceState(state: Parcelable?) {
		if (state !is SavedState) {
			super.onRestoreInstanceState(state)
			return
		}
		super.onRestoreInstanceState(state.superState)
		searchBar.setQuery(state.query)
		searchBar.setSearchActive(state.isSearchActive)
	}

	private class SavedState : BaseSavedState {
		var query: String? = null
		var isSearchActive: Boolean = false

		constructor(superState: Parcelable?) : super(superState)

		constructor(parcel: Parcel) : super(parcel) {
			query = parcel.readString()
			isSearchActive = parcel.readInt() != 0
		}

		override fun writeToParcel(out: Parcel, flags: Int) {
			super.writeToParcel(out, flags)
			out.writeString(query)
			out.writeInt(if (isSearchActive) 1 else 0)
		}

		companion object CREATOR : Parcelable.Creator<SavedState> {
			override fun createFromParcel(parcel: Parcel) = SavedState(parcel)
			override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
		}
	}

	// endregion
}