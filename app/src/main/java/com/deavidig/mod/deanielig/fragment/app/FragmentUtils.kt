package com.deavidig.mod.deanielig.fragment.app

import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment

fun Fragment.applyWindowInsets() {
	ViewCompat.setOnApplyWindowInsetsListener(view!!) { view, insets ->

		val ime = insets.getInsets(
			WindowInsetsCompat.Type.ime()
		)

		val systemBars = insets.getInsets(
			WindowInsetsCompat.Type.systemBars()
		)

		view.setPadding(
			view.paddingLeft,
			view.paddingTop,
			view.paddingRight,
			maxOf(ime.bottom, systemBars.bottom)
		)

		insets
	}
}