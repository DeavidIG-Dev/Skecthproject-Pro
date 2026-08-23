package com.deavidig.mod.deaniel.divider.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.util.AttributeSet
import androidx.annotation.ColorInt
import androidx.core.content.withStyledAttributes
import com.deavidig.sketchprojectpro.R
import com.google.android.material.divider.MaterialDivider
import com.google.android.material.shape.MaterialShapeDrawable

// Beta (Powered by DeanielIG & DeavidIG)
open class ComponentMaterialDivider(context: Context, attrs: AttributeSet? = null) : MaterialDivider(context, attrs) {
	protected val dividerDrawable: MaterialShapeDrawable = MaterialShapeDrawable()
	private var mRadiusCorner: Float = 0f

	init {
		context.withStyledAttributes(attrs, R.styleable.ComponentMaterialDivider) {
			this@ComponentMaterialDivider.setRadiusCornerSize(getDimension(R.styleable.ComponentMaterialDivider_dividerRadiusSize, 0f))
		}
	}

	fun setRadiusCornerSize(cornerSize: Float) {
		this.mRadiusCorner = cornerSize
		this.dividerDrawable.setCornerSize(mRadiusCorner)
	}

	fun getRadiusCornerSize(): Float = this.mRadiusCorner


	/**
	 * Sets the color of the divider.
	 *
	 * @param color The color to be set.
	 * @see .getDividerColor
	 * @attr ref com.google.android.material.R.styleable#MaterialDivider_dividerColor
	 */
	fun setDividerColor(color: ColorStateList) {
		dividerDrawable.fillColor = color
		invalidate()
	}

	override fun onDraw(canvas: Canvas) {
		val isRtl = this.layoutDirection == LAYOUT_DIRECTION_RTL
		val left = if (isRtl) this.dividerInsetEnd else this.dividerInsetStart
		val right = if (isRtl) this.width - this.dividerInsetStart else this.width - this.dividerInsetEnd
		this.dividerDrawable.setBounds(left, 0, right, this.bottom - this.top)
		this.dividerDrawable.draw(canvas)
	}
}