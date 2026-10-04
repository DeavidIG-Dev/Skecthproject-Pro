package com.deavidig.mod.deanielig.floatingactionbutton

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Parcel
import android.os.Parcelable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import com.deavidig.sketchprojectpro.R
import com.google.android.material.color.MaterialColors

class FloatingActionButtonGroup @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr), CoordinatorLayout.AttachedBehavior {

	enum class IconSide { START, END }
	enum class ActivationMode { CLICK, LONG_CLICK }
	enum class MenuOrientation { TOP, BOTTOM, LEFT, RIGHT, AUTO }

	private val items = mutableListOf<FloatingActionButtonItem>()
	private var scrimView: View? = null

	private val itemAnimationDuration = 220L
	private val itemStaggerDelay = 40L
	private val interpolator = DecelerateInterpolator()

	private val density: Float = resources.displayMetrics.density
	private val itemSpacingPx: Int = (12 * density).toInt()

	private inline val Int.dp: Float get() = this * density
	private inline val Float.dp: Float get() = this * density

	// Especificaciones M3
	private val triggerHeightPx: Float = 56f.dp
	private val triggerIconSizePx: Float = 24f.dp
	private val triggerPaddingStartPx: Float = 16f.dp
	private val triggerIconTextGapPx: Float = 12f.dp
	private val triggerPaddingEndPx: Float = 16f.dp

	// Margen extra para evitar cortar la sombra
	private val shadowPaddingPx: Int = (12 * density).toInt()

	// Elevación dinámica M3: reposo, presionado, hover y arrastrado
	private val restingElevationPx: Float = 6f.dp
	private val pressedElevationPx: Float = 6f.dp
	private val hoverElevationPx: Float = 8f.dp
	private val draggedElevationPx: Float = 12f.dp
	private val elevationAnimationDuration = 120L

	private var isPressedState = false
	private var isHoveredState = false
	private var isDraggedState = false
	private var elevationAnimator: ValueAnimator? = null

	private var customClickListener: OnClickListener? = null
	private var customLongClickListener: OnLongClickListener? = null

	override fun setOnClickListener(l: OnClickListener?) {
		customClickListener = l
	}

	override fun setOnLongClickListener(l: OnLongClickListener?) {
		customLongClickListener = l
	}

	private var iconSideValue: IconSide = IconSide.START
	fun getIconSide(): IconSide = iconSideValue
	fun setIconSide(side: IconSide): FloatingActionButtonGroup {
		iconSideValue = side
		recomputeTriggerExpandedWidth()
		if (triggerExtended) layoutTriggerRect()
		invalidate()
		return this
	}

	private var triggerCornerRadiusValue: Float = 16f.dp

	// Radio de esquina explícito para el estado contraído (icono solo). Si es null, se usa el radio base.
	private var shrunkCornerRadiusValue: Float? = null
	fun getShrunkCornerRadius(): Float? = shrunkCornerRadiusValue
	fun setShrunkCornerRadius(radiusPx: Float?): FloatingActionButtonGroup {
		shrunkCornerRadiusValue = radiusPx
		invalidate()
		return this
	}

	// Radio de esquina explícito para el estado extendido (icono + texto). Si es null, se usa el radio base.
	private var extendCornerRadiusValue: Float? = null
	fun getExtendCornerRadius(): Float? = extendCornerRadiusValue
	fun setExtendCornerRadius(radiusPx: Float?): FloatingActionButtonGroup {
		extendCornerRadiusValue = radiusPx
		invalidate()
		return this
	}

	/**
	 * Si se definió shrunkCornerRadius y/o extendCornerRadius, el radio se interpola junto con
	 * triggerWidthFraction (la misma fracción que anima el ancho contraído/extendido), para que
	 * la forma cambie en sincronía con el ancho. Si ninguno de los dos se definió, se conserva el
	 * comportamiento previo de un único radio constante (o circular si triggerCornerRadiusValue < 0).
	 */
	private val resolvedTriggerCornerRadius: Float
		get() {
			val baseRadius =
				if (triggerCornerRadiusValue >= 0f) triggerCornerRadiusValue else triggerHeightPx / 2f
			if (shrunkCornerRadiusValue == null && extendCornerRadiusValue == null) return baseRadius
			val shrunkRadius = shrunkCornerRadiusValue ?: baseRadius
			val extendRadius = extendCornerRadiusValue ?: baseRadius
			return shrunkRadius + (extendRadius - shrunkRadius) * triggerWidthFraction
		}

	fun getTriggerCornerRadius(): Float = triggerCornerRadiusValue
	fun setTriggerCornerRadius(radiusPx: Float): FloatingActionButtonGroup {
		triggerCornerRadiusValue = radiusPx
		invalidate()
		return this
	}

	private var triggerBackgroundTintValue: ColorStateList = ColorStateList.valueOf(
		MaterialColors.getColor(
			this,
			com.google.android.material.R.attr.colorPrimaryContainer,
			Color.DKGRAY
		)
	)

	fun getTriggerBackgroundTint(): ColorStateList = triggerBackgroundTintValue
	fun setTriggerBackgroundTint(tint: ColorStateList): FloatingActionButtonGroup {
		triggerBackgroundTintValue = tint
		invalidate()
		return this
	}

	// Tinte de fondo mientras el menú está abierto. Si no se define, se usa triggerBackgroundTintValue.
	private var triggerBackgroundTintVisibleValue: ColorStateList? = null
	fun getTriggerBackgroundTintVisible(): ColorStateList? = triggerBackgroundTintVisibleValue
	fun setTriggerBackgroundTintVisible(tint: ColorStateList?): FloatingActionButtonGroup {
		triggerBackgroundTintVisibleValue = tint
		invalidate()
		return this
	}

	private val resolvedTriggerBackgroundTint: ColorStateList
		get() = (if (menuOpen) triggerBackgroundTintVisibleValue else null)
			?: triggerBackgroundTintValue

	private var triggerIconTintValue: ColorStateList = ColorStateList.valueOf(
		MaterialColors.getColor(
			this,
			com.google.android.material.R.attr.colorOnPrimaryContainer,
			Color.WHITE
		)
	)

	fun getTriggerIconTint(): ColorStateList = triggerIconTintValue
	fun setTriggerIconTint(tint: ColorStateList): FloatingActionButtonGroup {
		triggerIconTintValue = tint
		// Actualizar dinámicamente el ripple M3 según el nuevo tinte
		updateM3RippleColor()
		invalidate()
		return this
	}

	private var textColorValue: Int = MaterialColors.getColor(
		this, com.google.android.material.R.attr.colorOnPrimaryContainer, Color.WHITE
	)

	fun getTriggerTextColor(): Int = textColorValue
	fun setTextColor(color: Int): FloatingActionButtonGroup {
		textColorValue = color
		textPaint.color = color
		invalidate()
		return this
	}

	// Color de texto mientras el menú está abierto. Si no se define, se usa textColorValue.
	private var textColorVisibleValue: Int? = null
	fun getTriggerTextColorVisible(): Int? = textColorVisibleValue
	fun setTextColorVisible(color: Int?): FloatingActionButtonGroup {
		textColorVisibleValue = color
		invalidate()
		return this
	}

	private val resolvedTextColor: Int
		get() = (if (menuOpen) textColorVisibleValue else null) ?: textColorValue

	// M3 Ripple: colorOnPrimaryContainer al 12% de alpha (0.12f)
	private var triggerRippleColorValue: Int = ColorUtils.setAlphaComponent(
		MaterialColors.getColor(
			this,
			com.google.android.material.R.attr.colorOnPrimaryContainer,
			Color.WHITE
		),
		(255 * 0.12f).toInt()
	)

	fun getTriggerRippleColor(): Int = triggerRippleColorValue
	fun setTriggerColor(color: Int): FloatingActionButtonGroup {
		triggerRippleColorValue = color
		invalidate()
		return this
	}

	// Color de ripple explícito para el estado contraído (icono solo).
	private var shrunkRippleColorValue: Int? = null
	fun getShrunkRippleColor(): Int? = shrunkRippleColorValue
	fun setShrunkRippleColor(color: Int?): FloatingActionButtonGroup {
		shrunkRippleColorValue = color
		invalidate()
		return this
	}

	// Color de ripple explícito para el estado extendido (icono + texto).
	private var extendRippleColorValue: Int? = null
	fun getExtendRippleColor(): Int? = extendRippleColorValue
	fun setExtendRippleColor(color: Int?): FloatingActionButtonGroup {
		extendRippleColorValue = color
		invalidate()
		return this
	}

	/** Prioriza el override de ripple del estado actual (contraído/extendido); si no hay, usa el color base. */
	private val resolvedTriggerRippleColor: Int
		get() = (if (triggerExtended) extendRippleColorValue else shrunkRippleColorValue)
			?: triggerRippleColorValue

	private fun updateM3RippleColor() {
		val onColor =
			triggerIconTintValue.getColorForState(drawableState, triggerIconTintValue.defaultColor)
		triggerRippleColorValue = ColorUtils.setAlphaComponent(onColor, (255 * 0.12f).toInt())
	}

	/** Devuelve la elevación objetivo según la prioridad de estados M3: arrastrado > presionado > hover > reposo. */
	private fun resolveTargetElevation(): Float = when {
		isDraggedState -> draggedElevationPx
		isPressedState -> pressedElevationPx
		isHoveredState -> hoverElevationPx
		else -> restingElevationPx
	}

	private fun updateElevationState(animate: Boolean = true) {
		val target = resolveTargetElevation()
		elevationAnimator?.cancel()
		if (!animate) {
			elevation = target
			return
		}
		elevationAnimator = ValueAnimator.ofFloat(elevation, target).apply {
			duration = elevationAnimationDuration
			interpolator = this@FloatingActionButtonGroup.interpolator
			addUpdateListener { elevation = it.animatedValue as Float }
			start()
		}
	}

	/**
	 * Permite marcar el trigger como "arrastrado" (por ejemplo, desde un listener de
	 * drag-and-drop externo), elevándolo por encima del resto de los estados M3.
	 */
	fun setDragged(dragged: Boolean): FloatingActionButtonGroup {
		if (isDraggedState == dragged) return this
		isDraggedState = dragged
		updateElevationState()
		return this
	}

	fun isDragged(): Boolean = isDraggedState

	override fun onHoverEvent(event: MotionEvent): Boolean {
		when (event.actionMasked) {
			MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
				val overTrigger = triggerRect.contains(event.x, event.y)
				if (overTrigger != isHoveredState) {
					isHoveredState = overTrigger
					updateElevationState()
				}
			}

			MotionEvent.ACTION_HOVER_EXIT -> {
				if (isHoveredState) {
					isHoveredState = false
					updateElevationState()
				}
			}
		}
		return super.onHoverEvent(event)
	}

	private val triggerFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val triggerRipplePaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val triggerShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		textSize = 14f.dp
		color = textColorValue
	}

	private val triggerPillPath = Path()
	private val triggerRect = RectF()

	private var currentTriggerIcon: Drawable? = null
	private var currentTriggerText: CharSequence? = null

	private var triggerExpandedWidthPx: Float = triggerHeightPx
	private var triggerSlotLeft = 0
	private var triggerSlotTop = 0
	private var triggerWidthFraction = 1f
	private var triggerExtended: Boolean = true

	private var wasExtendedBeforeMenuOpen: Boolean = true

	fun isTriggerExtended(): Boolean = triggerExtended

	private var triggerMotionAnimator: ValueAnimator? = null
	private var rippleX = 0f
	private var rippleY = 0f
	private var rippleRadius = 0f
	private var rippleAlphaProgress = 0f
	private var rippleAnimator: ValueAnimator? = null
	private var triggerTouchClaimed = false

	private val triggerGestureDetector = GestureDetector(
		context,
		object : GestureDetector.SimpleOnGestureListener() {
			override fun onDown(e: MotionEvent): Boolean {
				startRipple(e.x, e.y)
				return true
			}

			override fun onSingleTapUp(e: MotionEvent): Boolean {
				if (activeMenuByMode == ActivationMode.CLICK) {
					toggleMenu()
				} else {
					customClickListener?.onClick(this@FloatingActionButtonGroup)
				}
				return true
			}

			override fun onLongPress(e: MotionEvent) {
				if (activeMenuByMode == ActivationMode.LONG_CLICK) {
					toggleMenu()
				} else {
					customLongClickListener?.onLongClick(this@FloatingActionButtonGroup)
				}
			}
		}
	)

	private var iconMenuDrawable: Drawable? = null
	fun getIconMenu(): Drawable? = iconMenuDrawable
	fun setIconMenu(icon: Drawable?): FloatingActionButtonGroup {
		iconMenuDrawable = icon
		refreshTriggerAppearance()
		return this
	}

	private var textMenuValue: CharSequence? = null
	fun getTextMenu(): CharSequence? = textMenuValue
	fun setTextMenu(text: CharSequence?): FloatingActionButtonGroup {
		textMenuValue = text
		refreshTriggerAppearance()
		return this
	}

	private var iconMenuVisibleDrawable: Drawable? = null
	fun getIconMenuVisible(): Drawable? = iconMenuVisibleDrawable
	fun setIconMenuVisible(icon: Drawable?): FloatingActionButtonGroup {
		iconMenuVisibleDrawable = icon
		refreshTriggerAppearance()
		return this
	}

	private var textMenuVisibleValue: CharSequence? = null
	fun getTextMenuVisible(): CharSequence? = textMenuVisibleValue
	fun setTextMenuVisible(text: CharSequence?): FloatingActionButtonGroup {
		textMenuVisibleValue = text
		refreshTriggerAppearance()
		return this
	}

	private var activeMenuByMode: ActivationMode = ActivationMode.CLICK
	fun getActiveMenuBy(): ActivationMode = activeMenuByMode
	fun setActiveMenuBy(mode: ActivationMode): FloatingActionButtonGroup {
		activeMenuByMode = mode
		return this
	}

	private var menuOrientationValue: MenuOrientation = MenuOrientation.AUTO
	fun getMenuOrientation(): MenuOrientation = menuOrientationValue
	fun setMenuOrientation(orientation: MenuOrientation): FloatingActionButtonGroup {
		menuOrientationValue = orientation
		return this
	}

	private var resolvedOrientation: MenuOrientation = MenuOrientation.TOP
	private var closeOnItemClickValue: Boolean = true
	fun isCloseOnItemClick(): Boolean = closeOnItemClickValue
	fun setCloseOnItemClick(closeOnClick: Boolean): FloatingActionButtonGroup {
		closeOnItemClickValue = closeOnClick
		return this
	}

	private var scrimEnabledValue: Boolean = true
	fun isScrimEnabled(): Boolean = scrimEnabledValue
	fun setScrimEnabled(enabled: Boolean): FloatingActionButtonGroup {
		scrimEnabledValue = enabled
		return this
	}

	/**
	 * Cuando está activo, el ScrollBehavior oculta/muestra por completo el trigger
	 * (deslizándolo fuera de pantalla) en vez de solo colapsar el texto extendido.
	 * Desactivado por defecto para preservar el comportamiento previo.
	 */
	private var hideOnScrollValue: Boolean = false
	fun isHideOnScroll(): Boolean = hideOnScrollValue
	fun setHideOnScroll(enabled: Boolean): FloatingActionButtonGroup {
		hideOnScrollValue = enabled
		return this
	}

	private var isHiddenState: Boolean = false
	fun isHidden(): Boolean = isHiddenState
	private var hideShowAnimator: ValueAnimator? = null

	private fun marginBottomPx(): Int =
		(layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0

	/** Desliza el trigger completo fuera de pantalla (comportamiento M3 de hide en scroll). */
	fun hide(animate: Boolean = true) {
		if (isHiddenState || menuOpen) return
		isHiddenState = true
		hideShowAnimator?.cancel()
		val targetY = (height + marginBottomPx()).toFloat()
		if (!animate) {
			translationY = targetY
			visibility = View.INVISIBLE
			return
		}
		hideShowAnimator = ValueAnimator.ofFloat(translationY, targetY).apply {
			duration = itemAnimationDuration
			interpolator = this@FloatingActionButtonGroup.interpolator
			addUpdateListener { translationY = it.animatedValue as Float }
			addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					if (isHiddenState) visibility = View.INVISIBLE
				}
			})
			start()
		}
	}

	/** Vuelve a mostrar el trigger tras un hide(). */
	fun show(animate: Boolean = true) {
		if (!isHiddenState) return
		isHiddenState = false
		visibility = View.VISIBLE
		hideShowAnimator?.cancel()
		if (!animate) {
			translationY = 0f
			return
		}
		hideShowAnimator = ValueAnimator.ofFloat(translationY, 0f).apply {
			duration = itemAnimationDuration
			interpolator = this@FloatingActionButtonGroup.interpolator
			addUpdateListener { translationY = it.animatedValue as Float }
			start()
		}
	}

	private var scrimColorValue: Int = ColorUtils.setAlphaComponent(
		Color.BLACK,
		12
	)

	fun getScrimColor(): Int = scrimColorValue
	fun setScrimColor(color: Int): FloatingActionButtonGroup {
		scrimColorValue = color
		scrimView?.setBackgroundColor(color)
		return this
	}

	private var menuOpen: Boolean = false
	fun isMenuOpen(): Boolean = menuOpen

	private var selectedItemValue: FloatingActionButtonItem? = null
	fun getSelectedItem(): FloatingActionButtonItem? = selectedItemValue

	init {
		clipChildren = false
		clipToPadding = false
		isClickable = true
		isFocusable = true
		setWillNotDraw(false)

		context.obtainStyledAttributes(
			attrs,
			R.styleable.FloatingActionButtonGroup,
			defStyleAttr,
			0
		)
			.apply {
				try {
					setIconMenuVisible(getDrawable(R.styleable.FloatingActionButtonGroup_icon))
					setTextMenuVisible(getString(R.styleable.FloatingActionButtonGroup_text))
					setIconMenu(getDrawable(R.styleable.FloatingActionButtonGroup_android_icon))
					setTextMenu(getString(R.styleable.FloatingActionButtonGroup_android_text))
					setTextColor(
						getColor(
							R.styleable.FloatingActionButtonGroup_android_textColor,
							getTriggerTextColor()
						)
					)
					if (hasValue(R.styleable.FloatingActionButtonGroup_textColor)) {
						setTextColorVisible(
							getColor(
								R.styleable.FloatingActionButtonGroup_textColor,
								getTriggerTextColor()
							)
						)
					}
					setTriggerBackgroundTint(
						ColorStateList.valueOf(
							getColor(
								R.styleable.FloatingActionButtonGroup_android_backgroundTint,
								getTriggerBackgroundTint().defaultColor
							)
						)
					)
					if (hasValue(R.styleable.FloatingActionButtonGroup_backgroundTint)) {
						setTriggerBackgroundTintVisible(
							ColorStateList.valueOf(
								getColor(
									R.styleable.FloatingActionButtonGroup_backgroundTint,
									getTriggerBackgroundTint().defaultColor
								)
							)
						)
					}
					setActiveMenuBy(
						ActivationMode.entries.toTypedArray()[getInt(
							R.styleable.FloatingActionButtonGroup_activeMenuBy,
							0
						)]
					)
					setCloseOnItemClick(
						getBoolean(
							R.styleable.FloatingActionButtonGroup_closeOnItemClick,
							true
						)
					)
					setScrimEnabled(
						getBoolean(
							R.styleable.FloatingActionButtonGroup_scrimEnabled,
							true
						)
					)
					setScrimColor(
						getColor(
							R.styleable.FloatingActionButtonGroup_scrimColor,
							getScrimColor()
						)
					)
					setMenuOrientation(
						MenuOrientation.entries.toTypedArray()[getInt(
							R.styleable.FloatingActionButtonGroup_menuOrientation,
							MenuOrientation.AUTO.ordinal
						)]
					)
					setTriggerColor(
						getColor(
							R.styleable.FloatingActionButtonGroup_rippleColor,
							getTriggerRippleColor()
						)
					)
					setTriggerCornerRadius(
						getDimension(
							R.styleable.FloatingActionButtonGroup_cornerRadius,
							16f.dp
						)
					)
					if (hasValue(R.styleable.FloatingActionButtonGroup_shrunkRippleColor)) {
						setShrunkRippleColor(
							getColor(
								R.styleable.FloatingActionButtonGroup_shrunkRippleColor,
								getTriggerRippleColor()
							)
						)
					}
					if (hasValue(R.styleable.FloatingActionButtonGroup_shrunkCornerRadius)) {
						setShrunkCornerRadius(
							getDimension(
								R.styleable.FloatingActionButtonGroup_shrunkCornerRadius,
								0f
							)
						)
					}
					if (hasValue(R.styleable.FloatingActionButtonGroup_extendRippleColor)) {
						setExtendRippleColor(
							getColor(
								R.styleable.FloatingActionButtonGroup_extendRippleColor,
								getTriggerRippleColor()
							)
						)
					}
					if (hasValue(R.styleable.FloatingActionButtonGroup_extendCornerRadius)) {
						setExtendCornerRadius(
							getDimension(
								R.styleable.FloatingActionButtonGroup_extendCornerRadius,
								0f
							)
						)
					}
					setIconSide(
						IconSide.entries.toTypedArray()[getInt(
							R.styleable.FloatingActionButtonGroup_iconSide,
							0
						)]
					)
				} finally {
					recycle()
				}
			}
		resolvedOrientation =
			if (menuOrientationValue == MenuOrientation.AUTO) MenuOrientation.TOP else menuOrientationValue
		updateM3RippleColor()
		refreshTriggerAppearance()
		elevation = restingElevationPx
	}

	override fun onAttachedToWindow() {
		super.onAttachedToWindow()
		(parent as? ViewGroup)?.let {
			it.clipChildren = false
			it.clipToPadding = false
		}
	}

	override fun addView(child: View, index: Int, params: LayoutParams?) {
		if (child is FloatingActionButtonItem) {
			if (child.visibility == View.GONE) {
				// Se respeta GONE tal cual fue declarado: no entra a la lista de items,
				// no se mide, no se anima y no participa del layout del menú.
				super.addView(child, index, params)
				return
			}
			items.add(child)
			child.visibility = View.INVISIBLE
			child.alpha = 0f
			child.setOnClickListener { onItemTapped(child) }
		}
		super.addView(child, index, params)
	}

	fun getItemCount(): Int = items.size
	fun getItemAt(index: Int): FloatingActionButtonItem = items[index]

	private val MenuOrientation.isVertical: Boolean
		get() = this == MenuOrientation.TOP || this == MenuOrientation.BOTTOM

	/** true si el layout actual corre de derecha a izquierda (locale RTL). */
	private val isLayoutRtl: Boolean
		get() = layoutDirection == LAYOUT_DIRECTION_RTL

	/**
	 * TOP/BOTTOM (y el fallback de AUTO) no especifican un lado explícito: el trigger
	 * se pega al borde "final" (end), que es la derecha física en LTR y la izquierda en RTL.
	 * LEFT/RIGHT son elecciones físicas explícitas del desarrollador y no se espejan.
	 */
	private val triggerHugsPhysicalRight: Boolean
		get() = when (resolvedOrientation) {
			MenuOrientation.RIGHT -> false
			MenuOrientation.LEFT -> true
			else -> !isLayoutRtl
		}

	override fun onRtlPropertiesChanged(layoutDirection: Int) {
		super.onRtlPropertiesChanged(layoutDirection)
		recomputeTriggerExpandedWidth()
		requestLayout()
		invalidate()
	}

	private fun recomputeTriggerExpandedWidth() {
		val text = currentTriggerText
		triggerExpandedWidthPx = if (text.isNullOrEmpty()) {
			triggerHeightPx
		} else {
			val textWidth = textPaint.measureText(text, 0, text.length)
			val hasIcon = currentTriggerIcon != null
			val iconGap = if (hasIcon) triggerIconSizePx + triggerIconTextGapPx else 0f
			triggerPaddingStartPx + iconGap + textWidth + triggerPaddingEndPx
		}
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		recomputeTriggerExpandedWidth()
		for (i in 0 until items.size) {
			items[i].measure(
				MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
				MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
			)
		}

		val desiredWidth: Int
		val desiredHeight: Int
		if (resolvedOrientation.isVertical) {
			var maxChildWidth = 0
			var sumHeight = 0
			for (i in 0 until items.size) {
				val item = items[i]
				if (item.measuredWidth > maxChildWidth) maxChildWidth = item.measuredWidth
				sumHeight += item.measuredHeight + itemSpacingPx
			}
			desiredWidth =
				maxOf(triggerExpandedWidthPx.toInt(), maxChildWidth) + (shadowPaddingPx * 2)
			desiredHeight = triggerHeightPx.toInt() + sumHeight + (shadowPaddingPx * 2)
		} else {
			var sumWidth = 0
			var maxChildHeight = 0
			for (i in 0 until items.size) {
				val item = items[i]
				sumWidth += item.measuredWidth + itemSpacingPx
				if (item.measuredHeight > maxChildHeight) maxChildHeight = item.measuredHeight
			}
			desiredWidth = triggerExpandedWidthPx.toInt() + sumWidth + (shadowPaddingPx * 2)
			desiredHeight = maxOf(triggerHeightPx.toInt(), maxChildHeight) + (shadowPaddingPx * 2)
		}
		setMeasuredDimension(
			resolveSize(desiredWidth, widthMeasureSpec),
			resolveSize(desiredHeight, heightMeasureSpec)
		)
	}

	override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
		val width = r - l - (shadowPaddingPx * 2)
		val height = b - t - (shadowPaddingPx * 2)

		triggerSlotLeft =
			if (triggerHugsPhysicalRight) width - triggerExpandedWidthPx.toInt() + shadowPaddingPx else shadowPaddingPx
		triggerSlotTop =
			if (resolvedOrientation == MenuOrientation.BOTTOM) shadowPaddingPx else height - triggerHeightPx.toInt() + shadowPaddingPx
		layoutTriggerRect()

		when (resolvedOrientation) {
			MenuOrientation.TOP -> {
				var itemBottom = triggerSlotTop - itemSpacingPx
				for (i in 0 until items.size) {
					val item = items[i]
					val itemLeft = if (triggerHugsPhysicalRight) {
						width + shadowPaddingPx - item.measuredWidth
					} else {
						shadowPaddingPx
					}
					val itemTop = itemBottom - item.measuredHeight
					item.layout(itemLeft, itemTop, itemLeft + item.measuredWidth, itemBottom)
					item.translationY =
						if (menuOpen) 0f else (itemBottom - triggerSlotTop).toFloat()
					itemBottom = itemTop - itemSpacingPx
				}
			}

			MenuOrientation.BOTTOM -> {
				var itemTop = triggerSlotTop + triggerHeightPx.toInt() + itemSpacingPx
				for (i in 0 until items.size) {
					val item = items[i]
					val itemLeft = if (triggerHugsPhysicalRight) {
						width + shadowPaddingPx - item.measuredWidth
					} else {
						shadowPaddingPx
					}
					item.layout(
						itemLeft,
						itemTop,
						itemLeft + item.measuredWidth,
						itemTop + item.measuredHeight
					)
					item.translationY =
						if (menuOpen) 0f else (itemTop - (triggerSlotTop + triggerHeightPx.toInt())).toFloat()
					itemTop += item.measuredHeight + itemSpacingPx
				}
			}

			MenuOrientation.LEFT -> {
				var itemRight = triggerSlotLeft - itemSpacingPx
				for (i in 0 until items.size) {
					val item = items[i]
					val itemTop = height + shadowPaddingPx - item.measuredHeight
					val itemLeft = itemRight - item.measuredWidth
					item.layout(itemLeft, itemTop, itemRight, itemTop + item.measuredHeight)
					item.translationX =
						if (menuOpen) 0f else (itemRight - triggerSlotLeft).toFloat()
					itemRight = itemLeft - itemSpacingPx
				}
			}

			MenuOrientation.RIGHT -> {
				var itemLeft = triggerSlotLeft + triggerExpandedWidthPx.toInt() + itemSpacingPx
				for (i in 0 until items.size) {
					val item = items[i]
					val itemTop = height + shadowPaddingPx - item.measuredHeight
					item.layout(
						itemLeft,
						itemTop,
						itemLeft + item.measuredWidth,
						itemTop + item.measuredHeight
					)
					item.translationX =
						if (menuOpen) 0f else (itemLeft - (triggerSlotLeft + triggerExpandedWidthPx.toInt())).toFloat()
					itemLeft += item.measuredWidth + itemSpacingPx
				}
			}

			MenuOrientation.AUTO -> Unit
		}
	}

	private fun layoutTriggerRect() {
		val currentWidth =
			triggerHeightPx + (triggerExpandedWidthPx - triggerHeightPx) * triggerWidthFraction
		val pillLeft = if (triggerHugsPhysicalRight) {
			triggerSlotLeft + (triggerExpandedWidthPx - currentWidth)
		} else {
			triggerSlotLeft.toFloat()
		}
		triggerRect.set(
			pillLeft,
			triggerSlotTop.toFloat(),
			pillLeft + currentWidth,
			triggerSlotTop + triggerHeightPx
		)
	}

	private fun computeAutoOrientation(): MenuOrientation {
		val loc = IntArray(2)
		getLocationOnScreen(loc)
		val triggerScreenLeft = loc[0] + triggerSlotLeft
		val triggerScreenTop = loc[1] + triggerSlotTop
		val screenWidth = resources.displayMetrics.widthPixels
		val screenHeight = resources.displayMetrics.heightPixels

		val spaceAbove = triggerScreenTop
		val spaceBelow = screenHeight - (triggerScreenTop + triggerHeightPx.toInt())
		val spaceLeft = triggerScreenLeft
		val spaceRight = screenWidth - (triggerScreenLeft + triggerExpandedWidthPx.toInt())

		var verticalNeeded = 0
		var horizontalNeeded = 0
		for (i in 0 until items.size) {
			verticalNeeded += items[i].measuredHeight + itemSpacingPx
			horizontalNeeded += items[i].measuredWidth + itemSpacingPx
		}

		return when {
			spaceAbove >= verticalNeeded -> MenuOrientation.TOP
			spaceBelow >= verticalNeeded -> MenuOrientation.BOTTOM
			spaceLeft >= horizontalNeeded -> MenuOrientation.LEFT
			spaceRight >= horizontalNeeded -> MenuOrientation.RIGHT
			maxOf(spaceAbove, spaceBelow) >= maxOf(spaceLeft, spaceRight) ->
				if (spaceAbove >= spaceBelow) MenuOrientation.TOP else MenuOrientation.BOTTOM

			else -> if (spaceLeft >= spaceRight) MenuOrientation.LEFT else MenuOrientation.RIGHT
		}
	}

	override fun onDraw(canvas: Canvas) {
		super.onDraw(canvas)

		drawTriggerShadow(canvas)

		triggerPillPath.reset()
		triggerPillPath.addRoundRect(
			triggerRect,
			resolvedTriggerCornerRadius,
			resolvedTriggerCornerRadius,
			Path.Direction.CW
		)
		val activeBackgroundTint = resolvedTriggerBackgroundTint
		triggerFillPaint.color = activeBackgroundTint.getColorForState(
			drawableState,
			activeBackgroundTint.defaultColor
		)
		canvas.drawPath(triggerPillPath, triggerFillPaint)

		if (rippleAlphaProgress > 0f) {
			canvas.save()
			canvas.clipPath(triggerPillPath)
			val activeRippleColor = resolvedTriggerRippleColor
			val baseAlpha = Color.alpha(activeRippleColor)
			triggerRipplePaint.color = activeRippleColor
			triggerRipplePaint.alpha = (baseAlpha * rippleAlphaProgress).toInt()
			canvas.drawCircle(rippleX, rippleY, rippleRadius, triggerRipplePaint)
			canvas.restore()
		}

		val text = currentTriggerText
		val hasText = !text.isNullOrEmpty() && triggerWidthFraction > 0.05f
		// IconSide es lógico (start/end): en RTL, START se dibuja visualmente a la derecha.
		val isLogicalStart = iconSideValue == IconSide.START
		val isIconStart = if (isLayoutRtl) !isLogicalStart else isLogicalStart

		val iconLeft = if (!hasText) {
			triggerRect.left + (triggerRect.width() - triggerIconSizePx) / 2f
		} else if (isIconStart) {
			triggerRect.left + triggerPaddingStartPx
		} else {
			triggerRect.right - triggerPaddingEndPx - triggerIconSizePx
		}
		val iconTop = triggerRect.top + (triggerHeightPx - triggerIconSizePx) / 2f

		currentTriggerIcon?.let { icon ->
			val tinted = icon.mutate()
			tinted.setTint(
				triggerIconTintValue.getColorForState(
					drawableState,
					triggerIconTintValue.defaultColor
				)
			)
			tinted.setBounds(
				iconLeft.toInt(),
				iconTop.toInt(),
				(iconLeft + triggerIconSizePx).toInt(),
				(iconTop + triggerIconSizePx).toInt()
			)
			tinted.draw(canvas)
		}

		if (hasText && text != null) {
			textPaint.color = resolvedTextColor
			textPaint.alpha = (triggerWidthFraction * 255).toInt()
			textPaint.typeface = Typeface.DEFAULT_BOLD
			val baseline = triggerRect.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f
			val textStart = if (currentTriggerIcon == null) {
				triggerRect.left + triggerPaddingStartPx
			} else if (isIconStart) {
				iconLeft + triggerIconSizePx + triggerIconTextGapPx
			} else {
				triggerRect.left + triggerPaddingStartPx
			}
			canvas.save()
			canvas.clipRect(triggerRect)
			canvas.drawText(text, 0, text.length, textStart, baseline, textPaint)
			canvas.restore()
		}
	}

	private fun drawTriggerShadow(canvas: Canvas) {
		triggerShadowPaint.color = Color.BLACK
		for (i in 3 downTo 1) {
			val offset = 1.5f.dp * i
			triggerShadowPaint.alpha = 20 / i
			canvas.drawRoundRect(
				triggerRect.left - (1f.dp * i),
				triggerRect.top + offset - (1f.dp * i),
				triggerRect.right + (1f.dp * i),
				triggerRect.bottom + offset + (1f.dp * i),
				resolvedTriggerCornerRadius + (1f.dp * i),
				resolvedTriggerCornerRadius + (1f.dp * i),
				triggerShadowPaint
			)
		}
	}

	override fun onTouchEvent(event: MotionEvent): Boolean {
		if (!isEnabled) return false
		when (event.actionMasked) {
			MotionEvent.ACTION_DOWN -> {
				triggerTouchClaimed = triggerRect.contains(event.x, event.y)
				if (!triggerTouchClaimed) return false
				isPressedState = true
				updateElevationState()
			}

			MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
				if (triggerTouchClaimed) endRipple()
				val wasClaimed = triggerTouchClaimed
				triggerTouchClaimed = false
				if (isPressedState) {
					isPressedState = false
					updateElevationState()
				}
				if (!wasClaimed) return false
			}
		}
		if (!triggerTouchClaimed && event.actionMasked != MotionEvent.ACTION_UP &&
			event.actionMasked != MotionEvent.ACTION_CANCEL
		) return false
		triggerGestureDetector.onTouchEvent(event)
		return true
	}

	override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
		super.onInitializeAccessibilityNodeInfo(info)
		info.className = "android.widget.Button"
		info.isClickable = true
		if (contentDescription.isNullOrEmpty()) {
			info.text = currentTriggerText ?: (if (menuOpen) "Close menu" else "Open menu")
		}
	}

	private fun startRipple(x: Float, y: Float) {
		rippleAnimator?.cancel()
		rippleX = x
		rippleY = y
		rippleAlphaProgress = 1f
		val maxRadius = maxOf(triggerRect.width(), triggerRect.height()) * 1.2f
		rippleAnimator = ValueAnimator.ofFloat(0f, maxRadius).apply {
			duration = 240L
			interpolator = this@FloatingActionButtonGroup.interpolator
			addUpdateListener {
				rippleRadius = it.animatedValue as Float
				invalidate()
			}
			start()
		}
	}

	private fun endRipple() {
		ValueAnimator.ofFloat(rippleAlphaProgress, 0f).apply {
			duration = 200L
			addUpdateListener {
				rippleAlphaProgress = it.animatedValue as Float
				invalidate()
			}
			start()
		}
	}

	fun toggleMenu(animate: Boolean = true) {
		if (menuOpen) closeMenu(animate) else openMenu(animate)
	}

	fun openMenu(animate: Boolean = true) {
		if (menuOpen) return
		if (isHiddenState) show(animate)
		wasExtendedBeforeMenuOpen = triggerExtended
		menuOpen = true
		refreshTriggerAppearance()

		if (menuOrientationValue == MenuOrientation.AUTO) {
			resolvedOrientation = computeAutoOrientation()
		}
		requestLayout()

		runAfterNextLayout {
			val startCascade = {
				showScrim(animate)
				for (index in 0 until items.size) {
					val item = items[index]
					item.visibility = VISIBLE
					val animator = item.animate()
						.alpha(1f)
						.setStartDelay(if (animate) index * itemStaggerDelay else 0L)
						.setDuration(if (animate) itemAnimationDuration else 0L)
						.setInterpolator(interpolator)
					if (resolvedOrientation.isVertical) animator.translationY(0f) else animator.translationX(
						0f
					)
					animator.start()
				}
				onMenuStateChangeListener?.onMenuOpened(this)
			}

			if (animate && !triggerExtended) {
				expanded(true) { startCascade() }
			} else {
				startCascade()
			}
		}
	}

	private fun runAfterNextLayout(action: () -> Unit) {
		addOnLayoutChangeListener(object : OnLayoutChangeListener {
			override fun onLayoutChange(
				v: View, left: Int, top: Int, right: Int, bottom: Int,
				oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
			) {
				v.removeOnLayoutChangeListener(this)
				action()
			}
		})
	}

	fun closeMenu(animate: Boolean = true) {
		if (!menuOpen) return
		menuOpen = false
		refreshTriggerAppearance()
		hideScrim(animate)

		val vertical = resolvedOrientation.isVertical
		for (index in 0 until items.size) {
			val item = items[index]
			val animator = item.animate()
				.alpha(0f)
				.setStartDelay(if (animate) index * itemStaggerDelay else 0L)
				.setDuration(if (animate) itemAnimationDuration else 0L)
				.setInterpolator(interpolator)
				.withEndAction { item.visibility = View.INVISIBLE }
			if (vertical) {
				val collapsedTop = when (resolvedOrientation) {
					MenuOrientation.TOP -> triggerSlotTop - itemSpacingPx - item.height
					else -> triggerSlotTop + triggerHeightPx.toInt() + itemSpacingPx
				}
				animator.translationY((collapsedTop - item.top).toFloat())
			} else {
				val collapsedLeft = when (resolvedOrientation) {
					MenuOrientation.LEFT -> triggerSlotLeft - itemSpacingPx - item.width
					else -> triggerSlotLeft + triggerExpandedWidthPx.toInt() + itemSpacingPx
				}
				animator.translationX((collapsedLeft - item.left).toFloat())
			}
			animator.start()
		}

		if (!wasExtendedBeforeMenuOpen) {
			shrunk(animate)
		}

		onMenuStateChangeListener?.onMenuClosed(this)
	}

	fun selectItem(item: FloatingActionButtonItem, animate: Boolean = true) {
		onItemTapped(item)
	}

	private fun onItemTapped(item: FloatingActionButtonItem) {
		onMenuItemClickedListener?.invoke(item)

		val previous = selectedItemValue
		if (previous !== item) {
			previous?.let {
				it.isSelected = false
				onMenuItemChangedListener?.onFloatingActionItemUnselect(it)
			}
			item.isSelected = true
			selectedItemValue = item
			onMenuItemChangedListener?.onFloatingActionItemSelect(item)
		} else {
			onMenuItemChangedListener?.onFloatingActionItemReselect(item)
		}

		if (closeOnItemClickValue) closeMenu()
	}

	private fun refreshTriggerAppearance() {
		currentTriggerIcon = (if (menuOpen) iconMenuVisibleDrawable else null) ?: iconMenuDrawable
		currentTriggerText = (if (menuOpen) textMenuVisibleValue else null) ?: textMenuValue
		recomputeTriggerExpandedWidth()
		if (triggerExtended) layoutTriggerRect()
		invalidate()
	}

	fun expanded(animate: Boolean = true, onEnd: (() -> Unit)? = null) {
		if (triggerExtended) {
			onEnd?.invoke()
			return
		}
		animateTriggerWidth(1f, animate, onEnd)
	}

	fun shrunk(animate: Boolean = true) {
		if (!triggerExtended) return
		animateTriggerWidth(0f, animate, null)
	}

	private fun animateTriggerWidth(target: Float, animate: Boolean, onEnd: (() -> Unit)?) {
		triggerMotionAnimator?.cancel()
		triggerExtended = target == 1f
		if (!animate) {
			triggerWidthFraction = target
			layoutTriggerRect()
			invalidate()
			onEnd?.invoke()
			return
		}
		triggerMotionAnimator = ValueAnimator.ofFloat(triggerWidthFraction, target).apply {
			duration = 200L
			interpolator = this@FloatingActionButtonGroup.interpolator
			addUpdateListener {
				triggerWidthFraction = it.animatedValue as Float
				layoutTriggerRect()
				invalidate()
			}
			addListener(object : AnimatorListenerAdapter() {
				override fun onAnimationEnd(animation: Animator) {
					onEnd?.invoke()
				}
			})
			start()
		}
	}

	private fun findScrimHost(): Pair<ViewGroup, View>? {
		var child: View = this
		var current: ViewParent? = parent
		while (current is ViewGroup) {
			if (current is CoordinatorLayout || current.id == android.R.id.content) {
				return current to child
			}
			child = current
			current = current.parent
		}
		return null
	}

	private fun ensureScrim(): View {
		scrimView?.let { return it }
		val (host, anchorChild) = findScrimHost() ?: return View(context).also { scrimView = it }
		val scrim = View(context).apply {
			setBackgroundColor(scrimColorValue)
			alpha = 0f
			setOnClickListener { closeMenu() }
		}
		scrimView = scrim
		host.addView(
			scrim,
			host.indexOfChild(anchorChild),
			ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.MATCH_PARENT
			)
		)
		return scrim
	}

	private fun showScrim(animate: Boolean) {
		if (!scrimEnabledValue) return
		val scrim = ensureScrim()
		scrim.visibility = View.VISIBLE
		scrim.animate()
			.alpha(1f)
			.setDuration(if (animate) itemAnimationDuration else 0L)
			.setInterpolator(interpolator)
			.start()
	}

	private fun hideScrim(animate: Boolean) {
		if (!scrimEnabledValue) return
		scrimView?.animate()
			?.alpha(0f)
			?.setDuration(if (animate) itemAnimationDuration else 0L)
			?.setInterpolator(interpolator)
			?.withEndAction { scrimView?.visibility = View.INVISIBLE }
			?.start()
	}

	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		scrimView?.let { (it.parent as? ViewGroup)?.removeView(it) }
		scrimView = null
		triggerMotionAnimator?.cancel()
		rippleAnimator?.cancel()
		elevationAnimator?.cancel()
		hideShowAnimator?.cancel()
	}

	override fun onSaveInstanceState(): Parcelable? {
		val superState = super.onSaveInstanceState()
		val state = SavedState(superState)
		state.menuOpenState = menuOpen
		state.triggerExtendedState = triggerExtended
		state.triggerWidthFractionState = triggerWidthFraction
		state.resolvedOrientationOrdinal = resolvedOrientation.ordinal
		state.hiddenState = isHiddenState
		state.selectedItemIndex = selectedItemValue?.let { items.indexOf(it) } ?: -1
		return state
	}

	override fun onRestoreInstanceState(state: Parcelable?) {
		if (state !is SavedState) {
			super.onRestoreInstanceState(state)
			return
		}
		super.onRestoreInstanceState(state.superState)

		menuOpen = state.menuOpenState
		triggerExtended = state.triggerExtendedState
		triggerWidthFraction = state.triggerWidthFractionState
		resolvedOrientation = MenuOrientation.entries.toTypedArray()
			.getOrElse(state.resolvedOrientationOrdinal) { resolvedOrientation }
		isHiddenState = state.hiddenState

		if (state.selectedItemIndex in items.indices) {
			val restored = items[state.selectedItemIndex]
			restored.isSelected = true
			selectedItemValue = restored
		}

		refreshTriggerAppearance()
		visibility = if (isHiddenState && !menuOpen) View.INVISIBLE else View.VISIBLE

		if (menuOpen) {
			for (index in 0 until items.size) {
				val item = items[index]
				item.visibility = View.VISIBLE
				item.alpha = 1f
			}
			showScrim(false)
		}

		requestLayout()
	}

	/** Estado persistido de FloatingActionButtonGroup para sobrevivir rotaciones y recreación de proceso. */
	class SavedState : BaseSavedState {
		var menuOpenState: Boolean = false
		var triggerExtendedState: Boolean = true
		var triggerWidthFractionState: Float = 1f
		var resolvedOrientationOrdinal: Int = MenuOrientation.TOP.ordinal
		var hiddenState: Boolean = false
		var selectedItemIndex: Int = -1

		constructor(superState: Parcelable?) : super(superState)

		private constructor(source: Parcel) : super(source) {
			menuOpenState = source.readByte() != 0.toByte()
			triggerExtendedState = source.readByte() != 0.toByte()
			triggerWidthFractionState = source.readFloat()
			resolvedOrientationOrdinal = source.readInt()
			hiddenState = source.readByte() != 0.toByte()
			selectedItemIndex = source.readInt()
		}

		override fun writeToParcel(out: Parcel, flags: Int) {
			super.writeToParcel(out, flags)
			out.writeByte(if (menuOpenState) 1 else 0)
			out.writeByte(if (triggerExtendedState) 1 else 0)
			out.writeFloat(triggerWidthFractionState)
			out.writeInt(resolvedOrientationOrdinal)
			out.writeByte(if (hiddenState) 1 else 0)
			out.writeInt(selectedItemIndex)
		}

		companion object {
			@JvmField
			val CREATOR = object : Parcelable.Creator<SavedState> {
				override fun createFromParcel(source: Parcel): SavedState = SavedState(source)
				override fun newArray(size: Int): Array<SavedState?> = arrayOfNulls(size)
			}
		}
	}

	private var onMenuItemClickedListener: ((FloatingActionButtonItem) -> Unit)? = null
	private var onMenuItemChangedListener: OnMenuItemChangedListener? = null
	private var onMenuStateChangeListener: OnMenuStateChangeListener? = null

	fun setOnMenuItemClickedListener(listener: (FloatingActionButtonItem) -> Unit): FloatingActionButtonGroup {
		onMenuItemClickedListener = listener
		return this
	}

	fun setOnMenuItemChangedListener(listener: OnMenuItemChangedListener): FloatingActionButtonGroup {
		onMenuItemChangedListener = listener
		return this
	}

	fun setOnMenuStateChangeListener(listener: OnMenuStateChangeListener): FloatingActionButtonGroup {
		onMenuStateChangeListener = listener
		return this
	}

	interface OnMenuItemChangedListener {
		fun onFloatingActionItemSelect(item: FloatingActionButtonItem)
		fun onFloatingActionItemUnselect(item: FloatingActionButtonItem)
		fun onFloatingActionItemReselect(item: FloatingActionButtonItem)
	}

	interface OnMenuStateChangeListener {
		fun onMenuOpened(menu: FloatingActionButtonGroup)
		fun onMenuClosed(menu: FloatingActionButtonGroup)
	}

	override fun getBehavior(): CoordinatorLayout.Behavior<FloatingActionButtonGroup> =
		ScrollBehavior()

	inner class ScrollBehavior : CoordinatorLayout.Behavior<FloatingActionButtonGroup>() {
		override fun onStartNestedScroll(
			coordinatorLayout: CoordinatorLayout,
			child: FloatingActionButtonGroup,
			directTargetChild: View,
			target: View,
			axes: Int,
			type: Int
		): Boolean {
			if (menuOpen) return false
			return axes and ViewCompat.SCROLL_AXIS_VERTICAL != 0
		}

		override fun onNestedScroll(
			coordinatorLayout: CoordinatorLayout,
			child: FloatingActionButtonGroup,
			target: View,
			dxConsumed: Int,
			dyConsumed: Int,
			dxUnconsumed: Int,
			dyUnconsumed: Int,
			type: Int,
			consumed: IntArray
		) {
			if (menuOpen) return
			if (hideOnScrollValue) {
				when {
					dyConsumed > 0 -> hide()
					dyConsumed < 0 -> show()
				}
			} else {
				when {
					dyConsumed > 0 -> shrunk()
					dyConsumed < 0 -> expanded()
				}
			}
		}
	}
}