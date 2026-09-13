package com.deavidig.mod.deanielig.appcompat.app

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.get


open class ComponentAppCompatActivity() : AppCompatActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
	}

	fun applyWindowInsets() {
		val root = (findViewById<View>(android.R.id.content) as ViewGroup)[0]

		ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->

			val systemBars = insets.getInsets(
				WindowInsetsCompat.Type.systemBars()
			)

			val ime = insets.getInsets(
				WindowInsetsCompat.Type.ime()
			)

			v.setPadding(
				systemBars.left,
				systemBars.top,
				systemBars.right,
				systemBars.bottom
			)

			insets
		}
	}

	fun isDarkMode(): Boolean =
		(this.theme.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

	fun getInputMethodService(): InputMethodManager =
		getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
}