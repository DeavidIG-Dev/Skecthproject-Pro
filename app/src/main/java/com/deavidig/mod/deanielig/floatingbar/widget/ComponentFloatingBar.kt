package com.deavidig.mod.deanielig.floatingbar.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.shapes.OvalShape
import android.util.AttributeSet
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.annotation.ColorInt
import androidx.annotation.IdRes
import androidx.annotation.MenuRes
import androidx.appcompat.widget.AppCompatImageButton
import androidx.appcompat.widget.TooltipCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.widget.ImageViewCompat
import com.deavidig.mod.deanielig.floatingbar.widget.ComponentFloatingBar.Companion.HORIZONTAL
import com.deavidig.mod.deanielig.floatingbar.widget.ComponentFloatingBar.Companion.VERTICAL
import com.deavidig.sketchprojectpro.R
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.shape.Shapeable
import kotlin.math.max
import kotlin.math.roundToInt
import com.google.android.material.R as MR

/**
 * A Material 3 floating bar: the View-system counterpart of Compose's `FloatingToolbar`.
 *
 * The bar is a pill-shaped, elevated container that hosts, along its main axis:
 *
 *  1. the icons of the left menu ([getLeftMenu]; first / top),
 *  2. an optional [FloatingActionButton] **or** [ExtendedFloatingActionButton] declared as the
 *     only child in XML,
 *  3. the icons of the right menu ([getRightMenu]; last / bottom).
 *
 * There is no title, subtitle, overflow or any other slot: only menus and the optional action.
 *
 * ### XML
 * ```xml
 * <com.deavidig.mod.deanielig.floatingbar.widget.ComponentFloatingBar
 *     android:layout_width="wrap_content"
 *     android:layout_height="wrap_content"
 *     app:leftMenu="@menu/floating_left"
 *     app:rightMenu="@menu/floating_right">
 *
 *     <com.google.android.material.floatingactionbutton.FloatingActionButton
 *         android:layout_width="wrap_content"
 *         android:layout_height="wrap_content"
 *         app:srcCompat="@drawable/ic_add" />
 *
 * </com.deavidig.mod.deanielig.floatingbar.widget.ComponentFloatingBar>
 * ```
 *
 * ### Sizing
 *  - The cross-axis size (height when horizontal) comes from `layout_height`. With
 *    `wrap_content` it is the smallest possible: 48dp icon + bar padding (64dp by default).
 *  - The main-axis size grows with every menu item that is added.
 *  - The action view always fills the cross axis minus the bar padding (so it is a bit smaller
 *    than the bar) and is forced into a pill shape.
 *  - A regular [FloatingActionButton] hard-codes a square measure, so the bar gives it a
 *    capsule frame of `crossSize * fabWidthRatio` at layout time (`1f` keeps it circular).
 *    [ExtendedFloatingActionButton] measures itself normally and only has its height forced.
 *  - The action view is centred in the bar and clamped between both menus.
 *
 * ### Click, long-click and selection
 * Every menu owns an independent set of listeners and state (`Left` / `Right` in each name):
 *  - [OnItemMenuClickListener] / [OnItemMenuLongClickListener] fire only for the icons of that menu.
 *  - Items declared `android:checkable="true"` form one single selection **per menu**, drawn with
 *    an M3 active indicator, and drive [OnItemMenuChangedListener] (select / unselect / reselect).
 *    `MenuItem.isChecked` is synced best-effort; [getLeftSelectedItemId] / [getRightSelectedItemId]
 *    are the source of truth.
 *  - Long-press has its own lifecycle ([OnItemMenuLongChangedListener]) for every item, kept apart
 *    from the checked state and without visual change.
 *
 * ### Notes
 *  - All state is private; it is read and written only through the `get*` / `set*` functions.
 *  - Menu items without icon or not visible are skipped. Mutate [getLeftMenu] / [getRightMenu] and
 *    call [invalidateMenus] to refresh.
 *  - The action view's elevation is set to 0 on attach, because it already floats over the bar.
 *  - In RTL the horizontal bar mirrors; when vertical, the left menu is on top and the right menu
 *    at the bottom.
 */
class ComponentFloatingBar @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = R.attr.componentFloatingBarStyle
) : ViewGroup(context, attrs, defStyleAttr, R.style.Widget_ComponentMaterial_ComponentFloatingBar) {

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

	/** Everything that belongs to one side: its [Menu], its icons, its listeners and its selection state. */
	private inner class MenuSide(val atStart: Boolean) {
		val menu: Menu by lazy(LazyThreadSafetyMode.NONE) {
			PopupMenu(
				context,
				this@ComponentFloatingBar
			).menu
		}
		val buttons = ArrayList<View>()

		var clickListener: OnItemMenuClickListener? = null
		var longClickListener: OnItemMenuLongClickListener? = null
		var changedListener: OnItemMenuChangedListener? = null
		var longChangedListener: OnItemMenuLongChangedListener? = null

		/** Source of truth for the click-driven selection (`MenuItem.isChecked` is synced best-effort). */
		var selectedItem: MenuItem? = null

		/** State of the independent long-press channel; never touches the checked state. */
		var longSelectedItem: MenuItem? = null
	}

	// Declared before `init`: property initializers run after the super constructor.
	private val buttonSize = dp(48)
	private val rippleInset = dp(4) // 48dp - 2 * 4dp = 40dp indicator / ripple
	private val leftSide = MenuSide(atStart = true)
	private val rightSide = MenuSide(atStart = false)
	private val leftButtons = leftSide.buttons
	private val rightButtons = rightSide.buttons
	private var actionView: View? = null

	private val containerDrawable = MaterialShapeDrawable(
		ShapeAppearanceModel.builder().setAllCornerSizes(RelativeCornerSize(0.5f)).build()
	)

	private var orientation: Int = HORIZONTAL
	private var fabWidthRatio: Float = 1.5f
	private var containerColor: Int = Color.TRANSPARENT
	private var menuIconTint: ColorStateList = defaultMenuIconTint()

	init {
		clipChildren = false
		clipToPadding = false
		clipToOutline = false

		val a = context.obtainStyledAttributes(
			attrs,
			R.styleable.ComponentFloatingBar,
			defStyleAttr,
			R.style.Widget_ComponentMaterial_ComponentFloatingBar
		)
		try {
			setOrientation(
				a.getInt(
					R.styleable.ComponentFloatingBar_android_orientation,
					HORIZONTAL
				)
			)
			setFabWidthRatio(
				a.getFloat(
					R.styleable.ComponentFloatingBar_floatingBarFabWidthRatio,
					1.5f
				)
			)
			setContainerColor(
				a.getColor(
					R.styleable.ComponentFloatingBar_floatingBarContainerColor,
					defaultContainerColor()
				)
			)
			a.getColorStateList(R.styleable.ComponentFloatingBar_floatingBarIconTint)
				?.let { setMenuIconTint(it) }

			val left = a.getResourceId(R.styleable.ComponentFloatingBar_leftMenu, 0)
			val right = a.getResourceId(R.styleable.ComponentFloatingBar_rightMenu, 0)
			if (left != 0) setLeftMenu(left)
			if (right != 0) setRightMenu(right)
		} finally {
			a.recycle()
		}

		containerDrawable.initializeElevationOverlay(context)
		containerDrawable.elevation = elevation
		ViewCompat.setBackground(this, containerDrawable)
	}

	// ---------------------------------------------------------------------------------------------
	// Public API: appearance
	// ---------------------------------------------------------------------------------------------

	/** Layout axis: [HORIZONTAL] (default) or [VERTICAL]. Maps to `android:orientation`. */
	fun getOrientation(): Int = orientation

	fun setOrientation(value: Int) {
		require(value == HORIZONTAL || value == VERTICAL) { "orientation must be HORIZONTAL or VERTICAL" }
		if (orientation != value) {
			orientation = value
			requestLayout()
		}
	}

	/** Width of a plain [FloatingActionButton] relative to its height (`>= 1`). `1f` = circle. */
	fun getFabWidthRatio(): Float = fabWidthRatio

	fun setFabWidthRatio(value: Float) {
		require(value >= 1f) { "fabWidthRatio must be >= 1" }
		if (fabWidthRatio != value) {
			fabWidthRatio = value
			requestLayout()
		}
	}

	/** Fill color of the pill container. */
	@ColorInt
	fun getContainerColor(): Int = containerColor

	fun setContainerColor(@ColorInt color: Int) {
		containerColor = color
		containerDrawable.fillColor = ColorStateList.valueOf(color)
	}

	/** Tint applied to every menu icon (both menus). Changing it rebuilds the icons. */
	fun getMenuIconTint(): ColorStateList = menuIconTint

	fun setMenuIconTint(tint: ColorStateList) {
		menuIconTint = tint
		invalidateMenus()
	}

	// ---------------------------------------------------------------------------------------------
	// Public API: menus
	// ---------------------------------------------------------------------------------------------

	/** Menu shown before the action view (left, or top when vertical). */
	fun getLeftMenu(): Menu = leftSide.menu

	/** Menu shown after the action view (right, or bottom when vertical). */
	fun getRightMenu(): Menu = rightSide.menu

	/** Replaces the content of the left menu with [menuRes] (`0` clears it). */
	fun setLeftMenu(@MenuRes menuRes: Int) = inflateMenu(leftSide, menuRes)

	/** Replaces the content of the right menu with [menuRes] (`0` clears it). */
	fun setRightMenu(@MenuRes menuRes: Int) = inflateMenu(rightSide, menuRes)

	/** Rebuilds the icons after the left / right menu were mutated programmatically. */
	fun invalidateMenus() {
		syncItemState(leftSide)
		syncItemState(rightSide)
		rebuildButtons(leftSide)
		rebuildButtons(rightSide)
	}

	// ---------------------------------------------------------------------------------------------
	// Public API: listeners (one independent set per menu)
	// ---------------------------------------------------------------------------------------------

	/** Called for every click on an icon of the left menu, before any selection change. */
	fun getOnLeftItemMenuClickListener(): OnItemMenuClickListener? = leftSide.clickListener

	fun setOnLeftItemMenuClickListener(listener: OnItemMenuClickListener?) {
		leftSide.clickListener = listener
	}

	/**
	 * Called on long-press of an icon of the left menu. Return `true` to consume it. When any
	 * long-press listener of that menu is set the gesture is consumed, so the system tooltip is
	 * not shown (and no click follows).
	 */
	fun getOnLeftItemMenuLongClickListener(): OnItemMenuLongClickListener? =
		leftSide.longClickListener

	fun setOnLeftItemMenuLongClickListener(listener: OnItemMenuLongClickListener?) {
		leftSide.longClickListener = listener
	}

	/** Selection lifecycle driven by clicks on the `android:checkable="true"` items of the left menu. */
	fun getOnLeftItemMenuChangedListener(): OnItemMenuChangedListener? = leftSide.changedListener

	fun setOnLeftItemMenuChangedListener(listener: OnItemMenuChangedListener?) {
		leftSide.changedListener = listener
	}

	/** Independent selection lifecycle driven by long-press, for every item of the left menu. */
	fun getOnLeftItemMenuLongChangedListener(): OnItemMenuLongChangedListener? =
		leftSide.longChangedListener

	fun setOnLeftItemMenuLongChangedListener(listener: OnItemMenuLongChangedListener?) {
		leftSide.longChangedListener = listener
	}

	/** Called for every click on an icon of the right menu, before any selection change. */
	fun getOnRightItemMenuClickListener(): OnItemMenuClickListener? = rightSide.clickListener

	fun setOnRightItemMenuClickListener(listener: OnItemMenuClickListener?) {
		rightSide.clickListener = listener
	}

	/** Right-menu counterpart of [getOnLeftItemMenuLongClickListener]. */
	fun getOnRightItemMenuLongClickListener(): OnItemMenuLongClickListener? =
		rightSide.longClickListener

	fun setOnRightItemMenuLongClickListener(listener: OnItemMenuLongClickListener?) {
		rightSide.longClickListener = listener
	}

	/** Selection lifecycle driven by clicks on the `android:checkable="true"` items of the right menu. */
	fun getOnRightItemMenuChangedListener(): OnItemMenuChangedListener? = rightSide.changedListener

	fun setOnRightItemMenuChangedListener(listener: OnItemMenuChangedListener?) {
		rightSide.changedListener = listener
	}

	/** Independent selection lifecycle driven by long-press, for every item of the right menu. */
	fun getOnRightItemMenuLongChangedListener(): OnItemMenuLongChangedListener? =
		rightSide.longChangedListener

	fun setOnRightItemMenuLongChangedListener(listener: OnItemMenuLongChangedListener?) {
		rightSide.longChangedListener = listener
	}

	// ---------------------------------------------------------------------------------------------
	// Public API: selection state (per menu)
	// ---------------------------------------------------------------------------------------------

	/** Id of the selected item of the left menu, or [View.NO_ID]. */
	fun getLeftSelectedItemId(): Int = leftSide.selectedItem?.itemId ?: View.NO_ID

	/** Behaves like tapping that item (callbacks fire). The item must be checkable. */
	fun setLeftSelectedItemId(@IdRes itemId: Int) = selectItemById(leftSide, itemId)

	/** Id of the selected item of the right menu, or [View.NO_ID]. */
	fun getRightSelectedItemId(): Int = rightSide.selectedItem?.itemId ?: View.NO_ID

	/** Behaves like tapping that item (callbacks fire). The item must be checkable. */
	fun setRightSelectedItemId(@IdRes itemId: Int) = selectItemById(rightSide, itemId)

	/** Id of the long-selected item of the left menu, or [View.NO_ID]. */
	fun getLeftLongSelectedItemId(): Int = leftSide.longSelectedItem?.itemId ?: View.NO_ID

	/** Behaves like long-pressing that item, without the long-click callback. */
	fun setLeftLongSelectedItemId(@IdRes itemId: Int) = longSelectItemById(leftSide, itemId)

	/** Id of the long-selected item of the right menu, or [View.NO_ID]. */
	fun getRightLongSelectedItemId(): Int = rightSide.longSelectedItem?.itemId ?: View.NO_ID

	/** Behaves like long-pressing that item, without the long-click callback. */
	fun setRightLongSelectedItemId(@IdRes itemId: Int) = longSelectItemById(rightSide, itemId)

	// ---------------------------------------------------------------------------------------------
	// Public API: action view
	// ---------------------------------------------------------------------------------------------

	/** The declared action view (FAB or EFAB), or `null` when there is none. */
	fun getActionView(): View? = actionView

	/** The declared [FloatingActionButton], or `null` if absent or if an EFAB is used instead. */
	fun getFloatingActionButton(): FloatingActionButton? = actionView as? FloatingActionButton

	/** The declared [ExtendedFloatingActionButton], or `null` if absent or if a FAB is used instead. */
	fun getExtendedFloatingActionButton(): ExtendedFloatingActionButton? =
		actionView as? ExtendedFloatingActionButton

	// ---------------------------------------------------------------------------------------------
	// Children management
	// ---------------------------------------------------------------------------------------------

	/** Only the single FAB / EFAB can be added from outside; menu icons are managed internally. */
	override fun addView(child: View, index: Int, params: LayoutParams) {
		require(child is FloatingActionButton || child is ExtendedFloatingActionButton) {
			"ComponentFloatingBar only accepts a FloatingActionButton or ExtendedFloatingActionButton as child"
		}
		check(actionView == null) { "ComponentFloatingBar accepts a single FAB / EFAB" }
		actionView = child
		prepareActionView(child)
		// Keeps focus order: left icons -> action -> right icons.
		super.addView(child, leftButtons.size, params)
	}

	override fun onViewRemoved(child: View) {
		super.onViewRemoved(child)
		if (child === actionView) actionView = null
	}

	private fun prepareActionView(view: View) {
		val shapeable = view as Shapeable
		shapeable.shapeAppearanceModel = shapeable.shapeAppearanceModel.toBuilder()
			.setAllCornerSizes(RelativeCornerSize(0.5f))
			.build()
		view.elevation = 0f
	}

	private fun inflateMenu(side: MenuSide, @MenuRes menuRes: Int) {
		side.menu.clear()
		if (menuRes != 0) MenuInflater(context).inflate(menuRes, side.menu)
		syncItemState(side)
		rebuildButtons(side)
	}

	private fun rebuildButtons(side: MenuSide) {
		for (old in side.buttons) removeView(old)
		side.buttons.clear()

		val menu = side.menu
		var insertAt = if (side.atStart) 0 else -1
		for (i in 0 until menu.size()) {
			val item = menu.getItem(i)
			if (!item.isVisible || item.icon == null) continue
			val button = createButton(side, item)
			super.addView(button, insertAt, generateDefaultLayoutParams())
			if (side.atStart) insertAt++
			side.buttons += button
		}
		requestLayout()
	}

	private fun createButton(side: MenuSide, item: MenuItem): View =
		AppCompatImageButton(context).apply {
			setImageDrawable(item.icon)
			ImageViewCompat.setImageTintList(this, menuIconTint)
			background = createButtonBackground()
			contentDescription = item.title
			TooltipCompat.setTooltipText(this, item.title)
			isEnabled = item.isEnabled
			tag = item
			isSelected = item === side.selectedItem
			setOnClickListener { handleClick(side, item) }
			setOnLongClickListener { handleLongClick(side, item) }
		}

	/** 40dp circular M3 active indicator (only when selected) + ripple, inside the 48dp touch target. */
	private fun createButtonBackground(): Drawable {
		val indicatorColor =
			MaterialColors.getColor(context, MR.attr.colorSecondaryContainer, Color.LTGRAY)
		val indicator = StateListDrawable().apply {
			addState(
				intArrayOf(android.R.attr.state_selected),
				ShapeDrawable(OvalShape()).apply { paint.color = indicatorColor }
			)
			addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
		}
		val rippleColor = ColorUtils.setAlphaComponent(menuIconTint.defaultColor, 0x1F)
		val mask = ShapeDrawable(OvalShape()).apply { paint.color = Color.WHITE }
		val ripple = RippleDrawable(ColorStateList.valueOf(rippleColor), null, mask)
		return InsetDrawable(LayerDrawable(arrayOf(indicator, ripple)), rippleInset)
	}

	// ---------------------------------------------------------------------------------------------
	// Click / long-click / selection (always scoped to one side)
	// ---------------------------------------------------------------------------------------------

	private fun handleClick(side: MenuSide, item: MenuItem) {
		side.clickListener?.onMenuItemClicked(item)
		if (item.isCheckable) dispatchSelection(side, item)
	}

	/** Single selection inside one menu: unselect previous -> select new, or reselect. */
	private fun dispatchSelection(side: MenuSide, item: MenuItem) {
		val previous = side.selectedItem
		if (previous === item) {
			side.changedListener?.onMenuReselect(item)
			return
		}
		side.selectedItem = item
		item.isChecked = true
		// Best effort: items in an exclusive group are already unchecked by the menu itself.
		if (previous != null && previous.isChecked) previous.isChecked = false
		refreshSelectedState(side)
		side.changedListener?.let {
			if (previous != null) it.onMenuUnselect(previous)
			it.onMenuSelect(item)
		}
	}

	/** Independent long-press channel. Returns whether the gesture is consumed. */
	private fun handleLongClick(side: MenuSide, item: MenuItem): Boolean {
		val consumed = side.longClickListener?.onMenuItemLongClicked(item) ?: false
		dispatchLongSelection(side, item)
		return consumed || side.longChangedListener != null
	}

	private fun dispatchLongSelection(side: MenuSide, item: MenuItem) {
		val previous = side.longSelectedItem
		val listener = side.longChangedListener
		if (previous === item) {
			listener?.onMenuLongReselect(item)
			return
		}
		side.longSelectedItem = item
		listener?.let {
			if (previous != null) it.onMenuLongUnselect(previous)
			it.onMenuLongSelect(item)
		}
	}

	private fun selectItemById(side: MenuSide, itemId: Int) {
		val item = side.menu.findItem(itemId)
		requireNotNull(item) { "No menu item with id $itemId" }
		require(item.isCheckable) { "Menu item $itemId is not checkable" }
		dispatchSelection(side, item)
	}

	private fun longSelectItemById(side: MenuSide, itemId: Int) {
		val item = side.menu.findItem(itemId)
		requireNotNull(item) { "No menu item with id $itemId" }
		dispatchLongSelection(side, item)
	}

	private fun refreshSelectedState(side: MenuSide) {
		for (b in side.buttons) b.isSelected = b.tag === side.selectedItem
	}

	/** Drops state of items that no longer exist and picks up an initial `android:checked` item. */
	private fun syncItemState(side: MenuSide) {
		val menu = side.menu
		val longItem = side.longSelectedItem
		if (longItem != null && !owns(menu, longItem)) side.longSelectedItem = null

		val current = side.selectedItem
		if (current == null || !owns(menu, current) || !current.isCheckable) {
			side.selectedItem = firstChecked(menu)
		}
	}

	private fun owns(menu: Menu, item: MenuItem): Boolean {
		for (i in 0 until menu.size()) if (menu.getItem(i) === item) return true
		return false
	}

	private fun firstChecked(menu: Menu): MenuItem? {
		for (i in 0 until menu.size()) {
			val item = menu.getItem(i)
			if (item.isCheckable && item.isChecked) return item
		}
		return null
	}


	// ---------------------------------------------------------------------------------------------
	// Measure / layout (axis-agnostic: "main" = orientation axis, "cross" = the other one)
	// ---------------------------------------------------------------------------------------------

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val horizontal = orientation == HORIZONTAL
		val mainSpec = if (horizontal) widthMeasureSpec else heightMeasureSpec
		val crossSpec = if (horizontal) heightMeasureSpec else widthMeasureSpec
		val padMain = if (horizontal) paddingStart + paddingEnd else paddingTop + paddingBottom
		val padCross = if (horizontal) paddingTop + paddingBottom else paddingLeft + paddingRight

		val buttonSpec = MeasureSpec.makeMeasureSpec(buttonSize, MeasureSpec.EXACTLY)
		for (b in leftButtons) b.measure(buttonSpec, buttonSpec)
		for (b in rightButtons) b.measure(buttonSpec, buttonSpec)
		val menuMain = (leftButtons.size + rightButtons.size) * buttonSize

		val action = visibleAction()
		val lp = action?.layoutParams as? MarginLayoutParams
		val marginMain = when {
			lp == null -> 0
			horizontal -> lp.marginStart + lp.marginEnd
			else -> lp.topMargin + lp.bottomMargin
		}
		val marginCross = when {
			lp == null -> 0
			horizontal -> lp.topMargin + lp.bottomMargin
			else -> lp.leftMargin + lp.rightMargin
		}

		// Smallest possible cross size; layout_height / layout_width can only make it bigger.
		val crossSize = resolveSize(padCross + buttonSize + marginCross, crossSpec)
		val actionCross = (crossSize - padCross - marginCross).coerceAtLeast(0)

		var actionMain = 0
		if (action != null && lp != null) {
			val crossChildSpec = MeasureSpec.makeMeasureSpec(actionCross, MeasureSpec.EXACTLY)
			if (action is ExtendedFloatingActionButton) {
				// Honors lp.width/height so EFAB extend()/shrink() animations keep working.
				val lpMain = if (horizontal) lp.width else lp.height
				val mainChildSpec = when {
					lpMain >= 0 -> MeasureSpec.makeMeasureSpec(lpMain, MeasureSpec.EXACTLY)
					MeasureSpec.getMode(mainSpec) == MeasureSpec.UNSPECIFIED ->
						MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)

					else -> MeasureSpec.makeMeasureSpec(
						(MeasureSpec.getSize(mainSpec) - padMain - menuMain - marginMain).coerceAtLeast(
							0
						),
						MeasureSpec.AT_MOST
					)
				}
				if (horizontal) action.measure(mainChildSpec, crossChildSpec)
				else action.measure(crossChildSpec, mainChildSpec)
				actionMain = if (horizontal) action.measuredWidth else action.measuredHeight
			} else {
				// FloatingActionButton always measures square; the capsule width is applied in onLayout.
				action.measure(crossChildSpec, crossChildSpec)
				actionMain = (actionCross * fabWidthRatio).roundToInt()
			}
			actionMain += marginMain
		}

		val mainSize = resolveSize(padMain + menuMain + actionMain, mainSpec)
		if (horizontal) setMeasuredDimension(mainSize, crossSize) else setMeasuredDimension(
			crossSize,
			mainSize
		)
	}

	override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
		val horizontal = orientation == HORIZONTAL
		val width = r - l
		val height = b - t
		val mainSize = if (horizontal) width else height
		val crossSize = if (horizontal) height else width
		val padStart = if (horizontal) paddingStart else paddingTop
		val padEnd = if (horizontal) paddingEnd else paddingBottom
		val padCrossStart = if (horizontal) paddingTop else paddingLeft
		val padCrossEnd = if (horizontal) paddingBottom else paddingRight
		val innerCross = crossSize - padCrossStart - padCrossEnd
		val rtl = horizontal && layoutDirection == LAYOUT_DIRECTION_RTL

		fun place(view: View, main: Int, cross: Int, mainLen: Int, crossLen: Int) {
			if (horizontal) {
				val x = if (rtl) width - main - mainLen else main
				view.layout(x, cross, x + mainLen, cross + crossLen)
			} else {
				view.layout(cross, main, cross + crossLen, main + mainLen)
			}
		}

		val buttonCross = padCrossStart + (innerCross - buttonSize) / 2
		var pos = padStart
		for (v in leftButtons) {
			place(v, pos, buttonCross, buttonSize, buttonSize)
			pos += buttonSize
		}
		val leftEnd = pos

		val rightStart = mainSize - padEnd - rightButtons.size * buttonSize
		pos = rightStart
		for (v in rightButtons) {
			place(v, pos, buttonCross, buttonSize, buttonSize)
			pos += buttonSize
		}

		val action = visibleAction() ?: return
		val lp = action.layoutParams as MarginLayoutParams
		val marginStart = if (horizontal) lp.marginStart else lp.topMargin
		val marginEnd = if (horizontal) lp.marginEnd else lp.bottomMargin
		val marginCrossStart = if (horizontal) lp.topMargin else lp.leftMargin
		val marginCrossEnd = if (horizontal) lp.bottomMargin else lp.rightMargin

		val crossLen = if (horizontal) action.measuredHeight else action.measuredWidth
		val mainLen = when (action) {
			is ExtendedFloatingActionButton -> if (horizontal) action.measuredWidth else action.measuredHeight
			else -> (crossLen * fabWidthRatio).roundToInt()
		}

		val minPos = leftEnd + marginStart
		val maxPos = max(minPos, rightStart - marginEnd - mainLen)
		val mainPos = ((mainSize - mainLen) / 2).coerceIn(minPos, maxPos)
		val crossPos = padCrossStart + marginCrossStart +
				(innerCross - marginCrossStart - marginCrossEnd - crossLen) / 2
		place(action, mainPos, crossPos, mainLen, crossLen)
	}

	private fun visibleAction(): View? = actionView?.takeIf { it.visibility != GONE }

	override fun generateDefaultLayoutParams(): LayoutParams =
		MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

	override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams =
		MarginLayoutParams(context, attrs)

	override fun generateLayoutParams(p: LayoutParams): LayoutParams = MarginLayoutParams(p)

	override fun checkLayoutParams(p: LayoutParams?): Boolean = p is MarginLayoutParams

	// ---------------------------------------------------------------------------------------------
	// Defaults
	// ---------------------------------------------------------------------------------------------

	private fun defaultContainerColor(): Int = MaterialColors.getColor(
		context,
		MR.attr.colorSurfaceContainer,
		MaterialColors.getColor(context, MR.attr.colorSurface, Color.WHITE)
	)

	private fun defaultMenuIconTint(): ColorStateList {
		val color = MaterialColors.getColor(context, MR.attr.colorOnSurfaceVariant, Color.BLACK)
		val selected = MaterialColors.getColor(context, MR.attr.colorOnSecondaryContainer, color)
		return ColorStateList(
			arrayOf(
				intArrayOf(-android.R.attr.state_enabled),
				intArrayOf(android.R.attr.state_selected),
				intArrayOf()
			),
			intArrayOf(ColorUtils.setAlphaComponent(color, 0x61), selected, color)
		)
	}

	private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

	companion object {
		const val HORIZONTAL = 0
		const val VERTICAL = 1
	}
}