package com.deavidig.skecth.project.creator.fragment

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import com.deavidig.skecth.project.creator.activities.ProjectModulesCreatorActivity

import com.deavidig.sketchprojectpro.databinding.ActivityProjectCreatorHomeBinding

import com.google.android.material.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout

class ActivityManagerCreatorHomeViewPagerFragment() : Fragment() {
	private lateinit var activity_binding: ActivityProjectCreatorHomeBinding

	private var listOfNameSpace = arrayListOf(
		"com.example", "com.dev",
		"org.example", "org.dev"
	)

	private var scalaOptionLanguage: MaterialAlertDialogBuilder? = null

	private val projectModulesCreatorLauncher =
		registerForActivityResult(
			ActivityResultContracts.StartActivityForResult()
		) { result ->
			if (result.resultCode == Activity.RESULT_OK) {
				val modules = result.data
					?.getStringArrayListExtra("Modules")

				if (modules != null) {
					listOfNameSpace = modules
					activity_binding.packageName.setPrefixTextList(listOfNameSpace)

				}
			}
		}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		activity_binding = ActivityProjectCreatorHomeBinding.inflate(layoutInflater)

		setupComponentMaterialTextInputLayout()

		setupTabLayout()

		return activity_binding.root
	}

	fun setupComponentMaterialTextInputLayout() {
		fun setupEdiText() {
			activity_binding.applicationName.getEditText()?.doOnTextChanged { text, _, _, _ ->
				val hasSpecialSymbols = text?.contains("[^a-zA-Z0-9 _]+".toRegex()) == true

				if (hasSpecialSymbols) {
					activity_binding.applicationName.setErrorEnabled(true)
					activity_binding.packageName.setErrorEnabled(true)

					activity_binding.applicationName.setErrorText("Contains symbols unknown!")
					activity_binding.packageName.setErrorText("Contains symbols unknown in the package!")
				}
				activity_binding.packageName.getEditText()?.setText(text.toString().lowercase().replace(" +".toRegex(), "."))
			}

			activity_binding.packageName.getEditText()?.doOnTextChanged { text, _, _, _ ->
				val hasSpecialSymbols = text!!.contains("[^a-zA-Z0-9 ._]+".toRegex())

				if (hasSpecialSymbols || text.endsWith('.') || text.startsWith('.') || text.contains("\\.[0-9]+$".toRegex()) || text.contains("^[0-9]+$".toRegex())) {
					activity_binding.packageName.setErrorEnabled(true)
					activity_binding.packageName.setErrorText("Contains symbols unknown in the package!")
				}
			}
		}

		setupEdiText()

		activity_binding.packageName.setPrefixTextList(listOfNameSpace)

		activity_binding.packageName.setEndLayoutOnClickListener {
			val intent = Intent(requireContext(), ProjectModulesCreatorActivity::class.java).apply {
				putStringArrayListExtra("Prefix Name", listOfNameSpace)
			}

			projectModulesCreatorLauncher.launch(intent)
		}
	}

	fun setupTabLayout() {
		activity_binding.languageOption.selectTab(activity_binding.languageOption.getTabAt(1), true)
		activity_binding.languageOption.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
			override fun onTabSelected(tab: TabLayout.Tab) {
				if (tab.position == 2) {
					if (scalaOptionLanguage == null) {
						scalaOptionLanguage = MaterialAlertDialogBuilder(requireContext())
							.setTitle("Scala Language Option.")
							.setMessage("You select Scala as the project's build, are you sure you want to take the risk that comes with using Scala?")
							.setPositiveButton("Accept", null)
							.setNegativeButton("Cancel") { _, _ ->
								activity_binding.languageOption.selectTab(activity_binding.languageOption.getTabAt(1), true)
							}
					}
					scalaOptionLanguage!!.show()
				}
			}

			override fun onTabUnselected(tab: TabLayout.Tab) { }

			override fun onTabReselected(tab: TabLayout.Tab) { }

		})


		val tabStrip = activity_binding.languageOption.getChildAt(0) as LinearLayout
		tabStrip.showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
		tabStrip.dividerDrawable = GradientDrawable().apply {
			val typed = TypedValue()
			context?.theme?.resolveAttribute(R.attr.colorSurfaceContainerHighest, typed, true)
			setColor(typed.data)
			setSize(4, 20)
		}
	}
}