package com.deavidig.mod.deaniel.appcompat.app

import android.graphics.Color
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.fragment.app.DialogFragment

fun DialogFragment.getAttributeToColor(@AttrRes id: Int): Int {
	val typedValue = TypedValue()
	context?.theme?.resolveAttribute(id, typedValue, true)
	return typedValue.data
}