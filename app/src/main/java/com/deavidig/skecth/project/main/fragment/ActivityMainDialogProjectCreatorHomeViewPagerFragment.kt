package com.deavidig.skecth.project.main.fragment

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import com.deavidig.sketchprojectpro.databinding.ActivityMainDialogProjectCreatorBinding
import com.deavidig.sketchprojectpro.databinding.ActivityMainDialogProjectCreatorHomeBinding
import com.google.android.material.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout

class ActivityMainDialogProjectCreatorHomeViewPagerFragment() : Fragment() {
	private lateinit var layoutBinding: ActivityMainDialogProjectCreatorHomeBinding

	private val listOfNameSpace = arrayListOf(
		"com.example", "com.dev",
		"org.example", "org.dev",
		"net.example", "net.dev",
		"dev.example", "dev.app",
		"app.example", "app.dev",
		"io.example", "io.dev"
	)

	private var scalaOptionLanguage: MaterialAlertDialogBuilder? = null


	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		layoutBinding = ActivityMainDialogProjectCreatorHomeBinding.inflate(layoutInflater)

		layoutBinding.applicationName.getEditText()?.doOnTextChanged { text, _, _, _ ->
			val hasSpecialSymbols = text?.contains("[^a-zA-Z0-9 _]+".toRegex()) == true

			if (hasSpecialSymbols) {
				layoutBinding.applicationName.setErrorEnabled(true)
				layoutBinding.packageName.setErrorEnabled(true)

				layoutBinding.applicationName.setErrorText("Contains symbols unknown!")
				layoutBinding.packageName.setErrorText("Contains symbols unknown in the package!")
			} else {
				layoutBinding.applicationName.setErrorEnabled(false)
				layoutBinding.packageName.setErrorEnabled(false)
			}
			layoutBinding.packageName.getEditText()?.setText(text.toString().lowercase().replace(" +".toRegex(), "."))
		}

		layoutBinding.packageName.setPrefixTextList(listOfNameSpace)
		layoutBinding.packageName.getEditText()?.doOnTextChanged { text, _, _, _ ->
			val hasSpecialSymbols = text!!.contains("[^a-zA-Z0-9 ._]+".toRegex())

			if (hasSpecialSymbols || text.endsWith('.') || text.startsWith('.') || text.contains("\\.[0-9]+$".toRegex()) || text.contains("^[0-9]+$".toRegex())) {
				layoutBinding.packageName.setErrorEnabled(true)
				layoutBinding.packageName.setErrorText("Contains symbols unknown in the package!")
			} else {
				layoutBinding.packageName.setErrorEnabled(false)
			}
		}

		layoutBinding.packageName.setEndLayoutOnClickListener {
			MaterialAlertDialogBuilder(requireContext())
				.setTitle("Name-Space Option.")
				.setMessage("Create, Delete and rename your custom Name-Space")
				.setPositiveButton("Accept", null)
				.setNegativeButton("Cancel", null)
				.show()
		}

		layoutBinding.languageOption.selectTab(layoutBinding.languageOption.getTabAt(1), true)

		layoutBinding.languageOption.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
			override fun onTabSelected(tab: TabLayout.Tab) {
				if (tab.position == 2) {
					if (scalaOptionLanguage == null) {
						scalaOptionLanguage = MaterialAlertDialogBuilder(requireContext())
							.setTitle("Scala Language Option.")
							.setMessage("You select Scala as the project's build, are you sure you want to take the risk that comes with using Scala?")
							.setPositiveButton("Accept", null)
							.setNegativeButton("Cancel") { _, _ ->
								layoutBinding.languageOption.selectTab(layoutBinding.languageOption.getTabAt(1), true)
							}
					}
					scalaOptionLanguage!!.show()
				}
			}

			override fun onTabUnselected(tab: TabLayout.Tab) { }

			override fun onTabReselected(tab: TabLayout.Tab) { }

		})


		val tabStrip = layoutBinding.languageOption.getChildAt(0) as LinearLayout
		tabStrip.showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
		tabStrip.dividerDrawable = GradientDrawable().apply {
			val typed = TypedValue()
			context?.theme?.resolveAttribute(R.attr.colorSurfaceContainerHighest, typed, true)
			setColor(typed.data)
			setSize(4, 20)
		}

		return layoutBinding.root
	}
}