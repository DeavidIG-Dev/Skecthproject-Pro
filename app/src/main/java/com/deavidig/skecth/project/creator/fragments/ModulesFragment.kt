package com.deavidig.skecth.project.creator.fragments

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.forEach
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import com.deavidig.mod.deanielig.search.widget.ComponentSearchBar
import com.deavidig.skecth.project.creator.activities.ProjectCreatorActivity
import com.deavidig.sketchprojectpro.R
import com.deavidig.sketchprojectpro.databinding.ActivityProjectCreatorBackBinding
import com.deavidig.sketchprojectpro.databinding.ActivityProjectCreatorBackDialogBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class ModulesFragment : Fragment() {
	private lateinit var layout_binding: ActivityProjectCreatorBackBinding
	private lateinit var layout_input_binding: ActivityProjectCreatorBackDialogBinding

	private val MODULE_REGEX =
		Regex("[a-zA-Z][a-zA-Z0-9]*(?:-[a-zA-Z][a-zA-Z0-9]*)*")

	private val CharSequence.moduleValidate: Boolean
		get() = matches(MODULE_REGEX)

	private var moduleList = hashMapOf(
		"Phone & Tablets Android" to arrayListOf("app"),
		"Android Library" to arrayListOf(),
		"Java or Kotlin Library" to arrayListOf()
	)

	private var selectedTitle: String = "app"

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putSerializable("modules", moduleList)
	}

	override fun onViewStateRestored(savedInstanceState: Bundle?) {
		super.onViewStateRestored(savedInstanceState)
		savedInstanceState
			?.getSerializable("modules")
			?.let {
				moduleList.clear()
				moduleList.putAll(it as HashMap<String, ArrayList<String>>)
				layout_binding.moduleNavigation.menu.clear()
				for (item in moduleList) {
					when (item.key) {
						"Phone & Tablets Android" -> {
							createMenuItem(item.value[0], -1)
						}

						"Android Library" -> {
							item.value.forEach { text ->
								createMenuItem(text, 0)
							}
						}

						"Java or Kotlin Library" -> {
							item.value.forEach { text ->
								createMenuItem(text, 1)
							}
						}
					}
				}
			}
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		val activity = (requireActivity() as ProjectCreatorActivity)
		layout_binding = ActivityProjectCreatorBackBinding.inflate(layoutInflater)

		layout_binding.moduleNavigation.setNavigationItemSelectedListener {
			activity.layout_binding.bar.setSubtitle(
				it.title!!.toString() + " -" + activity.layout_binding.bar.getSubtitle()!!
					.toString().substringAfter('-', "")
			)
			selectedTitle = it.title.toString().removePrefix(":")
			true
		}

		val searchBar =
			layout_binding.moduleNavigation.getHeaderView(0) as ComponentSearchBar
		var ignoreCase = false

		searchBar.setOnQueryTextChangeListener { text ->
			layout_binding.moduleNavigation.menu.children.forEach { item ->
				if (item.hasSubMenu()) {
					item.subMenu?.forEach { module ->
						module.isVisible =
							text.isEmpty() ||
									module.title
										.toString()
										.removePrefix(":")
										.startsWith(text.toString(), ignoreCase)
					}

					item.isVisible = item.subMenu?.children?.any { it.isVisible } == true
				}
			}
		}

		searchBar.setOnPillRightItemMenuClickListener {
			MaterialAlertDialogBuilder(requireContext())
				.setTitle("Search Options")
				.setSingleChoiceItems(
					arrayOf("Ignore Case", "Case"),
					if (ignoreCase) 0 else 1
				) { _, which -> ignoreCase = which == 0 }
				.setPositiveButton("Accept", null)
				.setNegativeButton("Cancel", null)
				.show()
		}

		layout_binding.newModue.setOnClickListener {
			layout_input_binding =
				ActivityProjectCreatorBackDialogBinding.inflate(layoutInflater)
			var typeChoice = 0

			val dialog = MaterialAlertDialogBuilder(requireContext())
				.setCancelable(false)
				.setTitle("Set name of your module")
				.setSingleChoiceItems(
					arrayOf("Android Library", "Java or Kotlin Library"),
					0
				) { _, choice ->
					typeChoice = choice
				}
				.setView(layout_input_binding.root, 50, 0, 50, 0)
				.setPositiveButton("Create", null)
				.setNegativeButton("Cancel", null)
				.setOnDismissListener {
					(layout_input_binding.root.parent as FrameLayout).removeAllViews(); layout_input_binding.root.getEditText()!!
					.setText("")
				}
				.show()

			layout_input_binding.root.getEditText()!!.doOnTextChanged { text, _, _, _ ->
				if (moduleList.values.any { text.toString() in it }) {
					layout_input_binding.root.setErrorEnabled(true)
					layout_input_binding.root.setErrorText("Contains duplicate name module!")
					layout_input_binding.root.getEditText()!!.requestFocus()
				}
				if (!text.toString().moduleValidate) {
					layout_input_binding.root.setErrorEnabled(true)
					layout_input_binding.root.setErrorText("Contains symbols unknown in the module!")
					layout_input_binding.root.getEditText()!!.requestFocus()
				}
			}

			dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
				val text = layout_input_binding.root.getText()

				if (text.isEmpty()) {
					layout_input_binding.root.setErrorEnabled(true)
					layout_input_binding.root.setErrorText("The module name, cannot empty")
					layout_input_binding.root.getEditText()!!.requestFocus()
					return@setOnClickListener
				}
				if (!text.moduleValidate) {
					layout_input_binding.root.setErrorEnabled(true)
					layout_input_binding.root.setErrorText("Contains symbols unknown in the module!")
					layout_input_binding.root.getEditText()!!.requestFocus()
					return@setOnClickListener
				}
				if (text in moduleList["Phone & Tablets Android"]!! || text in moduleList["Android Library"]!! || text in moduleList["Java or Kotlin Library"]!!) {
					layout_input_binding.root.setErrorEnabled(true)
					layout_input_binding.root.setErrorText("Contains duplicate name module!")
					layout_input_binding.root.getEditText()!!.requestFocus()
					return@setOnClickListener
				}
				moduleList[if (typeChoice == 0) "Android Library" else "Java or Kotlin Library"]!!.add(
					text
				)
				createMenuItem(":" + layout_input_binding.root.getText(), typeChoice)

				dialog.dismiss()
			}
		}

		createMenuItem(":app", -1)

		return layout_binding.root
	}

	fun createMenuItem(title: CharSequence, typo: Int = 0) {
		val menu = layout_binding.moduleNavigation.menu

		val subMenuTitle = when (typo) {
			-1 -> "Phone & Tablets Android"
			0 -> "Android Library"
			1 -> "Java or Kotlin Library"
			else -> return
		}

		val subMenu = menu.children
			.firstOrNull { it.title == subMenuTitle }
			?.subMenu
			?: menu.addSubMenu(
				Menu.NONE,
				typo,
				Menu.NONE,
				subMenuTitle
			)

		val menuItem = subMenu.add(
			Menu.NONE,
			moduleList[subMenuTitle]!!.size,
			Menu.NONE,
			title
		)

		menuItem.isCheckable = true
		menuItem.isChecked = typo == -1
		menuItem.icon = ContextCompat.getDrawable(
			requireContext(),
			R.drawable.ic_folder_managed_24
		)
	}
}