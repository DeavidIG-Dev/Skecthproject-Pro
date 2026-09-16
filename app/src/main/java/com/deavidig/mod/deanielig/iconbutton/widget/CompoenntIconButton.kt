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
import com.google.android.material.color.MaterialColors

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
 * <com.deavidig.mod.deanielig.iconbutton.widget.ComponentIconButton
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
 * - `app:rippleColor` (color | color state list): color del ripple. Si no se define,
 *   se deriva de [iconTintList] (o del token de tema si tampoco hay tinte) con una
 *   capa de opacidad ~24% acorde al estado M3.
 *
 * @constructor Crea una instancia de [ComponentIconButton], resolviendo los atributos XML
 * indicados contra [defStyleAttr] y [defStyleRes] antes de construir el fondo y aplicar
 * el icono inicial.
 * @param context Contexto usado para resolver recursos, tema y densidad de pantalla.
 * @param attrs Atributos XML aplicados a la vista, o `null` si se crea programáticamente.
 * @param defStyleAttr Atributo de estilo por defecto a nivel de tema (theme attr).
 * @param defStyleRes Recurso de estilo por defecto aplicado si [defStyleAttr] no resuelve
 * ningún estilo en el tema activo.
 * @author DeanielIG, DeavidIG
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
	private var rippleColorList: ColorStateList? = null

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
			// NOTA: si `iconRes == 0`, se cae de vuelta a `drawable` (el getter real de
			// ImageView), NO al campo `iconDrawable`. Esto es intencional: si el XML trae
			// `android:src`, AppCompatImageButton puede invocar el `setImageResource`
			// sobreescrito más abajo de forma virtual durante el constructor de la
			// superclase, ANTES de que corran los inicializadores de campo de Kotlin. Eso
			// resetea el campo `iconDrawable` a `null`, pero el drawable ya quedó aplicado
			// al widget real vía `super.setImageDrawable(...)` dentro de `applyIcon()`, así
			// que `drawable` lo sigue devolviendo correctamente. No reemplazar este fallback
			// por el campo `iconDrawable` sin entender esta secuencia (ver principio de
			// "virtual call from base constructor").
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
			rippleColorList = ta.getColorStateList(R.styleable.ComponentIconButton_rippleColor)
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

	/**
	 * Setter fluido para el color del ripple. Acepta un [ColorStateList] completo
	 * (por ejemplo, uno ya definido con estados/alpha en XML) o `null` para volver
	 * a la derivación automática desde [iconTintList] / token de tema.
	 */
	fun setRippleColor(color: ColorStateList?): ComponentIconButton {
		rippleColorList = color
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
		imageTintList = iconTintList ?: ColorStateList.valueOf(resolveFallbackIconColor())
	}

	private fun buildBackground(): Drawable {
		val shapeDrawable = GradientDrawable().apply {
			// RECTANGLE + radio dinámico ("pill"): con contenedor cuadrado da el mismo
			// círculo perfecto que un OVAL, pero si en el futuro el ancho y el alto del
			// contenedor difieren (p. ej. una variante extendida), el resultado sigue
			// siendo un pill correcto en vez de un óvalo estirado. El radio se calcula
			// sobre containerSizePx (el tamaño real dibujado), nunca un valor fijo.
			shape = GradientDrawable.RECTANGLE
			cornerRadius = containerSizePx / 2f
			setColor(fillColorValue)
			if (strokeWidthPx > 0) setStroke(strokeWidthPx, strokeColorValue)
		}

		// Se reutiliza el mismo GradientDrawable como contenido y máscara para que el
		// ripple quede recortado exactamente a la forma del pill.
		val backgroundDrawable: Drawable =
			RippleDrawable(resolveRippleColor(), shapeDrawable, shapeDrawable)

		val inset = ((touchTargetPx - containerSizePx) / 2).coerceAtLeast(0)
		return if (inset > 0) InsetDrawable(backgroundDrawable, inset) else backgroundDrawable
	}

	/**
	 * Resuelve el [ColorStateList] del ripple: usa `app:rippleColor` si fue provisto
	 * explícitamente (respetando sus estados/alpha tal cual), o lo deriva de
	 * [iconTintList] / token de tema con una capa de opacidad ~24% (M3).
	 */
	private fun resolveRippleColor(): ColorStateList {
		rippleColorList?.let { return it }
		val base = iconTintList?.defaultColor ?: resolveFallbackIconColor()
		return ColorStateList.valueOf(ColorUtils.setAlphaComponent(base, RIPPLE_ALPHA))
	}

	/**
	 * Resuelve el color de icono por defecto contra `colorOnSurfaceVariant` del tema
	 * activo, en vez de un valor fijo, para respetar la disciplina de tokens M3.
	 * [Color.DKGRAY] solo se usa como último recurso si el tema no resuelve el atributo.
	 */
	private fun resolveFallbackIconColor(): Int =
		MaterialColors.getColor(
			this,
			com.google.android.material.R.attr.colorOnSurfaceVariant,
			Color.DKGRAY
		)

	private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

	companion object {
		private const val DEFAULT_CONTAINER_SIZE_DP = 40
		private const val DEFAULT_ICON_SIZE_DP = 24
		private const val MIN_TOUCH_TARGET_DP = 48
		private const val DISABLED_ALPHA = 0.38f
		private const val RIPPLE_ALPHA = 62 // ~24% de opacidad, capa de estado M3
	}
}