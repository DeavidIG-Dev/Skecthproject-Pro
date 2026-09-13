package com.deavidig.skecth.project.creator.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.get
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import com.deavidig.mod.deanielig.badge.widget.ComponentBadge
import com.deavidig.mod.deanielig.tooltip.widget.ComponentTooltip
import com.deavidig.skecth.project.creator.activities.ProjectCreatorActivity
import com.deavidig.skecth.project.creator.activities.ProjectModulesCreatorActivity
import com.deavidig.skecth.project.utils.FileUtil
import com.deavidig.sketchprojectpro.databinding.ActivityProjectCreatorFrontHomeBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class HomeFragment : Fragment() {
	private lateinit var layout_binding: ActivityProjectCreatorFrontHomeBinding
	private val listPrefix: ArrayList<String> = arrayListOf()

	private val DEFAULT_PREFIXES =
		arrayListOf("com.example", "com.dev", "org.example", "org.dev")
	private val EMPTY_LIST: ArrayList<String> = arrayListOf()

	private val NAME_APPLICATION_REGEX =
		"[a-zA-Z]([a-zA-Z0-9 ]|\\.([a-zA-Z0-9][ ._+\\-:!?&()]*)+)*".toRegex()

	private val NAME_PACKAGE_REGEX =
		"[a-zA-Z]([a-zA-Z0-9\\- ]|\\.([a-zA-Z0-9][a-zA-Z0-9\\- ]*)+)*".toRegex()

	override fun onStart() {
		super.onStart()
		reloadListPrefix()
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?
	): View = layout_binding.root

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		layout_binding = ActivityProjectCreatorFrontHomeBinding.inflate(layoutInflater)

		if (!FileUtil.isFile(FileUtil.externalStorageDir + FileUtil.separator + "Sketchproject Pro" + FileUtil.separator + "prefix.json")) FileUtil.writeFile(
			FileUtil.externalStorageDir + FileUtil.separator + "Sketchproject Pro" + FileUtil.separator + "prefix.json",
			"[\"com.example\", \"com.dev\", \"org.example\", \"org.dev\"]"
		)

		layout_binding.packageName.setPrefixEnabled(true)

		ComponentBadge(requireContext())
			.setText("Experimental")
			.show(layout_binding.languageOptionScala)

		layout_binding.packageName.setStartImageOnLongClickListener {
			ComponentTooltip(it)
				.setTitle("Namespace Usage")
				.setMessage("Tap the Package Name icon to enable or disable namespaces in your project.")
				.setNeutralButton("OK", null)
				.setPlacement(ComponentTooltip.Placement.TOP)
				.show()
			false
		}

		layout_binding.packageName.setStartImageOnClickListener {
			layout_binding.packageName.setPrefixEnabled(!layout_binding.packageName.isPrefixEnabled())
		}

		layout_binding.packageName.setEndLayoutOnClickListener {
			startActivity(
				Intent(
					requireContext(),
					ProjectModulesCreatorActivity::class.java
				)
			)
		}

		layout_binding.applicationName.getEditText()?.doOnTextChanged { text, _, _, _ ->
			if (!text!!.matches(NAME_APPLICATION_REGEX)) {
				layout_binding.applicationName.setErrorEnabled(true)
				layout_binding.applicationName.setErrorText("The Application Name contains invalid characters.")
				(requireActivity() as ProjectCreatorActivity).layout_binding.bar.getRightMenu()
					?.get(0)?.isEnabled = false
				return@doOnTextChanged
			}
			(requireActivity() as ProjectCreatorActivity).layout_binding.bar.getRightMenu()
				?.get(0)?.isEnabled = true

			val nameText = text.trim().replace(" +".toRegex(), ".").lowercase()
			layout_binding.packageName.getEditText()?.setText(nameText)
		}

		layout_binding.packageName.getEditText()?.doOnTextChanged { text, _, _, _ ->
			if (!text!!.matches(NAME_PACKAGE_REGEX)) {
				layout_binding.packageName.setErrorEnabled(true)
				layout_binding.packageName.setErrorText("The Application Name contains invalid characters.")
				(requireActivity() as ProjectCreatorActivity).layout_binding.bar.getRightMenu()
					?.get(0)?.isEnabled = false
				return@doOnTextChanged
			}
			(requireActivity() as ProjectCreatorActivity).layout_binding.bar.getRightMenu()
				?.get(0)?.isEnabled = true
		}

		layout_binding.languageOptionScala.setOnClickListener {

			if (layout_binding.languageOptionScala.isChecked) {
				MaterialAlertDialogBuilder(requireContext())
					.setTitle("Scala Language Option.")
					.setMessage("You select Scala as the project's build, are you sure you want to take the risk that comes with using Scala?")
					.setPositiveButton("Accept", null)
					.setNegativeButton("Cancel") { _, _ ->
						layout_binding.languageOptionScala.isChecked = false
					}
					.show()
			}
		}

		reloadListPrefix()
	}

	fun reloadListPrefix() {
		listPrefix.clear()
		listPrefix.addAll(
			try {
				(Gson().fromJson<ArrayList<String>>(
					FileUtil.readFile(FileUtil.externalStorageDir + FileUtil.separator + "Sketchproject Pro" + FileUtil.separator + "prefix.json"),
					object : TypeToken<ArrayList<String>>() {}.type
				) ?: EMPTY_LIST).ifEmpty { DEFAULT_PREFIXES }
			} catch (_: Exception) {
				DEFAULT_PREFIXES
			})
		layout_binding.packageName.setPrefixTextList(listPrefix)
	}
}