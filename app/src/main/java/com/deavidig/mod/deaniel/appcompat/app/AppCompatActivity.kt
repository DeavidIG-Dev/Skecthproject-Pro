package com.deavidig.mod.deaniel.appcompat.app

import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.DialogFragment
import com.deavidig.sketchprojectpro.R

fun AppCompatActivity.applyWindowInsets() {
	ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
		val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
		v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
		insets
	}
}

fun AppCompatActivity.getAttributeToColor(@AttrRes id: Int): Int {
	val typedValue = TypedValue()
	this.theme?.resolveAttribute(id, typedValue, true)
	return typedValue.data
}