package com.deavidig.mod.deaniel.toolbar.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
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
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.AppCompatTextView
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.TextViewCompat
import com.deavidig.sketchprojectpro.R
import com.google.android.material.appbar.AppBarLayout
import kotlin.math.max
import kotlin.math.min

/**
 * A custom top app bar built from scratch on top of [ViewGroup] -it does not
 * wrap or extend `androidx.appcompat.widget.Toolbar` internally- designed to
 * cover a case the classic Toolbar does not: a menu on the LEFT **and** a
 * menu on the RIGHT at the same time.
 *
 * ### Material Design 3 alignment
 * - Typography roles `titleLarge`/`titleMedium` for title/subtitle.
 * - Color roles `colorOnSurface` (navigation icon, logo, title) and
 *   `colorOnSurfaceVariant` (action icons / overflow), resolved from the
 *   active theme at runtime -nothing is hardcoded.
 * - A bounded ripple (it does not fill the whole 48dp touch target), the
 *   same way action icons on a real `MaterialToolbar` behave.
 *
 * ### showAsAction
 * An item declared with `app:showAsAction="always"` is always rendered as a
 * button in the bar (an icon, or a text button when it has no icon). Any
 * other case (`ifRoom`, `never`, or unspecified) always falls back to the
 * overflow button ("⋮"). Reading that flag requires casting to
 * `androidx.appcompat.view.menu.MenuItemImpl` directly (see
 * [isShowAsActionAlways]), since `android.view.MenuItem` does not expose a
 * public getter for it.
 *
 * ### Nested submenus (long-press)
 * An item declaring a nested `<menu>` in XML reveals it as a flyout on
 * long-press instead of firing the normal long-click callbacks -see
 * [showItemSubMenu]. The recommended pattern for this is combining it with
 * `android:menuCategory="secondary"` and `app:showAsAction="always"` (a
 * persistent action whose variants are one long-press away), though it is
 * not required: any item with a nested `<menu>` gets this behavior.
 *
 * ### Reactive MenuItems
 * `android.view.MenuItem` never notifies anyone when it changes. Every item
 * this class hands out -through click/selection callbacks or through
 * [getLeftMenu]/[getRightMenu]- is wrapped so that calling `setIcon`,
 * `setTitle`, `setShowAsAction`, `setVisible` or `setEnabled` on it
 * automatically triggers a rebuild of that side. See the "MenuItem
 * reactivity" region for the full rationale.
 *
 * ### AppBarLayout
 * Auto-hide/collapse on scroll is driven EXACTLY the way
 * `androidx.appcompat.widget.Toolbar`/`MaterialToolbar` do it: through
 * `app:layout_scrollFlags` on the `AppBarLayout.LayoutParams` (or
 * [attachToAppBarLayout]/[setAppBarScrollFlags] from code). There is no
 * custom `Behavior` hiding behind this class -the `AppBarLayout` itself
 * resolves scrolling against the `CoordinatorLayout`, exactly like it would
 * for any real Toolbar.
 */
class ComponentToolbar @JvmOverloads constructor(
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

	// region Listener interfaces

	/** Invoked when the navigation icon is tapped. */
	fun interface OnNavigationClickListener {
		fun onNavigationClick(view: View)
	}

	/** Invoked when the navigation icon is long-pressed. Return `true` if the event was consumed. */
	fun interface OnNavigationLongClickListener {
		fun onNavigationLongClick(view: View): Boolean
	}

	/** Invoked when the logo is tapped. */
	fun interface OnLogoClickListener {
		fun onLogoClick(view: View)
	}

	/** Invoked when the logo is long-pressed. Return `true` if the event was consumed. */
	fun interface OnLogoLongClickListener {
		fun onLogoLongClick(view: View): Boolean
	}

	/** Notifies a click on a specific item of one of the menus (whether it was visible or inside the overflow). */
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

	// endregion

	// region Internal views

	private val navigationIconView: AppCompatImageButton = AppCompatImageButton(context)
	private val logoView: AppCompatImageButton = AppCompatImageButton(context)
	private val leftMenuView: LinearLayout = LinearLayout(context)
	private val rightMenuView: LinearLayout = LinearLayout(context)
	private val leftOverflowButton: AppCompatImageButton = AppCompatImageButton(context)
	private val rightOverflowButton: AppCompatImageButton = AppCompatImageButton(context)
	private val titleView: AppCompatTextView = AppCompatTextView(context)
	private val subtitleView: AppCompatTextView = AppCompatTextView(context)

	// endregion

	// region Menu state

	/** [view] is either an AppCompatImageButton (item with an icon) or a text AppCompatButton (item without one). */
	private data class MenuButtonEntry(val item: MenuItem, val view: View)

	private var leftMenuHolder: PopupMenu? = null
	private var rightMenuHolder: PopupMenu? = null

	private var leftEntries: List<MenuButtonEntry> = emptyList()
	private var rightEntries: List<MenuButtonEntry> = emptyList()

	private var leftOverflowPopup: PopupMenu? = null
	private var rightOverflowPopup: PopupMenu? = null

	/** IDs that ALWAYS go to their side's overflow, even if the item requests showAsAction="always". */
	private val leftForcedOverflowIds = mutableSetOf<Int>()
	private val rightForcedOverflowIds = mutableSetOf<Int>()

	private var selectedLeftItemId: Int = NO_ITEM_ID
	private var selectedRightItemId: Int = NO_ITEM_ID
	private var longSelectedLeftItemId: Int = NO_ITEM_ID
	private var longSelectedRightItemId: Int = NO_ITEM_ID

	/** Tint applied to menu item icons and to the overflow button; `null` means "keep the drawable's own color". */
	private var menuIconTint: Int? = null

	// endregion

	// region Public listeners

	private var onNavigationClickListener: OnNavigationClickListener? = null
	private var onNavigationLongClickListener: OnNavigationLongClickListener? = null
	private var onLogoClickListener: OnLogoClickListener? = null
	private var onLogoLongClickListener: OnLogoLongClickListener? = null

	private var onLeftItemMenuClickListener: OnItemMenuClickListener? = null
	private var onLeftItemMenuLongClickListener: OnItemMenuLongClickListener? = null
	private var onLeftItemMenuChangedListener: OnItemMenuChangedListener? = null
	private var onLeftItemMenuLongChangedListener: OnItemMenuLongChangedListener? = null

	private var onRightItemMenuClickListener: OnItemMenuClickListener? = null
	private var onRightItemMenuLongClickListener: OnItemMenuLongClickListener? = null
	private var onRightItemMenuChangedListener: OnItemMenuChangedListener? = null
	private var onRightItemMenuLongChangedListener: OnItemMenuLongChangedListener? = null

	// endregion

	// region Dimensions / configuration

	private val iconTouchTargetSize: Int = dpToPx(48)
	private val iconDrawablePadding: Int = dpToPx(12)
	private val rippleInset: Int = dpToPx(8) // see createCompactRipple(): shrinks the ripple inside the 48dp touch target
	private val menuItemSpacing: Int = dpToPx(4)
	private val titleBlockHorizontalMargin: Int = dpToPx(12)
	private val minTitleReserve: Int = dpToPx(48)

	private var contentInsetStart: Int = dpToPx(16)
	private var contentInsetEnd: Int = dpToPx(16)

	private var titleMarginStart = 0
	private var titleMarginTop = 0
	private var titleMarginEnd = 0
	private var titleMarginBottom = 0

	private var titleAlignment: HorizontalAlignment = HorizontalAlignment.START
	private var subtitleAlignment: HorizontalAlignment = HorizontalAlignment.START

	// endregion

	init {
		clipToPadding = false
		clipChildren = false

		minimumHeight = resolveActionBarSize()

		setupNavigationIconView()
		setupLogoView()
		setupTitleView()
		setupSubtitleView()
		setupMenuContainer(leftMenuView)
		setupMenuContainer(rightMenuView)
		setupOverflowButton(leftOverflowButton)
		setupOverflowButton(rightOverflowButton)

		addView(navigationIconView)
		addView(leftMenuView)
		addView(logoView)
		addView(titleView)
		addView(subtitleView)
		addView(rightMenuView)

		navigationIconView.isClickable = false
		navigationIconView.isLongClickable = false
		navigationIconView.visibility = GONE

		logoView.isClickable = false
		logoView.isLongClickable = false
		logoView.visibility = GONE

		subtitleView.visibility = GONE

		// Order matters: Material 3 theme defaults are applied first, then
		// any explicit XML attributes -so XML always wins over the default,
		// never the other way around.
		applyMaterial3Defaults()
		attrs?.let { applyXmlAttributes(it, defStyleAttr) }
	}

	// region Internal view setup

	private fun setupNavigationIconView() {
		navigationIconView.scaleType = ImageView.ScaleType.CENTER_INSIDE
		navigationIconView.background = createCompactRipple()
		navigationIconView.setPadding(iconDrawablePadding, iconDrawablePadding, iconDrawablePadding, iconDrawablePadding)
		navigationIconView.setOnClickListener { view -> onNavigationClickListener?.onNavigationClick(view) }
		navigationIconView.setOnLongClickListener { view -> onNavigationLongClickListener?.onNavigationLongClick(view) ?: false }
	}

	private fun setupLogoView() {
		logoView.scaleType = ImageView.ScaleType.CENTER_INSIDE
		logoView.background = createCompactRipple()
		logoView.setPadding(iconDrawablePadding, iconDrawablePadding, iconDrawablePadding, iconDrawablePadding)
		logoView.setOnClickListener { view -> onLogoClickListener?.onLogoClick(view) }
		logoView.setOnLongClickListener { view -> onLogoLongClickListener?.onLogoLongClick(view) ?: false }
	}

	private fun setupTitleView() {
		titleView.setSingleLine(true)
		titleView.ellipsize = android.text.TextUtils.TruncateAt.END
		titleView.gravity = Gravity.START or Gravity.CENTER_VERTICAL
	}

	private fun setupSubtitleView() {
		subtitleView.setSingleLine(true)
		subtitleView.ellipsize = android.text.TextUtils.TruncateAt.END
		subtitleView.gravity = Gravity.START or Gravity.CENTER_VERTICAL
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

	// endregion

	// region Public API · Navigation icon
	// M3: the Top App Bar's "leading icon" -opens navigation (drawer/back).
	// 48dp touch target with a 24dp centered icon, per spec.

	/** Sets the navigation icon drawable, or `null` to hide it. */
	fun setNavigationIcon(icon: Drawable?) {
		navigationIconView.setImageDrawable(icon)
		navigationIconView.visibility = if (icon != null) VISIBLE else GONE
		requestLayout()
	}

	/** @see setNavigationIcon */
	fun setNavigationIcon(@DrawableRes iconRes: Int) = setNavigationIcon(ContextCompat.getDrawable(context, iconRes))

	fun getNavigationIcon(): Drawable? = navigationIconView.drawable

	fun setNavigationIconTint(@ColorInt color: Int) {
		ImageViewCompat.setImageTintList(navigationIconView, ColorStateList.valueOf(color))
	}

	fun setNavigationIconTintList(tint: ColorStateList?) {
		ImageViewCompat.setImageTintList(navigationIconView, tint)
	}

	fun setNavigationContentDescription(description: CharSequence?) {
		navigationIconView.contentDescription = description
		if (!description.isNullOrEmpty()) TooltipCompat.setTooltipText(navigationIconView, description)
	}

	fun setNavigationContentDescription(@StringRes resId: Int) = setNavigationContentDescription(context.getString(resId))

	/** The view only becomes clickable once both a listener and an icon are set. */
	fun setNavigationOnClickListener(listener: OnNavigationClickListener?) {
		onNavigationClickListener = listener
		navigationIconView.isClickable = listener != null && navigationIconView.drawable != null
	}

	fun setNavigationOnLongClickListener(listener: OnNavigationLongClickListener?) {
		onNavigationLongClickListener = listener
		navigationIconView.isLongClickable = listener != null && navigationIconView.drawable != null
	}

	// endregion

	// region Public API · Logo / secondary icon
	// Not part of the M3 Top App Bar spec (M3 does not define its own
	// "logo" slot the way the classic Toolbar did), but kept as a custom
	// extension since it was explicitly requested; that is why it stays
	// 100% optional and non-clickable until a listener is assigned.

	fun setLogo(icon: Drawable?) {
		logoView.setImageDrawable(icon)
		logoView.visibility = if (icon != null) VISIBLE else GONE
		requestLayout()
	}

	fun setLogo(@DrawableRes iconRes: Int) = setLogo(ContextCompat.getDrawable(context, iconRes))
	fun getLogo(): Drawable? = logoView.drawable

	fun setLogoTint(@ColorInt color: Int) {
		ImageViewCompat.setImageTintList(logoView, ColorStateList.valueOf(color))
	}

	fun setLogoTintList(tint: ColorStateList?) {
		ImageViewCompat.setImageTintList(logoView, tint)
	}

	fun setLogoContentDescription(description: CharSequence?) {
		logoView.contentDescription = description
		if (!description.isNullOrEmpty()) TooltipCompat.setTooltipText(logoView, description)
	}

	fun setOnLogoClickListener(listener: OnLogoClickListener?) {
		onLogoClickListener = listener
		logoView.isClickable = listener != null && logoView.drawable != null
	}

	fun setOnLogoLongClickListener(listener: OnLogoLongClickListener?) {
		onLogoLongClickListener = listener
		logoView.isLongClickable = listener != null && logoView.drawable != null
	}

	// endregion

	// region Public API · Title / Subtitle
	// M3: the title uses the `titleLarge` type role; the subtitle (outside
	// the standard spec but common in real apps) uses `titleMedium`. Both
	// default colors come from `colorOnSurface` -see applyMaterial3Defaults().
	//
	// Alignment: each line is laid out spanning the FULL width available
	// between the navigation/menu blocks, and it is the TextView's own
	// `gravity` that decides where the text lands within that width. This
	// lets the title and the subtitle use different alignments without
	// coupling their measured widths together -see [layoutTitleSubtitleBlock].

	fun setTitle(title: CharSequence?) {
		titleView.text = title
		titleView.visibility = if (title.isNullOrEmpty()) GONE else VISIBLE
		requestLayout()
	}

	fun setTitle(@StringRes resId: Int) = setTitle(context.getString(resId))
	fun getTitle(): CharSequence? = titleView.text
	fun setTitleTextColor(@ColorInt color: Int) = titleView.setTextColor(color)
	fun setTitleTextColor(colors: ColorStateList) = titleView.setTextColor(colors)
	fun setTitleTextSize(sizeSp: Float) = titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
	fun setTitleTypeface(typeface: Typeface?, style: Int = Typeface.NORMAL) = titleView.setTypeface(typeface, style)
	fun setTitleTextAppearance(@StyleRes resId: Int) = TextViewCompat.setTextAppearance(titleView, resId)

	fun setSubtitle(subtitle: CharSequence?) {
		subtitleView.text = subtitle
		subtitleView.visibility = if (subtitle.isNullOrEmpty()) GONE else VISIBLE
		requestLayout()
	}

	fun setSubtitle(@StringRes resId: Int) = setSubtitle(context.getString(resId))
	fun getSubtitle(): CharSequence? = subtitleView.text
	fun setSubtitleTextColor(@ColorInt color: Int) = subtitleView.setTextColor(color)
	fun setSubtitleTextColor(colors: ColorStateList) = subtitleView.setTextColor(colors)
	fun setSubtitleTextSize(sizeSp: Float) = subtitleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
	fun setSubtitleTypeface(typeface: Typeface?, style: Int = Typeface.NORMAL) = subtitleView.setTypeface(typeface, style)
	fun setSubtitleTextAppearance(@StyleRes resId: Int) = TextViewCompat.setTextAppearance(subtitleView, resId)

	fun setTitleMargins(start: Int, top: Int, end: Int, bottom: Int) {
		titleMarginStart = start; titleMarginTop = top; titleMarginEnd = end; titleMarginBottom = bottom
		requestLayout()
	}

	fun setTitleMarginStart(@Px margin: Int) { titleMarginStart = margin; requestLayout() }
	fun setTitleMarginTop(@Px margin: Int) { titleMarginTop = margin; requestLayout() }
	fun setTitleMarginEnd(@Px margin: Int) { titleMarginEnd = margin; requestLayout() }
	fun setTitleMarginBottom(@Px margin: Int) { titleMarginBottom = margin; requestLayout() }

	/** Title alignment: START (default), CENTER or END. Independent from the subtitle's. */
	fun setTitleAlignment(alignment: HorizontalAlignment) { titleAlignment = alignment; requestLayout() }
	fun getTitleAlignment(): HorizontalAlignment = titleAlignment

	/** Subtitle alignment: START (default), CENTER or END. Independent from the title's. */
	fun setSubtitleAlignment(alignment: HorizontalAlignment) { subtitleAlignment = alignment; requestLayout() }
	fun getSubtitleAlignment(): HorizontalAlignment = subtitleAlignment

	/** Backwards-compatible shortcut, equivalent to `setTitleAlignment(CENTER or START)`. Prefer [setTitleAlignment]. */
	fun setTitleCentered(centered: Boolean) =
		setTitleAlignment(if (centered) HorizontalAlignment.CENTER else HorizontalAlignment.START)

	fun isTitleCentered(): Boolean = titleAlignment == HorizontalAlignment.CENTER

	// endregion

	// region Public API · Content insets

	fun setContentInsetStartWithNavigation(@Px inset: Int) { contentInsetStart = inset; requestLayout() }
	fun setContentInsetEnd(@Px inset: Int) { contentInsetEnd = inset; requestLayout() }
	fun getContentInsetStart(): Int = contentInsetStart
	fun getContentInsetEnd(): Int = contentInsetEnd

	// endregion

	// region Public API · LEFT menu
	// M3 does not officially define a left-side menu (only a "leading icon"
	// and "trailing icons"); this remains a custom extension of the
	// component. Visually these icons are treated exactly like the ones on
	// the right: same touch target size, same `colorOnSurfaceVariant` tint,
	// same overflow mechanism.

	fun inflateLeftMenu(@MenuRes menuRes: Int) {
		val popup = PopupMenu(context, leftMenuView)
		popup.menuInflater.inflate(menuRes, popup.menu)
		leftMenuHolder = popup
		leftEntries = buildEntries(popup.menu, isLeft = true)
		populateMenuSide(leftMenuView, leftOverflowButton, leftEntries, popup.menu, isLeft = true)
		requestLayout()
	}

	/** Returns a reactive [Menu] -see the "MenuItem reactivity" region- or `null` if nothing was inflated yet. */
	fun getLeftMenu(): Menu? = leftMenuHolder?.menu?.let { ReactiveMenu(it) { item -> wrapReactive(item, isLeft = true) } }

	/** Returns the currently selected item (reactive), or `null` if nothing is selected. */
	fun getSelectedLeftMenuItem(): MenuItem? = leftMenuHolder?.menu?.findItem(selectedLeftItemId)?.let { wrapReactive(it, isLeft = true) }

	fun clearLeftMenu() {
		leftMenuHolder?.menu?.clear()
		leftEntries = emptyList()
		selectedLeftItemId = NO_ITEM_ID
		populateMenuSide(leftMenuView, leftOverflowButton, leftEntries, null, isLeft = true)
		requestLayout()
	}

	/** Rebuilds the buttons from the Menu's current state (visibility/enabled/icon mutated externally). */
	fun refreshLeftMenu() {
		val menu = leftMenuHolder?.menu ?: return
		leftEntries = buildEntries(menu, isLeft = true)
		populateMenuSide(leftMenuView, leftOverflowButton, leftEntries, menu, isLeft = true)
		requestLayout()
	}

	fun setOnLeftItemMenuClickListener(listener: OnItemMenuClickListener?) { onLeftItemMenuClickListener = listener }
	fun setOnLeftItemMenuLongClickListener(listener: OnItemMenuLongClickListener?) { onLeftItemMenuLongClickListener = listener }
	fun setOnLeftItemMenuChangedListener(listener: OnItemMenuChangedListener?) { onLeftItemMenuChangedListener = listener }
	fun setOnLeftItemMenuLongChangedListener(listener: OnItemMenuLongChangedListener?) { onLeftItemMenuLongChangedListener = listener }

	fun setLeftOverflowIcon(icon: Drawable?) { leftOverflowButton.setImageDrawable(icon) }
	fun setLeftOverflowIcon(@DrawableRes iconRes: Int) = setLeftOverflowIcon(ContextCompat.getDrawable(context, iconRes))

	/**
	 * Forces the item with [itemId] to ALWAYS live in this side's overflow,
	 * regardless of whether it declares `showAsAction="always"`. Useful for
	 * secondary actions you want to hide from the main row on purpose.
	 */
	fun setLeftItemAlwaysInOverflow(itemId: Int, alwaysInOverflow: Boolean) {
		if (alwaysInOverflow) leftForcedOverflowIds.add(itemId) else leftForcedOverflowIds.remove(itemId)
		populateMenuSide(leftMenuView, leftOverflowButton, leftEntries, leftMenuHolder?.menu, isLeft = true)
		requestLayout()
	}

	// endregion

	// region Public API · RIGHT menu

	fun inflateRightMenu(@MenuRes menuRes: Int) {
		val popup = PopupMenu(context, rightMenuView)
		popup.menuInflater.inflate(menuRes, popup.menu)
		rightMenuHolder = popup
		rightEntries = buildEntries(popup.menu, isLeft = false)
		populateMenuSide(rightMenuView, rightOverflowButton, rightEntries, popup.menu, isLeft = false)
		requestLayout()
	}

	fun getRightMenu(): Menu? = rightMenuHolder?.menu?.let { ReactiveMenu(it) { item -> wrapReactive(item, isLeft = false) } }
	fun getSelectedRightMenuItem(): MenuItem? = rightMenuHolder?.menu?.findItem(selectedRightItemId)?.let { wrapReactive(it, isLeft = false) }

	fun clearRightMenu() {
		rightMenuHolder?.menu?.clear()
		rightEntries = emptyList()
		selectedRightItemId = NO_ITEM_ID
		populateMenuSide(rightMenuView, rightOverflowButton, rightEntries, null, isLeft = false)
		requestLayout()
	}

	fun refreshRightMenu() {
		val menu = rightMenuHolder?.menu ?: return
		rightEntries = buildEntries(menu, isLeft = false)
		populateMenuSide(rightMenuView, rightOverflowButton, rightEntries, menu, isLeft = false)
		requestLayout()
	}

	fun setOnRightItemMenuClickListener(listener: OnItemMenuClickListener?) { onRightItemMenuClickListener = listener }
	fun setOnRightItemMenuLongClickListener(listener: OnItemMenuLongClickListener?) { onRightItemMenuLongClickListener = listener }
	fun setOnRightItemMenuChangedListener(listener: OnItemMenuChangedListener?) { onRightItemMenuChangedListener = listener }
	fun setOnRightItemMenuLongChangedListener(listener: OnItemMenuLongChangedListener?) { onRightItemMenuLongChangedListener = listener }

	fun setRightOverflowIcon(icon: Drawable?) { rightOverflowButton.setImageDrawable(icon) }
	fun setRightOverflowIcon(@DrawableRes iconRes: Int) = setRightOverflowIcon(ContextCompat.getDrawable(context, iconRes))

	/** Same as [setLeftItemAlwaysInOverflow], for the right side. */
	fun setRightItemAlwaysInOverflow(itemId: Int, alwaysInOverflow: Boolean) {
		if (alwaysInOverflow) rightForcedOverflowIds.add(itemId) else rightForcedOverflowIds.remove(itemId)
		populateMenuSide(rightMenuView, rightOverflowButton, rightEntries, rightMenuHolder?.menu, isLeft = false)
		requestLayout()
	}

	// endregion

	// region Public API · Menu icon styling

	/** Tint applied to ALL item icons (both sides) and the overflow button. `null` keeps each drawable's own color. */
	fun setMenuIconTint(@ColorInt color: Int?) {
		menuIconTint = color
		refreshLeftMenu()
		refreshRightMenu()
		applyOverflowButtonTint()
	}

	// endregion

	// region MenuItem reactivity
	//
	// android.view.MenuItem never notifies anyone when it changes -there is
	// no listener for "my icon/title/showAsAction just changed"- so every
	// item is wrapped in a proxy that, besides delegating everything to the
	// real item, triggers a refresh of the corresponding side whenever a
	// setter that can affect what's on screen is called. This is what
	// buildEntries()/getLeftMenu()/getRightMenu() hand out -never the raw
	// MenuItem.
	//
	// Note: `setChecked` intentionally does NOT trigger a refresh -that is
	// already handled by our own selection cycle (handleMenuItemClicked),
	// and there is currently no visual representation of the "checked"
	// state on the button itself, so reacting to it would only cause
	// unnecessary rebuilds on every click.

	private fun wrapReactive(item: MenuItem, isLeft: Boolean): MenuItem =
		ReactiveMenuItem(item) { if (isLeft) refreshLeftMenu() else refreshRightMenu() }

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

	// endregion

	// region Building menu buttons and overflow

	/**
	 * Checks whether [item] was inflated with `app:showAsAction="always"`.
	 *
	 * `android.view.MenuItem` does not expose a public getter for this
	 * (only `setShowAsAction`), so this casts directly to the real
	 * implementation androidx's inflater produces:
	 * [androidx.appcompat.view.menu.MenuItemImpl]. That class is marked as
	 * library-internal use (`@RestrictTo`), so the cast could break on a
	 * future appcompat release; that's why it is wrapped in try/catch with
	 * a safe fallback to `false` (the item simply falls back to the
	 * overflow instead of crashing).
	 */
	private fun isShowAsActionAlways(item: MenuItem): Boolean {
		val realItem = (item as? ReactiveMenuItem)?.unwrap() ?: item
		return try {
			(realItem as? MenuItemImpl)?.requiresActionButton() == true
		} catch (error: Throwable) {
			false
		}
	}

	private fun buildEntries(menu: Menu, isLeft: Boolean): List<MenuButtonEntry> {
		val entries = mutableListOf<MenuButtonEntry>()
		for (index in 0 until menu.size()) {
			val rawItem = menu.getItem(index)
			if (!rawItem.isVisible) continue
			val item = wrapReactive(rawItem, isLeft)

			val view = createEntryView(item)
			view.setOnClickListener { handleMenuItemClicked(menu, item, isLeft) }

			if (item.hasSubMenu()) {
				// A nested <menu> takes over long-press entirely: it reveals
				// its own flyout instead of firing the normal long-click
				// callbacks. See showItemSubMenu() KDoc for the rationale.
				view.setOnLongClickListener { showItemSubMenu(view, item, isLeft); true }
			} else {
				view.setOnLongClickListener { handleMenuItemLongClicked(menu, item, isLeft) }
			}

			entries.add(MenuButtonEntry(item, view))
		}
		return entries
	}

	/**
	 * Reveals the nested `<menu>` of [item] (i.e. `item.subMenu`) as a
	 * [PopupMenu] anchored to [anchor], triggered by long-pressing an entry
	 * that declares child items in XML, for example:
	 *
	 * ```xml
	 * <item android:icon="@drawable/ic_save_24"
	 *       app:showAsAction="always"
	 *       android:menuCategory="secondary">
	 *     <menu>
	 *         <item android:title="Create Template" />
	 *         <item android:title="Replace Template" />
	 *     </menu>
	 * </item>
	 * ```
	 *
	 * `menuCategory="secondary"` + `showAsAction="always"` is the
	 * recommended pattern for this (a persistent, always-visible action
	 * whose secondary variants sit behind a long-press), but it is not
	 * enforced: any item with a nested `<menu>` gets this behavior
	 * regardless of its category or showAsAction value. Both are readable
	 * through public API on [MenuItem] -[MenuItem.hasSubMenu]/
	 * [MenuItem.getSubMenu] for the nested menu, [MenuItem.getOrder] masked
	 * with [Menu.CATEGORY_MASK] for the category (see [isSecondaryCategory])-
	 * so, unlike [isShowAsActionAlways], none of this needs the
	 * [MenuItemImpl] fallback.
	 *
	 * Submenu item clicks are routed through the side's
	 * [OnItemMenuClickListener] only; they intentionally do not participate
	 * in the select/unselect/reselect cycle ([OnItemMenuChangedListener]),
	 * since a submenu reads as a one-off action picker, not a persistent
	 * selection.
	 */
	private fun showItemSubMenu(anchor: View, item: MenuItem, isLeft: Boolean) {
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
			val clickListener = if (isLeft) onLeftItemMenuClickListener else onRightItemMenuClickListener
			clickListener?.onMenuItemClicked(wrapReactive(realSubItem, isLeft))
			true
		}
		popup.show()
	}

	/** Whether [item] belongs to `android:menuCategory="secondary"`. Read via the public [MenuItem.getOrder] API. */
	private fun isSecondaryCategory(item: MenuItem): Boolean =
		(item.order and Menu.CATEGORY_MASK) == Menu.CATEGORY_SECONDARY

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
	}

	/**
	 * Splits [entries] between "shown directly on the bar" and "goes to the
	 * overflow", following the requested rule: `showAsAction="always"` ->
	 * button (icon or text); any other case -> overflow. An id present in
	 * [leftForcedOverflowIds]/[rightForcedOverflowIds] always wins, even
	 * over an "always" item. This does not depend on available width -unlike
	 * an earlier space-based version of this method-, so it only needs to
	 * run once whenever the menu changes (inflate/refresh/clear/forced), not
	 * on every `onMeasure`.
	 */
	private fun populateMenuSide(
		container: LinearLayout,
		overflowButton: AppCompatImageButton,
		entries: List<MenuButtonEntry>,
		masterMenu: Menu?,
		isLeft: Boolean
	) {
		container.removeAllViews()

		if (entries.isEmpty()) {
			overflowButton.visibility = GONE
			return
		}

		val forcedIds = if (isLeft) leftForcedOverflowIds else rightForcedOverflowIds
		val visibleEntries = entries.filter { isShowAsActionAlways(it.item) && it.item.itemId !in forcedIds }
		val overflowEntries = entries.filterNot { entry -> visibleEntries.any { it.item.itemId == entry.item.itemId } }

		visibleEntries.forEachIndexed { index, entry ->
			(entry.view.layoutParams as LinearLayout.LayoutParams).marginStart = if (index == 0) 0 else menuItemSpacing
			container.addView(entry.view)
		}

		if (overflowEntries.isNotEmpty()) {
			(overflowButton.layoutParams as LinearLayout.LayoutParams).marginStart = if (visibleEntries.isEmpty()) 0 else menuItemSpacing
			overflowButton.visibility = VISIBLE
			container.addView(overflowButton)
			buildOverflowPopup(overflowButton, masterMenu, overflowEntries.map { it.item }, isLeft)
		} else {
			overflowButton.visibility = GONE
		}
	}

	private fun buildOverflowPopup(anchor: AppCompatImageButton, masterMenu: Menu?, overflowItems: List<MenuItem>, isLeft: Boolean) {
		if (masterMenu == null) return
		val popup = PopupMenu(context, anchor)
		overflowItems.forEachIndexed { order, item ->
			popup.menu.add(Menu.NONE, item.itemId, order, item.title)
				.setIcon(item.icon)
				.setEnabled(item.isEnabled)
		}
		popup.setOnMenuItemClickListener { proxyItem ->
			val realItem = masterMenu.findItem(proxyItem.itemId) ?: return@setOnMenuItemClickListener false
			handleMenuItemClicked(masterMenu, wrapReactive(realItem, isLeft), isLeft)
			true
		}
		anchor.setOnClickListener { popup.show() }

		if (isLeft) leftOverflowPopup = popup else rightOverflowPopup = popup
	}

	private fun handleMenuItemClicked(menu: Menu, item: MenuItem, isLeft: Boolean) {
		if (!item.isEnabled) return

		val clickListener = if (isLeft) onLeftItemMenuClickListener else onRightItemMenuClickListener
		val changedListener = if (isLeft) onLeftItemMenuChangedListener else onRightItemMenuChangedListener
		val currentSelectedId = if (isLeft) selectedLeftItemId else selectedRightItemId

		clickListener?.onMenuItemClicked(item)

		if (item.itemId == currentSelectedId) {
			changedListener?.onMenuReselect(item)
			return
		}

		val previousItem = menu.findItem(currentSelectedId)
		previousItem?.isChecked = false
		item.isChecked = true

		if (isLeft) selectedLeftItemId = item.itemId else selectedRightItemId = item.itemId

		if (previousItem != null) changedListener?.onMenuUnselect(wrapReactive(previousItem, isLeft))
		changedListener?.onMenuSelect(item)
	}

	private fun handleMenuItemLongClicked(menu: Menu, item: MenuItem, isLeft: Boolean): Boolean {
		if (!item.isEnabled) return false

		val longClickListener = if (isLeft) onLeftItemMenuLongClickListener else onRightItemMenuLongClickListener
		val longChangedListener = if (isLeft) onLeftItemMenuLongChangedListener else onRightItemMenuLongChangedListener
		val consumed = longClickListener?.onMenuItemLongClicked(item) ?: false

		val currentLongSelectedId = if (isLeft) longSelectedLeftItemId else longSelectedRightItemId
		if (item.itemId == currentLongSelectedId) {
			longChangedListener?.onMenuLongReselect(item)
		} else {
			val previousItem = menu.findItem(currentLongSelectedId)
			if (isLeft) longSelectedLeftItemId = item.itemId else longSelectedRightItemId = item.itemId
			if (previousItem != null) longChangedListener?.onMenuLongUnselect(wrapReactive(previousItem, isLeft))
			longChangedListener?.onMenuLongSelect(item)
		}
		return consumed
	}

	// endregion

	// region AppBarLayout
	//
	// Auto-hide/collapse is driven through app:layout_scrollFlags, exactly
	// the way androidx.appcompat.widget.Toolbar / MaterialToolbar do it: the
	// AppBarLayout is the one resolving scroll against the CoordinatorLayout,
	// not a Behavior owned by the Toolbar itself.
	// attachToAppBarLayout()/setAppBarScrollFlags() are just helpers so you
	// don't have to write the LayoutParams by hand.

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

	// endregion

	// region Ecosystem · Window insets (edge-to-edge)

	/**
	 * When `true`, automatically adds top padding equal to the status bar
	 * height on edge-to-edge windows. Off by default so it never surprises
	 * a project that already handles insets on its own container.
	 */
	fun setHandleWindowInsets(enabled: Boolean) {
		if (enabled) {
			ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
				val statusBarInsets = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())
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

	// region Manual measurement and layout

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val widthMode = MeasureSpec.getMode(widthMeasureSpec)
		val availableWidth = MeasureSpec.getSize(widthMeasureSpec) - paddingStart - paddingEnd -
				contentInsetStart - contentInsetEnd

		measureFixedChild(navigationIconView, iconTouchTargetSize)
		measureFixedChild(logoView, iconTouchTargetSize)

		val fixedSideWidth = navigationIconView.measuredWidthOrZero() + logoView.measuredWidthOrZero()
		val remainingForMenus = max(0, availableWidth - fixedSideWidth - minTitleReserve)
		// Simple 50/50 split between both sides: keeps one side from hogging
		// all the space at the other's expense (see populateMenuSide KDoc).
		val perSideMenuCap = remainingForMenus / 2

		measureWrapContentChild(leftMenuView, perSideMenuCap)
		measureWrapContentChild(rightMenuView, perSideMenuCap)

		val sideBlocksWidth = fixedSideWidth + leftMenuView.measuredWidthOrZero() + rightMenuView.measuredWidthOrZero() +
				titleBlockHorizontalMargin * 2

		val titleAvailableWidth = max(0, availableWidth - sideBlocksWidth)
		val titleWidthSpec = MeasureSpec.makeMeasureSpec(
			titleAvailableWidth,
			if (widthMode == MeasureSpec.UNSPECIFIED) MeasureSpec.UNSPECIFIED else MeasureSpec.AT_MOST
		)

		measureChild(titleView, titleWidthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
		measureChild(subtitleView, titleWidthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))

		val titleBlockHeight = titleView.measuredHeightOrZero() + subtitleView.measuredHeightOrZero() +
				titleMarginTop + titleMarginBottom

		val tallestChild = max(
			titleBlockHeight,
			max(
				navigationIconView.measuredHeightOrZero(),
				max(logoView.measuredHeightOrZero(), max(leftMenuView.measuredHeightOrZero(), rightMenuView.measuredHeightOrZero()))
			)
		)

		val desiredHeight = max(minimumHeight, tallestChild + paddingTop + paddingBottom)
		val resolvedWidth = resolveSize(suggestedMinimumWidth, widthMeasureSpec)
		val resolvedHeight = resolveSize(desiredHeight, heightMeasureSpec)

		setMeasuredDimension(resolvedWidth, resolvedHeight)
	}

	override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
		val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
		val height = b - t
		var cursorStart = paddingStart + contentInsetStart
		var cursorEnd = (r - l) - paddingEnd - contentInsetEnd

		cursorStart = layoutChildAtStart(navigationIconView, cursorStart, height, isRtl, l, r)
		cursorStart = layoutChildAtStart(leftMenuView, cursorStart, height, isRtl, l, r)
		cursorStart = layoutChildAtStart(logoView, cursorStart, height, isRtl, l, r)

		cursorEnd = layoutChildAtEnd(rightMenuView, cursorEnd, height, isRtl, l, r)

		val titleBlockLeftEdge = cursorStart + titleBlockHorizontalMargin
		val titleBlockRightEdge = cursorEnd - titleBlockHorizontalMargin
		layoutTitleSubtitleBlock(titleBlockLeftEdge, titleBlockRightEdge, height, isRtl, l, r)
	}

	private fun layoutChildAtStart(child: View, cursorStart: Int, parentHeight: Int, isRtl: Boolean, l: Int, r: Int): Int {
		if (child.visibility == GONE) return cursorStart
		val childWidth = child.measuredWidth
		val childHeight = child.measuredHeight
		val top = (parentHeight - childHeight) / 2

		if (isRtl) {
			val right = (r - l) - cursorStart
			val left = right - childWidth
			child.layout(left, top, right, top + childHeight)
		} else {
			child.layout(cursorStart, top, cursorStart + childWidth, top + childHeight)
		}
		return cursorStart + childWidth
	}

	private fun layoutChildAtEnd(child: View, cursorEnd: Int, parentHeight: Int, isRtl: Boolean, l: Int, r: Int): Int {
		if (child.visibility == GONE) return cursorEnd
		val childWidth = child.measuredWidth
		val childHeight = child.measuredHeight
		val top = (parentHeight - childHeight) / 2

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

	/**
	 * Lays out the title and subtitle always spanning the full width between
	 * [leftEdge] and [rightEdge]; the actual alignment is resolved by each
	 * TextView's own `gravity` (see [resolveGravity]), not by manually
	 * shrinking the layout rectangle -this way each line can align
	 * differently without one pushing the other around.
	 */
	private fun layoutTitleSubtitleBlock(leftEdge: Int, rightEdge: Int, parentHeight: Int, isRtl: Boolean, l: Int, r: Int) {
		val titleVisible = titleView.visibility != GONE
		val subtitleVisible = subtitleView.visibility != GONE

		val blockHeight = titleView.measuredHeightOrZero() + subtitleView.measuredHeightOrZero()
		var top = (parentHeight - blockHeight) / 2 + titleMarginTop

		val startX: Int
		val endX: Int
		if (isRtl) {
			startX = (r - l) - rightEdge - titleMarginEnd
			endX = (r - l) - leftEdge - titleMarginStart
		} else {
			startX = leftEdge + titleMarginStart
			endX = rightEdge - titleMarginEnd
		}

		if (titleVisible) {
			titleView.gravity = resolveGravity(titleAlignment) or Gravity.CENTER_VERTICAL
			titleView.layout(startX, top, endX, top + titleView.measuredHeight)
			top += titleView.measuredHeight
		}
		if (subtitleVisible) {
			subtitleView.gravity = resolveGravity(subtitleAlignment) or Gravity.CENTER_VERTICAL
			subtitleView.layout(startX, top, endX, top + subtitleView.measuredHeight)
		}
	}

	/** Gravity.START/END are already direction-aware: Android resolves them for RTL on its own. */
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

	// region XML attributes
	//
	// Requires declaring the `ComponentToolbar` styleable (see the attached
	// `attrs_component_toolbar.xml`) in your module's `res/values/`. The `R`
	// class referenced here is the one your build generates for your
	// module's namespace -if your package isn't
	// `com.deavidig.components.toolbar`, adjust the `R` import to yours.

	private fun applyXmlAttributes(attributeSet: AttributeSet, defStyleAttr: Int) {
		val typedArray = context.obtainStyledAttributes(attributeSet, R.styleable.ComponentToolbar, defStyleAttr, 0)
		try {
			typedArray.getString(R.styleable.ComponentToolbar_title)?.let { setTitle(it) }
			typedArray.getString(R.styleable.ComponentToolbar_subtitle)?.let { setSubtitle(it) }

			if (typedArray.hasValue(R.styleable.ComponentToolbar_titleTextColor)) {
				setTitleTextColor(typedArray.getColor(R.styleable.ComponentToolbar_titleTextColor, titleView.currentTextColor))
			}
			if (typedArray.hasValue(R.styleable.ComponentToolbar_titleTextSize)) {
				val sizePx = typedArray.getDimension(R.styleable.ComponentToolbar_titleTextSize, titleView.textSize)
				setTitleTextSize(sizePx / resources.displayMetrics.scaledDensity)
			}
			typedArray.getResourceId(R.styleable.ComponentToolbar_titleTextAppearance, 0)
				.takeIf { it != 0 }?.let { setTitleTextAppearance(it) }

			if (typedArray.hasValue(R.styleable.ComponentToolbar_subtitleTextColor)) {
				setSubtitleTextColor(typedArray.getColor(R.styleable.ComponentToolbar_subtitleTextColor, subtitleView.currentTextColor))
			}
			if (typedArray.hasValue(R.styleable.ComponentToolbar_subtitleTextSize)) {
				val sizePx = typedArray.getDimension(R.styleable.ComponentToolbar_subtitleTextSize, subtitleView.textSize)
				setSubtitleTextSize(sizePx / resources.displayMetrics.scaledDensity)
			}
			typedArray.getResourceId(R.styleable.ComponentToolbar_subtitleTextAppearance, 0)
				.takeIf { it != 0 }?.let { setSubtitleTextAppearance(it) }

			typedArray.getInt(R.styleable.ComponentToolbar_titleAlignment, titleAlignment.ordinal)
				.let { setTitleAlignment(HorizontalAlignment.values()[it]) }
			typedArray.getInt(R.styleable.ComponentToolbar_subtitleAlignment, subtitleAlignment.ordinal)
				.let { setSubtitleAlignment(HorizontalAlignment.values()[it]) }

			typedArray.getDrawable(R.styleable.ComponentToolbar_navigationIcon)?.let { setNavigationIcon(it) }
			if (typedArray.hasValue(R.styleable.ComponentToolbar_navigationIconTint)) {
				setNavigationIconTint(typedArray.getColor(R.styleable.ComponentToolbar_navigationIconTint, Color.BLACK))
			}
			typedArray.getString(R.styleable.ComponentToolbar_navigationContentDescription)?.let { setNavigationContentDescription(it) }

			typedArray.getDrawable(R.styleable.ComponentToolbar_logo)?.let { setLogo(it) }
			if (typedArray.hasValue(R.styleable.ComponentToolbar_logoTint)) {
				setLogoTint(typedArray.getColor(R.styleable.ComponentToolbar_logoTint, Color.BLACK))
			}
			typedArray.getString(R.styleable.ComponentToolbar_logoContentDescription)?.let { setLogoContentDescription(it) }

			typedArray.getResourceId(R.styleable.ComponentToolbar_leftMenu, 0).takeIf { it != 0 }?.let { inflateLeftMenu(it) }
			typedArray.getResourceId(R.styleable.ComponentToolbar_rightMenu, 0).takeIf { it != 0 }?.let { inflateRightMenu(it) }
			typedArray.getDrawable(R.styleable.ComponentToolbar_leftOverflowIcon)?.let { setLeftOverflowIcon(it) }
			typedArray.getDrawable(R.styleable.ComponentToolbar_rightOverflowIcon)?.let { setRightOverflowIcon(it) }

			if (typedArray.hasValue(R.styleable.ComponentToolbar_contentInsetStartWithNavigation)) {
				setContentInsetStartWithNavigation(
					typedArray.getDimensionPixelSize(R.styleable.ComponentToolbar_contentInsetStartWithNavigation, contentInsetStart)
				)
			}
			if (typedArray.hasValue(R.styleable.ComponentToolbar_contentInsetEnd)) {
				setContentInsetEnd(typedArray.getDimensionPixelSize(R.styleable.ComponentToolbar_contentInsetEnd, contentInsetEnd))
			}
		} finally {
			typedArray.recycle()
		}
	}

	// endregion

	// region Default Material 3 styling
	//
	// Color/typography roles are read from the ACTIVE THEME -nothing is
	// hardcoded here- so if the app uses a custom Material 3 theme (with its
	// own `colorOnSurface`/`colorOnSurfaceVariant`/`textAppearanceTitleLarge`),
	// ComponentToolbar inherits those values automatically, exactly like a
	// real `MaterialToolbar` would. If the theme doesn't define those
	// attributes (e.g. a theme that doesn't extend Material 3), this falls
	// back safely to the defaults the component already had.

	private fun applyMaterial3Defaults() {
		val onSurface = resolveThemeColor(com.google.android.material.R.attr.colorOnSurface, resolveDefaultIconTintColor())
		val onSurfaceVariant = resolveThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant, onSurface)

		resolveThemeAttrResId(com.google.android.material.R.attr.textAppearanceTitleLarge)
			.takeIf { it != 0 }?.let { setTitleTextAppearance(it) }
		resolveThemeAttrResId(com.google.android.material.R.attr.textAppearanceTitleMedium)
			.takeIf { it != 0 }?.let { setSubtitleTextAppearance(it) }

		setNavigationIconTint(onSurface)
		setLogoTint(onSurface)
		menuIconTint = onSurfaceVariant
		applyOverflowButtonTint()
	}

	// endregion

	// region Ecosystem · Saved state (rotation / process recreation)
	//
	// Without this, menu selection and alignment settings would be lost on
	// every screen rotation -this requires the view to have an `id` (from
	// XML or `View.generateViewId()`) so Android's standard state-saving
	// mechanism picks it up automatically.

	override fun onSaveInstanceState(): Parcelable {
		val superState = super.onSaveInstanceState()
		val state = SavedState(superState)
		state.selectedLeftItemId = selectedLeftItemId
		state.selectedRightItemId = selectedRightItemId
		state.titleAlignmentOrdinal = titleAlignment.ordinal
		state.subtitleAlignmentOrdinal = subtitleAlignment.ordinal
		return state
	}

	override fun onRestoreInstanceState(state: Parcelable?) {
		if (state !is SavedState) {
			super.onRestoreInstanceState(state)
			return
		}
		super.onRestoreInstanceState(state.superState)

		selectedLeftItemId = state.selectedLeftItemId
		selectedRightItemId = state.selectedRightItemId
		titleAlignment = HorizontalAlignment.values()[state.titleAlignmentOrdinal]
		subtitleAlignment = HorizontalAlignment.values()[state.subtitleAlignmentOrdinal]

		leftMenuHolder?.menu?.findItem(selectedLeftItemId)?.isChecked = true
		rightMenuHolder?.menu?.findItem(selectedRightItemId)?.isChecked = true
		requestLayout()
	}

	private class SavedState : BaseSavedState {
		var selectedLeftItemId: Int = NO_ITEM_ID
		var selectedRightItemId: Int = NO_ITEM_ID
		var titleAlignmentOrdinal: Int = 0
		var subtitleAlignmentOrdinal: Int = 0

		constructor(superState: Parcelable?) : super(superState)

		constructor(parcel: Parcel) : super(parcel) {
			selectedLeftItemId = parcel.readInt()
			selectedRightItemId = parcel.readInt()
			titleAlignmentOrdinal = parcel.readInt()
			subtitleAlignmentOrdinal = parcel.readInt()
		}

		override fun writeToParcel(out: Parcel, flags: Int) {
			super.writeToParcel(out, flags)
			out.writeInt(selectedLeftItemId)
			out.writeInt(selectedRightItemId)
			out.writeInt(titleAlignmentOrdinal)
			out.writeInt(subtitleAlignmentOrdinal)
		}

		companion object CREATOR : Parcelable.Creator<SavedState> {
			override fun createFromParcel(parcel: Parcel) = SavedState(parcel)
			override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
		}
	}

	// endregion

	// region Theme helpers

	private fun dpToPx(dp: Int): Int =
		TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp.toFloat(), resources.displayMetrics).toInt()

	private fun resolveActionBarSize(): Int {
		val typedValue = TypedValue()
		return if (context.theme.resolveAttribute(android.R.attr.actionBarSize, typedValue, true)) {
			TypedValue.complexToDimensionPixelSize(typedValue.data, resources.displayMetrics)
		} else {
			dpToPx(56)
		}
	}

	/**
	 * A bounded ripple (smaller than the 48dp touch target), instead of
	 * Android's default borderless ripple that visually "inflates" the
	 * circle until it fills the whole view. Achieved by clipping a circular
	 * [RippleDrawable] with an [InsetDrawable].
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

	private fun resolveSelectableItemBackgroundBorderless(): Drawable? {
		val typedValue = TypedValue()
		val resolved = context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, typedValue, true)
		return if (resolved) ContextCompat.getDrawable(context, typedValue.resourceId) else null
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

	// endregion

	/**
	 * A hand-drawn three-dot drawable for the overflow button, so it does
	 * not depend on a vector resource the consuming project may or may not
	 * declare. Replaceable at any time with
	 * [setLeftOverflowIcon]/[setRightOverflowIcon].
	 */
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
		override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
		override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
	}
}