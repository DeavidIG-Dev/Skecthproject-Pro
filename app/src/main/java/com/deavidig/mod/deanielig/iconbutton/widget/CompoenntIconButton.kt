package com.deavidig.mod.deanielig.iconbutton.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.deavidig.sketchprojectpro.R

/**
 * ## ComponentIconButton
 *
 * Botón de icono Material Design 3, autocontenido y de un solo archivo. Envuelve
 * [AppCompatImageButton] y construye su propio fondo (relleno / borde / ripple) en
 * tiempo de ejecución, sin depender de drawables XML externos.
 *
 * ### Contrato de icono único
 * El componente solo admite **un** icono a la vez. [setImageDrawable] y
 * [setImageResource] están sobreescritos para reemplazar siempre el icono actual
 * en lugar de acumularlo; se recomienda usar [setIcon] o el atributo `app:icon`
 * en XML como punto de entrada único.
 *
 * ### Uso en XML
 * ```xml
 * <com.deavidig.mod.deaniel.ComponentIconButton
 *     android:layout_width="wrap_content"
 *     android:layout_height="wrap_content"
 *     style="@style/Widget.Deaniel.IconButton.Filled"
 *     app:icon="@drawable/ic_star" />
 * ```
 *
 * ### Estilos disponibles
 * - `Widget.Deaniel.IconButton.Filled`: fondo relleno con `colorPrimary`, icono en `colorOnPrimary`.
 * - `Widget.Deaniel.IconButton.Outline`: fondo transparente con borde `colorOutline`, icono en `colorOnSurfaceVariant`.
 *
 * Si no se aplica ningún estilo, el botón se comporta como variante "Standard"
 * (sin relleno ni borde), útil como base para nuevas variantes futuras (p. ej. Tonal).
 *
 * ### Atributos personalizados (`R.styleable.ComponentIconButton`)
 * - `app:icon` (reference): drawable único del botón.
 * - `app:iconTint` (color): tinte aplicado al icono.
 * - `app:iconSize` (dimension): tamaño visual del icono. Default 24dp.
 * - `app:containerSize` (dimension): diámetro del círculo de fondo. Default 40dp.
 * - `app:fillColor` (color): color de relleno del círculo.
 * - `app:strokeColor` (color): color del borde.
 * - `app:strokeWidth` (dimension): grosor del borde.
 */
class ComponentIconButton @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = 0,
	defStyleRes: Int = R.style.Widget_ComponentMaterial_ComponentIconButton_Filled
) : AppCompatImageButton(context, attrs, defStyleAttr) {

	private var iconDrawable: Drawable? = null
	private var iconTintList: ColorStateList? = null
	private var iconSizePx: Int = 0
	private var containerSizePx: Int = 0
	private var touchTargetPx: Int = 0
	private var fillColorValue: Int = Color.TRANSPARENT
	private var strokeColorValue: Int = Color.TRANSPARENT
	private var strokeWidthPx: Int = 0

	init {
		val ta =
			context.obtainStyledAttributes(
				attrs,
				R.styleable.ComponentIconButton,
				defStyleAttr,
				defStyleRes
			)
		try {
			val iconRes = ta.getResourceId(R.styleable.ComponentIconButton_icon, 0)
			iconDrawable =
				if (iconRes != 0) ContextCompat.getDrawable(context, iconRes) else drawable
			iconTintList = ta.getColorStateList(R.styleable.ComponentIconButton_iconTint)
			iconSizePx = ta.getDimensionPixelSize(
				R.styleable.ComponentIconButton_iconSize,
				dp(DEFAULT_ICON_SIZE_DP)
			)
			containerSizePx = ta.getDimensionPixelSize(
				R.styleable.ComponentIconButton_containerSize,
				dp(DEFAULT_CONTAINER_SIZE_DP)
			)
			fillColorValue =
				ta.getColor(R.styleable.ComponentIconButton_fillColor, Color.TRANSPARENT)
			strokeColorValue =
				ta.getColor(R.styleable.ComponentIconButton_strokeColor, Color.TRANSPARENT)
			strokeWidthPx = ta.getDimensionPixelSize(R.styleable.ComponentIconButton_strokeWidth, 0)
		} finally {
			ta.recycle()
		}

		touchTargetPx = maxOf(containerSizePx, dp(MIN_TOUCH_TARGET_DP))

		scaleType = ScaleType.FIT_CENTER
		background = buildBackground()
		applyIcon()
		applyIconTint()
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		setMeasuredDimension(
			resolveSizeSpec(touchTargetPx, widthMeasureSpec),
			resolveSizeSpec(touchTargetPx, heightMeasureSpec)
		)
	}

	private fun resolveSizeSpec(desired: Int, spec: Int): Int {
		val mode = View.MeasureSpec.getMode(spec)
		val size = View.MeasureSpec.getSize(spec)
		return when (mode) {
			View.MeasureSpec.EXACTLY -> size
			View.MeasureSpec.AT_MOST -> minOf(desired, size)
			else -> desired
		}
	}

	/**
	 * Refuerza el contrato de icono único: cualquier llamada reemplaza el icono
	 * previo en lugar de añadirse a él.
	 */
	override fun setImageDrawable(drw: Drawable?) {
		iconDrawable = drw
		applyIcon()
	}

	override fun setImageResource(resId: Int) {
		iconDrawable = if (resId != 0) ContextCompat.getDrawable(context, resId) else null
		applyIcon()
	}

	/** Setter fluido para el icono único del botón. */
	fun setIcon(icon: Drawable?): ComponentIconButton {
		iconDrawable = icon
		applyIcon()
		return this
	}

	/** Setter fluido para el icono único del botón, a partir de un recurso. */
	fun setIcon(@androidx.annotation.DrawableRes resId: Int): ComponentIconButton =
		setIcon(ContextCompat.getDrawable(context, resId))

	/** Setter fluido para el tinte del icono. */
	fun setIconTint(tint: ColorStateList?): ComponentIconButton {
		iconTintList = tint
		applyIconTint()
		background = buildBackground()
		return this
	}

	override fun setEnabled(enabled: Boolean) {
		super.setEnabled(enabled)
		alpha = if (enabled) 1f else DISABLED_ALPHA
	}

	private fun applyIcon() {
		val padding = ((touchTargetPx - iconSizePx) / 2).coerceAtLeast(0)
		setPadding(padding, padding, padding, padding)
		super.setImageDrawable(iconDrawable)
	}

	private fun applyIconTint() {
		imageTintList = iconTintList ?: ColorStateList.valueOf(FALLBACK_ICON_COLOR)
	}

	private fun buildBackground(): Drawable {
		val shapeDrawable = GradientDrawable().apply {
			shape = GradientDrawable.RECTANGLE
			cornerRadius = 25f
			setColor(fillColorValue)
			if (strokeWidthPx > 0) setStroke(strokeWidthPx, strokeColorValue)
		}

		val backgroundDrawable: Drawable =
			run {
				val rippleBase = iconTintList?.defaultColor ?: FALLBACK_ICON_COLOR
				val rippleColor =
					ColorStateList.valueOf(ColorUtils.setAlphaComponent(rippleBase, RIPPLE_ALPHA))
				// Se reutiliza el mismo GradientDrawable como contenido y máscara para
				// que el ripple quede recortado exactamente a la forma circular.
				RippleDrawable(rippleColor, shapeDrawable, shapeDrawable)
			}

		val inset = ((touchTargetPx - containerSizePx) / 2).coerceAtLeast(0)
		return if (inset > 0) InsetDrawable(backgroundDrawable, inset) else backgroundDrawable
	}

	private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

	companion object {
		private const val DEFAULT_CONTAINER_SIZE_DP = 40
		private const val DEFAULT_ICON_SIZE_DP = 24
		private const val MIN_TOUCH_TARGET_DP = 48
		private const val DISABLED_ALPHA = 0.38f
		private const val RIPPLE_ALPHA = 62 // ~24% de opacidad, capa de estado M3
		private val FALLBACK_ICON_COLOR = Color.DKGRAY
	}
}