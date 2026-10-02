package com.deavidig.mod.deanielig.floatingactionbutton

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.os.Parcel
import android.os.Parcelable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.OnBackPressedDispatcher
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.MenuRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.deavidig.sketchprojectpro.R
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import androidx.appcompat.R as AndroidXR
import com.google.android.material.R as MaterialR

// =================================================================================================
// Menu wrapper
// =================================================================================================

/**
 * Wrapper that owns the items of a [FloatingActionButtonMenu].
 *
 * A [FloatingActionMenuItem] cannot be instantiated directly: it is always created through a [FloatingActionMenu]
 * (`menu.add(...)` or `menu.inflate(...)`), the same way `android.view.Menu.add` returns a
 * `MenuItem`. Obtain the instance with [FloatingActionButtonMenu.getMenu].
 *
 * Items are listed top to bottom in the order they are added.
 */
class FloatingActionMenu internal constructor(private val context: Context) {

	internal interface Callback {
		/** Items were added, removed, cleared or shown/hidden. */
		fun onStructureChanged()

		/** A property of a single item changed. */
		fun onItemChanged(item: FloatingActionMenuItem)
	}

	internal var callback: Callback? = null
	private val items = ArrayList<FloatingActionMenuItem>()

	/** Creates and appends an item with an optional [icon] and [helpText]. */
	fun add(
		id: Int,
		label: CharSequence,
		icon: Drawable? = null,
		helpText: CharSequence? = null
	): FloatingActionMenuItem {
		val item = createItem(id, label, icon, helpText, enabled = true, visible = true)
		callback?.onStructureChanged()
		return item
	}

	/** Same as above, resolving [iconRes] (0 = no icon). */
	fun add(
		id: Int,
		label: CharSequence,
		@DrawableRes iconRes: Int,
		helpText: CharSequence? = null
	): FloatingActionMenuItem {
		val icon = if (iconRes != 0) ContextCompat.getDrawable(context, iconRes) else null
		return add(id, label, icon, helpText)
	}

	/**
	 * Appends the items declared in a menu resource.
	 * `title` → label, `icon` → icon, `titleCondensed` → help text (when different from the title).
	 */
	fun inflate(@MenuRes menuRes: Int) {
		val platformMenu = PopupMenu(context, View(context)).menu
		MenuInflater(context).inflate(menuRes, platformMenu)
		for (i in 0 until platformMenu.size()) {
			val mi = platformMenu.getItem(i)
			val condensed = mi.titleCondensed
			val help = if (condensed != null && condensed.toString() != mi.title?.toString()) {
				condensed
			} else {
				null
			}
			createItem(mi.itemId, mi.title ?: "", mi.icon, help, mi.isEnabled, mi.isVisible)
		}
		callback?.onStructureChanged()
	}

	/** Returns the first item with the given [id], or `null`. */
	fun findItem(id: Int): FloatingActionMenuItem? = items.firstOrNull { it.getId() == id }

	fun getItem(index: Int): FloatingActionMenuItem = items[index]

	fun indexOf(item: FloatingActionMenuItem): Int = items.indexOf(item)

	fun size(): Int = items.size

	/** Read-only snapshot of the items, top to bottom. */
	fun getItems(): List<FloatingActionMenuItem> = items.toList()

	fun remove(id: Int) {
		if (items.removeAll { it.getId() == id }) callback?.onStructureChanged()
	}

	fun remove(item: FloatingActionMenuItem) {
		if (items.remove(item)) callback?.onStructureChanged()
	}

	fun clear() {
		if (items.isEmpty()) return
		items.clear()
		callback?.onStructureChanged()
	}

	private fun createItem(
		id: Int,
		label: CharSequence,
		icon: Drawable?,
		helpText: CharSequence?,
		enabled: Boolean,
		visible: Boolean
	): FloatingActionMenuItem {
		val item = FloatingActionMenuItem(this, id, label, icon, helpText, enabled, visible)
		items.add(item)
		return item
	}

	internal fun notifyItemChanged(item: FloatingActionMenuItem) {
		callback?.onItemChanged(item)
	}

	internal fun notifyStructureChanged() {
		callback?.onStructureChanged()
	}
}

/**
 * One action of a [FloatingActionMenu], rendered as an `ExtendedFloatingActionButton` with an icon, a text
 * label and an optional secondary *help text* line explaining the action.
 *
 * Instances are created only through [FloatingActionMenu]; every setter updates the visible button.
 */
class FloatingActionMenuItem internal constructor(
	private val menu: FloatingActionMenu,
	private val id: Int,
	label: CharSequence,
	icon: Drawable?,
	helpText: CharSequence?,
	enabled: Boolean,
	visible: Boolean
) {

	/** Click listener of a single item. */
	fun interface OnClickListener {
		fun onClick(item: FloatingActionMenuItem)
	}

	private var label: CharSequence = label
	private var icon: Drawable? = icon
	private var helpText: CharSequence? = helpText
	private var contentDescription: CharSequence? = null
	private var enabled: Boolean = enabled
	private var visible: Boolean = visible
	private var clickListener: OnClickListener? = null

	private var button: ExtendedFloatingActionButton? = null

	fun getId(): Int = id

	/**
	 * The [ExtendedFloatingActionButton] currently rendering this item, so it can be manipulated
	 * directly (animations, `extend()`/`shrink()`, extra styling, etc.).
	 *
	 * It is `null` while the item is hidden, removed, or the menu has not been built yet, and it is
	 * a **new instance every time the menu is rebuilt** (items added/removed/shown/hidden, or
	 * `setItemColors`), so do not keep a reference to it.
	 *
	 * Changing a property through this item's setters re-binds text, icon, colors and
	 * content description, overwriting manual changes to those. Do not replace the button's
	 * `OnClickListener`: the menu uses it to dispatch clicks and collapse; use
	 * [FloatingActionButtonMenu.setOnItemMenuClickListener] or [setOnClickListener] instead.
	 */
	fun getButton(): ExtendedFloatingActionButton? = button

	internal fun attachButton(value: ExtendedFloatingActionButton?) {
		button = value
	}

	fun getLabel(): CharSequence = label

	fun setLabel(value: CharSequence) {
		label = value
		menu.notifyItemChanged(this)
	}

	fun getIcon(): Drawable? = icon

	fun setIcon(value: Drawable?) {
		icon = value
		menu.notifyItemChanged(this)
	}

	fun getHelpText(): CharSequence? = helpText

	fun setHelpText(value: CharSequence?) {
		helpText = value
		menu.notifyItemChanged(this)
	}

	fun getContentDescription(): CharSequence? = contentDescription

	fun setContentDescription(value: CharSequence?) {
		contentDescription = value
		menu.notifyItemChanged(this)
	}

	fun isEnabled(): Boolean = enabled

	fun setEnabled(value: Boolean) {
		if (enabled == value) return
		enabled = value
		menu.notifyItemChanged(this)
	}

	fun isVisible(): Boolean = visible

	fun setVisible(value: Boolean) {
		if (visible == value) return
		visible = value
		menu.notifyStructureChanged()
	}

	fun setOnClickListener(listener: OnClickListener?) {
		clickListener = listener
	}

	internal fun dispatchClick() {
		clickListener?.onClick(this)
	}
}

// =================================================================================================
// Component
// =================================================================================================

/**
 * Material Design 3 **FloatingActionButtonMenu** for Android Views (equivalent of the Compose
 * `FloatingActionButtonMenu` + `ToggleFloatingActionButton` + `FloatingActionButtonMenuItem`).
 *
 * A toggle FAB opens a vertical list of actions. Every action is a real
 * [ExtendedFloatingActionButton] showing an icon and a text label, plus an optional help text line.
 * Items are created through the [FloatingActionMenu] wrapper returned by [getMenu].
 *
 * ### Behavior
 * - The toggle morphs `+` → `×`, its container goes from `colorPrimaryContainer` to `colorPrimary`
 *   and its shape from rounded square to circle.
 * - With `android:text` (or [setText]) the toggle is an extended FAB with a label; the label fades
 *   out and the button shrinks to a circle when the menu expands.
 * - Items appear staggered, starting with the one closest to the toggle, and disappear in reverse.
 * - An optional scrim dims the content behind; tapping it collapses the menu.
 * - System back collapses the menu while expanded (requires a `ComponentActivity` host).
 * - The expanded state survives configuration changes (the view needs an `android:id`).
 *
 * ### Usage
 * Place it as the **last** child of a `FrameLayout` / `CoordinatorLayout` with `match_parent`
 * size so the scrim can cover the screen. While collapsed, touches outside the toggle pass through.
 *
 * ```xml
 * <com.deavidig.mod.deaniel.fabmenu.ComponentFabMenu
 *     android:id="@+id/fabMenu"
 *     android:layout_width="match_parent"
 *     android:layout_height="match_parent"
 *     app:fabMenu="@menu/fab_menu"
 *     app:fabMenuAlignment="end"
 *     android:text="Actions" />
 * ```
 *
 * ```kotlin
 * val menu = fabMenu.getMenu()
 * val share = menu.add(R.id.action_share, "Share", R.drawable.ic_share, "Send a link to someone")
 * share.setOnClickListener { /* ... */ }
 * share.setEnabled(false)
 *
 * fabMenu.setOnItemMenuClickListener { item ->
 *     item.getButton()?.let { /* manipulate the ExtendedFloatingActionButton */ }
 * }
 * ```
 */
class FloatingActionButtonMenu @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

	/**
	 * Receives clicks on any menu item. Use [FloatingActionMenuItem.getButton] to manipulate its
	 * `ExtendedFloatingActionButton` and [FloatingActionMenu.indexOf] if you need its position.
	 */
	fun interface OnItemMenuClickListener {
		fun onItemMenuClick(item: FloatingActionMenuItem)
	}

	/** Receives expand/collapse changes. */
	fun interface OnExpandedChangeListener {
		fun onExpandedChange(expanded: Boolean)
	}

	/** Side of the container where the toggle (and the items) are anchored. Honors RTL. */
	enum class Alignment { START, END }

	/** Built-in glyph of the collapsed toggle; every glyph morphs into an `×` when expanded. */
	enum class ToggleGlyph {
		/** `+` that rotates into `×`. */
		PLUS,

		/** Three vertical dots (`more_vert`) that split and merge into `×`. */
		MORE_VERT
	}

	// ---------------------------------------------------------------------------------------------
	// State (everything private; public API through getters/setters)
	// ---------------------------------------------------------------------------------------------

	private val density = context.resources.displayMetrics.density
	private val menu = FloatingActionMenu(context)
	private var expanded = false
	private var progress = 0f
	private var animator: ValueAnimator? = null
	private var alignment = Alignment.END
	private var scrimEnabled = true
	private var collapseOnItemClick = true
	private var edgeMarginPx = 16f * density
	private var toggleIcon: Drawable? = null
	private var toggleGlyph = ToggleGlyph.PLUS
	private var openDescription: CharSequence = "Open menu"
	private var closeDescription: CharSequence = "Close menu"
	private var descriptionsCustomized = false
	private var toggleText: CharSequence? = null
	private var itemMenuClickListener: OnItemMenuClickListener? = null
	private var expandedListener: OnExpandedChangeListener? = null
	private var backDispatcher: OnBackPressedDispatcher? = null

	@ColorInt
	private var scrimColor =
		ColorUtils.setAlphaComponent(themeColor(MaterialR.attr.colorSurface, Color.WHITE), 0xCC)

	@ColorInt
	private var toggleCollapsedContainer =
		themeColor(MaterialR.attr.colorPrimaryContainer, 0xFFEADDFF.toInt())

	@ColorInt
	private var toggleCollapsedContent =
		themeColor(MaterialR.attr.colorOnPrimaryContainer, 0xFF21005D.toInt())

	@ColorInt
	private var toggleExpandedContainer =
		themeColor(AndroidXR.attr.colorPrimary, 0xFF6750A4.toInt())

	@ColorInt
	private var toggleExpandedContent =
		themeColor(MaterialR.attr.colorOnPrimary, Color.WHITE)

	@ColorInt
	private var itemContainerColor =
		themeColor(MaterialR.attr.colorPrimaryContainer, 0xFFEADDFF.toInt())

	@ColorInt
	private var itemContentColor =
		themeColor(MaterialR.attr.colorOnPrimaryContainer, 0xFF21005D.toInt())

	@ColorInt
	private var disabledOnSurface =
		themeColor(MaterialR.attr.colorOnSurface, Color.BLACK)

	private val easing = PathInterpolator(0.2f, 0f, 0f, 1f)
	private val scrimView = View(context)
	private val itemsContainer = LinearLayout(context)
	private val toggleButton = ToggleButtonView(context, density)

	private val backCallback = object : OnBackPressedCallback(false) {
		override fun handleOnBackPressed() {
			collapse()
		}
	}

	init {
		clipToOutline = false
		clipChildren = false
		clipToPadding = false

		menu.callback = object : FloatingActionMenu.Callback {
			override fun onStructureChanged() = rebuildItems()
			override fun onItemChanged(item: FloatingActionMenuItem) = refreshItem(item)
		}

		scrimView.apply {
			visibility = GONE
			importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
			setOnClickListener { collapse() }
		}
		itemsContainer.apply {
			orientation = LinearLayout.VERTICAL
			clipChildren = false
			clipToPadding = false
			visibility = GONE
		}
		toggleButton.apply {
			elevation = 6f * density
			contentDescription = openDescription
			setOnClickListener { toggle() }
		}

		addView(scrimView, LayoutParams(MATCH, MATCH))
		addView(itemsContainer, LayoutParams(WRAP, WRAP))
		addView(toggleButton, LayoutParams(WRAP, WRAP))

		var startExpanded = false
		val a = context.obtainStyledAttributes(
			attrs,
			R.styleable.FloatingActionButtonMenu,
			defStyleAttr,
			0
		)
		try {
			alignment =
				if (a.getInt(R.styleable.FloatingActionButtonMenu_menuAlign, 1) == 0) {
					Alignment.START
				} else {
					Alignment.END
				}
			scrimEnabled =
				a.getBoolean(R.styleable.FloatingActionButtonMenu_scrimEnabled, true)
			scrimColor =
				a.getColor(R.styleable.FloatingActionButtonMenu_scrimColor, scrimColor)
			collapseOnItemClick =
				a.getBoolean(R.styleable.FloatingActionButtonMenu_collapseItemClicked, true)
			startExpanded =
				a.getBoolean(R.styleable.FloatingActionButtonMenu_expanded, false)
			toggleGlyph = if (a.getInt(R.styleable.FloatingActionButtonMenu_menuIcon, 0) == 1) {
				ToggleGlyph.MORE_VERT
			} else {
				ToggleGlyph.PLUS
			}
			val menuRes = a.getResourceId(R.styleable.FloatingActionButtonMenu_menu, 0)
			if (menuRes != 0) menu.inflate(menuRes)
		} finally {
			a.recycle()
		}

		val textArray =
			context.obtainStyledAttributes(attrs, intArrayOf(android.R.attr.text), defStyleAttr, 0)
		try {
			toggleText = textArray.getText(0)
		} finally {
			textArray.recycle()
		}
		toggleButton.text = toggleText
		toggleButton.glyph = toggleGlyph
		updateToggleDescription()

		scrimView.setBackgroundColor(scrimColor)
		applyLayout()
		applyProgress(0f)
		if (startExpanded) setExpanded(true, animate = false)
	}

	// ---------------------------------------------------------------------------------------------
	// Public API: menu
	// ---------------------------------------------------------------------------------------------

	/** The wrapper that creates and owns the items. */
	fun getMenu(): FloatingActionMenu = menu

	/** Appends the items of a menu resource. Shortcut for `getMenu().inflate(menuRes)`. */
	fun inflateMenu(@MenuRes menuRes: Int) = menu.inflate(menuRes)

	// ---------------------------------------------------------------------------------------------
	// Public API: expansion
	// ---------------------------------------------------------------------------------------------

	/** Whether the menu is expanded (or expanding). */
	fun isExpanded(): Boolean = expanded

	/** Expands or collapses the menu, optionally animating. */
	fun setExpanded(expanded: Boolean, animate: Boolean = true) {
		val changed = this.expanded != expanded
		this.expanded = expanded
		backCallback.isEnabled = expanded
		updateToggleDescription()

		val target = if (expanded) 1f else 0f
		animator?.cancel()
		if (!animate || !isAttachedToWindow || progress == target) {
			applyProgress(target)
		} else {
			animator = ValueAnimator.ofFloat(progress, target).apply {
				duration = if (expanded) 300L else 200L
				interpolator = easing
				addUpdateListener { applyProgress(it.animatedValue as Float) }
				addListener(object : AnimatorListenerAdapter() {
					override fun onAnimationEnd(a: Animator) {
						if (animator === a) animator = null
					}
				})
				start()
			}
		}
		if (changed) expandedListener?.onExpandedChange(expanded)
	}

	/** Expands the menu. */
	fun expand(animate: Boolean = true) = setExpanded(true, animate)

	/** Collapses the menu. */
	fun collapse(animate: Boolean = true) = setExpanded(false, animate)

	/** Flips the current state. */
	fun toggle(animate: Boolean = true) = setExpanded(!expanded, animate)

	// ---------------------------------------------------------------------------------------------
	// Public API: appearance & behavior
	// ---------------------------------------------------------------------------------------------

	fun getAlignment(): Alignment = alignment

	fun setAlignment(value: Alignment) {
		if (alignment == value) return
		alignment = value
		applyLayout()
	}

	fun isScrimEnabled(): Boolean = scrimEnabled

	fun setScrimEnabled(enabled: Boolean) {
		scrimEnabled = enabled
		applyProgress(progress)
	}

	@ColorInt
	fun getScrimColor(): Int = scrimColor

	fun setScrimColor(@ColorInt color: Int) {
		scrimColor = color
		scrimView.setBackgroundColor(color)
	}

	fun isCollapseOnItemClick(): Boolean = collapseOnItemClick

	fun setCollapseOnItemClick(value: Boolean) {
		collapseOnItemClick = value
	}

	/** Distance, in pixels, between the toggle and the edges of this view. */
	fun getEdgeMargin(): Float = edgeMarginPx

	fun setEdgeMargin(px: Float) {
		edgeMarginPx = px
		applyLayout()
	}

	fun getToggleIcon(): Drawable? = toggleIcon

	/**
	 * Custom icon of the collapsed toggle. It cross-fades with the built-in close (`×`) glyph.
	 * `null` restores the built-in `+` → `×` morph.
	 */
	fun setToggleIcon(icon: Drawable?) {
		toggleIcon = icon
		toggleButton.collapsedIcon = icon
	}

	fun getToggleGlyph(): ToggleGlyph = toggleGlyph

	/**
	 * Built-in glyph of the collapsed toggle (`app:fabMenuIcon`): [ToggleGlyph.PLUS] or
	 * [ToggleGlyph.MORE_VERT]. Both animate into an `×` when the menu expands.
	 * Ignored while a custom drawable is set with [setToggleIcon].
	 */
	fun setToggleGlyph(value: ToggleGlyph) {
		toggleGlyph = value
		toggleButton.glyph = value
	}

	/** Accessibility labels of the toggle for each state. */
	fun setToggleContentDescriptions(open: CharSequence, close: CharSequence) {
		openDescription = open
		closeDescription = close
		descriptionsCustomized = true
		updateToggleDescription()
	}

	/** Text of the toggle (same as `android:text`), or `null` when it is a plain FAB. */
	fun getText(): CharSequence? = toggleText

	/**
	 * Sets the toggle label, making it an extended FAB while collapsed. The label fades out and the
	 * button shrinks to a circle when the menu expands. `null` or empty restores the plain FAB.
	 * Unless [setToggleContentDescriptions] was called, the label is also the collapsed
	 * accessibility description.
	 */
	fun setText(text: CharSequence?) {
		toggleText = text
		toggleButton.text = text
		updateToggleDescription()
	}

	fun setText(@StringRes resId: Int) {
		setText(context.getText(resId))
	}

	private fun updateToggleDescription() {
		toggleButton.contentDescription = when {
			expanded -> closeDescription
			!descriptionsCustomized && !toggleText.isNullOrEmpty() -> toggleText
			else -> openDescription
		}
	}

	fun setToggleCollapsedColors(@ColorInt container: Int, @ColorInt content: Int) {
		toggleCollapsedContainer = container
		toggleCollapsedContent = content
		applyProgress(progress)
	}

	fun setToggleExpandedColors(@ColorInt container: Int, @ColorInt content: Int) {
		toggleExpandedContainer = container
		toggleExpandedContent = content
		applyProgress(progress)
	}

	/** Colors of the item buttons (container and icon/text). Disabled items use M3 disabled colors. */
	fun setItemColors(@ColorInt container: Int, @ColorInt content: Int) {
		itemContainerColor = container
		itemContentColor = content
		rebuildItems()
	}

	fun setOnItemMenuClickListener(listener: OnItemMenuClickListener?) {
		itemMenuClickListener = listener
	}

	fun setOnExpandedChangeListener(listener: OnExpandedChangeListener?) {
		expandedListener = listener
	}

	// ---------------------------------------------------------------------------------------------
	// Internals
	// ---------------------------------------------------------------------------------------------

	private fun dp(value: Float) = value * density

	private fun themeColor(@AttrRes attr: Int, @ColorInt fallback: Int): Int =
		MaterialColors.getColor(context, attr, fallback)

	private fun applyLayout() {
		val horizontal = if (alignment == Alignment.START) Gravity.START else Gravity.END
		val margin = edgeMarginPx.toInt()
		val size = dp(56f).toInt()
		toggleButton.layoutParams =
			LayoutParams(WRAP, size, Gravity.BOTTOM or horizontal).apply {
				setMargins(margin, margin, margin, margin)
			}
		itemsContainer.gravity = horizontal
		itemsContainer.layoutParams =
			LayoutParams(WRAP, WRAP, Gravity.BOTTOM or horizontal).apply {
				setMargins(margin, margin, margin, margin + size + dp(8f).toInt())
			}
		applyItemTransforms(progress)
	}

	/** Rebuilds every visible item as an [ExtendedFloatingActionButton]. */
	private fun rebuildItems() {
		for (i in 0 until itemsContainer.childCount) {
			(itemsContainer.getChildAt(i).tag as? FloatingActionMenuItem)?.attachButton(null)
		}
		itemsContainer.removeAllViews()
		var shown = 0
		for (item in menu.getItems()) {
			if (!item.isVisible()) continue
			val lp = LinearLayout.LayoutParams(WRAP, WRAP)
			if (shown > 0) lp.topMargin = dp(4f).toInt()
			itemsContainer.addView(createItemView(item), lp)
			shown++
		}
		applyItemTransforms(progress)
	}

	private fun createItemView(item: FloatingActionMenuItem): ExtendedFloatingActionButton {
		val fab = ExtendedFloatingActionButton(context)
		fab.tag = item
		fab.cornerRadius = 128
		fab.stateListAnimator = null
		item.attachButton(fab)
		fab.isSingleLine = false
		fab.maxLines = 2
		fab.ellipsize = TextUtils.TruncateAt.END
		fab.textAlignment = TEXT_ALIGNMENT_VIEW_START
		fab.setOnClickListener { handleItemClick(item) }
		bindItemView(fab, item)
		return fab
	}

	/** Re-binds the button of a single item after one of its properties changed. */
	private fun refreshItem(item: FloatingActionMenuItem) {
		for (i in 0 until itemsContainer.childCount) {
			val child = itemsContainer.getChildAt(i)
			if (child.tag === item && child is ExtendedFloatingActionButton) {
				bindItemView(child, item)
				return
			}
		}
	}

	private fun bindItemView(fab: ExtendedFloatingActionButton, item: FloatingActionMenuItem) {
		val enabled = item.isEnabled()
		val container = if (enabled) itemContainerColor
		else ColorUtils.setAlphaComponent(disabledOnSurface, 0x1F)
		val content = if (enabled) itemContentColor
		else ColorUtils.setAlphaComponent(disabledOnSurface, 0x61)

		fab.isEnabled = enabled
		fab.backgroundTintList = ColorStateList.valueOf(container)
		fab.rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(content, 0x1F))
		fab.setTextColor(content)
		fab.iconTint = ColorStateList.valueOf(content)
		fab.icon = item.getIcon()
		fab.contentDescription = item.getContentDescription()

		val help = item.getHelpText()
		if (help.isNullOrEmpty()) {
			fab.text = item.getLabel()
		} else {
			val helpColor = if (enabled) ColorUtils.setAlphaComponent(content, 0xB8) else content
			val text = SpannableStringBuilder(item.getLabel()).append('\n')
			val start = text.length
			text.append(help)
			text.setSpan(
				RelativeSizeSpan(0.85f),
				start,
				text.length,
				Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
			)
			text.setSpan(
				ForegroundColorSpan(helpColor),
				start,
				text.length,
				Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
			)
			fab.text = text
		}
	}

	private fun handleItemClick(item: FloatingActionMenuItem) {
		if (!expanded || !item.isEnabled()) return
		item.dispatchClick()
		itemMenuClickListener?.onItemMenuClick(item)
		if (collapseOnItemClick) collapse()
	}

	/** Single place that renders a progress value (0 = collapsed, 1 = expanded). */
	private fun applyProgress(p: Float) {
		progress = p
		val radius = dp(16f) + (dp(28f) - dp(16f)) * p
		toggleButton.update(
			p,
			ColorUtils.blendARGB(toggleCollapsedContainer, toggleExpandedContainer, p),
			ColorUtils.blendARGB(toggleCollapsedContent, toggleExpandedContent, p),
			radius
		)
		val visible = p > 0f
		itemsContainer.visibility = if (visible) VISIBLE else GONE
		scrimView.visibility = if (visible && scrimEnabled) VISIBLE else GONE
		scrimView.alpha = p
		applyItemTransforms(p)
	}

	/** Staggers the items: the one closest to the toggle enters first and leaves last. */
	private fun applyItemTransforms(p: Float) {
		val n = itemsContainer.childCount
		if (n == 0) return
		val window = if (n == 1) 1f else 0.6f
		val step = if (n > 1) (1f - window) / (n - 1) else 0f
		val pivotRight =
			(alignment == Alignment.END) != (layoutDirection == LAYOUT_DIRECTION_RTL)
		for (i in 0 until n) {
			val v = itemsContainer.getChildAt(i)
			val distanceToToggle = n - 1 - i
			val local = ((p - distanceToToggle * step) / window).coerceIn(0f, 1f)
			val scale = 0.85f + 0.15f * local
			v.alpha = local
			v.translationY = (1f - local) * dp(16f)
			v.scaleX = scale
			v.scaleY = scale
			v.pivotX = if (pivotRight) v.width.toFloat() else 0f
			v.pivotY = v.height.toFloat()
		}
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		super.onLayout(changed, left, top, right, bottom)
		// Item sizes are known only now, so the scale pivots can be resolved.
		applyItemTransforms(progress)
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		backDispatcher = findComponentActivity(context)?.onBackPressedDispatcher?.also {
			it.addCallback(backCallback)
		}
		backCallback.isEnabled = expanded
	}

	override fun onDetachedFromWindow() {
		// Keep the logical state; only drop the back callback and snap any running animation.
		backCallback.remove()
		backDispatcher = null
		animator?.cancel()
		animator = null
		applyProgress(if (expanded) 1f else 0f)
		super.onDetachedFromWindow()
	}

	private fun findComponentActivity(start: Context): ComponentActivity? {
		var c: Context? = start
		while (c is ContextWrapper) {
			if (c is ComponentActivity) return c
			c = c.baseContext
		}
		return null
	}

	override fun onSaveInstanceState(): Parcelable {
		val state = SavedState(super.onSaveInstanceState())
		state.expanded = expanded
		return state
	}

	override fun onRestoreInstanceState(state: Parcelable?) {
		if (state !is SavedState) {
			super.onRestoreInstanceState(state)
			return
		}
		super.onRestoreInstanceState(state.superState)
		setExpanded(state.expanded, animate = false)
	}

	// ---------------------------------------------------------------------------------------------
	// Toggle FAB: drawn by hand so the + → × glyph and the shape can be driven by one progress value
	// ---------------------------------------------------------------------------------------------

	private class ToggleButtonView(context: Context, private val density: Float) :
		FrameLayout(context) {

		private val containerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
		private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			style = Paint.Style.STROKE
			strokeCap = Paint.Cap.ROUND
			strokeWidth = 2f * density
		}
		private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
			textSize = TypedValue.applyDimension(
				TypedValue.COMPLEX_UNIT_SP, 14f, context.resources.displayMetrics
			)
			typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
		}
		private val glyphFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
		private val armPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			style = Paint.Style.STROKE
			strokeCap = Paint.Cap.BUTT
		}
		private val baseSize = 56f * density
		private val iconCenter = 28f * density
		private val textStart = 52f * density
		private val textEndPadding = 20f * density
		private val bounds = RectF()
		private val glyphHalfLength = 7f * density
		private val iconHalfSize = (12f * density).toInt()
		private var cornerRadius = 0f
		private var progress = 0f

		@ColorInt
		private var contentColor = Color.BLACK

		var collapsedIcon: Drawable? = null
			set(value) {
				field = value?.mutate()
				invalidate()
			}

		var text: CharSequence? = null
			set(value) {
				field = value
				requestLayout()
				invalidate()
			}

		var glyph: ToggleGlyph = ToggleGlyph.PLUS
			set(value) {
				field = value
				invalidate()
			}

		init {
			setWillNotDraw(false)
			isClickable = true
			isFocusable = true
			clipToOutline = true
			outlineProvider = object : ViewOutlineProvider() {
				override fun getOutline(view: View, outline: Outline) {
					outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
				}
			}
			foreground = RippleDrawable(
				ColorStateList.valueOf(0x1F000000),
				null,
				ColorDrawable(Color.BLACK)
			)
		}

		fun update(
			progress: Float,
			@ColorInt container: Int,
			@ColorInt content: Int,
			radius: Float
		) {
			this.progress = progress
			containerPaint.color = container
			contentColor = content
			cornerRadius = radius
			(foreground as? RippleDrawable)
				?.setColor(ColorStateList.valueOf(ColorUtils.setAlphaComponent(content, 0x1F)))
			if (!text.isNullOrEmpty()) requestLayout()
			invalidateOutline()
			invalidate()
		}

		/** Width goes from "icon + label" (collapsed) to a 56dp circle (expanded). */
		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val t = text
			val extended = if (t.isNullOrEmpty()) {
				baseSize
			} else {
				textStart + textPaint.measureText(t, 0, t.length) + textEndPadding
			}
			var w = extended + (baseSize - extended) * progress
			if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
				w = w.coerceAtMost(MeasureSpec.getSize(widthMeasureSpec).toFloat())
			}
			setMeasuredDimension((w + 0.5f).toInt(), baseSize.toInt())
		}

		override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
			super.onSizeChanged(w, h, oldw, oldh)
			invalidateOutline()
		}

		/** Draws the label; it fades out during the first half of the expansion. */
		private fun drawLabel(canvas: Canvas, cy: Float, rtl: Boolean) {
			val t = text
			if (t.isNullOrEmpty()) return
			val alpha = (1f - progress * 2f).coerceIn(0f, 1f)
			if (alpha <= 0f) return
			textPaint.color = contentColor
			textPaint.alpha = (alpha * 255).toInt()
			textPaint.textAlign = if (rtl) Paint.Align.RIGHT else Paint.Align.LEFT
			val metrics = textPaint.fontMetrics
			val baseline = cy - (metrics.ascent + metrics.descent) / 2f
			val x = if (rtl) width - textStart else textStart
			canvas.drawText(t, 0, t.length, x, baseline, textPaint)
		}

		override fun onDraw(canvas: Canvas) {
			super.onDraw(canvas)
			bounds.set(0f, 0f, width.toFloat(), height.toFloat())
			canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, containerPaint)

			val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
			val cx = if (rtl) width - iconCenter else iconCenter
			val cy = height / 2f
			drawLabel(canvas, cy, rtl)
			val custom = collapsedIcon
			if (custom == null) {
				when (glyph) {
					ToggleGlyph.PLUS -> drawPlus(canvas, cx, cy, 45f * progress, 255)
					ToggleGlyph.MORE_VERT -> drawMoreVert(canvas, cx, cy)
				}
			} else {
				custom.setBounds(
					(cx - iconHalfSize).toInt(), (cy - iconHalfSize).toInt(),
					(cx + iconHalfSize).toInt(), (cy + iconHalfSize).toInt()
				)
				custom.setTint(contentColor)
				custom.alpha = ((1f - progress) * 255).toInt()
				custom.draw(canvas)
				drawPlus(canvas, cx, cy, 45f, (progress * 255).toInt())
			}
		}

		/**
		 * Three dots that become an `×` as [progress] goes 0 → 1. The top dot splits into the two
		 * upper arms, the bottom dot into the two lower arms, and the middle dot ends up as the
		 * crossing. Each arm starts as a dot (zero length) and stretches from its dot to a corner,
		 * while its base slides to the center a bit later.
		 */
		private fun drawMoreVert(canvas: Canvas, cx: Float, cy: Float) {
			glyphFill.color = contentColor
			armPaint.color = contentColor
			val radius = (2f - progress) * density // dot radius 2dp → stroke half-width 1dp
			armPaint.strokeWidth = radius * 2f
			val spacing = 6f * density
			val corner = 5f * density
			val tipT = smoothStep(progress / 0.7f)
			val baseT = smoothStep((progress - 0.3f) / 0.7f)

			canvas.drawCircle(cx, cy, radius, glyphFill)
			for (sy in intArrayOf(-1, 1)) {
				val startY = sy * spacing
				val baseY = startY - startY * baseT
				val tipY = startY + (sy * corner - startY) * tipT
				for (sx in intArrayOf(-1, 1)) {
					val tipX = sx * corner * tipT
					canvas.drawLine(cx, cy + baseY, cx + tipX, cy + tipY, armPaint)
					canvas.drawCircle(cx, cy + baseY, radius, glyphFill)
					canvas.drawCircle(cx + tipX, cy + tipY, radius, glyphFill)
				}
			}
		}

		private fun smoothStep(t: Float): Float {
			val c = t.coerceIn(0f, 1f)
			return c * c * (3f - 2f * c)
		}

		private fun drawPlus(canvas: Canvas, cx: Float, cy: Float, rotation: Float, alpha: Int) {
			if (alpha <= 0) return
			glyphPaint.color = contentColor
			glyphPaint.alpha = alpha
			canvas.save()
			canvas.rotate(rotation, cx, cy)
			canvas.drawLine(cx - glyphHalfLength, cy, cx + glyphHalfLength, cy, glyphPaint)
			canvas.drawLine(cx, cy - glyphHalfLength, cx, cy + glyphHalfLength, glyphPaint)
			canvas.restore()
		}

		override fun getAccessibilityClassName(): CharSequence = Button::class.java.name
	}

	private class SavedState : View.BaseSavedState {
		var expanded = false

		constructor(superState: Parcelable?) : super(superState)

		private constructor(source: Parcel) : super(source) {
			expanded = source.readInt() == 1
		}

		override fun writeToParcel(out: Parcel, flags: Int) {
			super.writeToParcel(out, flags)
			out.writeInt(if (expanded) 1 else 0)
		}

		companion object {
			@JvmField
			val CREATOR: Parcelable.Creator<SavedState> = object : Parcelable.Creator<SavedState> {
				override fun createFromParcel(source: Parcel): SavedState = SavedState(source)
				override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
			}
		}
	}

	private companion object {
		const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
		const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
	}
}