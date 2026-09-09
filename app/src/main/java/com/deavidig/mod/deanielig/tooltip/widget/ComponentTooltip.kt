package com.deavidig.mod.deanielig.tooltip.widget

import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Space
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.R as MaterialR
import kotlin.math.max
import kotlin.math.min
import androidx.core.graphics.toColorInt
import com.deavidig.sketchprojectpro.R

/**
 * Tooltip M3 anclado a una View con la apariencia y API inspiradas en `MaterialAlertDialogBuilder`.
 * Ajusta dinámicamente el radio de sus esquinas para que el caret/flecha nunca quede flotando.
 */
class ComponentTooltip(private val anchor: View) {

	enum class Placement { AUTO, TOP, BOTTOM, LEFT, RIGHT, START, END }

	enum class ArrowDirection { UP, DOWN, LEFT, RIGHT }

	fun interface OnClickListener {
		fun onClick(tooltip: ComponentTooltip, which: Int)
	}

	companion object {
		const val BUTTON_POSITIVE = DialogInterface.BUTTON_POSITIVE
		const val BUTTON_NEGATIVE = DialogInterface.BUTTON_NEGATIVE
		const val BUTTON_NEUTRAL = DialogInterface.BUTTON_NEUTRAL

		private var active: ComponentTooltip? = null

		private val DEFAULT_PLAIN_CONTAINER = Color.parseColor("#313033")
		private val DEFAULT_PLAIN_ON_CONTAINER = Color.parseColor("#F4EFF4")
		private val DEFAULT_RICH_CONTAINER = Color.parseColor("#ECE6F0")
		private val DEFAULT_ON_SURFACE = Color.parseColor("#1C1B1F")
		private val DEFAULT_ON_SURFACE_VARIANT = Color.parseColor("#49454F")
		private val DEFAULT_COLOR_PRIMARY = Color.parseColor("#6750A4")

		fun attach(
			anchor: View,
			message: CharSequence,
			placement: Placement = Placement.AUTO,
			dismissAfterMs: Long = 3000L
		) {
			anchor.setOnLongClickListener {
				ComponentTooltip(anchor)
					.setMessage(message)
					.setPlacement(placement)
					.setDismissAfter(dismissAfterMs)
					.show()
				true
			}
		}
	}

	private var iconDrawable: Drawable? = null
	private var titleText: CharSequence? = null
	private var messageText: CharSequence? = null
	private var customView: View? = null
	private var placement: Placement = Placement.AUTO
	private var dismissAfterMs: Long = 0L

	private var positiveText: CharSequence? = null
	private var positiveListener: OnClickListener? = null
	private var negativeText: CharSequence? = null
	private var negativeListener: OnClickListener? = null
	private var neutralText: CharSequence? = null
	private var neutralListener: OnClickListener? = null

	private var popup: PopupWindow? = null

	// ---- API Fluent ----

	fun setIcon(drawable: Drawable?): ComponentTooltip = apply { iconDrawable = drawable }
	fun setIcon(@DrawableRes resId: Int): ComponentTooltip = apply { iconDrawable = ContextCompat.getDrawable(anchor.context, resId) }

	fun setTitle(title: CharSequence?): ComponentTooltip = apply { titleText = title }
	fun setTitle(@StringRes resId: Int): ComponentTooltip = apply { titleText = anchor.context.getText(resId) }

	fun setMessage(message: CharSequence?): ComponentTooltip = apply { messageText = message }
	fun setMessage(@StringRes resId: Int): ComponentTooltip = apply { messageText = anchor.context.getText(resId) }

	fun setCustomView(view: View?): ComponentTooltip = apply { customView = view }
	fun setCustomView(@LayoutRes layoutResId: Int): ComponentTooltip = apply {
		val inflater = LayoutInflater.from(anchor.context)
		customView = inflater.inflate(layoutResId, null, false)
	}

	fun getCustomView(): View? = customView

	fun setPositiveButton(text: CharSequence, listener: OnClickListener?): ComponentTooltip = apply {
		positiveText = text; positiveListener = listener
	}
	fun setPositiveButton(@StringRes resId: Int, listener: OnClickListener?): ComponentTooltip =
		setPositiveButton(anchor.context.getText(resId), listener)

	fun setNegativeButton(text: CharSequence, listener: OnClickListener?): ComponentTooltip = apply {
		negativeText = text; negativeListener = listener
	}
	fun setNegativeButton(@StringRes resId: Int, listener: OnClickListener?): ComponentTooltip =
		setNegativeButton(anchor.context.getText(resId), listener)

	fun setNeutralButton(text: CharSequence, listener: OnClickListener?): ComponentTooltip = apply {
		neutralText = text; neutralListener = listener
	}
	fun setNeutralButton(@StringRes resId: Int, listener: OnClickListener?): ComponentTooltip =
		setNeutralButton(anchor.context.getText(resId), listener)

	fun setPlacement(placement: Placement): ComponentTooltip = apply { this.placement = placement }
	fun setDismissAfter(ms: Long): ComponentTooltip = apply { dismissAfterMs = ms }

	// ---- Mostrar y Ocultar ----

	fun show(): ComponentTooltip = apply {
		active?.dismiss()

		val context = anchor.context
		val isRich = titleText != null ||
				positiveText != null ||
				negativeText != null ||
				neutralText != null ||
				iconDrawable != null ||
				customView != null

		if (isRich) {
			presentPopup(buildRichView(context), elevationDp = 12f)
		} else {
			presentPopup(buildPlainView(context), elevationDp = 2f)
		}
		active = this
	}

	fun dismiss() {
		popup?.dismiss()
		popup = null
		if (active === this) active = null
	}

	// ---- Construcción interna ----

	private fun buildPlainView(context: Context): PlainTooltipView {
		val container = MaterialColors.getColor(anchor, MaterialR.attr.colorSurfaceInverse, DEFAULT_PLAIN_CONTAINER)
		val onContainer = MaterialColors.getColor(anchor, MaterialR.attr.colorOnSurfaceInverse, DEFAULT_PLAIN_ON_CONTAINER)
		val corner = resolveCornerRadiusPx(context, MaterialR.attr.shapeAppearanceCornerExtraSmall, fallbackDp = 4f)
		return PlainTooltipView(context, messageText ?: "", container, onContainer, corner)
	}

	private fun buildRichView(context: Context): RichTooltipView {
		val container = MaterialColors.getColor(anchor, MaterialR.attr.colorSurfaceContainerHigh, DEFAULT_RICH_CONTAINER)
		val onSurface = MaterialColors.getColor(anchor, MaterialR.attr.colorOnSurface, DEFAULT_ON_SURFACE)
		val onSurfaceVariant = MaterialColors.getColor(anchor, MaterialR.attr.colorOnSurfaceVariant, DEFAULT_ON_SURFACE_VARIANT)
		val primary = MaterialColors.getColor(anchor, androidx.appcompat.R.attr.colorPrimary, DEFAULT_COLOR_PRIMARY)

		val corner = resolveCornerRadiusPx(context, MaterialR.attr.shapeAppearanceCornerExtraLarge, fallbackDp = 20f)

		return RichTooltipView(
			context = context,
			icon = iconDrawable,
			title = titleText,
			message = messageText,
			customView = customView,
			container = container,
			onSurface = onSurface,
			onSurfaceVariant = onSurfaceVariant,
			primary = primary,
			corner = corner,
			positive = positiveText?.let { it to positiveListener },
			negative = negativeText?.let { it to negativeListener },
			neutral = neutralText?.let { it to neutralListener },
			onButtonClicked = { which, listener ->
				listener?.onClick(this, which)
				dismiss()
			}
		)
	}

	// ---- Posicionamiento ----

	private fun <T> presentPopup(contentView: T, elevationDp: Float) where T : View, T : ArrowHost {
		val context = anchor.context
		val isRtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL

		val window = PopupWindow(
			contentView,
			ViewGroup.LayoutParams.WRAP_CONTENT,
			ViewGroup.LayoutParams.WRAP_CONTENT,
			false
		).apply {
			isOutsideTouchable = true
			isFocusable = true
			elevation = dp(context, elevationDp)
		}

		contentView.measure(
			View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
			View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
		)
		val contentWidth = contentView.measuredWidth
		val contentHeight = contentView.measuredHeight

		val anchorLoc = IntArray(2)
		anchor.getLocationOnScreen(anchorLoc)
		val screenWidth = context.resources.displayMetrics.widthPixels
		val screenHeight = context.resources.displayMetrics.heightPixels
		val margin = dp(context, 8f).toInt()

		val targetPlacement = when (placement) {
			Placement.START -> if (isRtl) Placement.RIGHT else Placement.LEFT
			Placement.END -> if (isRtl) Placement.LEFT else Placement.RIGHT
			else -> placement
		}

		val spaceAbove = anchorLoc[1]
		val spaceBelow = screenHeight - (anchorLoc[1] + anchor.height)

		val finalPlacement = if (targetPlacement == Placement.AUTO) {
			if (spaceBelow < contentHeight + margin && spaceAbove > spaceBelow) Placement.TOP else Placement.BOTTOM
		} else targetPlacement

		var x = 0
		var y = 0

		when (finalPlacement) {
			Placement.TOP -> {
				contentView.arrowDir = ArrowDirection.DOWN
				x = anchorLoc[0] + anchor.width / 2 - contentWidth / 2
				y = anchorLoc[1] - contentHeight - margin
				contentView.arrowOffset = (anchorLoc[0] + anchor.width / 2f) - x
			}
			Placement.BOTTOM -> {
				contentView.arrowDir = ArrowDirection.UP
				x = anchorLoc[0] + anchor.width / 2 - contentWidth / 2
				y = anchorLoc[1] + anchor.height + margin
				contentView.arrowOffset = (anchorLoc[0] + anchor.width / 2f) - x
			}
			Placement.LEFT -> {
				contentView.arrowDir = ArrowDirection.RIGHT
				x = anchorLoc[0] - contentWidth - margin
				y = anchorLoc[1] + anchor.height / 2 - contentHeight / 2
				contentView.arrowOffset = (anchorLoc[1] + anchor.height / 2f) - y
			}
			Placement.RIGHT -> {
				contentView.arrowDir = ArrowDirection.LEFT
				x = anchorLoc[0] + anchor.width + margin
				y = anchorLoc[1] + anchor.height / 2 - contentHeight / 2
				contentView.arrowOffset = (anchorLoc[1] + anchor.height / 2f) - y
			}
			else -> {}
		}

		if (finalPlacement == Placement.TOP || finalPlacement == Placement.BOTTOM) {
			x = max(margin, min(x, screenWidth - contentWidth - margin))
			contentView.arrowOffset = (anchorLoc[0] + anchor.width / 2f) - x
		} else {
			y = max(margin, min(y, screenHeight - contentHeight - margin))
			contentView.arrowOffset = (anchorLoc[1] + anchor.height / 2f) - y
		}

		contentView.invalidate()
		window.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y)
		popup = window

		if (contentView is PlainTooltipView) {
			contentView.setOnClickListener { dismiss() }
		}
		if (dismissAfterMs > 0) {
			anchor.postDelayed({ if (popup === window) dismiss() }, dismissAfterMs)
		}
	}

	private fun resolveCornerRadiusPx(context: Context, attrId: Int, fallbackDp: Float): Float {
		val tv = TypedValue()
		return if (context.theme.resolveAttribute(attrId, tv, true) && tv.resourceId != 0) {
			try {
				ShapeAppearanceModel.builder(context, tv.resourceId, 0)
					.build()
					.topLeftCornerSize
					.getCornerSize(RectF(0f, 0f, 1000f, 1000f))
			} catch (e: Exception) {
				dp(context, fallbackDp)
			}
		} else dp(context, fallbackDp)
	}
}

// ---------------------------------------------------------------------
// Componentes visuales y renderizado de flecha con esquinas adaptativas
// ---------------------------------------------------------------------

private interface ArrowHost {
	var arrowDir: ComponentTooltip.ArrowDirection
	var arrowOffset: Float
}

private fun dp(context: Context, value: Float): Float =
	TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, context.resources.displayMetrics)

private class TooltipBackground(context: Context, containerColor: Int, private val maxCorner: Float) {
	val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = containerColor
		style = Paint.Style.FILL
	}
	val arrowW = dp(context, 14f)
	val arrowH = dp(context, 7f)

	private val minCorner = dp(context, 7f)

	fun draw(canvas: Canvas, width: Int, height: Int, arrowDir: ComponentTooltip.ArrowDirection, arrowOffset: Float) {
		val boxLeft = if (arrowDir == ComponentTooltip.ArrowDirection.LEFT) arrowH else 0f
		val boxTop = if (arrowDir == ComponentTooltip.ArrowDirection.UP) arrowH else 0f
		val boxRight = if (arrowDir == ComponentTooltip.ArrowDirection.RIGHT) width - arrowH else width.toFloat()
		val boxBottom = if (arrowDir == ComponentTooltip.ArrowDirection.DOWN) height - arrowH else height.toFloat()

		var topLeft = maxCorner
		var topRight = maxCorner
		var bottomLeft = maxCorner
		var bottomRight = maxCorner

		when (arrowDir) {
			ComponentTooltip.ArrowDirection.UP -> {
				topLeft = calculateDynamicCorner(arrowOffset, 0f, width.toFloat())
				topRight = calculateDynamicCorner(width - arrowOffset, 0f, width.toFloat())
			}
			ComponentTooltip.ArrowDirection.DOWN -> {
				bottomLeft = calculateDynamicCorner(arrowOffset, 0f, width.toFloat())
				bottomRight = calculateDynamicCorner(width - arrowOffset, 0f, width.toFloat())
			}
			ComponentTooltip.ArrowDirection.LEFT -> {
				topLeft = calculateDynamicCorner(arrowOffset, 0f, height.toFloat())
				bottomLeft = calculateDynamicCorner(height - arrowOffset, 0f, height.toFloat())
			}
			ComponentTooltip.ArrowDirection.RIGHT -> {
				topRight = calculateDynamicCorner(arrowOffset, 0f, height.toFloat())
				bottomRight = calculateDynamicCorner(height - arrowOffset, 0f, height.toFloat())
			}
		}

		val boxPath = Path().apply {
			val radii = floatArrayOf(
				topLeft, topLeft,
				topRight, topRight,
				bottomRight, bottomRight,
				bottomLeft, bottomLeft
			)
			addRoundRect(RectF(boxLeft, boxTop, boxRight, boxBottom), radii, Path.Direction.CW)
		}
		canvas.drawPath(boxPath, bgPaint)

		// Dibuja la flecha (caret)
		val arrowPath = Path()
		when (arrowDir) {
			ComponentTooltip.ArrowDirection.DOWN -> {
				val cx = arrowOffset.coerceIn(arrowW, width - arrowW)
				arrowPath.moveTo(cx - arrowW / 2, boxBottom)
				arrowPath.lineTo(cx + arrowW / 2, boxBottom)
				arrowPath.lineTo(cx, height.toFloat())
			}
			ComponentTooltip.ArrowDirection.UP -> {
				val cx = arrowOffset.coerceIn(arrowW, width - arrowW)
				arrowPath.moveTo(cx - arrowW / 2, boxTop)
				arrowPath.lineTo(cx + arrowW / 2, boxTop)
				arrowPath.lineTo(cx, 0f)
			}
			ComponentTooltip.ArrowDirection.RIGHT -> {
				val cy = arrowOffset.coerceIn(arrowW, height - arrowW)
				arrowPath.moveTo(boxRight, cy - arrowW / 2)
				arrowPath.lineTo(boxRight, cy + arrowW / 2)
				arrowPath.lineTo(width.toFloat(), cy)
			}
			ComponentTooltip.ArrowDirection.LEFT -> {
				val cy = arrowOffset.coerceIn(arrowW, height - arrowW)
				arrowPath.moveTo(boxLeft, cy - arrowW / 2)
				arrowPath.lineTo(boxLeft, cy + arrowW / 2)
				arrowPath.lineTo(0f, cy)
			}
		}
		arrowPath.close()
		canvas.drawPath(arrowPath, bgPaint)
	}

	private fun calculateDynamicCorner(distanceToCorner: Float, minLimit: Float, maxLimit: Float): Float {
		val threshold = maxCorner + arrowW

		if (distanceToCorner >= threshold) {
			return maxCorner
		}

		val fraction = (distanceToCorner / threshold).coerceIn(0f, 1f)

		return minCorner + (maxCorner - minCorner) * fraction
	}
}

private class PlainTooltipView(
	context: Context,
	private val text: CharSequence,
	container: Int,
	onContainer: Int,
	corner: Float
) : View(context), ArrowHost {

	override var arrowDir = ComponentTooltip.ArrowDirection.DOWN
	override var arrowOffset = 0f

	private val bg = TooltipBackground(context, container, corner)
	private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = onContainer
		textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, context.resources.displayMetrics)
		typeface = Typeface.DEFAULT
	}
	private val hPad = dp(context, 12f)
	private val vPad = dp(context, 6f)

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val textWidth = textPaint.measureText(text.toString())
		val isHorizontal = arrowDir == ComponentTooltip.ArrowDirection.LEFT || arrowDir == ComponentTooltip.ArrowDirection.RIGHT

		val w = textWidth + hPad * 2 + if (isHorizontal) bg.arrowH else 0f
		val fm = textPaint.fontMetrics
		val h = (fm.descent - fm.ascent) + vPad * 2 + if (!isHorizontal) bg.arrowH else 0f

		setMeasuredDimension(w.toInt() + 1, h.toInt() + 1)
	}

	override fun onDraw(canvas: Canvas) {
		bg.draw(canvas, width, height, arrowDir, arrowOffset)
		val boxLeft = if (arrowDir == ComponentTooltip.ArrowDirection.LEFT) bg.arrowH else 0f
		val boxTop = if (arrowDir == ComponentTooltip.ArrowDirection.UP) bg.arrowH else 0f
		val boxWidth = width - if (arrowDir == ComponentTooltip.ArrowDirection.LEFT || arrowDir == ComponentTooltip.ArrowDirection.RIGHT) bg.arrowH else 0f
		val boxHeight = height - if (arrowDir == ComponentTooltip.ArrowDirection.UP || arrowDir == ComponentTooltip.ArrowDirection.DOWN) bg.arrowH else 0f

		val fm = textPaint.fontMetrics
		val textY = boxTop + (boxHeight - (fm.descent - fm.ascent)) / 2 - fm.ascent
		canvas.drawText(text.toString(), boxLeft + hPad, textY, textPaint)
	}
}

private class RichTooltipView(
	context: Context,
	icon: Drawable?,
	title: CharSequence?,
	message: CharSequence?,
	customView: View?,
	container: Int,
	onSurface: Int,
	onSurfaceVariant: Int,
	primary: Int,
	val corner: Float,
	positive: Pair<CharSequence, ComponentTooltip.OnClickListener?>?,
	negative: Pair<CharSequence, ComponentTooltip.OnClickListener?>?,
	neutral: Pair<CharSequence, ComponentTooltip.OnClickListener?>?,
	private val onButtonClicked: (Int, ComponentTooltip.OnClickListener?) -> Unit
) : FrameLayout(context), ArrowHost {

	override var arrowDir = ComponentTooltip.ArrowDirection.DOWN
	override var arrowOffset = 0f

	private val bg = TooltipBackground(context, container, corner)
	private val maxWidthPx = dp(context, 300f).toInt()
	private val contentLayout: LinearLayout

	init {
		setWillNotDraw(false)

		contentLayout = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			val pad = dp(context, 18f).toInt()
			setPadding(pad, pad, pad, pad)
		}

		if (icon != null) {
			contentLayout.addView(ImageView(context).apply {
				setImageDrawable(icon)
				setColorFilter(primary)
				layoutParams = LinearLayout.LayoutParams(dp(context, 24f).toInt(), dp(context, 24f).toInt()).apply {
					bottomMargin = dp(context, 8f).toInt()
				}
			})
		}

		if (title != null) {
			contentLayout.addView(TextView(context).apply {
				text = title
				setTextColor(onSurface)
				// setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
				setTypeface(Typeface.DEFAULT_BOLD)
			})
		}

		if (!message.isNullAndEmpty()) {
			contentLayout.addView(TextView(context).apply {
				text = message
				setTextColor(onSurfaceVariant)
				setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
				setPadding(0, dp(context, 8f).toInt(), 0, dp(context, 8f).toInt())
			})
		}

		if (customView != null) {
			(customView.parent as? ViewGroup)?.removeView(customView)
			val lp = LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,
				ViewGroup.LayoutParams.WRAP_CONTENT
			).apply {
				if (title != null || !message.isNullAndEmpty()) {
					topMargin = dp(context, 8f).toInt()
				}
				bottomMargin = dp(context, 8f).toInt()
			}
			contentLayout.addView(customView, lp)
		}

		if (positive != null || negative != null || neutral != null) {
			val row = LinearLayout(context).apply {
				orientation = LinearLayout.HORIZONTAL
				gravity = Gravity.CENTER_VERTICAL or Gravity.END
				setPadding(0, dp(context, 4f).toInt(), 0, 0)
			}

			neutral?.let { (text, listener) ->
				row.addView(makeButton(context, text, primary) {
					onButtonClicked(ComponentTooltip.BUTTON_NEUTRAL, listener)
				})
			}
			row.addView(Space(context), LinearLayout.LayoutParams(0, 0, 1f))
			negative?.let { (text, listener) ->
				row.addView(makeButton(context, text, primary) {
					onButtonClicked(ComponentTooltip.BUTTON_NEGATIVE, listener)
				})
			}
			positive?.let { (text, listener) ->
				row.addView(makeButton(context, text, primary) {
					onButtonClicked(ComponentTooltip.BUTTON_POSITIVE, listener)
				})
			}

			contentLayout.addView(row)
		}

		val bgView = object : View(context) {
			override fun onDraw(canvas: Canvas) {
				bg.draw(canvas, width, height, arrowDir, arrowOffset)
			}
		}
		addView(bgView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
		addView(contentLayout, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
	}

	private fun makeButton(context: Context, text: CharSequence, color: Int, onClick: () -> Unit): TextView {
		return TextView(context).apply {
			this.text = text
			setTextColor(color)
			setTypeface(Typeface.DEFAULT_BOLD)
			setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
			isAllCaps = false
			isClickable = true
			isFocusable = true
			val padH = dp(context, 20f).toInt()
			val padV = dp(context, 8f).toInt()
			setPadding(padH, padV, padH, padV)

			background = RippleDrawable(
				ColorStateList.valueOf(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorControlHighlight)),
				GradientDrawable().apply {
					setColor(Color.TRANSPARENT)
					cornerRadius = corner
				},
				GradientDrawable().apply {
					setColor(Color.WHITE)
					cornerRadius = corner
				}
			)
			setOnClickListener { onClick() }
		}
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		contentLayout.measure(
			MeasureSpec.makeMeasureSpec(maxWidthPx, MeasureSpec.AT_MOST),
			MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
		)
		val isHorizontal = arrowDir == ComponentTooltip.ArrowDirection.LEFT || arrowDir == ComponentTooltip.ArrowDirection.RIGHT

		val w = contentLayout.measuredWidth + if (isHorizontal) bg.arrowH.toInt() else 0
		val h = contentLayout.measuredHeight + if (!isHorizontal) bg.arrowH.toInt() else 0

		(contentLayout.layoutParams as FrameLayout.LayoutParams).apply {
			leftMargin = if (arrowDir == ComponentTooltip.ArrowDirection.LEFT) bg.arrowH.toInt() else 0
			topMargin = if (arrowDir == ComponentTooltip.ArrowDirection.UP) bg.arrowH.toInt() else 0
			rightMargin = if (arrowDir == ComponentTooltip.ArrowDirection.RIGHT) bg.arrowH.toInt() else 0
			bottomMargin = if (arrowDir == ComponentTooltip.ArrowDirection.DOWN) bg.arrowH.toInt() else 0
		}

		super.onMeasure(
			MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
			MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
		)
	}

	private fun CharSequence?.isNullAndEmpty(): Boolean = this == null || this.isEmpty()
}