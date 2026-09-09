package com.deavidig.mod.deanielig.search.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.os.Parcel
import android.os.Parcelable
import android.text.Editable
import android.text.InputType
import android.text.TextUtils
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.MenuRes
import androidx.annotation.Px
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.TextViewCompat
import com.deavidig.sketchprojectpro.R
import com.google.android.material.appbar.AppBarLayout
import kotlin.math.max
import kotlin.math.min

/**
 * A custom Material Design 3 **SearchBar** built from scratch on top of
 * [ViewGroup] -exactly the same philosophy as `ComponentToolbar`- with four
 * *simultaneous* menu slots instead of the usual two:
 *
 * - **Outer left menu** / **outer right menu**: rendered OUTSIDE the rounded
 *   pill, framing it -the same mechanics `ComponentToolbar` already has
 *   (showAsAction/overflow/reactive items/selection lifecycle), just reused
 *   here for two extra slots.
 * - **Pill left menu** / **pill right menu**: rendered INSIDE the pill,
 *   leading and trailing the query field respectively -the "avatar / quick
 *   actions" row a real M3 SearchBar shows on its edges. Both share 100% of
 *   the outer menus' engine (see [MenuSideState]); only the container each
 *   one lives in changes.
 *
 * On top of that:
 * - **Navigation icon** (outer, outside the pill) and **Search navigation
 *   icon** (inner, leading icon INSIDE the pill) are fully independent:
 *   separate drawables, separate tints, separate click/long-click listeners.
 * - The pill has two mutually-exclusive, toggleable contents -see
 *   [setSearchActive]: **inactive** (the default) shows the **title/
 *   subtitle** heading centered in the pill, with the query field hidden;
 *   **active** hides title/subtitle and shows the query field instead,
 *   focused and ready to type. Tapping the search navigation icon toggles
 *   between the two, which is also what swaps its own drawable between
 *   [setSearchNavigationIcon] (a magnifier by default, inactive) and
 *   [setSearchNavigationActiveIcon] (a back arrow by default, active) -so
 *   tapping it again while active deactivates the search field.
 * - Optional **title/subtitle**, laid out INSIDE the pill, with the same
 *   per-line [HorizontalAlignment] `ComponentToolbar` offers.
 * - Optional **clear ("x") icon**, auto-shown inside the pill only while
 *   there is query text, positioned right before the pill right menu.
 *
 * ### Internal structure
 * Unlike `ComponentToolbar` (every child manually positioned in
 * [onLayout]), the pill itself is a plain [LinearLayout] -there is no
 * value in hand-rolling horizontal flex math a `LinearLayout` already does
 * correctly for a single row of icon/content/icon. The title/subtitle pair
 * and the query field are stacked vertically inside their own flexible
 * [LinearLayout] cell of that row (`pillContentContainer`), so the pill
 * grows taller automatically whenever a title or subtitle is set. This
 * class still manually lays out the OUTER row (navigation icon, outer
 * menus, the pill as one flexible block), the same way `ComponentToolbar`
 * does.
 *
 * ### Basic usage (XML)
 * ```xml
 * <com.google.android.material.appbar.AppBarLayout
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content">
 *
 *     <com.deavidig.mod.deaniel.searchbar.widget.ComponentSearchBar
 *         android:id="@+id/searchBar"
 *         android:layout_width="match_parent"
 *         android:layout_height="wrap_content"
 *         app:layout_scrollFlags="scroll|enterAlways"
 *         app:title="Inbox"
 *         app:subtitle="3 new messages"
 *         app:queryHint="Type here to Search Project"
 *         app:navigationIcon="@drawable/ic_menu_hamburger_24"
 *         app:searchNavigationIcon="@drawable/ic_search_24"
 *         app:searchNavigationActiveIcon="@drawable/ic_arrow_back_24"
 *         app:pillMenuLeft="@menu/menu_search_pill_left"
 *         app:pillMenuRight="@menu/menu_search_pill_right"
 *         app:rightMenu="@menu/menu_search_outer_right" />
 *
 * </com.google.android.material.appbar.AppBarLayout>
 * ```
 * (requires declaring the `ComponentSearchBar` styleable from
 * `attrs_component_toolbar.xml` under `res/values/` of your module.)
 *
 * ### Basic usage (Kotlin)
 * ```kotlin
 * searchBar.setOnQueryTextChangeListener { text -> adapter.filter(text) }
 * searchBar.setOnQueryTextSubmitListener { text -> search(text); true }
 *
 * searchBar.inflatePillRightMenu(R.menu.menu_search_pill_right)
 * searchBar.setOnPillRightItemMenuClickListener { item ->
 *     if (item.itemId == R.id.action_avatar) openProfile()
 * }
 *
 * searchBar.setSearchNavigationOnClickListener { searchBar.clearQuery() }
 * ```
 *
 * @constructor Sets up every internal child (title/subtitle and query field
 *   inside the pill, outer navigation icon, both outer menus, the pill with
 *   its inner navigation icon/clear icon/pill left menu/pill right menu),
 *   applies the Material 3 theme defaults, and finally parses [attrs]/
 *   [defStyleAttr] so any explicit XML attribute overrides those defaults.
 * @param context source context used to inflate drawables, resolve theme
 *   attributes and build child views.
 * @param attrs XML attributes for the component; see the
 *   `ComponentSearchBar` styleable in `attrs_component_toolbar.xml`.
 * @param defStyleAttr default style attribute resolved from the theme when
 *   an attribute isn't explicitly set in [attrs].
 *
 * @author DeanielIG, DeavidIG
 */
class ComponentSearchBar @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

	companion object {
		private const val NO_ITEM_ID = Menu.NONE
		private const val DISABLED_ALPHA = 0.38f
		private const val ENABLED_ALPHA = 1f
	}

	/** Horizontal alignment available for the title and the subtitle, each configurable independently. */
	enum class HorizontalAlignment { START, CENTER, END }

	/** Identifies which of the four *simultaneous* menu slots a call refers to -see the class KDoc. */
	enum class MenuSide { LEFT, RIGHT, PILL_LEFT, PILL_RIGHT }


	/** Invoked when the OUTER navigation icon (outside the pill) is tapped. */
	fun interface OnNavigationClickListener {
		fun onNavigationClick(view: View)
	}

	/** Long-press variant of [OnNavigationClickListener]. Return `true` if the event was consumed. */
	fun interface OnNavigationLongClickListener {
		fun onNavigationLongClick(view: View): Boolean
	}

	/** Invoked when the INNER search navigation icon (leading icon inside the pill) is tapped. Independent from [OnNavigationClickListener]. */
	fun interface OnSearchNavigationClickListener {
		fun onSearchNavigationClick(view: View)
	}

	/** Long-press variant of [OnSearchNavigationClickListener]. Return `true` if the event was consumed. */
	fun interface OnSearchNavigationLongClickListener {
		fun onSearchNavigationLongClick(view: View): Boolean
	}

	/** Invoked whenever [setSearchActive] actually changes state -see [setOnSearchActiveChangeListener]. */
	fun interface OnSearchActiveChangeListener {
		fun onSearchActiveChange(active: Boolean)
	}

	/** Invoked on every keystroke of the query field. */
	fun interface OnQueryTextChangeListener {
		fun onQueryTextChange(text: CharSequence)
	}

	/** Invoked when the query is submitted (IME "search" action or the hardware Enter key). Return `true` if handled. */
	fun interface OnQueryTextSubmitListener {
		fun onQueryTextSubmit(text: CharSequence): Boolean
	}

	/** Notifies a click on a specific item of one of the three menus (whether it was visible or inside the overflow). */
	fun interface OnItemMenuClickListener {
		fun onMenuItemClicked(item: MenuItem)
	}

	/** Long-press variant of [OnItemMenuClickListener]. Does not affect the item's "checked" state. */
	fun interface OnItemMenuLongClickListener {
		fun onMenuItemLongClicked(item: MenuItem): Boolean
	}

	/**
	 * `BottomNavigationView`-style selection lifecycle for the items of a
	 * menu: selecting a new item, deselecting the previous one, or tapping
	 * the one that was already selected again (reselect).
	 */
	interface OnItemMenuChangedListener {
		fun onMenuSelect(item: MenuItem)
		fun onMenuUnselect(item: MenuItem)
		fun onMenuReselect(item: MenuItem)
	}

	/** Same lifecycle as [OnItemMenuChangedListener], but as an independent channel driven by long-press. */
	interface OnItemMenuLongChangedListener {
		fun onMenuLongSelect(item: MenuItem)
		fun onMenuLongUnselect(item: MenuItem)
		fun onMenuLongReselect(item: MenuItem)
	}


	// region Views -------------------------------------------------------

	/** Title/subtitle now live INSIDE the pill -stacked above the query field, see [pillContentContainer]. */
	private val titleView: AppCompatTextView = AppCompatTextView(context)
	private val subtitleView: AppCompatTextView = AppCompatTextView(context)

	private val navigationIconView: AppCompatImageButton = AppCompatImageButton(context)
	private val leftMenuView: LinearLayout = LinearLayout(context)
	private val rightMenuView: LinearLayout = LinearLayout(context)
	private val leftOverflowButton: AppCompatImageButton = AppCompatImageButton(context)
	private val rightOverflowButton: AppCompatImageButton = AppCompatImageButton(context)

	/** The rounded "pill" -a plain LinearLayout; see the class KDoc for why. */
	private val pillContainer: LinearLayout = LinearLayout(context)
	private val pillMenuLeftView: LinearLayout = LinearLayout(context)
	private val pillMenuLeftOverflowButton: AppCompatImageButton = AppCompatImageButton(context)
	private val searchNavigationIconView: AppCompatImageButton = AppCompatImageButton(context)

	/** Vertical cell of [pillContainer] holding [titleView]/[subtitleView] stacked above [queryEditText]; grows the pill's height on its own. */
	private val pillContentContainer: LinearLayout = LinearLayout(context)
	private val queryEditText: AppCompatEditText = AppCompatEditText(context)
	private val clearIconView: AppCompatImageButton = AppCompatImageButton(context)
	private val pillMenuRightView: LinearLayout = LinearLayout(context)
	private val pillMenuRightOverflowButton: AppCompatImageButton = AppCompatImageButton(context)

	// endregion


	// region Menu engine (shared by LEFT / RIGHT / PILL_LEFT / PILL_RIGHT) --

	/** [view] is either an AppCompatImageButton (item with an icon) or a text AppCompatButton (item without one). */
	private data class MenuButtonEntry(val item: MenuItem, val view: View)

	/**
	 * Everything a single menu slot needs to operate, grouped so the click/
	 * overflow/reactivity engine below can be written ONCE and reused by
	 * all four simultaneous slots -LEFT and RIGHT (outer, framing the pill)
	 * and PILL_LEFT/PILL_RIGHT (leading/trailing, inside the pill)- instead
	 * of quadruplicating every function.
	 */
	private class MenuSideState(
		val container: LinearLayout,
		val overflowButton: AppCompatImageButton
	) {
		var holder: PopupMenu? = null
		var entries: List<MenuButtonEntry> = emptyList()
		val forcedOverflowIds = mutableSetOf<Int>()
		var selectedItemId: Int = NO_ITEM_ID
		var longSelectedItemId: Int = NO_ITEM_ID
		var overflowPopup: PopupMenu? = null

		var clickListener: OnItemMenuClickListener? = null
		var longClickListener: OnItemMenuLongClickListener? = null
		var changedListener: OnItemMenuChangedListener? = null
		var longChangedListener: OnItemMenuLongChangedListener? = null
	}

	private val leftState = MenuSideState(leftMenuView, leftOverflowButton)
	private val rightState = MenuSideState(rightMenuView, rightOverflowButton)
	private val pillLeftState = MenuSideState(pillMenuLeftView, pillMenuLeftOverflowButton)
	private val pillRightState = MenuSideState(pillMenuRightView, pillMenuRightOverflowButton)

	private fun stateFor(side: MenuSide): MenuSideState = when (side) {
		MenuSide.LEFT -> leftState
		MenuSide.RIGHT -> rightState
		MenuSide.PILL_LEFT -> pillLeftState
		MenuSide.PILL_RIGHT -> pillRightState
	}

	/** Tint applied to ALL item icons (all three sides) and all overflow buttons; `null` keeps each drawable's own color. */
	private var menuIconTint: Int? = null

	// endregion


	private var onNavigationClickListener: OnNavigationClickListener? = null
	private var onNavigationLongClickListener: OnNavigationLongClickListener? = null
	private var onSearchNavigationClickListener: OnSearchNavigationClickListener? = null
	private var onSearchNavigationLongClickListener: OnSearchNavigationLongClickListener? = null
	private var onSearchActiveChangeListener: OnSearchActiveChangeListener? = null
	private var onQueryTextChangeListener: OnQueryTextChangeListener? = null
	private var onQueryTextSubmitListener: OnQueryTextSubmitListener? = null


	private val iconTouchTargetSize: Int = dpToPx(48)
	private val iconDrawablePadding: Int = dpToPx(12)
	private val rippleInset: Int = dpToPx(6)
	private val menuItemSpacing: Int = dpToPx(4)
	private val searchBarHeight: Int = dpToPx(56)
	private val minPillReserve: Int = dpToPx(120)
	private val titleBlockMarginBottom: Int = dpToPx(8)
	private val pillHorizontalPadding: Int = dpToPx(4)

	private var contentInsetStart: Int = dpToPx(16)
	private var contentInsetEnd: Int = dpToPx(16)

	private var titleAlignment: HorizontalAlignment = HorizontalAlignment.START
	private var subtitleAlignment: HorizontalAlignment = HorizontalAlignment.START

	private var pillBackgroundColor: Int? = null
	private var pillCornerRadius: Float? = null
	private var pillElevation: Float = dpToPx(2).toFloat()
	private var clearIconEnabled: Boolean = true

	init {
		clipToPadding = false
		clipChildren = false
		minimumHeight = searchBarHeight

		setupTitleView()
		setupSubtitleView()
		setupNavigationIconView()
		setupMenuContainer(leftMenuView)
		setupMenuContainer(rightMenuView)
		setupMenuContainer(pillMenuLeftView)
		setupMenuContainer(pillMenuRightView)
		setupOverflowButton(leftOverflowButton)
		setupOverflowButton(rightOverflowButton)
		setupOverflowButton(pillMenuLeftOverflowButton)
		setupOverflowButton(pillMenuRightOverflowButton)
		setupSearchNavigationIconView()
		setupQueryEditText()
		setupClearIconView()
		setupPillContentContainer()
		setupPillContainer()

		addView(navigationIconView)
		addView(leftMenuView)
		addView(pillContainer)
		addView(rightMenuView)

		navigationIconView.isClickable = false
		navigationIconView.isLongClickable = false
		navigationIconView.visibility = GONE

		updateTitleSubtitleVisibility()

		// Order matters: Material 3 theme defaults first, then any explicit
		// XML attributes -so XML always wins over the default, never the
		// other way around.
		applyMaterial3Defaults()
		attrs?.let { applyXmlAttributes(it, defStyleAttr) }
	}


	// region Child setup ---------------------------------------------------

	/** [titleView] lives inside [pillContentContainer], stacked above [subtitleView] and [queryEditText]. */
	private fun setupTitleView() {
		titleView.setSingleLine(true)
		titleView.ellipsize = TextUtils.TruncateAt.END
		titleView.gravity = Gravity.START or Gravity.CENTER_VERTICAL
		titleView.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
	}

	/**
	 * [subtitleView] lives inside [pillContentContainer], between [titleView]
	 * and [queryEditText]. Carries [titleBlockMarginBottom] as its own
	 * bottom margin -a GONE view never contributes margin, so this is a
	 * no-op whenever there's no subtitle to separate from the query field.
	 */
	private fun setupSubtitleView() {
		subtitleView.setSingleLine(true)
		subtitleView.ellipsize = TextUtils.TruncateAt.END
		subtitleView.gravity = Gravity.START or Gravity.CENTER_VERTICAL
		subtitleView.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
			bottomMargin = titleBlockMarginBottom
		}
	}

	private fun setupNavigationIconView() {
		navigationIconView.scaleType = ImageView.ScaleType.CENTER_INSIDE
		navigationIconView.background = createCompactRipple()
		navigationIconView.setPadding(iconDrawablePadding, iconDrawablePadding, iconDrawablePadding, iconDrawablePadding)
		navigationIconView.setOnClickListener { view -> onNavigationClickListener?.onNavigationClick(view) }
		navigationIconView.setOnLongClickListener { view -> onNavigationLongClickListener?.onNavigationLongClick(view) ?: false }
	}

	private fun setupMenuContainer(container: LinearLayout) {
		container.orientation = LinearLayout.HORIZONTAL
		container.gravity = Gravity.CENTER_VERTICAL
	}

	private fun setupOverflowButton(button: AppCompatImageButton) {
		button.scaleType = ImageView.ScaleType.CENTER_INSIDE
		button.background = createCompactRipple()
		button.setPadding(iconDrawablePadding, iconDrawablePadding, iconDrawablePadding, iconDrawablePadding)
		button.setImageDrawable(ThreeDotsDrawable(resolveDefaultIconTintColor()))
		button.contentDescription = "More options"
		button.layoutParams = LinearLayout.LayoutParams(iconTouchTargetSize, iconTouchTargetSize)
	}

	/**
	 * Whether the pill is showing the query field ([true], "active") or the
	 * title/subtitle heading ([false], "inactive", the default). Drives
	 * [titleView]/[subtitleView] vs [queryEditText] visibility and which of
	 * [searchInactiveIcon]/[searchActiveIcon] the search navigation icon
	 * shows -see [setSearchActive].
	 */
	private var searchActive: Boolean = false

	/** Icon shown while INACTIVE -defaults to a hand-drawn magnifier. `null` means "use the default". See [setSearchNavigationIcon]. */
	private var searchInactiveIcon: Drawable? = null

	/** Icon shown while ACTIVE -defaults to a hand-drawn back arrow. `null` means "use the default". See [setSearchNavigationActiveIcon]. */
	private var searchActiveIcon: Drawable? = null

	/**
	 * The leading icon INSIDE the pill, always visible per the M3 spec.
	 * Tapping it toggles [searchActive] -see [setSearchActive]- which is
	 * what swaps its own drawable between [searchInactiveIcon] (magnifier)
	 * and [searchActiveIcon] (back arrow), and what shows/hides the title/
	 * subtitle heading versus [queryEditText]. Always clickable on its own,
	 * regardless of whether [onSearchNavigationClickListener] is set.
	 */
	private fun setupSearchNavigationIconView() {
		searchNavigationIconView.scaleType = ImageView.ScaleType.CENTER_INSIDE
		searchNavigationIconView.background = createCompactRipple()
		searchNavigationIconView.setPadding(iconDrawablePadding, iconDrawablePadding, iconDrawablePadding, iconDrawablePadding)
		searchNavigationIconView.contentDescription = "Search"
		searchNavigationIconView.isClickable = true
		searchNavigationIconView.setOnClickListener { view ->
			setSearchActive(!searchActive)
			onSearchNavigationClickListener?.onSearchNavigationClick(view)
		}
		searchNavigationIconView.setOnLongClickListener { view -> onSearchNavigationLongClickListener?.onSearchNavigationLongClick(view) ?: false }
		searchNavigationIconView.layoutParams = LinearLayout.LayoutParams(iconTouchTargetSize, iconTouchTargetSize)
		applySearchNavigationIconForState()
	}

	/** Applies whichever of [searchInactiveIcon]/[searchActiveIcon] matches [searchActive] -falling back to the hand-drawn defaults. */
	private fun applySearchNavigationIconForState() {
		val icon = if (searchActive) {
			searchActiveIcon ?: BackArrowGlyphDrawable(resolveDefaultIconTintColor())
		} else {
			searchInactiveIcon ?: SearchGlyphDrawable(resolveDefaultIconTintColor())
		}
		searchNavigationIconView.setImageDrawable(icon)
	}

	/**
	 * Toggles the pill between its two mutually-exclusive contents:
	 * - **Inactive** (`active = false`, the default): [titleView]/
	 *   [subtitleView] are shown -centered vertically in the pill- and
	 *   [queryEditText] is hidden. The search navigation icon shows
	 *   [searchInactiveIcon] (a magnifier by default).
	 * - **Active** (`active = true`): the exact opposite -title/subtitle
	 *   hidden, [queryEditText] shown and focused. The search navigation
	 *   icon shows [searchActiveIcon] (a back arrow by default); tapping it
	 *   again deactivates the search field, calling this same function with
	 *   `false`.
	 *
	 * Toggled automatically by tapping the search navigation icon -see
	 * [setupSearchNavigationIconView]- but can also be driven
	 * programmatically, e.g. to activate the search field from an outer
	 * menu action.
	 */
	fun setSearchActive(active: Boolean) {
		if (searchActive == active) return
		searchActive = active
		applySearchNavigationIconForState()
		updateTitleSubtitleVisibility()
		queryEditText.visibility = if (active) VISIBLE else GONE
		if (active) {
			queryEditText.requestFocus()
			ContextCompat.getSystemService(context, InputMethodManager::class.java)?.showSoftInput(queryEditText, InputMethodManager.SHOW_IMPLICIT)
			clearIconView.visibility = if (queryEditText.text.isNullOrEmpty()) GONE else VISIBLE
		} else {
			queryEditText.clearFocus()
			ContextCompat.getSystemService(context, InputMethodManager::class.java)?.hideSoftInputFromWindow(queryEditText.windowToken, 0)
			clearIconView.visibility = GONE
		}
		requestLayout()
		onSearchActiveChangeListener?.onSearchActiveChange(active)
	}

	/** @see setSearchActive */
	fun isSearchActive(): Boolean = searchActive

	/** Invoked whenever [setSearchActive] actually changes state -e.g. to let a host container show/hide docked or expanded content. */
	fun setOnSearchActiveChangeListener(listener: OnSearchActiveChangeListener?) { onSearchActiveChangeListener = listener }

	/**
	 * Replaces the default hand-drawn back arrow shown while ACTIVE -tapping
	 * it deactivates the search field, see [setSearchActive].
	 */
	fun setSearchNavigationActiveIcon(icon: Drawable?) {
		searchActiveIcon = icon
		if (searchActive) applySearchNavigationIconForState()
	}

	/** @see setSearchNavigationActiveIcon */
	fun setSearchNavigationActiveIcon(@DrawableRes iconRes: Int) = setSearchNavigationActiveIcon(ContextCompat.getDrawable(context, iconRes))

	/** Returns the custom ACTIVE icon, or `null` if the default back arrow is in use. */
	fun getSearchNavigationActiveIcon(): Drawable? = searchActiveIcon

	private fun setupQueryEditText() {
		queryEditText.background = null
		queryEditText.setSingleLine(true)
		queryEditText.imeOptions = EditorInfo.IME_ACTION_SEARCH
		queryEditText.inputType = InputType.TYPE_CLASS_TEXT
		queryEditText.setPadding(dpToPx(4), 0, dpToPx(4), 0)
		queryEditText.hint = "Search…"
		// Hidden until the search navigation icon is tapped -see
		// setSearchActive- so a fresh SearchBar reads as the title/subtitle
		// heading, not as an already-active search field.
		queryEditText.visibility = GONE

		queryEditText.addTextChangedListener(object : TextWatcher {
			override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
			override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
			override fun afterTextChanged(editable: Editable?) {
				updateClearIconVisibility()
				onQueryTextChangeListener?.onQueryTextChange(editable ?: "")
			}
		})

		queryEditText.setOnEditorActionListener { _, actionId, event ->
			val isSubmit = actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE ||
					(event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
			if (isSubmit) onQueryTextSubmitListener?.onQueryTextSubmit(queryEditText.text ?: "") ?: false else false
		}

		queryEditText.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
	}

	/** Auto-shown only while there is query text -see [updateClearIconVisibility]. */
	private fun setupClearIconView() {
		clearIconView.scaleType = ImageView.ScaleType.CENTER_INSIDE
		clearIconView.background = createCompactRipple()
		clearIconView.setPadding(iconDrawablePadding, iconDrawablePadding, iconDrawablePadding, iconDrawablePadding)
		clearIconView.contentDescription = "Clear search text"
		clearIconView.setImageDrawable(ClearGlyphDrawable(resolveDefaultIconTintColor()))
		clearIconView.setOnClickListener { clearQuery() }
		clearIconView.visibility = GONE
		clearIconView.layoutParams = LinearLayout.LayoutParams(iconTouchTargetSize, iconTouchTargetSize)
	}

	/**
	 * Vertical cell that stacks [titleView] and [subtitleView] above
	 * [queryEditText] -this is what puts the title/subtitle block INSIDE
	 * the pill instead of above it. Being a plain [LinearLayout], its
	 * `wrap_content` height is exactly what lets the pill grow taller on
	 * its own whenever a title or subtitle is set -see [layoutPill].
	 */
	private fun setupPillContentContainer() {
		pillContentContainer.orientation = LinearLayout.VERTICAL
		pillContentContainer.gravity = Gravity.CENTER_VERTICAL
		pillContentContainer.addView(titleView)
		pillContentContainer.addView(subtitleView)
		pillContentContainer.addView(queryEditText)
		pillContentContainer.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
	}

	private fun setupPillContainer() {
		pillContainer.orientation = LinearLayout.HORIZONTAL
		pillContainer.gravity = Gravity.CENTER_VERTICAL
		pillContainer.clipToPadding = false
		pillContainer.clipChildren = false
		pillContainer.setPadding(pillHorizontalPadding, 0, pillHorizontalPadding, 0)
		pillContainer.background = createPillBackground()
		ViewCompat.setElevation(pillContainer, pillElevation)

		pillContainer.addView(pillMenuLeftView)
		pillContainer.addView(searchNavigationIconView)
		pillContainer.addView(pillContentContainer)
		pillContainer.addView(clearIconView)
		pillContainer.addView(pillMenuRightView)
	}

	private fun createPillBackground(): Drawable = GradientDrawable().apply {
		shape = GradientDrawable.RECTANGLE
		cornerRadius = pillCornerRadius ?: (searchBarHeight / 2f)
		setColor(
			pillBackgroundColor ?: resolveThemeColor(
				com.google.android.material.R.attr.colorSurfaceContainerHigh,
				resolveThemeColor(com.google.android.material.R.attr.colorSurfaceVariant, Color.LTGRAY)
			)
		)
	}

	private fun refreshPillBackground() {
		pillContainer.background = createPillBackground()
	}

	// endregion


	// region Outer navigation icon (public API) -----------------------------

	/** Sets the OUTER navigation icon drawable (outside the pill), or `null` to hide it. */
	fun setNavigationIcon(icon: Drawable?) {
		navigationIconView.setImageDrawable(icon)
		navigationIconView.visibility = if (icon != null) VISIBLE else GONE
		requestLayout()
	}

	/** @see setNavigationIcon */
	fun setNavigationIcon(@DrawableRes iconRes: Int) = setNavigationIcon(ContextCompat.getDrawable(context, iconRes))

	/** Returns the current OUTER navigation icon drawable, or `null` if none is set. */
	fun getNavigationIcon(): Drawable? = navigationIconView.drawable

	/** Tints the OUTER navigation icon with a single color. */
	fun setNavigationIconTint(@ColorInt color: Int) {
		ImageViewCompat.setImageTintList(navigationIconView, ColorStateList.valueOf(color))
	}

	/** Tints the OUTER navigation icon with a full [ColorStateList]. */
	fun setNavigationIconTintList(tint: ColorStateList?) {
		ImageViewCompat.setImageTintList(navigationIconView, tint)
	}

	/** Sets the accessibility content description and tooltip text for the OUTER navigation icon. */
	fun setNavigationContentDescription(description: CharSequence?) {
		navigationIconView.contentDescription = description
		if (!description.isNullOrEmpty()) TooltipCompat.setTooltipText(navigationIconView, description)
	}

	/** @see setNavigationContentDescription */
	fun setNavigationContentDescription(@StringRes resId: Int) = setNavigationContentDescription(context.getString(resId))

	/** The view only becomes clickable once both a listener and an icon are set. */
	fun setNavigationOnClickListener(listener: OnNavigationClickListener?) {
		onNavigationClickListener = listener
		navigationIconView.isClickable = listener != null && navigationIconView.drawable != null
	}

	/** The view only becomes long-clickable once both a listener and an icon are set. */
	fun setNavigationOnLongClickListener(listener: OnNavigationLongClickListener?) {
		onNavigationLongClickListener = listener
		navigationIconView.isLongClickable = listener != null && navigationIconView.drawable != null
	}

	// endregion


	// region Inner search navigation icon (public API) -----------------------
	// Independent from the OUTER navigation icon above: separate drawable,
	// separate tint, separate click channel. This is what lets you keep a
	// hamburger OUTSIDE the pill while swapping the magnifier INSIDE the
	// pill for a back arrow once the search becomes "active", without the
	// two ever fighting over the same state.

	/** Sets the INNER search navigation icon (leading icon inside the pill). Always visible per the M3 spec -pass `null` to fall back to the default magnifier. */
	fun setSearchNavigationIcon(icon: Drawable?) {
		searchNavigationIconView.setImageDrawable(icon ?: SearchGlyphDrawable(resolveDefaultIconTintColor()))
		requestLayout()
	}

	/** @see setSearchNavigationIcon */
	fun setSearchNavigationIcon(@DrawableRes iconRes: Int) = setSearchNavigationIcon(ContextCompat.getDrawable(context, iconRes))

	/** Returns the current INNER search navigation icon drawable. */
	fun getSearchNavigationIcon(): Drawable? = searchNavigationIconView.drawable

	/** Tints the INNER search navigation icon with a single color. */
	fun setSearchNavigationIconTint(@ColorInt color: Int) {
		ImageViewCompat.setImageTintList(searchNavigationIconView, ColorStateList.valueOf(color))
	}

	/** Tints the INNER search navigation icon with a full [ColorStateList]. */
	fun setSearchNavigationIconTintList(tint: ColorStateList?) {
		ImageViewCompat.setImageTintList(searchNavigationIconView, tint)
	}

	/** Sets the accessibility content description and tooltip text for the INNER search navigation icon. */
	fun setSearchNavigationContentDescription(description: CharSequence?) {
		searchNavigationIconView.contentDescription = description
		if (!description.isNullOrEmpty()) TooltipCompat.setTooltipText(searchNavigationIconView, description)
	}

	/** @see setSearchNavigationContentDescription */
	fun setSearchNavigationContentDescription(@StringRes resId: Int) = setSearchNavigationContentDescription(context.getString(resId))

	/** The INNER icon is always clickable on its own -see [setSearchActive]- independently of whether a [listener] is assigned. */
	fun setSearchNavigationOnClickListener(listener: OnSearchNavigationClickListener?) {
		onSearchNavigationClickListener = listener
	}

	/** The INNER icon only becomes long-clickable once a listener is assigned. */
	fun setSearchNavigationOnLongClickListener(listener: OnSearchNavigationLongClickListener?) {
		onSearchNavigationLongClickListener = listener
		searchNavigationIconView.isLongClickable = listener != null
	}

	// endregion


	// region Query field (public API) ---------------------------------------

	/** Sets the query text; pass `submit = true` to also fire [OnQueryTextSubmitListener] right away. */
	fun setQuery(text: CharSequence?, submit: Boolean = false) {
		queryEditText.setText(text)
		queryEditText.setSelection(queryEditText.text?.length ?: 0)
		if (submit) onQueryTextSubmitListener?.onQueryTextSubmit(text ?: "")
	}

	/** Returns the current query text. */
	fun getQuery(): CharSequence = queryEditText.text ?: ""

	/** Clears the query text (also triggered by tapping the clear icon). */
	fun clearQuery() = queryEditText.setText("")

	/**
	 * Sets the placeholder shown while the query field is empty. The field
	 * itself is only visible while [searchActive] -see [setSearchActive]-
	 * so setting a hint doesn't make anything appear on its own.
	 */
	fun setQueryHint(hint: CharSequence?) { queryEditText.hint = hint }

	/** @see setQueryHint */
	fun setQueryHint(@StringRes resId: Int) = setQueryHint(context.getString(resId))

	/** Returns the current placeholder text. */
	fun getQueryHint(): CharSequence? = queryEditText.hint

	/** Sets the query field's text color. */
	fun setQueryTextColor(@ColorInt color: Int) = queryEditText.setTextColor(color)
	/** Sets the query field's text size, in SP. */
	fun setQueryTextSize(sizeSp: Float) = queryEditText.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
	/** Sets the query field's typeface and style. */
	fun setQueryTypeface(typeface: Typeface?, style: Int = Typeface.NORMAL) = queryEditText.setTypeface(typeface, style)
	/** Applies a text appearance style resource to the query field. */
	fun setQueryTextAppearance(@StyleRes resId: Int) = TextViewCompat.setTextAppearance(queryEditText, resId)

	fun setOnQueryTextChangeListener(listener: OnQueryTextChangeListener?) { onQueryTextChangeListener = listener }
	fun setOnQueryTextSubmitListener(listener: OnQueryTextSubmitListener?) { onQueryTextSubmitListener = listener }

	/**
	 * Direct reference to the real query [AppCompatEditText] living inside
	 * the pill. Exists so a host component -e.g. `ComponentSearchView`- can
	 * attach its own focus listener, read its on-screen position to anchor a
	 * docked dropdown, or otherwise integrate deeply with the SAME field the
	 * user is typing into, instead of creating a second, disconnected one.
	 * Prefer [getQuery]/[setQuery]/[setOnQueryTextChangeListener] for
	 * anything that only needs the text itself.
	 */
	fun getQueryEditText(): AppCompatEditText = queryEditText

	/** Requests input focus on the query field and opens the keyboard. */
	fun requestSearchFocus() {
		queryEditText.requestFocus()
		val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
		imm?.showSoftInput(queryEditText, InputMethodManager.SHOW_IMPLICIT)
	}

	/** Clears input focus from the query field and hides the keyboard. */
	fun clearSearchFocus() {
		val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
		imm?.hideSoftInputFromWindow(windowToken, 0)
		queryEditText.clearFocus()
	}

	/** Whether the built-in "x" icon should appear inside the pill while there is query text. `true` by default. */
	fun setClearIconEnabled(enabled: Boolean) {
		clearIconEnabled = enabled
		updateClearIconVisibility()
	}

	/** Replaces the default "x" clear icon drawable. */
	fun setClearIcon(icon: Drawable?) { clearIconView.setImageDrawable(icon ?: ClearGlyphDrawable(resolveDefaultIconTintColor())) }

	/** Tints the clear icon. */
	fun setClearIconTint(@ColorInt color: Int) { ImageViewCompat.setImageTintList(clearIconView, ColorStateList.valueOf(color)) }

	private fun updateClearIconVisibility() {
		clearIconView.visibility = if (clearIconEnabled && !queryEditText.text.isNullOrEmpty()) VISIBLE else GONE
	}

	// endregion


	// region Title / subtitle (public API) -----------------------------------
	// Rendered as an optional block INSIDE the pill, stacked right above the
	// query field -a page heading sitting where the user is about to type.
	// Same per-line HorizontalAlignment ComponentToolbar offers, each
	// independent from the other. See pillContentContainer for how the
	// stacking itself works.

	/** Sets the title text, or `null`/empty to hide it. Hidden regardless while [searchActive] -see [setSearchActive]. */
	fun setTitle(title: CharSequence?) {
		titleView.text = title
		updateTitleSubtitleVisibility()
		requestLayout()
	}

	/** @see setTitle */
	fun setTitle(@StringRes resId: Int) = setTitle(context.getString(resId))
	/** Returns the current title text, or `null` if none is set. */
	fun getTitle(): CharSequence? = titleView.text
	/** Sets the title's text color. */
	fun setTitleTextColor(@ColorInt color: Int) = titleView.setTextColor(color)
	/** Sets the title's text color from a [ColorStateList]. */
	fun setTitleTextColor(colors: ColorStateList) = titleView.setTextColor(colors)
	/** Sets the title's text size, in SP. */
	fun setTitleTextSize(sizeSp: Float) = titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
	/** Sets the title's typeface and style. */
	fun setTitleTypeface(typeface: Typeface?, style: Int = Typeface.NORMAL) = titleView.setTypeface(typeface, style)
	/** Applies a text appearance style resource to the title. */
	fun setTitleTextAppearance(@StyleRes resId: Int) = TextViewCompat.setTextAppearance(titleView, resId)
	/** Title alignment: START (default), CENTER or END. Independent from the subtitle's. */
	fun setTitleAlignment(alignment: HorizontalAlignment) {
		titleAlignment = alignment
		titleView.gravity = resolveGravity(alignment) or Gravity.CENTER_VERTICAL
	}
	/** Returns the current title alignment. */
	fun getTitleAlignment(): HorizontalAlignment = titleAlignment

	/** Sets the subtitle text, or `null`/empty to hide it. Hidden regardless while [searchActive] -see [setSearchActive]. */
	fun setSubtitle(subtitle: CharSequence?) {
		subtitleView.text = subtitle
		updateTitleSubtitleVisibility()
		requestLayout()
	}

	/** Applies [searchActive] on top of whether each TextView actually has text -both must hold for it to be VISIBLE. */
	private fun updateTitleSubtitleVisibility() {
		titleView.visibility = if (!searchActive && !titleView.text.isNullOrEmpty()) VISIBLE else GONE
		subtitleView.visibility = if (!searchActive && !subtitleView.text.isNullOrEmpty()) VISIBLE else GONE
	}

	/** @see setSubtitle */
	fun setSubtitle(@StringRes resId: Int) = setSubtitle(context.getString(resId))
	/** Returns the current subtitle text, or `null` if none is set. */
	fun getSubtitle(): CharSequence? = subtitleView.text
	/** Sets the subtitle's text color. */
	fun setSubtitleTextColor(@ColorInt color: Int) = subtitleView.setTextColor(color)
	/** Sets the subtitle's text color from a [ColorStateList]. */
	fun setSubtitleTextColor(colors: ColorStateList) = subtitleView.setTextColor(colors)
	/** Sets the subtitle's text size, in SP. */
	fun setSubtitleTextSize(sizeSp: Float) = subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
	/** Sets the subtitle's typeface and style. */
	fun setSubtitleTypeface(typeface: Typeface?, style: Int = Typeface.NORMAL) = subtitleView.setTypeface(typeface, style)
	/** Applies a text appearance style resource to the subtitle. */
	fun setSubtitleTextAppearance(@StyleRes resId: Int) = TextViewCompat.setTextAppearance(subtitleView, resId)
	/** Subtitle alignment: START (default), CENTER or END. Independent from the title's. */
	fun setSubtitleAlignment(alignment: HorizontalAlignment) {
		subtitleAlignment = alignment
		subtitleView.gravity = resolveGravity(alignment) or Gravity.CENTER_VERTICAL
	}
	/** Returns the current subtitle alignment. */
	fun getSubtitleAlignment(): HorizontalAlignment = subtitleAlignment

	// endregion


	// region Pill appearance (public API) ------------------------------------

	/** Overrides the pill's fill color; `null` restores the theme-derived M3 default (`colorSurfaceContainerHigh`). */
	fun setPillBackgroundColor(@ColorInt color: Int?) { pillBackgroundColor = color; refreshPillBackground() }
	/** Overrides the pill's corner radius -`null` restores the default, fully-rounded M3 pill. Used by `ComponentSearchView` to "flatten" it in its EXPANDABLE mode. */
	fun setPillCornerRadius(@Px radiusPx: Float?) { pillCornerRadius = radiusPx; refreshPillBackground() }

	/**
	 * The corner radius explicitly set via [setPillCornerRadius], or `null`
	 * if the pill is still using its automatic default (`searchBarHeight /
	 * 2`, a full stadium shape). Exists so a host component can snapshot
	 * this value before flattening the pill (e.g. for an EXPANDABLE search
	 * surface) and restore it exactly when collapsing back.
	 */
	fun getPillCornerRadius(): Float? = pillCornerRadius

	/** Sets the pill's elevation, in pixels. */
	fun setPillElevation(@Px elevation: Float) { pillElevation = elevation; ViewCompat.setElevation(pillContainer, elevation) }

	// endregion


	/** Sets the leading inset applied before the OUTER left content, in pixels. */
	fun setContentInsetStartWithNavigation(@Px inset: Int) { contentInsetStart = inset; requestLayout() }
	/** Sets the trailing inset applied after the OUTER right content, in pixels. */
	fun setContentInsetEnd(@Px inset: Int) { contentInsetEnd = inset; requestLayout() }
	fun getContentInsetStart(): Int = contentInsetStart
	fun getContentInsetEnd(): Int = contentInsetEnd


	// region Menu engine (public API) -----------------------------------------
	// Every function below is a thin, side-specific wrapper over the single
	// generic engine in the "Menu engine (internal)" region -see
	// MenuSideState. LEFT/RIGHT sit OUTSIDE the pill (framing it, exactly
	// like ComponentToolbar); PILL_LEFT/PILL_RIGHT sit INSIDE the pill,
	// leading and trailing the query field respectively.

	/** Inflates [menuRes] into [side], replacing whatever was there before. */
	fun inflateMenu(side: MenuSide, @MenuRes menuRes: Int) {
		val state = stateFor(side)
		val popup = PopupMenu(context, state.container)
		popup.menuInflater.inflate(menuRes, popup.menu)
		state.holder = popup
		state.entries = buildEntries(state, popup.menu)
		populateMenuSide(state, popup.menu)
		requestLayout()
	}

	/** @see inflateMenu */
	fun inflateLeftMenu(@MenuRes menuRes: Int) = inflateMenu(MenuSide.LEFT, menuRes)
	/** @see inflateMenu */
	fun inflateRightMenu(@MenuRes menuRes: Int) = inflateMenu(MenuSide.RIGHT, menuRes)
	/** @see inflateMenu -this is the PILL_LEFT slot, inside the pill, leading the query field. */
	fun inflatePillLeftMenu(@MenuRes menuRes: Int) = inflateMenu(MenuSide.PILL_LEFT, menuRes)
	/** @see inflateMenu -this is the PILL_RIGHT slot, inside the pill, trailing the query field. */
	fun inflatePillRightMenu(@MenuRes menuRes: Int) = inflateMenu(MenuSide.PILL_RIGHT, menuRes)

	/** Returns a reactive [Menu] for [side] -see the "MenuItem reactivity" region- or `null` if nothing was inflated yet. */
	fun getMenu(side: MenuSide): Menu? {
		val state = stateFor(side)
		return state.holder?.menu?.let { ReactiveMenu(it) { item -> wrapReactive(state, item) } }
	}

	fun getLeftMenu(): Menu? = getMenu(MenuSide.LEFT)
	fun getRightMenu(): Menu? = getMenu(MenuSide.RIGHT)
	fun getPillLeftMenu(): Menu? = getMenu(MenuSide.PILL_LEFT)
	fun getPillRightMenu(): Menu? = getMenu(MenuSide.PILL_RIGHT)

	/** Returns the currently selected item (reactive) for [side], or `null` if nothing is selected. */
	fun getSelectedMenuItem(side: MenuSide): MenuItem? {
		val state = stateFor(side)
		return state.holder?.menu?.findItem(state.selectedItemId)?.let { wrapReactive(state, it) }
	}

	fun getSelectedLeftMenuItem(): MenuItem? = getSelectedMenuItem(MenuSide.LEFT)
	fun getSelectedRightMenuItem(): MenuItem? = getSelectedMenuItem(MenuSide.RIGHT)
	fun getSelectedPillLeftMenuItem(): MenuItem? = getSelectedMenuItem(MenuSide.PILL_LEFT)
	fun getSelectedPillRightMenuItem(): MenuItem? = getSelectedMenuItem(MenuSide.PILL_RIGHT)

	/** Removes all items from [side] and resets its selection state. */
	fun clearMenu(side: MenuSide) {
		val state = stateFor(side)
		state.holder?.menu?.clear()
		state.entries = emptyList()
		state.selectedItemId = NO_ITEM_ID
		populateMenuSide(state, null)
		requestLayout()
	}

	fun clearLeftMenu() = clearMenu(MenuSide.LEFT)
	fun clearRightMenu() = clearMenu(MenuSide.RIGHT)
	fun clearPillLeftMenu() = clearMenu(MenuSide.PILL_LEFT)
	fun clearPillRightMenu() = clearMenu(MenuSide.PILL_RIGHT)

	/** Rebuilds the buttons of [side] from the Menu's current state (visibility/enabled/icon mutated externally). */
	fun refreshMenu(side: MenuSide) {
		val state = stateFor(side)
		val menu = state.holder?.menu ?: return
		state.entries = buildEntries(state, menu)
		populateMenuSide(state, menu)
		requestLayout()
	}

	fun refreshLeftMenu() = refreshMenu(MenuSide.LEFT)
	fun refreshRightMenu() = refreshMenu(MenuSide.RIGHT)
	fun refreshPillLeftMenu() = refreshMenu(MenuSide.PILL_LEFT)
	fun refreshPillRightMenu() = refreshMenu(MenuSide.PILL_RIGHT)

	/** Forces the item with [itemId] to ALWAYS live in [side]'s overflow, regardless of `showAsAction`. */
	fun setItemAlwaysInOverflow(side: MenuSide, itemId: Int, alwaysInOverflow: Boolean) {
		val state = stateFor(side)
		if (alwaysInOverflow) state.forcedOverflowIds.add(itemId) else state.forcedOverflowIds.remove(itemId)
		populateMenuSide(state, state.holder?.menu)
		requestLayout()
	}

	fun setLeftItemAlwaysInOverflow(itemId: Int, alwaysInOverflow: Boolean) = setItemAlwaysInOverflow(MenuSide.LEFT, itemId, alwaysInOverflow)
	fun setRightItemAlwaysInOverflow(itemId: Int, alwaysInOverflow: Boolean) = setItemAlwaysInOverflow(MenuSide.RIGHT, itemId, alwaysInOverflow)
	fun setPillLeftItemAlwaysInOverflow(itemId: Int, alwaysInOverflow: Boolean) = setItemAlwaysInOverflow(MenuSide.PILL_LEFT, itemId, alwaysInOverflow)
	fun setPillRightItemAlwaysInOverflow(itemId: Int, alwaysInOverflow: Boolean) = setItemAlwaysInOverflow(MenuSide.PILL_RIGHT, itemId, alwaysInOverflow)

	fun setOnItemMenuClickListener(side: MenuSide, listener: OnItemMenuClickListener?) { stateFor(side).clickListener = listener }
	fun setOnItemMenuLongClickListener(side: MenuSide, listener: OnItemMenuLongClickListener?) { stateFor(side).longClickListener = listener }
	fun setOnItemMenuChangedListener(side: MenuSide, listener: OnItemMenuChangedListener?) { stateFor(side).changedListener = listener }
	fun setOnItemMenuLongChangedListener(side: MenuSide, listener: OnItemMenuLongChangedListener?) { stateFor(side).longChangedListener = listener }

	fun setOnLeftItemMenuClickListener(listener: OnItemMenuClickListener?) = setOnItemMenuClickListener(MenuSide.LEFT, listener)
	fun setOnLeftItemMenuLongClickListener(listener: OnItemMenuLongClickListener?) = setOnItemMenuLongClickListener(MenuSide.LEFT, listener)
	fun setOnLeftItemMenuChangedListener(listener: OnItemMenuChangedListener?) = setOnItemMenuChangedListener(MenuSide.LEFT, listener)
	fun setOnLeftItemMenuLongChangedListener(listener: OnItemMenuLongChangedListener?) = setOnItemMenuLongChangedListener(MenuSide.LEFT, listener)

	fun setOnRightItemMenuClickListener(listener: OnItemMenuClickListener?) = setOnItemMenuClickListener(MenuSide.RIGHT, listener)
	fun setOnRightItemMenuLongClickListener(listener: OnItemMenuLongClickListener?) = setOnItemMenuLongClickListener(MenuSide.RIGHT, listener)
	fun setOnRightItemMenuChangedListener(listener: OnItemMenuChangedListener?) = setOnItemMenuChangedListener(MenuSide.RIGHT, listener)
	fun setOnRightItemMenuLongChangedListener(listener: OnItemMenuLongChangedListener?) = setOnItemMenuLongChangedListener(MenuSide.RIGHT, listener)

	/** @see setOnItemMenuClickListener -this is the PILL_LEFT slot, inside the pill, leading the query field. */
	fun setOnPillLeftItemMenuClickListener(listener: OnItemMenuClickListener?) = setOnItemMenuClickListener(MenuSide.PILL_LEFT, listener)
	fun setOnPillLeftItemMenuLongClickListener(listener: OnItemMenuLongClickListener?) = setOnItemMenuLongClickListener(MenuSide.PILL_LEFT, listener)
	fun setOnPillLeftItemMenuChangedListener(listener: OnItemMenuChangedListener?) = setOnItemMenuChangedListener(MenuSide.PILL_LEFT, listener)
	fun setOnPillLeftItemMenuLongChangedListener(listener: OnItemMenuLongChangedListener?) = setOnItemMenuLongChangedListener(MenuSide.PILL_LEFT, listener)

	/** @see setOnItemMenuClickListener -this is the PILL_RIGHT slot, inside the pill, trailing the query field. */
	fun setOnPillRightItemMenuClickListener(listener: OnItemMenuClickListener?) = setOnItemMenuClickListener(MenuSide.PILL_RIGHT, listener)
	fun setOnPillRightItemMenuLongClickListener(listener: OnItemMenuLongClickListener?) = setOnItemMenuLongClickListener(MenuSide.PILL_RIGHT, listener)
	fun setOnPillRightItemMenuChangedListener(listener: OnItemMenuChangedListener?) = setOnItemMenuChangedListener(MenuSide.PILL_RIGHT, listener)
	fun setOnPillRightItemMenuLongChangedListener(listener: OnItemMenuLongChangedListener?) = setOnItemMenuLongChangedListener(MenuSide.PILL_RIGHT, listener)

	/** Replaces the "⋮" overflow icon for [side]. */
	fun setOverflowIcon(side: MenuSide, icon: Drawable?) { stateFor(side).overflowButton.setImageDrawable(icon) }
	/** @see setOverflowIcon */
	fun setOverflowIcon(side: MenuSide, @DrawableRes iconRes: Int) = setOverflowIcon(side, ContextCompat.getDrawable(context, iconRes))

	fun setLeftOverflowIcon(icon: Drawable?) = setOverflowIcon(MenuSide.LEFT, icon)
	fun setLeftOverflowIcon(@DrawableRes iconRes: Int) = setOverflowIcon(MenuSide.LEFT, iconRes)
	fun setRightOverflowIcon(icon: Drawable?) = setOverflowIcon(MenuSide.RIGHT, icon)
	fun setRightOverflowIcon(@DrawableRes iconRes: Int) = setOverflowIcon(MenuSide.RIGHT, iconRes)
	fun setPillLeftOverflowIcon(icon: Drawable?) = setOverflowIcon(MenuSide.PILL_LEFT, icon)
	fun setPillLeftOverflowIcon(@DrawableRes iconRes: Int) = setOverflowIcon(MenuSide.PILL_LEFT, iconRes)
	fun setPillRightOverflowIcon(icon: Drawable?) = setOverflowIcon(MenuSide.PILL_RIGHT, icon)
	fun setPillRightOverflowIcon(@DrawableRes iconRes: Int) = setOverflowIcon(MenuSide.PILL_RIGHT, iconRes)

	/** Tint applied to ALL item icons (all four sides) and every overflow button. `null` keeps each drawable's own color. */
	fun setMenuIconTint(@ColorInt color: Int?) {
		menuIconTint = color
		refreshLeftMenu()
		refreshRightMenu()
		refreshPillLeftMenu()
		refreshPillRightMenu()
		applyOverflowButtonTint()
	}

	// endregion


	// region Menu engine (internal) -------------------------------------------

	// android.view.MenuItem never notifies anyone when it changes -there is
	// no listener for "my icon/title/showAsAction just changed"- so every
	// item is wrapped in a proxy that, besides delegating everything to the
	// real item, triggers a refresh of its OWN side whenever a setter that
	// can affect what's on screen is called. This is what buildEntries()/
	// getMenu() hand out -never the raw MenuItem.

	private fun wrapReactive(state: MenuSideState, item: MenuItem): MenuItem =
		ReactiveMenuItem(item) { refreshMenuFor(state) }

	private fun refreshMenuFor(state: MenuSideState) {
		val menu = state.holder?.menu ?: return
		state.entries = buildEntries(state, menu)
		populateMenuSide(state, menu)
		requestLayout()
	}

	private class ReactiveMenuItem(
		private val delegate: MenuItem,
		private val onChanged: () -> Unit
	) : MenuItem by delegate {
		override fun setIcon(icon: Drawable?): MenuItem { delegate.icon = icon; onChanged(); return this }
		override fun setIcon(iconRes: Int): MenuItem { delegate.setIcon(iconRes); onChanged(); return this }
		override fun setTitle(title: CharSequence?): MenuItem { delegate.title = title; onChanged(); return this }
		override fun setTitle(titleRes: Int): MenuItem { delegate.setTitle(titleRes); onChanged(); return this }
		override fun setTitleCondensed(title: CharSequence?): MenuItem { delegate.titleCondensed = title; onChanged(); return this }
		override fun setShowAsAction(actionEnum: Int) { delegate.setShowAsAction(actionEnum); onChanged() }
		override fun setShowAsActionFlags(actionEnum: Int): MenuItem { delegate.setShowAsActionFlags(actionEnum); onChanged(); return this }
		override fun setVisible(visible: Boolean): MenuItem { delegate.isVisible = visible; onChanged(); return this }
		override fun setEnabled(enabled: Boolean): MenuItem { delegate.isEnabled = enabled; onChanged(); return this }

		/** The real, unwrapped item -needed by [isShowAsActionAlways] to cast to MenuItemImpl. */
		fun unwrap(): MenuItem = delegate
	}

	/** Wraps an entire [Menu] so that the items it hands out (getItem/findItem/add) are already reactive. */
	private class ReactiveMenu(
		private val delegate: Menu,
		private val onChanged: (MenuItem) -> MenuItem
	) : Menu by delegate {
		override fun getItem(index: Int): MenuItem = onChanged(delegate.getItem(index))
		override fun findItem(id: Int): MenuItem? = delegate.findItem(id)?.let(onChanged)
		override fun add(title: CharSequence?): MenuItem = onChanged(delegate.add(title))
		override fun add(titleRes: Int): MenuItem = onChanged(delegate.add(titleRes))
		override fun add(groupId: Int, itemId: Int, order: Int, title: CharSequence?): MenuItem =
			onChanged(delegate.add(groupId, itemId, order, title))
		override fun add(groupId: Int, itemId: Int, order: Int, titleRes: Int): MenuItem =
			onChanged(delegate.add(groupId, itemId, order, titleRes))
	}

	/**
	 * Checks whether [item] was inflated with `app:showAsAction="always"".
	 * See `ComponentToolbar.isShowAsActionAlways` for the full rationale
	 * behind the [MenuItemImpl] cast and its try/catch safety net.
	 */
	private fun isShowAsActionAlways(item: MenuItem): Boolean {
		val realItem = (item as? ReactiveMenuItem)?.unwrap() ?: item
		return try {
			(realItem as? MenuItemImpl)?.requiresActionButton() == true
		} catch (error: Throwable) {
			false
		}
	}

	private fun buildEntries(state: MenuSideState, menu: Menu): List<MenuButtonEntry> {
		val entries = mutableListOf<MenuButtonEntry>()
		for (index in 0 until menu.size()) {
			val rawItem = menu.getItem(index)
			if (!rawItem.isVisible) continue
			val item = wrapReactive(state, rawItem)

			val view = createEntryView(item)

			if (item.hasSubMenu()) {
				view.setOnClickListener { showItemSubMenu(state, view, item) }
			} else {
				view.setOnClickListener { handleMenuItemClicked(state, menu, item) }
			}
			view.setOnLongClickListener { handleMenuItemLongClicked(state, menu, item) }

			entries.add(MenuButtonEntry(item, view))
		}
		return entries
	}

	/** Reveals a nested `<menu>` of [item] as a [PopupMenu] anchored to [anchor] -see `ComponentToolbar.showItemSubMenu`. */
	private fun showItemSubMenu(state: MenuSideState, anchor: View, item: MenuItem) {
		val subMenu = item.subMenu ?: return
		val popup = PopupMenu(context, anchor)
		for (index in 0 until subMenu.size()) {
			val subItem = subMenu.getItem(index)
			popup.menu.add(Menu.NONE, subItem.itemId, index, subItem.title)
				.setIcon(subItem.icon)
				.setEnabled(subItem.isEnabled)
		}
		popup.setOnMenuItemClickListener { proxyItem ->
			val realSubItem = subMenu.findItem(proxyItem.itemId) ?: return@setOnMenuItemClickListener false
			state.clickListener?.onMenuItemClicked(wrapReactive(state, realSubItem))
			true
		}
		popup.show()
	}

	/** Uses the item's icon when present; otherwise falls back to a text button with its title. */
	private fun createEntryView(item: MenuItem): View {
		return if (item.icon != null) {
			AppCompatImageButton(context).apply {
				scaleType = ImageView.ScaleType.CENTER_INSIDE
				background = createCompactRipple()
				setPadding(iconDrawablePadding, iconDrawablePadding, iconDrawablePadding, iconDrawablePadding)
				setImageDrawable(item.icon)
				contentDescription = item.title
				isEnabled = item.isEnabled
				alpha = if (item.isEnabled) ENABLED_ALPHA else DISABLED_ALPHA
				if (!item.title.isNullOrEmpty()) TooltipCompat.setTooltipText(this, item.title)
				layoutParams = LinearLayout.LayoutParams(iconTouchTargetSize, iconTouchTargetSize)
				menuIconTint?.let { ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(it)) }
			}
		} else {
			AppCompatButton(context, null, android.R.attr.borderlessButtonStyle).apply {
				text = item.title
				isAllCaps = false
				background = createCompactRipple()
				setPadding(dpToPx(12), 0, dpToPx(12), 0)
				minWidth = 0
				minimumWidth = 0
				isEnabled = item.isEnabled
				alpha = if (item.isEnabled) ENABLED_ALPHA else DISABLED_ALPHA
				menuIconTint?.let { setTextColor(it) }
				layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, iconTouchTargetSize)
			}
		}
	}

	private fun applyOverflowButtonTint() {
		val color = menuIconTint ?: resolveDefaultIconTintColor()
		leftOverflowButton.setImageDrawable(ThreeDotsDrawable(color))
		rightOverflowButton.setImageDrawable(ThreeDotsDrawable(color))
		pillMenuLeftOverflowButton.setImageDrawable(ThreeDotsDrawable(color))
		pillMenuRightOverflowButton.setImageDrawable(ThreeDotsDrawable(color))
	}

	/**
	 * Splits [state]'s entries between "shown directly" and "goes to the
	 * overflow" -see `ComponentToolbar.populateMenuSide` for the full rule
	 * (`showAsAction="always"` wins unless forced into overflow).
	 */
	private fun populateMenuSide(state: MenuSideState, masterMenu: Menu?) {
		state.container.removeAllViews()

		if (state.entries.isEmpty()) {
			state.overflowButton.visibility = GONE
			return
		}

		val visibleEntries = state.entries.filter { isShowAsActionAlways(it.item) && it.item.itemId !in state.forcedOverflowIds }
		val overflowEntries = state.entries.filterNot { entry -> visibleEntries.any { it.item.itemId == entry.item.itemId } }

		visibleEntries.forEachIndexed { index, entry ->
			(entry.view.layoutParams as LinearLayout.LayoutParams).marginStart = if (index == 0) 0 else menuItemSpacing
			state.container.addView(entry.view)
		}

		if (overflowEntries.isNotEmpty()) {
			(state.overflowButton.layoutParams as LinearLayout.LayoutParams).marginStart = if (visibleEntries.isEmpty()) 0 else menuItemSpacing
			state.overflowButton.visibility = VISIBLE
			state.container.addView(state.overflowButton)
			buildOverflowPopup(state, masterMenu, overflowEntries.map { it.item })
		} else {
			state.overflowButton.visibility = GONE
		}
	}

	private fun buildOverflowPopup(state: MenuSideState, masterMenu: Menu?, overflowItems: List<MenuItem>) {
		if (masterMenu == null) return
		val popup = PopupMenu(context, state.overflowButton)
		overflowItems.forEachIndexed { order, item ->
			popup.menu.add(Menu.NONE, item.itemId, order, item.title)
				.setIcon(item.icon)
				.setEnabled(item.isEnabled)
		}
		popup.setOnMenuItemClickListener { proxyItem ->
			val realItem = masterMenu.findItem(proxyItem.itemId) ?: return@setOnMenuItemClickListener false
			handleMenuItemClicked(state, masterMenu, wrapReactive(state, realItem))
			true
		}
		state.overflowButton.setOnClickListener { popup.show() }
		state.overflowPopup = popup
	}

	private fun handleMenuItemClicked(state: MenuSideState, menu: Menu, item: MenuItem) {
		if (!item.isEnabled) return

		state.clickListener?.onMenuItemClicked(item)

		if (item.itemId == state.selectedItemId) {
			state.changedListener?.onMenuReselect(item)
			return
		}

		val previousItem = menu.findItem(state.selectedItemId)
		previousItem?.isChecked = false
		item.isChecked = true
		state.selectedItemId = item.itemId

		if (previousItem != null) state.changedListener?.onMenuUnselect(wrapReactive(state, previousItem))
		state.changedListener?.onMenuSelect(item)
	}

	private fun handleMenuItemLongClicked(state: MenuSideState, menu: Menu, item: MenuItem): Boolean {
		if (!item.isEnabled) return false

		val consumed = state.longClickListener?.onMenuItemLongClicked(item) ?: false

		if (item.itemId == state.longSelectedItemId) {
			state.longChangedListener?.onMenuLongReselect(item)
		} else {
			val previousItem = menu.findItem(state.longSelectedItemId)
			state.longSelectedItemId = item.itemId
			if (previousItem != null) state.longChangedListener?.onMenuLongUnselect(wrapReactive(state, previousItem))
			state.longChangedListener?.onMenuLongSelect(item)
		}
		return consumed
	}

	// endregion


	// region AppBarLayout / insets --------------------------------------------
	// Same mechanics as ComponentToolbar: no custom Behavior hides behind
	// this class, the AppBarLayout itself resolves scrolling exactly like
	// it would for a real Toolbar.

	fun attachToAppBarLayout(
		appBarLayout: AppBarLayout,
		scrollFlags: Int = AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL or AppBarLayout.LayoutParams.SCROLL_FLAG_ENTER_ALWAYS
	) {
		val params = AppBarLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
			.also { it.scrollFlags = scrollFlags }

		val currentParent = parent
		if (currentParent is ViewGroup && currentParent !== appBarLayout) currentParent.removeView(this)
		if (parent !== appBarLayout) appBarLayout.addView(this, params) else layoutParams = params
	}

	fun setAppBarScrollFlags(scrollFlags: Int) {
		val params = layoutParams
		if (params is AppBarLayout.LayoutParams) {
			params.scrollFlags = scrollFlags
			layoutParams = params
		}
	}

	/** When `true`, automatically adds top padding equal to the status bar height on edge-to-edge windows. Off by default. */
	fun setHandleWindowInsets(enabled: Boolean) {
		if (enabled) {
			ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
				val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
				view.setPadding(view.paddingLeft, statusBarInsets.top, view.paddingRight, view.paddingBottom)
				insets
			}
			requestApplyInsets()
		} else {
			ViewCompat.setOnApplyWindowInsetsListener(this, null)
			setPadding(paddingLeft, 0, paddingRight, paddingBottom)
		}
	}

	// endregion


	// region Measure / layout ---------------------------------------------------
	// A single main row: [navigationIconView][leftMenuView][ PILL, flexible ][rightMenuView].
	// The pill (a LinearLayout) is measured with an EXACT width so its
	// weighted content cell actually expands to fill the remaining space.
	// Its HEIGHT, though, is measured in two passes: first UNSPECIFIED to
	// learn how tall pillContentContainer wants to be (title + subtitle +
	// query field, whichever of those are visible), then EXACTLY at
	// whichever is bigger between that natural height and searchBarHeight
	// -this is what lets the pill grow taller on its own the moment a
	// title or subtitle is set, and shrink back to the default row height
	// once they're cleared. Everything below the pill's own boundary
	// (title, subtitle, query field, pill menus) is then handled entirely
	// by the pill's OWN internal LinearLayout pass -this class never
	// touches those children directly in onLayout.

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val availableWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingStart - paddingEnd -
				contentInsetStart - contentInsetEnd

		measureFixedChild(navigationIconView, iconTouchTargetSize)

		val fixedWidth = navigationIconView.measuredWidthOrZero()
		val remainingForMenus = max(0, availableWidth - fixedWidth - minPillReserve)
		val perSideMenuCap = remainingForMenus / 2

		measureWrapContentChild(leftMenuView, perSideMenuCap)
		measureWrapContentChild(rightMenuView, perSideMenuCap)

		val pillWidth = max(0, availableWidth - fixedWidth - leftMenuView.measuredWidthOrZero() - rightMenuView.measuredWidthOrZero())
		val pillWidthSpec = MeasureSpec.makeMeasureSpec(pillWidth, MeasureSpec.EXACTLY)

		pillContainer.measure(pillWidthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
		val pillHeight = max(searchBarHeight, pillContainer.measuredHeight)
		pillContainer.measure(pillWidthSpec, MeasureSpec.makeMeasureSpec(pillHeight, MeasureSpec.EXACTLY))

		val mainRowHeight = max(
			pillContainer.measuredHeight,
			max(navigationIconView.measuredHeightOrZero(), max(leftMenuView.measuredHeightOrZero(), rightMenuView.measuredHeightOrZero()))
		)

		val desiredHeight = max(minimumHeight, mainRowHeight) + paddingTop + paddingBottom
		val resolvedWidth = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
		val resolvedHeight = resolveSize(desiredHeight, heightMeasureSpec)

		setMeasuredDimension(resolvedWidth, resolvedHeight)
	}

	override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
		val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
		val width = r - l
		val rowTop = paddingTop

		val mainRowHeight = max(
			pillContainer.measuredHeight,
			max(navigationIconView.measuredHeightOrZero(), max(leftMenuView.measuredHeightOrZero(), rightMenuView.measuredHeightOrZero()))
		)

		var cursorStart = paddingStart + contentInsetStart
		var cursorEnd = width - paddingEnd - contentInsetEnd

		cursorStart = layoutChildAtStart(navigationIconView, cursorStart, rowTop, mainRowHeight, isRtl, l, r)
		cursorStart = layoutChildAtStart(leftMenuView, cursorStart, rowTop, mainRowHeight, isRtl, l, r)
		cursorEnd = layoutChildAtEnd(rightMenuView, cursorEnd, rowTop, mainRowHeight, isRtl, l, r)

		layoutPill(cursorStart, cursorEnd, rowTop, mainRowHeight, isRtl, l, r)
	}

	private fun layoutChildAtStart(child: View, cursorStart: Int, rowTop: Int, rowHeight: Int, isRtl: Boolean, l: Int, r: Int): Int {
		if (child.visibility == GONE) return cursorStart
		val childWidth = child.measuredWidth
		val childHeight = child.measuredHeight
		val top = rowTop + (rowHeight - childHeight) / 2

		if (isRtl) {
			val right = (r - l) - cursorStart
			val left = right - childWidth
			child.layout(left, top, right, top + childHeight)
		} else {
			child.layout(cursorStart, top, cursorStart + childWidth, top + childHeight)
		}
		return cursorStart + childWidth
	}

	private fun layoutChildAtEnd(child: View, cursorEnd: Int, rowTop: Int, rowHeight: Int, isRtl: Boolean, l: Int, r: Int): Int {
		if (child.visibility == GONE) return cursorEnd
		val childWidth = child.measuredWidth
		val childHeight = child.measuredHeight
		val top = rowTop + (rowHeight - childHeight) / 2

		if (isRtl) {
			val left = (r - l) - cursorEnd
			child.layout(left, top, left + childWidth, top + childHeight)
		} else {
			val right = cursorEnd
			val left = right - childWidth
			child.layout(left, top, right, top + childHeight)
		}
		return cursorEnd - childWidth
	}

	/** The pill fills the FULL remaining space between [cursorStart] and [cursorEnd] -it never shrinks to its content. */
	private fun layoutPill(cursorStart: Int, cursorEnd: Int, rowTop: Int, rowHeight: Int, isRtl: Boolean, l: Int, r: Int) {
		val top = rowTop + (rowHeight - pillContainer.measuredHeight) / 2
		if (isRtl) {
			val right = (r - l) - cursorStart
			val left = (r - l) - cursorEnd
			pillContainer.layout(left, top, right, top + pillContainer.measuredHeight)
		} else {
			pillContainer.layout(cursorStart, top, cursorEnd, top + pillContainer.measuredHeight)
		}
	}

	/**
	 * Gravity.START/END are already direction-aware: Android resolves them
	 * for RTL on its own. Applied directly to [titleView]/[subtitleView]'s
	 * own `gravity` by [setTitleAlignment]/[setSubtitleAlignment] -both are
	 * plain children of [pillContentContainer] now, so there's no separate
	 * layout pass to resolve this in anymore.
	 */
	private fun resolveGravity(alignment: HorizontalAlignment): Int = when (alignment) {
		HorizontalAlignment.START -> Gravity.START
		HorizontalAlignment.CENTER -> Gravity.CENTER_HORIZONTAL
		HorizontalAlignment.END -> Gravity.END
	}

	private fun measureFixedChild(child: View, size: Int) {
		if (child.visibility == GONE) return
		val spec = MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
		child.measure(spec, spec)
	}

	private fun measureWrapContentChild(child: View, maxWidth: Int) {
		if (child.visibility == GONE) return
		val widthSpec = MeasureSpec.makeMeasureSpec(max(0, maxWidth), MeasureSpec.AT_MOST)
		val heightSpec = MeasureSpec.makeMeasureSpec(iconTouchTargetSize, MeasureSpec.EXACTLY)
		child.measure(widthSpec, heightSpec)
	}

	private fun View.measuredWidthOrZero() = if (visibility == GONE) 0 else measuredWidth
	private fun View.measuredHeightOrZero() = if (visibility == GONE) 0 else measuredHeight

	override fun generateDefaultLayoutParams(): LayoutParams = MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
	override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams = MarginLayoutParams(context, attrs)
	override fun generateLayoutParams(p: LayoutParams?): LayoutParams = MarginLayoutParams(p)
	override fun checkLayoutParams(p: LayoutParams?): Boolean = p is MarginLayoutParams

	// endregion


	// region XML attributes -----------------------------------------------------

	private fun applyXmlAttributes(attributeSet: AttributeSet, defStyleAttr: Int) {
		val typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.ComponentSearchBar, defStyleAttr, 0)
		try {
			typedArray.getString(R.styleable.ComponentSearchBar_title)?.let { setTitle(it) }
			typedArray.getString(R.styleable.ComponentSearchBar_subtitle)?.let { setSubtitle(it) }

			if (typedArray.hasValue(R.styleable.ComponentSearchBar_titleTextColor)) {
				setTitleTextColor(typedArray.getColor(R.styleable.ComponentSearchBar_titleTextColor, titleView.currentTextColor))
			}
			if (typedArray.hasValue(R.styleable.ComponentSearchBar_titleTextSize)) {
				val sizePx = typedArray.getDimension(R.styleable.ComponentSearchBar_titleTextSize, titleView.textSize)
				setTitleTextSize(sizePx / resources.displayMetrics.scaledDensity)
			}
			typedArray.getResourceId(R.styleable.ComponentSearchBar_titleTextAppearance, 0)
				.takeIf { it != 0 }?.let { setTitleTextAppearance(it) }

			if (typedArray.hasValue(R.styleable.ComponentSearchBar_subtitleTextColor)) {
				setSubtitleTextColor(typedArray.getColor(R.styleable.ComponentSearchBar_subtitleTextColor, subtitleView.currentTextColor))
			}
			if (typedArray.hasValue(R.styleable.ComponentSearchBar_subtitleTextSize)) {
				val sizePx = typedArray.getDimension(R.styleable.ComponentSearchBar_subtitleTextSize, subtitleView.textSize)
				setSubtitleTextSize(sizePx / resources.displayMetrics.scaledDensity)
			}
			typedArray.getResourceId(R.styleable.ComponentSearchBar_subtitleTextAppearance, 0)
				.takeIf { it != 0 }?.let { setSubtitleTextAppearance(it) }

			typedArray.getInt(R.styleable.ComponentSearchBar_titleAlignment, titleAlignment.ordinal)
				.let { setTitleAlignment(HorizontalAlignment.values()[it]) }
			typedArray.getInt(R.styleable.ComponentSearchBar_subtitleAlignment, subtitleAlignment.ordinal)
				.let { setSubtitleAlignment(HorizontalAlignment.values()[it]) }

			typedArray.getDrawable(R.styleable.ComponentSearchBar_navigationIcon)?.let { setNavigationIcon(it) }
			if (typedArray.hasValue(R.styleable.ComponentSearchBar_navigationIconTint)) {
				setNavigationIconTint(typedArray.getColor(R.styleable.ComponentSearchBar_navigationIconTint, Color.BLACK))
			}
			typedArray.getString(R.styleable.ComponentSearchBar_navigationContentDescription)?.let { setNavigationContentDescription(it) }

			typedArray.getDrawable(R.styleable.ComponentSearchBar_searchNavigationIcon)?.let { setSearchNavigationIcon(it) }
			// typedArray.getDrawable(R.styleable.ComponentSearchBar_searchNavigationActiveIcon)?.let { setSearchNavigationActiveIcon(it) }
			if (typedArray.hasValue(R.styleable.ComponentSearchBar_searchNavigationIconTint)) {
				setSearchNavigationIconTint(typedArray.getColor(R.styleable.ComponentSearchBar_searchNavigationIconTint, Color.BLACK))
			}
			typedArray.getString(R.styleable.ComponentSearchBar_searchNavigationContentDescription)?.let { setSearchNavigationContentDescription(it) }

			typedArray.getString(R.styleable.ComponentSearchBar_android_hint)?.let { setQueryHint(it) }
			typedArray.getString(R.styleable.ComponentSearchBar_android_text)?.let { setQuery(it) }
			if (typedArray.hasValue(R.styleable.ComponentSearchBar_clearIconEnable)) {
				setClearIconEnabled(typedArray.getBoolean(R.styleable.ComponentSearchBar_clearIconEnable, true))
			}
			if (typedArray.hasValue(R.styleable.ComponentSearchBar_clearIconTint)) {
				setClearIconTint(typedArray.getColor(R.styleable.ComponentSearchBar_clearIconTint, Color.BLACK))
			}

			typedArray.getResourceId(R.styleable.ComponentSearchBar_leftMenu, 0).takeIf { it != 0 }?.let { inflateLeftMenu(it) }
			typedArray.getResourceId(R.styleable.ComponentSearchBar_rightMenu, 0).takeIf { it != 0 }?.let { inflateRightMenu(it) }
			typedArray.getResourceId(R.styleable.ComponentSearchBar_pillMenuLeft, 0).takeIf { it != 0 }?.let { inflatePillLeftMenu(it) }
			typedArray.getResourceId(R.styleable.ComponentSearchBar_pillMenuRight, 0).takeIf { it != 0 }?.let { inflatePillRightMenu(it) }
			typedArray.getDrawable(R.styleable.ComponentSearchBar_leftOverflowIcon)?.let { setLeftOverflowIcon(it) }
			typedArray.getDrawable(R.styleable.ComponentSearchBar_rightOverflowIcon)?.let { setRightOverflowIcon(it) }
			typedArray.getDrawable(R.styleable.ComponentSearchBar_pillMenuLeftOverflowIcon)?.let { setPillLeftOverflowIcon(it) }
			typedArray.getDrawable(R.styleable.ComponentSearchBar_pillMenuRightOverflowIcon)?.let { setPillRightOverflowIcon(it) }

			if (typedArray.hasValue(R.styleable.ComponentSearchBar_pillBackgroundColor)) {
				setPillBackgroundColor(typedArray.getColor(R.styleable.ComponentSearchBar_pillBackgroundColor, Color.LTGRAY))
			}
			if (typedArray.hasValue(R.styleable.ComponentSearchBar_pillElevation)) {
				setPillElevation(typedArray.getDimension(R.styleable.ComponentSearchBar_pillElevation, pillElevation))
			}

			if (typedArray.hasValue(R.styleable.ComponentSearchBar_contentInsetStartWithNavigation)) {
				setContentInsetStartWithNavigation(
					typedArray.getDimensionPixelSize(R.styleable.ComponentSearchBar_contentInsetStartWithNavigation, contentInsetStart)
				)
			}
			if (typedArray.hasValue(R.styleable.ComponentSearchBar_contentInsetEnd)) {
				setContentInsetEnd(typedArray.getDimensionPixelSize(R.styleable.ComponentSearchBar_contentInsetEnd, contentInsetEnd))
			}
		} finally {
			typedArray.recycle()
		}
	}

	// endregion


	// Color/typography roles are read from the ACTIVE THEME -nothing is
	// hardcoded- so a custom Material 3 theme is inherited automatically,
	// exactly like `ComponentToolbar` does.
	private fun applyMaterial3Defaults() {
		val onSurface = resolveThemeColor(com.google.android.material.R.attr.colorOnSurface, resolveDefaultIconTintColor())
		val onSurfaceVariant = resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant, onSurface)

		resolveThemeAttrResId(com.google.android.material.R.attr.textAppearanceTitleLarge)
			.takeIf { it != 0 }?.let { setTitleTextAppearance(it) }
		resolveThemeAttrResId(com.google.android.material.R.attr.textAppearanceTitleMedium)
			.takeIf { it != 0 }?.let { setSubtitleTextAppearance(it) }
		resolveThemeAttrResId(com.google.android.material.R.attr.textAppearanceBodyLarge)
			.takeIf { it != 0 }?.let { setQueryTextAppearance(it) }

		setNavigationIconTint(onSurface)
		setSearchNavigationIconTint(onSurfaceVariant)
		setClearIconTint(onSurfaceVariant)
		queryEditText.setHintTextColor(onSurfaceVariant)
		menuIconTint = onSurfaceVariant
		applyOverflowButtonTint()
		refreshPillBackground()
	}

	// Requires the view to have an `id` for Android's standard state-saving
	// to pick it up -same requirement `ComponentToolbar` has.
	override fun onSaveInstanceState(): Parcelable {
		val superState = super.onSaveInstanceState()
		val state = SavedState(superState)
		state.query = queryEditText.text?.toString()
		state.selectedLeftItemId = leftState.selectedItemId
		state.selectedRightItemId = rightState.selectedItemId
		state.selectedPillLeftItemId = pillLeftState.selectedItemId
		state.selectedPillRightItemId = pillRightState.selectedItemId
		state.titleAlignmentOrdinal = titleAlignment.ordinal
		state.subtitleAlignmentOrdinal = subtitleAlignment.ordinal
		state.isSearchActive = searchActive
		return state
	}

	override fun onRestoreInstanceState(state: Parcelable?) {
		if (state !is SavedState) {
			super.onRestoreInstanceState(state)
			return
		}
		super.onRestoreInstanceState(state.superState)

		setQuery(state.query)
		leftState.selectedItemId = state.selectedLeftItemId
		rightState.selectedItemId = state.selectedRightItemId
		pillLeftState.selectedItemId = state.selectedPillLeftItemId
		pillRightState.selectedItemId = state.selectedPillRightItemId
		setTitleAlignment(HorizontalAlignment.values()[state.titleAlignmentOrdinal])
		setSubtitleAlignment(HorizontalAlignment.values()[state.subtitleAlignmentOrdinal])
		setSearchActive(state.isSearchActive)

		leftState.holder?.menu?.findItem(leftState.selectedItemId)?.isChecked = true
		rightState.holder?.menu?.findItem(rightState.selectedItemId)?.isChecked = true
		pillLeftState.holder?.menu?.findItem(pillLeftState.selectedItemId)?.isChecked = true
		pillRightState.holder?.menu?.findItem(pillRightState.selectedItemId)?.isChecked = true
		requestLayout()
	}

	private class SavedState : BaseSavedState {
		var query: String? = null
		var selectedLeftItemId: Int = NO_ITEM_ID
		var selectedRightItemId: Int = NO_ITEM_ID
		var selectedPillLeftItemId: Int = NO_ITEM_ID
		var selectedPillRightItemId: Int = NO_ITEM_ID
		var titleAlignmentOrdinal: Int = 0
		var subtitleAlignmentOrdinal: Int = 0
		var isSearchActive: Boolean = false

		constructor(superState: Parcelable?) : super(superState)

		constructor(parcel: Parcel) : super(parcel) {
			query = parcel.readString()
			selectedLeftItemId = parcel.readInt()
			selectedRightItemId = parcel.readInt()
			selectedPillLeftItemId = parcel.readInt()
			selectedPillRightItemId = parcel.readInt()
			titleAlignmentOrdinal = parcel.readInt()
			subtitleAlignmentOrdinal = parcel.readInt()
			isSearchActive = parcel.readInt() != 0
		}

		override fun writeToParcel(out: Parcel, flags: Int) {
			super.writeToParcel(out, flags)
			out.writeString(query)
			out.writeInt(selectedLeftItemId)
			out.writeInt(selectedRightItemId)
			out.writeInt(selectedPillLeftItemId)
			out.writeInt(selectedPillRightItemId)
			out.writeInt(titleAlignmentOrdinal)
			out.writeInt(subtitleAlignmentOrdinal)
			out.writeInt(if (isSearchActive) 1 else 0)
		}

		companion object CREATOR : Parcelable.Creator<SavedState> {
			override fun createFromParcel(parcel: Parcel) = SavedState(parcel)
			override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
		}
	}


	private fun dpToPx(dp: Int): Int =
		TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()

	/**
	 * A bounded ripple (smaller than the 48dp touch target) -identical to
	 * `ComponentToolbar.createCompactRipple`, duplicated here on purpose:
	 * this class is intentionally self-contained rather than sharing a
	 * base class with `ComponentToolbar`, so it can be dropped into a
	 * project on its own. Extracting a shared `ComponentBarUtils` object is
	 * a reasonable follow-up if both components keep growing in parallel.
	 */
	private fun createCompactRipple(): Drawable {
		val typedValue = TypedValue()
		val resolved = context.theme.resolveAttribute(android.R.attr.colorControlHighlight, typedValue, true)
		val rippleColorInt = if (resolved) {
			if (typedValue.resourceId != 0) ContextCompat.getColor(context, typedValue.resourceId) else typedValue.data
		} else {
			Color.LTGRAY
		}
		val mask = ShapeDrawable(OvalShape())
		val ripple = RippleDrawable(ColorStateList.valueOf(rippleColorInt), null, mask)
		return InsetDrawable(ripple, rippleInset)
	}

	private fun resolveDefaultIconTintColor(): Int {
		val typedValue = TypedValue()
		val resolved = context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
		return if (resolved && typedValue.resourceId != 0) {
			ContextCompat.getColor(context, typedValue.resourceId)
		} else {
			Color.DKGRAY
		}
	}

	private fun resolveThemeColor(@AttrRes attrId: Int, fallback: Int): Int {
		val typedValue = TypedValue()
		if (!context.theme.resolveAttribute(attrId, typedValue, true)) return fallback
		return if (typedValue.resourceId != 0) ContextCompat.getColor(context, typedValue.resourceId) else typedValue.data
	}

	private fun resolveThemeAttrResId(@AttrRes attrId: Int): Int {
		val typedValue = TypedValue()
		return if (context.theme.resolveAttribute(attrId, typedValue, true)) typedValue.resourceId else 0
	}

	/** A hand-drawn three-dot drawable for overflow buttons -see `ComponentToolbar.ThreeDotsDrawable`. */
	private class ThreeDotsDrawable(@ColorInt private val color: Int) : Drawable() {
		private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			this.color = this@ThreeDotsDrawable.color
			style = Paint.Style.FILL
		}

		override fun draw(canvas: Canvas) {
			val bounds = bounds
			val centerY = bounds.exactCenterY()
			val radius = min(bounds.width(), bounds.height()) / 10f
			val spacing = radius * 3.2f
			val centerX = bounds.exactCenterX()

			canvas.drawCircle(centerX - spacing, centerY, radius, paint)
			canvas.drawCircle(centerX, centerY, radius, paint)
			canvas.drawCircle(centerX + spacing, centerY, radius, paint)
		}

		override fun setAlpha(alpha: Int) { paint.alpha = alpha }
		override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
		override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
	}

	/** Hand-drawn magnifying-glass glyph -the default INNER search navigation icon; needs no drawable resource. */
	private class SearchGlyphDrawable(@ColorInt private val color: Int) : Drawable() {
		private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			this.color = this@SearchGlyphDrawable.color
			style = Paint.Style.STROKE
			strokeCap = Paint.Cap.ROUND
		}

		override fun draw(canvas: Canvas) {
			val bounds = bounds
			val size = min(bounds.width(), bounds.height()).toFloat()
			paint.strokeWidth = size * 0.09f

			val radius = size * 0.26f
			val cx = bounds.exactCenterX() - size * 0.07f
			val cy = bounds.exactCenterY() - size * 0.07f
			canvas.drawCircle(cx, cy, radius, paint)

			val handleStart = cx + radius * 0.78f
			val handleEnd = cx + radius * 1.85f
			canvas.drawLine(handleStart, cy + radius * 0.78f, handleEnd, cy + radius * 1.85f, paint)
		}

		override fun setAlpha(alpha: Int) { paint.alpha = alpha }
		override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
		override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
	}

	/** Hand-drawn "x" glyph -the default clear-query icon; needs no drawable resource. */
	private class ClearGlyphDrawable(@ColorInt private val color: Int) : Drawable() {
		private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			this.color = this@ClearGlyphDrawable.color
			style = Paint.Style.STROKE
			strokeCap = Paint.Cap.ROUND
		}

		override fun draw(canvas: Canvas) {
			val bounds = bounds
			val size = min(bounds.width(), bounds.height()).toFloat()
			paint.strokeWidth = size * 0.09f
			val inset = size * 0.3f
			canvas.drawLine(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset, paint)
			canvas.drawLine(bounds.right - inset, bounds.top + inset, bounds.left + inset, bounds.bottom - inset, paint)
		}

		override fun setAlpha(alpha: Int) { paint.alpha = alpha }
		override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
		override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
	}

	/** Hand-drawn back-arrow glyph -the default ACTIVE search-navigation icon; needs no drawable resource. */
	private class BackArrowGlyphDrawable(@ColorInt private val color: Int) : Drawable() {
		private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			this.color = this@BackArrowGlyphDrawable.color
			style = Paint.Style.STROKE
			strokeCap = Paint.Cap.ROUND
			strokeJoin = Paint.Join.ROUND
		}

		override fun draw(canvas: Canvas) {
			val bounds = bounds
			val size = min(bounds.width(), bounds.height()).toFloat()
			paint.strokeWidth = size * 0.09f

			val cx = bounds.exactCenterX()
			val cy = bounds.exactCenterY()
			val halfShaft = size * 0.28f
			val headSize = size * 0.22f

			canvas.drawLine(cx - halfShaft, cy, cx + halfShaft, cy, paint)
			canvas.drawLine(cx - halfShaft, cy, cx - halfShaft + headSize, cy - headSize, paint)
			canvas.drawLine(cx - halfShaft, cy, cx - halfShaft + headSize, cy + headSize, paint)
		}

		override fun setAlpha(alpha: Int) { paint.alpha = alpha }
		override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
		override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
	}
}