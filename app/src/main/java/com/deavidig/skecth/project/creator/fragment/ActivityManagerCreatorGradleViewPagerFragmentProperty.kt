package com.deavidig.skecth.project.creator.fragment

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.view.removeItemAt
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import com.deavidig.sketchprojectpro.R
import com.deavidig.sketchprojectpro.databinding.ActivityProjectModulesCreatorDialogInputBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.core.view.size
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.deavidig.skecth.project.creator.activities.ProjectCreatorActivity
import com.deavidig.sketchprojectpro.databinding.ActivityProjectCreatorGradlePropertyBinding

class ActivityManagerCreatorGradleViewPagerFragmentProperty() : Fragment() {
	private lateinit var layout_binding: ActivityProjectCreatorGradlePropertyBinding
	private lateinit var input_binding: ActivityProjectModulesCreatorDialogInputBinding

	private val String.validateNameRegex: Boolean get() = this.matches("[a-zA-Z0-9-_]+".toRegex())
	private val reservedWords = arrayListOf("build", "settings")
	private val listName = arrayListOf("app")

	@RequiresApi(Build.VERSION_CODES.Q)
	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View {
		layout_binding = ActivityProjectCreatorGradlePropertyBinding.inflate(layoutInflater)

		layout_binding.modulesGradle.headerView!!.setOnClickListener {
			createDialogModule(
				title = "Create new Gradle Module.",
				subTitle = "Create a new Module for you project using Gradle.",
				buttonText = "Accept",
				input = { text ->
					if (!text.validateNameRegex) {
						input_binding.root.setErrorEnabled(true)
						input_binding.root.setErrorText("Contains symbol unknown in Module Name!")
					} else if (text in reservedWords) {
						input_binding.root.setErrorEnabled(true)
						input_binding.root.setErrorText("Contains a reserved work in Module Name!")
					} else if (text in listName) {
						input_binding.root.setErrorEnabled(true)
						input_binding.root.setErrorText("Duplicate name in Module Name!")
					}
				},
				actionAccept = { text  ->
					if (text.validateNameRegex && text !in reservedWords && text !in listName) {
						layout_binding.modulesGradle.menu.add(Menu.NONE, View.generateViewId(), layout_binding.modulesGradle.menu.size, ":" + input_binding.root.getEditText()!!.text)
							.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_folder_managed_24)
						listName.add(text)
					}
				}
			)
		}

		layout_binding.modulesGradle.setOnItemReselectedListener {
			if (it.title == ":app") {
				Toast.makeText(requireContext(), "Cannot rename the ':app' module", Toast.LENGTH_SHORT).show()
				return@setOnItemReselectedListener
			}

			val rawTitle = it.title?.toString() ?: ""
			val formattedTitle = if (rawTitle.startsWith(":")) rawTitle.drop(1) else rawTitle

			createDialogModule(
				title = "Rename Gradle Module.",
				subTitle = "Rename Module for your project.",
				buttonText = "Rename",
				apply = {
					root.getEditText()!!.setText(formattedTitle)
					root.setHelperEnabled(true)
					root.setHelperText("Leave blank to delete the Grade module.")
				},
				actionAccept = { text ->
					if (text.isEmpty()) {
						layout_binding.modulesGradle.menu.removeItemAt(it.order)
						listName.removeAt(it.order)
						return@createDialogModule
					}

					if (text.validateNameRegex && text !in reservedWords && text !in listName) {
						it.title = ":$text"
						listName[it.order] = text
					}
				},
				input = { text ->
					if (text.isEmpty()) return@createDialogModule

					if (!text.validateNameRegex) {
						input_binding.root.setErrorEnabled(true)
						input_binding.root.setErrorText("Contains symbol unknown in Module Name!")
					} else if (text in reservedWords) {
						input_binding.root.setErrorEnabled(true)
						input_binding.root.setErrorText("Contains a reserved work in Module Name!")
					} else if (text in listName && it.title != ":$text") {
						input_binding.root.setErrorEnabled(true)
						input_binding.root.setErrorText("Duplicate name in Module Name!")
					}
				}
			)
		}

		layout_binding.modulesPagerGradle.isUserInputEnabled = false
		layout_binding.modulesPagerGradle.adapter =
			ActivityManagerCreatorGradleViewPagerFragmentPropertyViewPagerFragmentAdapter(this)

		(requireActivity() as ProjectCreatorActivity).layout_binding.bar.setOnLeftItemMenuClickListener {

			if (it.title == "Open Menu") {
				// it.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_arrow_forward_24)
				it.title = "Close Menu"
				layout_binding.modulesGradle.visibility = View.GONE
			} else if (it.title == "Close Menu") {
				// it.icon = ContextCompat.getDrawable(requireContext(), R.drawable.ic_menu_24)
				it.title = "Open Menu"
				layout_binding.modulesGradle.visibility = View.VISIBLE
			}
		}

		return layout_binding.root
	}

	fun createDialogModule(title: String, subTitle: String, buttonText: String, apply: ActivityProjectModulesCreatorDialogInputBinding.() -> Unit = { }, actionAccept: (String) -> Unit = { }, input: (String) -> Unit = { }) {
		input_binding = ActivityProjectModulesCreatorDialogInputBinding.inflate(layoutInflater)
		input_binding.root.setPlaceholderText("Core")
		input_binding.root.setPrefixEnabled(true)
		input_binding.root.setPrefixText(":")
		input_binding.root.getEditText()!!.doOnTextChanged { text, _, _, _ ->
			input(text!!.toString())
		}

		MaterialAlertDialogBuilder(requireContext())
			.apply {
				apply(input_binding)
			}
			.setTitle(title)
			.setMessage(subTitle)
			.setView(input_binding.root, 50, 0, 50, 0)
			.setPositiveButton(buttonText) { _, _ ->
				actionAccept(input_binding.root.getEditText()!!.text.toString())
			}
			.setNegativeButton("Cancel", null)
			.setOnDismissListener {
				input_binding = ActivityProjectModulesCreatorDialogInputBinding.inflate(layoutInflater)
				input_binding.root.setPlaceholderText("Core")
				input_binding.root.setPrefixEnabled(true)
				input_binding.root.setPrefixText(":")
				input_binding.root.getEditText()!!.doOnTextChanged { text, _, _, _ ->
					input(text!!.toString())
				}
			}
			.show()
	}

	private class ActivityManagerCreatorGradleViewPagerFragmentPropertyViewPagerFragmentAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
		override fun createFragment(p0: Int): Fragment =
			ActivityManagerCreatorGradleViewPagerFragmentPropertyFragment()

		override fun getItemCount(): Int = 2
	}
}
