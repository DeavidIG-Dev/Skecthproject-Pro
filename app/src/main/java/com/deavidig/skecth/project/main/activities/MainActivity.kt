package com.deavidig.skecth.project.main.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import com.deavidig.mod.deanielig.appcompat.app.ComponentPermissionAppCompatActivity
import com.deavidig.skecth.project.creator.activities.ProjectCreatorActivity
import com.deavidig.skecth.project.main.adapter.`MainActivity-ProjectAdapter`
import com.deavidig.skecth.project.utils.FileUtil
import com.deavidig.skecth.project.utils.Gradle
import com.deavidig.skecth.project.utils.JavaVersion
import com.deavidig.skecth.project.utils.Language
import com.deavidig.skecth.project.utils.Module
import com.deavidig.skecth.project.utils.Project
import com.deavidig.skecth.project.utils.ProjectStructure
import com.deavidig.sketchprojectpro.R
import com.deavidig.sketchprojectpro.databinding.ActivityMainBinding
import com.deavidig.sketchprojectpro.databinding.ActivityMainBottomsheetFilterBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken


class MainActivity : ComponentPermissionAppCompatActivity() {
	private lateinit var layout_binding: ActivityMainBinding
	private lateinit var layout_filter_binding: ActivityMainBottomsheetFilterBinding

	private lateinit var bottomSheetDialog: BottomSheetDialog

	private val vListTabs: ArrayList<String> = arrayListOf("All")
	private val gson = Gson()

	private val projectList: ArrayList<ProjectStructure> = arrayListOf(
		ProjectStructure(
			project = Project(
				description = "This project is how to yo use Sketchproject in First Time!",

				language = Language.Kotlin,
				version = JavaVersion.Java_17
			),
			gradles = arrayListOf(
				Gradle(
					name = "First Project",
					`package` = "com.deavidig.example",

					modules = Module(
						name = ":app",

						suffixName = "Alpha",

						versionCode = 1,
						versionName = "1.0",

						minSDK = 20,
						targetSDK = 27,
						maxSDK = 31,
					)
				),
				Gradle(
					name = "First Project",
					`package` = "com.deavidig.sub.example",

					modules = Module(
						name = ":sub_module",

						suffixName = "Alpha",

						versionCode = 1,
						versionName = "1.0",

						minSDK = 20,
						targetSDK = 27,
						maxSDK = 31,
					)
				)
			)
		)
	)

	@SuppressLint("RestrictedApi")
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		bindings()

		enableEdgeToEdge()
		setContentView(layout_binding.root)
		applyWindowInsets()

		setupTabLayout()
	}

	override fun onStart() {
		super.onStart()

		if (isFileAccess()) {
			vListTabs.clear()

			try {
				vListTabs.addAll(
					gson.fromJson<ArrayList<String>>(
						FileUtil.readFile(
							FileUtil.PublicDirectoryOfProject + FileUtil.separator + ".project-section.json"
						),
						object : TypeToken<ArrayList<String>>() {}.type
					)
				)
			} catch (e: Exception) {
				vListTabs.add("All")
			}

			layout_binding.tabFilter.removeAllTabs()

			for (text in vListTabs) {
				layout_binding.tabFilter.addTab(layout_binding.tabFilter.newTab().setText(text))
			}
		}
	}

	private fun bindings() {
		layout_binding = ActivityMainBinding.inflate(layoutInflater)
		layout_filter_binding = ActivityMainBottomsheetFilterBinding.inflate(layoutInflater)


		bottomSheetDialog = BottomSheetDialog(this).apply {
			setContentView(layout_filter_binding.root)
		}

		layout_filter_binding.nameFilterContainer.setPlaceholderText(
			resources.getStringArray(
				R.array.placeholder_tab_filter
			).random()
		)

		layout_binding.projectList.adapter =
			`MainActivity-ProjectAdapter`(projectList = projectList)

		setupListeners()
	}

	private fun setupListeners() {
		layout_binding.projectCreator.setOnItemMenuClickListener {
			if (it.getId() == R.id.menu_add) {
				val intent = Intent(this, ProjectCreatorActivity::class.java)
				startActivity(intent)
			}
		}

		layout_binding.tabsButton.setOnRightItemMenuClickListener {
			if (it.itemId == R.id.menu_add) {
				layout_filter_binding.nameFilterContainer.setErrorEnabled(false)
				layout_filter_binding.nameFilterContainer.setHintText("Name Filter")
				layout_filter_binding.nameFilter.setText("")
				layout_filter_binding.nameFilterContainer.setHelperText("")
				layout_filter_binding.nameFilter.requestFocus()
				layout_filter_binding.nameFilterContainer.setPlaceholderText(
					resources.getStringArray(
						R.array.placeholder_tab_filter
					).random()
				)

				bottomSheetDialog.show()
			} else if (it.itemId == R.id.menu_options) {
				MaterialAlertDialogBuilder(this)
					.setTitle("Sort Options")
					.setSingleChoiceItems(
						arrayOf(
							"Sort by Project Name",
							"Sort by ID",
							"Ascending (A-Z)",
							"Descending (Z-A)"
						), 1
					) { _, _ -> }
					.setCancelable(false)
					.setNegativeButton("Cancel") { _, _ -> }
					.setPositiveButton("Accept") { _, _ -> }
					.show()
			}
		}

		layout_filter_binding.acceptButton.setOnClickListener {
			val newName = layout_filter_binding.nameFilter.text?.toString()?.trim().orEmpty()
			layout_filter_binding.nameFilterContainer.setHelperEnabled(false)

			if (newName.isEmpty()) {
				layout_filter_binding.nameFilterContainer.setErrorEnabled(true)
				layout_filter_binding.nameFilterContainer.setErrorText("Name can't be empty for the Filter.")
				return@setOnClickListener
			}

			layout_filter_binding.nameFilterContainer.setErrorEnabled(false)
			vListTabs.add(newName)

			layout_binding.tabFilter.addTab(
				layout_binding.tabFilter.newTab().apply { text = newName }, vListTabs.size - 1
			)

			layout_filter_binding.nameFilter.setText("")
			bottomSheetDialog.dismiss()
		}

		layout_filter_binding.cancelButton.setOnClickListener {
			bottomSheetDialog.dismiss()
		}
	}

	private fun setupTabLayout() {
		layout_binding.tabFilter.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
			private var oldTab: TabLayout.Tab? = null

			override fun onTabSelected(tab: TabLayout.Tab) {
				oldTab = tab
			}

			override fun onTabUnselected(tab: TabLayout.Tab) {
				val lastIndex = layout_binding.tabFilter.tabCount - 1
				if (tab.position != lastIndex) {
					this.oldTab = tab
				}
			}

			override fun onTabReselected(tab: TabLayout.Tab) {
				if (tab.position == 0) {
					Toast.makeText(
						this@MainActivity,
						"You can't modify the Main Tab",
						Toast.LENGTH_SHORT
					).show()
					return
				}

				layout_filter_binding.nameFilter.setText(tab.text)
				layout_filter_binding.nameFilterContainer.setHintText("Rename Filter")
				layout_filter_binding.nameFilterContainer.setHelperEnabled(false)
				bottomSheetDialog.show()

				layout_filter_binding.acceptButton.setOnClickListener {
					if (layout_filter_binding.nameFilterContainer.getHintText() == "Name Filter") {
						val newName =
							layout_filter_binding.nameFilter.text?.toString()?.trim().orEmpty()
						layout_filter_binding.nameFilterContainer.setHelperEnabled(false)

						if (newName.isEmpty()) {
							layout_filter_binding.nameFilterContainer.setErrorEnabled(true)
							layout_filter_binding.nameFilterContainer.setErrorText("Name can't be empty for the Filter.")
							return@setOnClickListener
						}

						layout_filter_binding.nameFilterContainer.setErrorEnabled(false)
						vListTabs.add(newName)

						layout_binding.tabFilter.addTab(
							layout_binding.tabFilter.newTab().apply { text = newName },
							vListTabs.size - 1
						)

						layout_filter_binding.nameFilter.setText("")
						bottomSheetDialog.dismiss()
					} else {
						val newName =
							layout_filter_binding.nameFilter.text?.toString()?.trim().orEmpty()

						if (newName.isEmpty()) {
							MaterialAlertDialogBuilder(this@MainActivity)
								.setTitle("Delete tab?")
								.setMessage("Leaving the name empty will delete this tab.")
								.setPositiveButton("Delete") { _, _ ->
									vListTabs.removeAt(tab.position)
									layout_binding.tabFilter.removeTab(tab)
									bottomSheetDialog.dismiss()
								}
								.setNegativeButton("Cancel", null)
								.show()
						} else {
							tab.text = newName
							bottomSheetDialog.dismiss()
						}
					}
				}
			}
		})
	}

	override fun onStop() {
		super.onStop()

		FileUtil.writeFile(
			FileUtil.PublicDirectoryOfProject + FileUtil.separator + ".project-section.json",
			gson.toJson(vListTabs)
		)
	}

	override fun onStoragePermissionGranted() {
		super.onStoragePermissionGranted()
		FileUtil.makeDir(FileUtil.PublicDirectoryOfProject)
		FileUtil.writeFile(
			FileUtil.PublicDirectoryOfProject + FileUtil.separator + ".project-section.json",
			"[\"All\"]"
		)
	}
}