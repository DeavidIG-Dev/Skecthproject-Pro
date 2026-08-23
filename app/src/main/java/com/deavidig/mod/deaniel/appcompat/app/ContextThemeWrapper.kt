package com.deavidig.mod.deaniel.appcompat.app

import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.appcompat.view.ContextThemeWrapper


fun ContextThemeWrapper.getAttributeToColor(@AttrRes id: Int): Int {
	val typedValue = TypedValue()
	this.theme?.resolveAttribute(id, typedValue, true)
	return typedValue.data
}