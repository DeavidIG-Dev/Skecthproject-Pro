package com.deavidig.skecth.project.main.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import com.deavidig.mod.deanielig.appcompat.app.ComponentPermissionAppCompatActivity
import com.deavidig.mod.deanielig.tablayout.widget.isLastTab
import com.deavidig.skecth.project.creator.activities.ProjectCreatorActivity
import com.deavidig.skecth.project.utils.FileUtil
import com.deavidig.sketchprojectpro.R
import com.deavidig.sketchprojectpro.databinding.ActivityMainBinding
import com.deavidig.sketchprojectpro.databinding.ActivityMainBottomsheetFilterBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout


class MainActivity : ComponentPermissionAppCompatActivity() {
	private lateinit var layout_binding: ActivityMainBinding
	private lateinit var layout_filter_binding: ActivityMainBottomsheetFilterBinding

	private lateinit var bottomSheetDialog: BottomSheetDialog

	@SuppressLint("RestrictedApi")
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		bindings()

		enableEdgeToEdge()
		setContentView(layout_binding.root)
		applyWindowInsets()

		setupTabLayout()
	}

	private fun bindings() {
		layout_binding = ActivityMainBinding.inflate(layoutInflater)
		layout_filter_binding = ActivityMainBottomsheetFilterBinding.inflate(layoutInflater)


		bottomSheetDialog = BottomSheetDialog(this).apply {
			setContentView(layout_filter_binding.root)
		}

		setupBottomSheetListeners()
		setupFabListeners()
	}

	private fun setupFabListeners() {
		layout_binding.projectCreator.setOnClickListener {
			val intent = Intent(this, ProjectCreatorActivity::class.java)
			startActivity(intent)
		}
	}

	private fun setupBottomSheetListeners() {
		layout_filter_binding.acceptButton.setOnClickListener {
			val newName = layout_filter_binding.nameFilter.text?.toString()?.trim().orEmpty()
			layout_filter_binding.nameFilterContainer.setHelperEnabled(false)

			if (newName.isEmpty()) {
				layout_filter_binding.nameFilterContainer.setErrorEnabled(true)
				layout_filter_binding.nameFilterContainer.setErrorText("Name can't be empty for the Filter.")
				return@setOnClickListener
			}

			layout_filter_binding.nameFilterContainer.setErrorEnabled(false)

			val newTab = layout_binding.tabFilter.newTab().apply { text = newName }
			val lastIndex = layout_binding.tabFilter.tabCount - 1

			layout_binding.tabFilter.addTab(newTab, lastIndex)

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
				if (tab.isLastTab) {
					oldTab?.select()

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
					return
				}

				oldTab = tab
			}

			override fun onTabUnselected(tab: TabLayout.Tab) {
				val lastIndex = layout_binding.tabFilter.tabCount - 1
				if (tab.position != lastIndex) {
					this.oldTab = tab
				}
			}

			override fun onTabReselected(tab: TabLayout.Tab) {
				val lastIndex = layout_binding.tabFilter.tabCount - 1

				if (tab.position == 0) {
					Toast.makeText(
						this@MainActivity,
						"You can't modify the Main Tab",
						Toast.LENGTH_SHORT
					).show()
					return
				} else if (tab.position == lastIndex) {
					bottomSheetDialog.show()
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

						layout_filter_binding.nameFilterContainer.setPlaceholderText(
							resources.getStringArray(
								R.array.placeholder_tab_filter
							).random()
						)

						if (newName.isEmpty()) {
							layout_filter_binding.nameFilterContainer.setErrorEnabled(true)
							layout_filter_binding.nameFilterContainer.setErrorText("Name can't be empty for the Filter.")
							return@setOnClickListener
						}

						layout_filter_binding.nameFilterContainer.setErrorEnabled(false)

						val newTab = layout_binding.tabFilter.newTab().apply { text = newName }
						val lastIndex = layout_binding.tabFilter.tabCount - 1

						layout_binding.tabFilter.addTab(newTab, lastIndex)

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

	override fun onStoragePermissionGranted() {
		super.onStoragePermissionGranted()
		FileUtil.makeDir(FileUtil.DirectoryOfProject)
		FileUtil.createNewFileIfNotPresent(FileUtil.DirectoryOfProject + FileUtil.separator + "project-section.json")
	}
}