package com.deavidig.mod.deaniel.appcompat.app

import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

fun Fragment.getAttributeToColor(@AttrRes id: Int): Int {
	val typedValue = TypedValue()
	context?.theme?.resolveAttribute(id, typedValue, true)
	return typedValue.data
}

fun Fragment.getAttributeToString(@StringRes id: Int): String {
	return ContextCompat.getString(this.requireContext(), id)
}