package com.deavidig.skecth.project.creator.activities

import android.content.Context
import android.os.Bundle
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.os.persistableBundleOf
import androidx.core.widget.doOnTextChanged
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.deavidig.mod.deanielig.appcompat.app.ComponentAppCompatActivity
import com.deavidig.skecth.project.utils.FileUtil
import com.deavidig.sketchprojectpro.R
import com.deavidig.sketchprojectpro.databinding.ActivityProjectModulesCreatorBinding
import com.deavidig.sketchprojectpro.databinding.ActivityProjectModulesCreatorDialogInputBinding
import com.deavidig.sketchprojectpro.databinding.ActivityProjectModulesCreatorRecyclerviewModuleBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Collections

class ProjectModulesCreatorActivity : ComponentAppCompatActivity() {
	private lateinit var layout_binding: ActivityProjectModulesCreatorBinding
	private lateinit var input_binding: ActivityProjectModulesCreatorDialogInputBinding

	private lateinit var modulesList: ArrayList<String>

	private val gson = Gson()

	private companion object {
		private val String.validatePrefix
			get() = this.matches("[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*".toRegex())
	}

	override fun onRestoreInstanceState(savedInstanceState: Bundle) {
		super.onRestoreInstanceState(savedInstanceState)
		modulesList = savedInstanceState.getStringArrayList("Modules List") ?: arrayListOf()
		layout_binding.listName.adapter?.notifyDataSetChanged()
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putStringArrayList("Modules List", modulesList)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		modulesList = gson.fromJson(
			FileUtil.readFile(
				FileUtil.externalStorageDir +
						FileUtil.separator +
						"Sketchproject Pro" +
						FileUtil.separator +
						"prefix.json"
			),
			object : TypeToken<ArrayList<String>>() {}.type
		)

		layout_binding = ActivityProjectModulesCreatorBinding.inflate(layoutInflater)
		input_binding = ActivityProjectModulesCreatorDialogInputBinding.inflate(layoutInflater)

		enableEdgeToEdge()
		setContentView(layout_binding.root)
		applyWindowInsets()

		layout_binding.bar.setNavigationOnClickListener { onBackPressed() }

		layout_binding.moduleCreator.setOnClickListener {
			MaterialAlertDialogBuilder(this).setTitle("Name of Namespace")
				.setMessage("Create a custom Namespace for your projects.")
				.setNegativeButton("Cancel", null)
				.setPositiveButton("Accept") { _, _ ->
					val text = input_binding.root.getEditText()!!.text.toString()
					if (text.validatePrefix) {
						modulesList.add(text)
						layout_binding.listName.adapter?.notifyItemRangeChanged(modulesList.size - 2, 1)
					}
				}
				.setOnDismissListener {
					input_binding = ActivityProjectModulesCreatorDialogInputBinding.inflate(layoutInflater)
				}
				.setView(input_binding.root, 50, 0, 50, 0)
				.show()
		}

		input_binding.root.getEditText()!!.doOnTextChanged { text, _, _, _ ->
			if (!text.toString().validatePrefix) {
				input_binding.root.setErrorEnabled(true)
				input_binding.root.setErrorText("Contains symbols unknown in the module!")
			} else {
				input_binding.root.setErrorEnabled(false)
			}
		}

		layout_binding.listName.adapter = ProjectModulesCreatorRecyclerViewAdapter(this)

		ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
			val adapter = layout_binding.listName.adapter!!

			override fun onMove(
				recyclerView: RecyclerView,
				viewHolder: RecyclerView.ViewHolder,
				target: RecyclerView.ViewHolder
			): Boolean {
				val fromPosition = viewHolder.bindingAdapterPosition
				val toPosition = target.bindingAdapterPosition

				Collections.swap(modulesList, fromPosition, toPosition)

				adapter.notifyItemMoved(fromPosition, toPosition)
				adapter.notifyItemRangeChanged(if (fromPosition < toPosition) fromPosition else toPosition, modulesList.size)

				return true
			}

			override fun onSwiped(
				holder: RecyclerView.ViewHolder,
				swipe: Int
			) {
				val position = holder.position
				if (modulesList.size > 1) {
					modulesList.removeAt(position)
				}
				adapter.notifyDataSetChanged()
			}
		}).attachToRecyclerView(layout_binding.listName)

	}

	override fun onBackPressed() {
		super.onBackPressed()
		FileUtil.writeFile(FileUtil.externalStorageDir + FileUtil.separator + "Sketchproject Pro" + FileUtil.separator + "prefix.json", gson.toJson(modulesList))
	}

	private class ProjectModulesCreatorRecyclerViewAdapter(private val activity: ProjectModulesCreatorActivity) : RecyclerView.Adapter<ProjectModulesCreatorRecyclerViewAdapter.ProjectModulesCreatorRecyclerViewAdapterModuleHolder>() {

		private lateinit var input_binding: ActivityProjectModulesCreatorDialogInputBinding
		private lateinit var layoutInflater: LayoutInflater

		private fun Int.toDP(context: Context): Int =
			TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), context.resources.displayMetrics).toInt()

		override fun onCreateViewHolder(container: ViewGroup, type: Int): ProjectModulesCreatorRecyclerViewAdapterModuleHolder {
			layoutInflater = LayoutInflater.from(container.context)

			val binding = ActivityProjectModulesCreatorRecyclerviewModuleBinding.inflate(layoutInflater, container, false)
			input_binding = ActivityProjectModulesCreatorDialogInputBinding.inflate(layoutInflater)

			(binding.root.layoutParams as ViewGroup.MarginLayoutParams).apply {
				marginStart = 10.toDP(container.context)
				marginEnd = 10.toDP(container.context)
				topMargin = 2.toDP(container.context)
				bottomMargin = 2.toDP(container.context)
			}
			return ProjectModulesCreatorRecyclerViewAdapterModuleHolder(binding)
		}

		override fun onBindViewHolder(holder: ProjectModulesCreatorRecyclerViewAdapterModuleHolder, position: Int) {
			holder.binding.titleProject.text = activity.modulesList[position]

			holder.binding.root.setBackgroundResource(when {
				activity.modulesList.size == 1 -> R.drawable.bg_project_single
				position == 0 -> R.drawable.bg_project_top
				position == activity.modulesList.size - 1 -> R.drawable.bg_project_bottom
				else -> R.drawable.bg_project_middle
			})

			holder.binding.optionsProject.setOnClickListener {
				input_binding.root.getEditText()!!.setText(holder.binding.titleProject.text)
				MaterialAlertDialogBuilder(ContextThemeWrapper(input_binding.root.context, com.google.android.material.R.style.Theme_Material3_DayNight_NoActionBar)).setTitle("Name of Namespace")
					.setMessage("Create a custom Namespace for your projects.")
					.setNegativeButton("Cancel", null)
					.setPositiveButton("Accept") { _, _ ->
						val text = input_binding.root.getEditText()!!.text.toString()
						if (text.validatePrefix) {
							holder.binding.titleProject.text = text
							activity.modulesList[position] = text
						}
					}
					.setOnDismissListener {
						input_binding = ActivityProjectModulesCreatorDialogInputBinding.inflate(layoutInflater)
					}
					.setView(input_binding.root, 50, 0, 50, 0)
					.setCancelable(false)
					.show()
			}
		}

		override fun getItemCount(): Int = activity.modulesList.size

		class ProjectModulesCreatorRecyclerViewAdapterModuleHolder(val binding: ActivityProjectModulesCreatorRecyclerviewModuleBinding) : RecyclerView.ViewHolder(binding.root)
	}
}