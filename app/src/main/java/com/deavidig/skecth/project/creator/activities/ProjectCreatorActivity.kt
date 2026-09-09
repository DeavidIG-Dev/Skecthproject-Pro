package com.deavidig.skecth.project.creator.activities

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.core.view.forEach
import androidx.core.view.get
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.deavidig.mod.deanielig.appcompat.app.ComponentAppCompatActivity
import com.deavidig.mod.deanielig.backdrop.widget.ComponentBackdropAdapter
import com.deavidig.mod.deanielig.badge.widget.ComponentBadge
import com.deavidig.mod.deanielig.search.widget.ComponentSearchBar
import com.deavidig.mod.deanielig.tooltip.widget.ComponentTooltip
import com.deavidig.skecth.project.utils.FileUtil
import com.deavidig.sketchprojectpro.R
import com.deavidig.sketchprojectpro.databinding.ActivityProjectCreatorBackBinding
import com.deavidig.sketchprojectpro.databinding.ActivityProjectCreatorBackDialogBinding
import com.deavidig.sketchprojectpro.databinding.ActivityProjectCreatorBinding
import com.deavidig.sketchprojectpro.databinding.ActivityProjectCreatorFrontBinding
import com.deavidig.sketchprojectpro.databinding.ActivityProjectCreatorFrontHomeBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ProjectCreatorActivity : ComponentAppCompatActivity() {
	private lateinit var layout_binding: ActivityProjectCreatorBinding

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)

		enableEdgeToEdge()
		layout_binding = ActivityProjectCreatorBinding.inflate(layoutInflater)
		setContentView(layout_binding.root)
		applyWindowInsets()

		layout_binding.bar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

		layout_binding.bar.setOnLeftItemMenuClickListener {
			if (it.itemId == R.id.menu_item) {
				layout_binding.backdrop.toggle()
			}
		}

		layout_binding.bar.setOnRightItemMenuClickListener {
			MaterialAlertDialogBuilder(this@ProjectCreatorActivity)
				.setTitle("Save Progress?")
				.setMessage("Are you sure you want to save your progress and finish creating your application?")
				.setPositiveButton("Yes") { _, _ -> savedProjectInJSON() }
				.setNegativeButton("Cancel", null)
				.show()
		}

		layout_binding.backdrop.adapter = ComponentBackDropFragment(this)

		onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
			override fun handleOnBackPressed() {
				if (isEnabled) {
					MaterialAlertDialogBuilder(this@ProjectCreatorActivity)
						.setTitle("Exit Project?")
						.setMessage("Are you sure you want to exit and cancel creating your new project?")
						.setNegativeButton("Cancel", null)
						.setPositiveButton("Exit") { _, _ ->
							isEnabled = false; onBackPressedDispatcher.onBackPressed()
						}
						.show()
				}
			}
		})
	}

	private fun savedProjectInJSON() {
		finish()
	}

	public class ComponentBackDropFragment(activity: AppCompatActivity) :
		ComponentBackdropAdapter.Companion.ComponentBaseBackdropAdapter(activity) {
		override fun createBackFragment(): Fragment = ModulesFragment()

		override fun createFrontFragment(): Fragment = ContentFragment()
	}

	public class ModulesFragment : Fragment() {
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

			searchBar.setOnQueryTextChangeListener { text ->
				layout_binding.moduleNavigation.menu.children.forEach { item ->
					if (item.hasSubMenu()) {
						item.subMenu?.forEach { module ->
							module.isVisible =
								text.isEmpty() ||
										module.title
											.toString()
											.removePrefix(":")
											.startsWith(text.toString())
						}

						item.isVisible = item.subMenu?.children?.any { it.isVisible } == true
					}
				}
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
			menuItem.isChecked = title == ":app"
			menuItem.icon = ContextCompat.getDrawable(
				requireContext(),
				R.drawable.ic_folder_managed_24
			)
		}
	}

	public class ContentFragment : Fragment() {
		private lateinit var layout_binding: ActivityProjectCreatorFrontBinding

		override fun onCreateView(
			inflater: LayoutInflater,
			container: ViewGroup?,
			savedInstanceState: Bundle?
		): View {
			layout_binding = ActivityProjectCreatorFrontBinding.inflate(layoutInflater)

			layout_binding.pagerFilter.adapter = ViewPager2Fragment(this)
			layout_binding.pagerFilter.isUserInputEnabled = false

			return layout_binding.root
		}

		public class ViewPager2Fragment(fragment: Fragment) : FragmentStateAdapter(fragment) {
			override fun createFragment(p0: Int): Fragment = HomeFragment()

			override fun getItemCount(): Int = 1
		}

		public class HomeFragment : Fragment() {
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
	}
}