package com.deavidig.mod.deaniel.divider.widget

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import androidx.core.content.withStyledAttributes
import com.deavidig.sketchprojectpro.R
import com.google.android.material.shape.CornerFamily.ROUNDED

// Beta (Powered by DeanielIG & DeavidIG)
class AppCompatComponentMaterialDivider(context: Context, attrs: AttributeSet? = null) : ComponentMaterialDivider(context, attrs) {
	private var mRadiusCornerSizeLeftTop: Float = 0f
	private var mRadiusCornerSizeRightTop: Float = 0f
	private var mRadiusCornerSizeLeftBottom: Float = 0f
	private var mRadiusCornerSizeRightBottom: Float = 0f

	init {
		context.withStyledAttributes(attrs, R.styleable.AppCompatComponentMaterialDivider) {
			this@AppCompatComponentMaterialDivider.mRadiusCornerSizeLeftTop = getDimension(R.styleable.ComponentMaterialDivider_dividerRadiusSize, 0f)
			this@AppCompatComponentMaterialDivider.mRadiusCornerSizeRightTop = getDimension(R.styleable.ComponentMaterialDivider_dividerRadiusSize, 0f)
			this@AppCompatComponentMaterialDivider.mRadiusCornerSizeLeftBottom = getDimension(R.styleable.ComponentMaterialDivider_dividerRadiusSize, 0f)
			this@AppCompatComponentMaterialDivider.mRadiusCornerSizeRightBottom = getDimension(R.styleable.ComponentMaterialDivider_dividerRadiusSize, 0f)
		}
	}

	fun setRadiusCornerSizeLeftTop(cornerSize: Float) {
		this.mRadiusCornerSizeLeftTop = cornerSize
		invalidateRadius()
	}

	fun setRadiusCornerSizeRightTop(cornerSize: Float) {
		this.mRadiusCornerSizeRightTop = cornerSize
		invalidateRadius()
	}

	fun setRadiusCornerSizeLeftBottom(cornerSize: Float) {
		this.mRadiusCornerSizeLeftBottom = cornerSize
		invalidateRadius()
	}

	fun setRadiusCornerSizeRightBottom(cornerSize: Float) {
		this.mRadiusCornerSizeRightBottom = cornerSize
		invalidateRadius()
	}

	fun getRadiusCornerSizeLeftTop(): Float = this.mRadiusCornerSizeLeftTop

	fun getRadiusCornerSizeRightTop(): Float = this.mRadiusCornerSizeRightTop

	fun getRadiusCornerSizeLeftBottom(): Float = this.mRadiusCornerSizeLeftBottom

	fun getRadiusCornerSizeRightBottom(): Float = this.mRadiusCornerSizeRightBottom

	private fun invalidateRadius() {
		this.dividerDrawable.shapeAppearanceModel.toBuilder()
			.setTopLeftCorner(ROUNDED, this.mRadiusCornerSizeLeftTop)
			.setTopRightCorner(ROUNDED, this.mRadiusCornerSizeRightTop)
			.setBottomLeftCorner(ROUNDED, this.mRadiusCornerSizeLeftBottom)
			.setBottomRightCorner(ROUNDED, this.mRadiusCornerSizeRightBottom)
	}

	override fun onDraw(canvas: Canvas) {
		val isRtl = this.layoutDirection == LAYOUT_DIRECTION_RTL
		val left = if (isRtl) this.dividerInsetEnd else this.dividerInsetStart
		val right = if (isRtl) this.width - this.dividerInsetStart else this.width - this.dividerInsetEnd
		this.dividerDrawable.setBounds(left, 0, right, this.bottom - this.top)
		this.dividerDrawable.draw(canvas)
	}
}