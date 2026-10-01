package com.deavidig.mod.deanielig.textinput.widget

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet

open class AppCompatComponentTextInputLayout(context: Context, attrs: AttributeSet?) :
	ComponentTextInputLayout(context, attrs) {
	private var mWarningColor: ColorStateList? = null

	init {
		mWarningColor = ColorStateList.valueOf(
			androidx.core.graphics.ColorUtils.blendARGB(
				Color.YELLOW,
				Color.BLACK,
				0.125f
			)
		)
	}

	fun setWarningEnable(boolean: Boolean) {
		warning(boolean)
	}

	fun setWarningText(string: String) {

	}

	fun setWarningTextColor(color: ColorStateList?) {
		this.mWarningColor = color
	}

	private fun warning(boolean: Boolean) {
		setErrorTextColor(mWarningColor)
		setErrorEnabled(boolean)
	}
}
