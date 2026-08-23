package com.deavidig.mod.deaniel.textinput.widget

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.PopupWindow
import androidx.annotation.AttrRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ContextThemeWrapper
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.widget.doOnTextChanged
import com.deavidig.mod.deaniel.appcompat.app.getAttributeToColor
import com.deavidig.sketchprojectpro.R
import com.deavidig.sketchprojectpro.databinding.ComponentMaterialTextInputLayoutBinding
import com.google.android.material.textview.MaterialTextView

/**
 * A text field container inspired by the Material Design 3 **TextField**.
 *
 * This component wraps a single [EditText] child (or a subclass such as
 * [AutoCompleteTextView]):
 *
 * - **Leading icon** / **trailing icon** (`start` / `end`), each with its own
 *   drawable, tint and click listener.
 * - **Prefix text** / **suffix text**, optionally expandable into a dropdown
 *   menu (e.g. a country-code picker, a unit-of-measurement picker, etc.).
 * - **Supporting text** (helper text) and an **error state**, which are
 *   mutually exclusive: while the field is in an error state, the supporting
 *   text is replaced by the error message and colors switch to the theme's
 *   `colorError`.
 * - **Character counter**, with an optional maximum length.
 * - A **hint / floating label**, animated M3-style: it rests over the
 *   [EditText] when empty and unfocused, and floats above it (smaller,
 *   translated up) once focused or filled.
 * - A **header**, a title-like label rendered above the whole field —
 *   useful to group several fields under one heading.
 * - Selection lifecycle listeners ([OnItemActionListener],
 *   [OnItemClickChangedListener]) for the prefix/suffix dropdown, covering
 *   select, reselect, unselect and "value actually changed" events.
 *
 * ### Basic usage (XML)
 * ```xml
 * <com.deavidig.sketchprojectpro.ComponentMaterialTextInputLayout
 *     android:layout_width="match_parent"
 *     android:layout_height="wrap_content"
 *     app:hintEnable="true"
 *     app:hintText="Name"
 *     app:counterEnable="true"
 *     app:counterMaxLength="40">
 *
 *     <EditText
 *         android:layout_width="match_parent"
 *         android:layout_height="wrap_content" />
 * </com.deavidig.sketchprojectpro.ComponentMaterialTextInputLayout>
 * ```
 *
 * ### Basic usage (Kotlin)
 * ```kotlin
 * textInputLayout.setHeaderEnabled(true)
 * textInputLayout.setHeaderText("Billing")
 *
 * textInputLayout.setPrefixEnabled(true)
 * textInputLayout.setPrefixTextList("+52", "+1", "+34")
 * textInputLayout.setPrefixOnItemClickChangedListener { old, new ->
 *     Log.d("Prefix", "Changed from $old to $new")
 * }
 *
 * textInputLayout.setEndImageOnClickListener(View.OnClickListener {
 *     textInputLayout.getEditText()?.text?.clear()
 * })
 * ```
 *
 * @constructor Inflates the private layout and resolves the attributes
 * declared in `R.styleable.ComponentMaterialTextInputLayout`.
 * @param context source context; it is wrapped in a [ContextThemeWrapper]
 *   using the `Theme.MaterialComponents.DayNight` theme so that M3 resources
 *   (error color, etc.) resolve correctly even if the hosting
 *   Activity/Fragment uses a different theme.
 * @param attrs XML attributes for the component.
 *
 * @author DeanielIG, DeavidIG
 */
class ComponentMaterialTextInputLayout(
	context: Context,
	attrs: AttributeSet? = null
) : ConstraintLayout(
	ContextThemeWrapper(context, com.google.android.material.R.style.Theme_MaterialComponents_DayNight),
	attrs
) {

	private companion object {
		/** Duration of the floating-label float-up/float-down transition. */
		const val HINT_ANIMATION_DURATION_MS = 150L

		/** Scale applied to the floating label once it's "floated" (focused or filled). */
		const val HINT_FLOATING_SCALE = 0.75f
	}

	/** Binding for the private layout (container, prefix/suffix, icons, counter, etc.). */
	private val viewBinding: ComponentMaterialTextInputLayoutBinding

	/** `true` while the binding is being inflated, so the private `addView` call isn't intercepted. */
	private var inflating: Boolean = true

	/**
	 * Direct reference to the [EditText] child added by the user of this
	 * component. Replaces the old positional lookup
	 * `container.getChildAt(6)`, which was fragile against any change to the
	 * private layout. Use [getEditText] to read it.
	 */
	private var mEditText: EditText? = null

	private var mPrefixText: String? = null
	private var mPrefixEnable: Boolean = false
	private var mPrefixTextColor: ColorStateList? = null
	private var mPrefixTextList: List<String> = emptyList()

	private var mSuffixText: String? = null
	private var mSuffixEnable: Boolean = false
	private var mSuffixTextColor: ColorStateList? = null
	private var mSuffixTextList: List<String> = emptyList()

	private var mHintText: String? = null
	private var mHintEnable: Boolean = false
	private var mHintTextColor: ColorStateList? = null

	/**
	 * Backing field for the floating label rendered above the [EditText]
	 * when [mHintEnable] is `true`. Created lazily the first time an
	 * [EditText] child is added, since it needs to be constrained relative
	 * to it. See [setupFloatingHintLabel] and [updateFloatingHintLabel].
	 */
	private var mFloatingHintLabel: MaterialTextView? = null

	/**
	 * Drives the float-up/float-down motion of [mFloatingHintLabel]. Kept as
	 * a field (instead of a fire-and-forget [ValueAnimator]) so a change
	 * mid-flight can cancel the previous run before starting a new one, and
	 * so it can be cancelled from [onDetachedFromWindow] instead of leaking
	 * a running animation tied to a detached view.
	 */
	private var mHintAnimator: ValueAnimator? = null

	/**
	 * Keeps [mFloatingHintLabel] in sync with the [EditText]'s real
	 * measurements on every layout pass — not just once — so its resting/
	 * floated position never drifts after a rotation or a keyboard
	 * open/close (both of which resize the field without recreating it).
	 * Stored so it can be detached in [onDetachedFromWindow].
	 */
	private var mHintLayoutListener: View.OnLayoutChangeListener? = null

	/**
	 * Border/divider colors resolved **once** from the current theme instead
	 * of on every focus/error/enabled transition. `setBackgroundResource()`
	 * used to be called on every such transition, which re-resolves a
	 * [Drawable] from resources and forces a re-layout each time; the actual
	 * visual state is now driven by a single state-list drawable (see
	 * [installBorderSelector]) that the platform switches natively, and
	 * these cached colors are only used for the [com.google.android.material.divider.MaterialDivider],
	 * which doesn't support state-list-driven colors on its own.
	 */
	private val mColorOutline: ColorStateList by lazy { resolveThemeColorStateList(com.google.android.material.R.attr.colorOutline) }
	private val mColorPrimary: ColorStateList by lazy { resolveThemeColorStateList(androidx.appcompat.R.attr.colorPrimary) }
	private val mColorOnError: ColorStateList by lazy { resolveThemeColorStateList(com.google.android.material.R.attr.colorOnError) }
	private val mColorDisabledDivider: ColorStateList by lazy { resolveThemeColorStateList(R.attr.darkGrey) }
	private val mColorError: Int by lazy {
		val typedValue = TypedValue()
		context.theme.resolveAttribute(androidx.appcompat.R.attr.colorError, typedValue, true)
		typedValue.data
	}

	/** Header text, a title-like label rendered above the whole field. */
	private var mHeaderText: String? = null
	private var mHeaderEnable: Boolean = false
	private var mHeaderTextColor: ColorStateList? = null

	/** Header text size, in pixels (as resolved from the `headerTextSize` dimension attribute). */
	private var mHeaderTextSize: Float = 0f

	private var mCounterEnable: Boolean = false
	private var mCounterTextColor: ColorStateList? = null

	/** Maximum character count. `-1` means "no limit". */
	private var mMaxLimit: Int = -1

	/**
	 * If `true`, exceeding [mMaxLimit] trims the text instead of just
	 * flagging an error.
	 */
	private var mOutLimitTextLimit: Boolean = false

	private var mHelperText: String? = null
	private var mHelperEnable: Boolean = false
	private var mHelperTextColor: ColorStateList? = null

	private var mStartIconEnable: Boolean = false
	private var mStartIcon: Drawable? = null
	private var mStartIconTint: ColorStateList? = null
	private var mStartLayoutEnable: Boolean = false

	private var mEndIconEnable: Boolean = false
	private var mEndIcon: Drawable? = null
	private var mEndIconTint: ColorStateList? = null
	private var mEndLayoutEnable: Boolean = false

	private var mErrorText: String = ""
	private var mErrorTextColor: ColorStateList? = null
	private var mInErrorState: Boolean = false

	private var mPrefixItemActionListener: OnItemActionListener? = null
	private var mPrefixItemClickChangedListener: OnItemClickChangedListener? = null
	private var mSuffixItemActionListener: OnItemActionListener? = null
	private var mSuffixItemClickChangedListener: OnItemClickChangedListener? = null

	/**
	 * Full lifecycle of a tap on a prefix/suffix dropdown option (see
	 * [setPrefixTextList] / [setSuffixTextList]).
	 *
	 * Register an implementation via [setPrefixOnItemActionListener] or
	 * [setSuffixOnItemActionListener].
	 */
	interface OnItemActionListener {
		/** A previously non-selected option was tapped and is now the current selection. */
		fun onItemSelected(item: String)

		/** The option that was already the current selection was tapped again. */
		fun onItemReselected(item: String)

		/**
		 * The current selection was cleared — e.g. via [clearPrefixSelection] /
		 * [clearSuffixSelection] — and [item] is the option that was selected
		 * right before the clear.
		 */
		fun onItemUnselected(item: String)
	}

	/**
	 * Fired only when a prefix/suffix dropdown selection actually *changes*
	 * from one option to a different one (not on reselect, and not on the
	 * very first selection, since there is no previous value to report).
	 *
	 * Register an implementation via [setPrefixOnItemClickChangedListener] or
	 * [setSuffixOnItemClickChangedListener]. Being a `fun interface`, it can
	 * be assigned with a lambda: `setPrefixOnItemClickChangedListener { old, new -> ... }`.
	 */
	fun interface OnItemClickChangedListener {
		fun onItemClickChanged(oldItem: String, newItem: String)
	}

	init {
		viewBinding = ComponentMaterialTextInputLayoutBinding.inflate(LayoutInflater.from(context), this, true)
		inflating = false

		context.withStyledAttributes(attrs, R.styleable.ComponentMaterialTextInputLayout) {

			mPrefixText = getString(R.styleable.ComponentMaterialTextInputLayout_prefixText)
			mPrefixEnable = getBoolean(R.styleable.ComponentMaterialTextInputLayout_prefixEnable, false)
			mPrefixTextColor = getColorStateList(R.styleable.ComponentMaterialTextInputLayout_prefixTextColor) ?: run {
				val typedValue = TypedValue()
				context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
				context.getColorStateList(typedValue.resourceId).takeIf { typedValue.resourceId != 0 }
					?: ColorStateList.valueOf(context.getColor(typedValue.data))
			}

			mSuffixText = getString(R.styleable.ComponentMaterialTextInputLayout_suffixText)
			mSuffixEnable = getBoolean(R.styleable.ComponentMaterialTextInputLayout_suffixEnable, false)
			mSuffixTextColor = getColorStateList(R.styleable.ComponentMaterialTextInputLayout_suffixTextColor) ?: run {
				val typedValue = TypedValue()
				context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
				context.getColorStateList(typedValue.resourceId).takeIf { typedValue.resourceId != 0 }
					?: ColorStateList.valueOf(context.getColor(typedValue.data))
			}

			mHintText = getString(R.styleable.ComponentMaterialTextInputLayout_hintText)
			mHintEnable = getBoolean(R.styleable.ComponentMaterialTextInputLayout_hintEnable, false)
			mHintTextColor = getColorStateList(R.styleable.ComponentMaterialTextInputLayout_hintTextColor) ?: run {
				val typedValue = TypedValue()
				context.theme.resolveAttribute(com.google.android.material.R.attr.colorOutline, typedValue, true)
				context.getColorStateList(typedValue.resourceId).takeIf { typedValue.resourceId != 0 }
					?: ColorStateList.valueOf(context.getColor(typedValue.data))
			}

			mHeaderText = getString(R.styleable.ComponentMaterialTextInputLayout_headerText)
			mHeaderEnable = getBoolean(R.styleable.ComponentMaterialTextInputLayout_headerEnable, false)
			mHeaderTextColor = getColorStateList(R.styleable.ComponentMaterialTextInputLayout_headerTextColor) ?: run {
				val typedValue = TypedValue()
				context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
				context.getColorStateList(typedValue.resourceId).takeIf { typedValue.resourceId != 0 }
					?: ColorStateList.valueOf(context.getColor(typedValue.data))
			}
			mHeaderTextSize = getDimension(R.styleable.ComponentMaterialTextInputLayout_headerTextSize, 14.dp.toFloat())

			mCounterEnable = getBoolean(R.styleable.ComponentMaterialTextInputLayout_counterEnable, false)
			mOutLimitTextLimit = getBoolean(R.styleable.ComponentMaterialTextInputLayout_counterLimit, false)
			mCounterTextColor = getColorStateList(R.styleable.ComponentMaterialTextInputLayout_counterTextColor) ?: run {
				val typedValue = TypedValue()
				context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
				context.getColorStateList(typedValue.resourceId).takeIf { typedValue.resourceId != 0 }
					?: ColorStateList.valueOf(context.getColor(typedValue.data))
			}
			mMaxLimit = getInteger(R.styleable.ComponentMaterialTextInputLayout_counterMaxLength, -1)

			mHelperText = getString(R.styleable.ComponentMaterialTextInputLayout_helperText)
			mHelperEnable = getBoolean(R.styleable.ComponentMaterialTextInputLayout_helperEnable, false)
			mHelperTextColor = getColorStateList(R.styleable.ComponentMaterialTextInputLayout_helperTextColor) ?: run {
				val typedValue = TypedValue()
				context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
				context.getColorStateList(typedValue.resourceId).takeIf { typedValue.resourceId != 0 }
					?: ColorStateList.valueOf(context.getColor(typedValue.data))
			}

			mStartIconEnable = getBoolean(R.styleable.ComponentMaterialTextInputLayout_startIconEnable, false)
			mStartIcon = getDrawable(R.styleable.ComponentMaterialTextInputLayout_startIconDrawable)
			mStartIconTint = getColorStateList(R.styleable.ComponentMaterialTextInputLayout_startIconTint) ?: run {
				val typedValue = TypedValue()
				context.theme.resolveAttribute(com.google.android.material.R.attr.colorOutline, typedValue, true)
				context.getColorStateList(typedValue.resourceId).takeIf { typedValue.resourceId != 0 }
					?: ColorStateList.valueOf(context.getColor(typedValue.data))
			}
			mStartLayoutEnable = getBoolean(R.styleable.ComponentMaterialTextInputLayout_startLayoutEnable, false)

			mEndIconEnable = getBoolean(R.styleable.ComponentMaterialTextInputLayout_endIconEnable, false)
			mEndIcon = getDrawable(R.styleable.ComponentMaterialTextInputLayout_endIconDrawable)
			mEndIconTint = getColorStateList(R.styleable.ComponentMaterialTextInputLayout_endIconTint) ?: run {
				val typedValue = TypedValue()
				context.theme.resolveAttribute(com.google.android.material.R.attr.colorOutline, typedValue, true)
				context.getColorStateList(typedValue.resourceId).takeIf { typedValue.resourceId != 0 }
					?: ColorStateList.valueOf(context.getColor(typedValue.data))
			}
			mEndLayoutEnable = getBoolean(R.styleable.ComponentMaterialTextInputLayout_endLayoutEnable, false)

			mErrorText = getString(R.styleable.ComponentMaterialTextInputLayout_errorText) ?: ""
			mErrorTextColor = getColorStateList(R.styleable.ComponentMaterialTextInputLayout_errorTextColor) ?: ColorStateList.valueOf(Color.RED)
		}

		// Apply every module once, independently. None of these depend on
		// the EditText child, which doesn't exist yet at this point.
		applyHeader()
		applyPrefix()
		applySuffix()
		applyCounterAppearance()
		applyStartIcon()
		applyStartLayout()
		applyEndIcon()
		applyEndLayout()
		error(mInErrorState)

		// Install the border state-list drawable ONCE (see installBorderSelector's
		// KDoc): from here on, focus/error/enabled changes just flip booleans
		// and the platform swaps the visible sub-drawable on its own.
		installBorderSelector()
		applyDividerColor(mColorOutline)

		// TalkBack announces the helper/error text automatically whenever it
		// changes, without needing an explicit accessibility event per toggle.
		viewBinding.message.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
	}

	/** Converts `dp` to pixels using the current device density. */
	private val Int.dp: Int get(): Int =
		TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this.toFloat(), resources.displayMetrics).toInt()

	/**
	 * Resolves a theme attribute (e.g. `colorPrimary`) into a [ColorStateList],
	 * falling back to a flat color when the attribute doesn't point at a
	 * `ColorStateList` resource. Shared by every `mColorXxx` cached field
	 * above, so the underlying `Resources.Theme.resolveAttribute()` call
	 * only runs once per attribute for the lifetime of this view.
	 */
	private fun resolveThemeColorStateList(@AttrRes attrId: Int): ColorStateList {
		val typedValue = TypedValue()
		context.theme.resolveAttribute(attrId, typedValue, true)
		return context.getColorStateList(typedValue.resourceId).takeIf { typedValue.resourceId != 0 }
			?: ColorStateList.valueOf(context.getColor(typedValue.data))
	}

	/** Returns the [EditText] child added by the user of this component, if any. */
	fun getEditText(): EditText? = mEditText

	// =======================================================================
	// Header
	// =======================================================================
	// A title-like label shown above the whole field (outside the outlined
	// box), for cases like grouping several fields under one heading.
	//
	// NOTE: this module renders into `viewBinding.header`, which must exist
	// in `component_material_text_input_layout.xml` as a MaterialTextView
	// (or plain TextView) with `android:id="@+id/header"`. It isn't part of
	// the layout shown in this conversation, so add it there if it's
	// missing before building.

	/** Sets the header text (a title-like label shown above the field). */
	fun setHeaderText(text: String?) {
		mHeaderText = text
		applyHeader()
	}

	/** Returns the current header text. */
	fun getHeaderText(): String? = mHeaderText

	/** Shows or hides the header. */
	fun setHeaderEnabled(enabled: Boolean) {
		mHeaderEnable = enabled
		applyHeader()
	}

	/** Returns whether the header is visible. */
	fun isHeaderEnabled(): Boolean = mHeaderEnable

	/** Sets the text color used for the header. */
	fun setHeaderTextColor(color: ColorStateList?) {
		mHeaderTextColor = color
		applyHeader()
	}

	/** Returns the text color used for the header. */
	fun getHeaderTextColor(): ColorStateList? = mHeaderTextColor

	/** Sets the header text size, in pixels. Prefer [setHeaderTextSizeSp] when working with `sp` values. */
	fun setHeaderTextSize(sizePx: Float) {
		mHeaderTextSize = sizePx
		applyHeader()
	}

	/** Sets the header text size in `sp`, converting it to pixels using the current display metrics. */
	fun setHeaderTextSizeSp(sizeSp: Float) {
		setHeaderTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sizeSp, resources.displayMetrics))
	}

	/** Returns the header text size, in pixels. */
	fun getHeaderTextSize(): Float = mHeaderTextSize

	/**
	 * Refreshes only the header text view (text, color, size, visibility).
	 *
	 * Accessibility: marks it as an [ViewCompat.setAccessibilityHeading]
	 * heading, so TalkBack lets users jump directly to it while navigating
	 * by headings, the same way it would for a section title.
	 */
	private fun applyHeader() {
		val header = viewBinding.header
		header.text = mHeaderText
		mHeaderTextColor?.let { header.setTextColor(it) }
		if (mHeaderTextSize > 0f) header.setTextSize(TypedValue.COMPLEX_UNIT_PX, mHeaderTextSize)
		header.visibility = if (mHeaderEnable) VISIBLE else GONE
		ViewCompat.setAccessibilityHeading(header, mHeaderEnable)
	}

	/**
	 * Sets the static prefix text, shown when [getPrefixTextList] has 0 or 1 entries.
	 *
	 * @throws IllegalStateException if a prefix text list with one or more
	 *   options is currently active (set via [setPrefixTextList]). A static
	 *   text and a dropdown list are mutually exclusive: call
	 *   `setPrefixTextList()` with no arguments, or [clearPrefixSelection],
	 *   before setting a static value.
	 */
	fun setPrefixText(text: String?) {
		check(mPrefixTextList.isEmpty()) {
			"Cannot call setPrefixText() while a prefix text list is active " +
					"(${mPrefixTextList.size} option(s) set via setPrefixTextList()). " +
					"Call setPrefixTextList() with no arguments to clear it first."
		}
		mPrefixText = text
		applyPrefix()
	}

	/** Returns the current static prefix text. */
	fun getPrefixText(): String? = mPrefixText

	/** Shows or hides the prefix text. */
	fun setPrefixEnabled(enabled: Boolean) {
		mPrefixEnable = enabled
		applyPrefix()
	}

	/** Returns whether the prefix text is visible. */
	fun isPrefixEnabled(): Boolean = mPrefixEnable

	/** Sets the text color used for the prefix. */
	fun setPrefixTextColor(color: ColorStateList?) {
		mPrefixTextColor = color
		applyPrefix()
	}

	/** Returns the text color used for the prefix. */
	fun getPrefixTextColor(): ColorStateList? = mPrefixTextColor

	/**
	 * Sets a list of selectable **prefix** options shown as a popup menu
	 * (e.g. country codes). The first entry is displayed by default; tapping
	 * the prefix opens the popup when there's more than one option.
	 *
	 * @param texts options to show, in order.
	 */
	fun setPrefixTextList(vararg texts: String) {
		mPrefixTextList = texts.toList()
		applyPrefix()
	}

	/**
	 * Sets a list of selectable **prefix** options shown as a popup menu
	 * (e.g. country codes). The first entry is displayed by default; tapping
	 * the prefix opens the popup when there's more than one option.
	 *
	 * @param texts options to show, in order.
	 */
	fun setPrefixTextList(texts: List<String>) {
		mPrefixTextList = texts
		applyPrefix()
	}

	/** Returns the current list of selectable prefix options. */
	fun getPrefixTextList(): List<String> = mPrefixTextList

	/**
	 * Registers a listener for the full select/reselect/unselect lifecycle
	 * of the prefix dropdown (see [setPrefixTextList]). Pass `null` to remove it.
	 */
	fun setPrefixOnItemActionListener(listener: OnItemActionListener?) {
		mPrefixItemActionListener = listener
	}

	/**
	 * Registers a listener that fires only when the prefix selection
	 * actually changes to a different option (see [OnItemClickChangedListener]).
	 * Pass `null` to remove it.
	 */
	fun setPrefixOnItemClickChangedListener(listener: OnItemClickChangedListener?) {
		mPrefixItemClickChangedListener = listener
	}

	/**
	 * Clears the current prefix selection, falling back to no text. If an
	 * option was selected, [OnItemActionListener.onItemUnselected] fires
	 * with that option.
	 *
	 * This only clears the *selected value* — the dropdown options set via
	 * [setPrefixTextList] are left untouched, so the popup still offers the
	 * same choices next time it's opened.
	 */
	fun clearPrefixSelection() {
		val previous = mPrefixText
		mPrefixText = null
		applyPrefix()
		if (previous != null) mPrefixItemActionListener?.onItemUnselected(previous)
	}

	/**
	 * Refreshes only the prefix [MaterialTextView] (text, color, visibility, popup).
	 *
	 * Accessibility: while the dropdown is active (more than one option),
	 * this is given a stable [View.setContentDescription] ("Prefix"), a
	 * [ViewCompat.setStateDescription] reflecting the current selection (so
	 * TalkBack announces e.g. "Prefix, +52" instead of just "+52"), and a
	 * polite [View.setAccessibilityLiveRegion] so re-selecting an option is
	 * announced automatically.
	 */
	private fun applyPrefix() {
		val textView = viewBinding.prefix
		val options = mPrefixTextList
		val current = if (options.isNotEmpty()) options[0] else mPrefixText

		textView.text = current
		mPrefixTextColor?.let { textView.setTextColor(it) }
		textView.visibility = if (mPrefixEnable) VISIBLE else GONE

		if (options.size > 1) {
			textView.contentDescription = "Prefix"
			textView.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
			ViewCompat.setStateDescription(textView, current)

			textView.setOnClickListener {
				showOptionsPopup(textView, options) { selected ->
					val previous = mPrefixText
					textView.text = selected
					mPrefixText = selected
					ViewCompat.setStateDescription(textView, selected)

					if (selected == previous) {
						mPrefixItemActionListener?.onItemReselected(selected)
					} else {
						mPrefixItemActionListener?.onItemSelected(selected)
						if (previous != null) mPrefixItemClickChangedListener?.onItemClickChanged(previous, selected)
					}
				}
			}
		} else {
			textView.contentDescription = null
			textView.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_NONE
			ViewCompat.setStateDescription(textView, null)
			textView.setOnClickListener(null)
			textView.isClickable = false
		}
	}

	/**
	 * Sets the static suffix text, shown when [getSuffixTextList] has 0 or 1 entries.
	 *
	 * @throws IllegalStateException if a suffix text list with one or more
	 *   options is currently active (set via [setSuffixTextList]). A static
	 *   text and a dropdown list are mutually exclusive: call
	 *   `setSuffixTextList()` with no arguments, or [clearSuffixSelection],
	 *   before setting a static value.
	 */
	fun setSuffixText(text: String?) {
		check(mSuffixTextList.isEmpty()) {
			"Cannot call setSuffixText() while a suffix text list is active " +
					"(${mSuffixTextList.size} option(s) set via setSuffixTextList()). " +
					"Call setSuffixTextList() with no arguments to clear it first."
		}
		mSuffixText = text
		applySuffix()
	}

	/** Returns the current static suffix text. */
	fun getSuffixText(): String? = mSuffixText

	/** Shows or hides the suffix text. */
	fun setSuffixEnabled(enabled: Boolean) {
		mSuffixEnable = enabled
		applySuffix()
	}

	/** Returns whether the suffix text is visible. */
	fun isSuffixEnabled(): Boolean = mSuffixEnable

	/** Sets the text color used for the suffix. */
	fun setSuffixTextColor(color: ColorStateList?) {
		mSuffixTextColor = color
		applySuffix()
	}

	/** Returns the text color used for the suffix. */
	fun getSuffixTextColor(): ColorStateList? = mSuffixTextColor

	/**
	 * Sets a list of selectable **suffix** options shown as a popup menu
	 * (e.g. units of measurement). The first entry is displayed by default;
	 * tapping the suffix opens the popup when there's more than one option.
	 *
	 * @param texts options to show, in order.
	 */
	fun setSuffixTextList(vararg texts: String) {
		mSuffixTextList = texts.toList()
		applySuffix()
	}

	/** Returns the current list of selectable suffix options. */
	fun getSuffixTextList(): List<String> = mSuffixTextList

	/**
	 * Registers a listener for the full select/reselect/unselect lifecycle
	 * of the suffix dropdown (see [setSuffixTextList]). Pass `null` to remove it.
	 */
	fun setSuffixOnItemActionListener(listener: OnItemActionListener?) {
		mSuffixItemActionListener = listener
	}

	/**
	 * Registers a listener that fires only when the suffix selection
	 * actually changes to a different option (see [OnItemClickChangedListener]).
	 * Pass `null` to remove it.
	 */
	fun setSuffixOnItemClickChangedListener(listener: OnItemClickChangedListener?) {
		mSuffixItemClickChangedListener = listener
	}

	/**
	 * Clears the current suffix selection, falling back to no text. If an
	 * option was selected, [OnItemActionListener.onItemUnselected] fires
	 * with that option.
	 *
	 * This only clears the *selected value* — the dropdown options set via
	 * [setSuffixTextList] are left untouched, so the popup still offers the
	 * same choices next time it's opened.
	 */
	fun clearSuffixSelection() {
		val previous = mSuffixText
		mSuffixText = null
		applySuffix()
		if (previous != null) mSuffixItemActionListener?.onItemUnselected(previous)
	}

	/**
	 * Refreshes only the suffix [MaterialTextView] (text, color, visibility, popup).
	 *
	 * Accessibility: mirrors [applyPrefix] — content description ("Suffix"),
	 * a live [ViewCompat.setStateDescription] for the current selection, and
	 * a polite live region while the dropdown is active.
	 */
	private fun applySuffix() {
		val textView = viewBinding.suffix
		val options = mSuffixTextList
		val current = if (options.isNotEmpty()) options[0] else mSuffixText

		textView.text = current
		mSuffixTextColor?.let { textView.setTextColor(it) }
		textView.visibility = if (mSuffixEnable) VISIBLE else GONE

		if (options.size > 1) {
			textView.contentDescription = "Suffix"
			textView.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
			ViewCompat.setStateDescription(textView, current)

			textView.setOnClickListener {
				showOptionsPopup(textView, options) { selected ->
					val previous = mSuffixText
					textView.text = selected
					mSuffixText = selected
					ViewCompat.setStateDescription(textView, selected)

					if (selected == previous) {
						mSuffixItemActionListener?.onItemReselected(selected)
					} else {
						mSuffixItemActionListener?.onItemSelected(selected)
						if (previous != null) mSuffixItemClickChangedListener?.onItemClickChanged(previous, selected)
					}
				}
			}
		} else {
			textView.contentDescription = null
			textView.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_NONE
			ViewCompat.setStateDescription(textView, null)
			textView.setOnClickListener(null)
			textView.isClickable = false
		}
	}

	/**
	 * Shared popup-menu implementation used by both [applyPrefix] and
	 * [applySuffix] so the dropdown behavior lives in a single place.
	 *
	 * @param anchor view the popup is anchored to.
	 * @param options entries shown in the popup.
	 * @param onSelected called with the chosen entry once the user taps it.
	 */
	private fun showOptionsPopup(
		anchor: View,
		options: List<String>,
		onSelected: (String) -> Unit
	) {
		val context = this.context
		val popup = PopupWindow(context)

		val listView = ListView(context).apply {
			adapter = ArrayAdapter(
				context,
				android.R.layout.simple_list_item_1,
				options
			)
			dividerHeight = 0
		}

		val maxItems = 5

		val adapter = listView.adapter
		var height = 0

		for (i in 0 until minOf(adapter.count, maxItems)) {
			val item = adapter.getView(i, null, listView)

			item.measure(
				MeasureSpec.makeMeasureSpec(
					150.dp,
					MeasureSpec.EXACTLY
				),
				MeasureSpec.makeMeasureSpec(
					0,
					MeasureSpec.UNSPECIFIED
				)
			)

			height += item.measuredHeight
		}

		popup.contentView = listView
		popup.width = 150.dp
		popup.height = height
		popup.isFocusable = true

		popup.setBackgroundDrawable(
			ContextCompat.getDrawable(
				context,
				R.drawable.bg_popup_background
			)
		)

		listView.setOnItemClickListener { _, _, position, _ ->
			onSelected(options[position])
			popup.dismiss()
		}

		popup.showAsDropDown(anchor)
	}

	/** Sets the hint / floating label text, forwarded to the inner [EditText]. */
	fun setHintText(text: String?) {
		mHintText = text
		applyHint()
	}

	/** Returns the current hint text. */
	fun getHintText(): String? = mHintText

	/** Shows or hides the hint on the inner [EditText]. */
	fun setHintEnabled(enabled: Boolean) {
		mHintEnable = enabled
		applyHint()
	}

	/** Returns whether the hint is enabled. */
	fun isHintEnabled(): Boolean = mHintEnable

	/** Sets the hint text color, forwarded to the inner [EditText]. */
	fun setHintTextColor(color: ColorStateList?) {
		mHintTextColor = color
		applyHint()
	}

	/** Returns the hint text color. */
	fun getHintTextColor(): ColorStateList? = mHintTextColor

	/**
	 * Refreshes the M3-style floating label: text, color and whether it's
	 * shown at all. No-ops until an [EditText] child has been added via
	 * [addView], since the label is positioned relative to it.
	 *
	 * The actual float-up/float-down motion is handled separately by
	 * [updateFloatingHintLabel], which reacts to focus and text changes.
	 */
	private fun applyHint() {
		val target = mEditText ?: return
		val label = mFloatingHintLabel ?: return

		label.text = mHintText
		mHintTextColor?.let { label.setTextColor(it) }
		updateFloatingHintLabel(target, animate = false)
	}

	/**
	 * Creates the floating label used for the M3 hint animation and adds it
	 * to [viewBinding.container], constrained to overlap the [EditText]
	 * when resting and to sit above it once floated. Called once from
	 * [addView].
	 *
	 * This label is created programmatically (rather than declared in XML)
	 * so the animation works without requiring any change to the existing
	 * layout file.
	 *
	 * Accessibility: the label is marked as [View.setLabelFor] the
	 * [EditText], so TalkBack reads its text as that field's accessible
	 * label — the same role `android:hint` would normally play. It's also
	 * marked [View.IMPORTANT_FOR_ACCESSIBILITY_NO] so TalkBack doesn't also
	 * announce it a second time as an unrelated, standalone text view when
	 * swiping through the field.
	 */
	private fun setupFloatingHintLabel(target: EditText) {
		val label = MaterialTextView(context).apply {
			id = View.generateViewId()
			text = mHintText
			mHintTextColor?.let { setTextColor(it) }
			setTextSize(TypedValue.COMPLEX_UNIT_PX, target.textSize)
			isClickable = false
			isFocusable = false
			labelFor = target.id
			importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
			setBackgroundColor((context as ContextThemeWrapper).getAttributeToColor(com.google.android.material.R.attr.colorSurface))
		}

		viewBinding.container.addView(label)
		label.layoutParams = ConstraintLayout.LayoutParams(
			ConstraintLayout.LayoutParams.WRAP_CONTENT,
			ConstraintLayout.LayoutParams.WRAP_CONTENT
		).apply {
			startToStart = target.id
			topToTop = target.id
			bottomToBottom = target.id
		}

		mFloatingHintLabel = label
		updateFloatingHintLabel(target, animate = false)

		// Re-sync the resting/floated position on every layout pass — not
		// just the first one — so it never drifts after a rotation or a
		// keyboard open/close, both of which resize `target` in place
		// without going through setupFloatingHintLabel() again. A one-shot
		// `target.post { ... }` (the previous approach) only fixed the very
		// first frame and then silently went stale.
		val listener = View.OnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
			if (bottom - top != oldBottom - oldTop) {
				updateFloatingHintLabel(target, animate = false)
			}
		}
		mHintLayoutListener = listener
		target.addOnLayoutChangeListener(listener)
	}

	/**
	 * Applies the M3 "float up" motion: while [target] is focused or has
	 * text, the label scales down and moves above the field; otherwise it
	 * rests directly over the (empty, unfocused) field, exactly like a
	 * normal hint. Also fades the label in/out based on [mHintEnable].
	 *
	 * Driven by a single reusable [ValueAnimator] ([mHintAnimator]) instead
	 * of a fire-and-forget [View.animate] call: any in-flight run is
	 * canceled before starting a new one (so rapid focus changes don't pile
	 * up competing animations), and [onDetachedFromWindow] cancels it too,
	 * so nothing keeps animating — or holding a reference to this view's
	 * [Context] — after the field leaves the screen.
	 *
	 * @param animate `false` on first layout to avoid an unwanted entrance
	 *   animation, and on every re-sync triggered by [mHintLayoutListener];
	 *   `true` for real focus/text changes.
	 */
	private fun updateFloatingHintLabel(target: EditText, animate: Boolean) {
		val label = mFloatingHintLabel ?: return

		val isFloating = target.isFocused || !target.text.isNullOrEmpty()
		val targetScale = if (isFloating) HINT_FLOATING_SCALE else 1f
		val targetTranslationY = if (isFloating) -(target.height / 2f) else 0f
		val targetAlpha = if (mHintEnable) 1f else 0f

		mHintAnimator?.cancel()

		if (!animate) {
			label.scaleX = targetScale
			label.scaleY = targetScale
			label.translationY = targetTranslationY
			label.alpha = targetAlpha
			return
		}

		val startScale = label.scaleX
		val startTranslationY = label.translationY
		val startAlpha = label.alpha

		mHintAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
			duration = HINT_ANIMATION_DURATION_MS
			interpolator = AccelerateDecelerateInterpolator()
			addUpdateListener { animator ->
				val fraction = animator.animatedValue as Float
				label.scaleX = startScale + fraction * (targetScale - startScale)
				label.scaleY = label.scaleX
				label.translationY = startTranslationY + fraction * (targetTranslationY - startTranslationY)
				label.alpha = startAlpha + fraction * (targetAlpha - startAlpha)
			}
			start()
		}
	}

	/** Shows or hides the character counter. */
	fun setCounterEnabled(enabled: Boolean) {
		mCounterEnable = enabled
		applyCounterAppearance()
	}

	/** Returns whether the character counter is visible. */
	fun isCounterEnabled(): Boolean = mCounterEnable

	/** Sets the text color used for the character counter. */
	fun setCounterTextColor(color: ColorStateList?) {
		mCounterTextColor = color
		applyCounterAppearance()
	}

	/** Returns the text color used for the character counter. */
	fun getCounterTextColor(): ColorStateList? = mCounterTextColor

	/** Sets the maximum character count. Pass `-1` for "no limit". */
	fun setCounterMaxLength(limit: Int) {
		mMaxLimit = limit
		refreshCounterText()
	}

	/** Returns the current maximum character count (`-1` = no limit). */
	fun getCounterMaxLength(): Int = mMaxLimit

	/**
	 * If `true`, text exceeding [getCounterMaxLength] is trimmed
	 * automatically. If `false`, exceeding the limit instead triggers the
	 * error state.
	 */
	fun setCounterFreezeOnLimit(freeze: Boolean) {
		mOutLimitTextLimit = freeze
	}

	/** Returns whether exceeding the limit trims the text instead of raising an error. */
	fun isCounterFreezeOnLimit(): Boolean = mOutLimitTextLimit

	/** Refreshes only the counter's visibility and color (not its text). */
	private fun applyCounterAppearance() {
		mCounterTextColor?.let { viewBinding.counter.setTextColor(it) }
		viewBinding.counter.visibility = if (mCounterEnable) VISIBLE else GONE
	}

	/**
	 * Refreshes only the counter's `count/limit` text based on the current
	 * [EditText] content. No-ops until an [EditText] child has been added.
	 */
	private fun refreshCounterText() {
		val target = mEditText ?: return
		if (mMaxLimit == -1) {
			viewBinding.counter.text = ""
			return
		}
		val count = target.text?.length ?: 0
		viewBinding.counter.text = "${minOf(count, mMaxLimit)}/${mMaxLimit}"
	}

	/**
	 * Attaches the text watcher that drives the character counter and the
	 * max-length behavior. Called once from [addView], since it needs the
	 * [EditText] child to exist. Reads [mMaxLimit] and [mOutLimitTextLimit]
	 * dynamically on every keystroke, so changing those later (via
	 * [setCounterMaxLength] / [setCounterFreezeOnLimit]) is picked up
	 * automatically without re-attaching anything.
	 */
	@SuppressLint("SetTextI18n")
	private fun setupCounterTextWatcher(target: EditText) {
		target.doOnTextChanged { text, _, _, count ->
			val limit = mMaxLimit
			if (limit != -1 && count > limit) {
				if (mOutLimitTextLimit) {
					val trimmed = text?.subSequence(0, limit)
					target.setText(trimmed)
					target.setSelection(limit)
				} else {
					setErrorEnabled(true)
				}
			} else {
				setErrorEnabled(false)
			}
			refreshCounterText()
		}
	}

	/** Sets the supporting/helper text shown below the field when there's no error. */
	fun setHelperText(text: String?) {
		mHelperText = text
		if (!mInErrorState) error(false)
	}

	/** Returns the current helper text. */
	fun getHelperText(): String? = mHelperText

	/** Shows or hides the helper text. */
	fun setHelperEnabled(enabled: Boolean) {
		mHelperEnable = enabled
		if (!mInErrorState) error(false)
	}

	/** Returns whether the helper text is enabled. */
	fun isHelperEnabled(): Boolean = mHelperEnable

	/** Sets the text color used for the helper text. */
	fun setHelperTextColor(color: ColorStateList?) {
		mHelperTextColor = color
		if (!mInErrorState) error(false)
	}

	/** Returns the text color used for the helper text. */
	fun getHelperTextColor(): ColorStateList? = mHelperTextColor

	/** Sets the message shown instead of the helper text while in an error state. */
	fun setErrorText(text: String) {
		mErrorText = text
		if (mInErrorState) error(true)
	}

	/** Returns the current error message. */
	fun getErrorText(): String = mErrorText

	/** Sets the color applied to borders/icons/text while in an error state. */
	fun setErrorTextColor(color: ColorStateList?) {
		mErrorTextColor = color
		if (mInErrorState) error(true)
	}

	/** Returns the color applied while in an error state. */
	fun getErrorTextColor(): ColorStateList? = mErrorTextColor

	/**
	 * Switches the field's **error state**, the public equivalent of
	 * `TextInputLayout.setError(...)` / `setErrorEnabled(...)`. Pass `true`
	 * to show the error state (message = [getErrorText]) and `false` to go
	 * back to the normal helper-text state.
	 *
	 * TalkBack is notified of the transition so it re-announces the field:
	 * see [installAccessibilityDelegate], which exposes [getErrorText] on
	 * the [EditText]'s accessibility node while this is `true`.
	 */
	fun setErrorEnabled(enabled: Boolean) {
		mInErrorState = enabled
		error(enabled)
		mEditText?.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
	}

	/** Returns whether the field is currently showing the error state. */
	fun isErrorEnabled(): Boolean = mInErrorState

	/**
	 * Refreshes the message row (helper/error text), the field border and
	 * the prefix/suffix/icon colors, following M3: while [bool] is `true`,
	 * the supporting text is replaced by [mErrorText] and colors switch to
	 * the theme's `colorError`; while `false`, the normal helper/prefix/
	 * suffix/icon colors are restored.
	 *
	 * The border itself is switched by [setBorderErrorState] purely through
	 * the `isActivated` flag — no drawable resource is looked up here (see
	 * [installBorderSelector]).
	 */
	private fun error(bool: Boolean) {
		setBorderErrorState(bool)

		if (bool) {
			applyDividerColor(mColorOnError)
			viewBinding.message.text = mErrorText
			viewBinding.message.setTextColor(mColorError)
			viewBinding.counter.setTextColor(mColorError)
			viewBinding.prefix.setTextColor(mColorError)
			viewBinding.suffix.setTextColor(mColorError)
			viewBinding.start.foregroundTintList = ColorStateList.valueOf(mColorError)
			viewBinding.end.foregroundTintList = ColorStateList.valueOf(mColorError)
		} else {
			val focused = mEditText?.isFocused == true
			applyDividerColor(if (focused) mColorPrimary else mColorOutline)
			viewBinding.message.text = mHelperText
			mHelperTextColor?.let { viewBinding.message.setTextColor(it) }
			mCounterTextColor?.let { viewBinding.counter.setTextColor(it) }
			mPrefixTextColor?.let { viewBinding.prefix.setTextColor(it) }
			mSuffixTextColor?.let { viewBinding.suffix.setTextColor(it) }
			viewBinding.start.foregroundTintList = mStartIconTint
			viewBinding.end.foregroundTintList = mEndIconTint
		}
	}

	/** Shows or hides the leading (start) icon. */
	fun setStartIconEnabled(enabled: Boolean) {
		mStartIconEnable = enabled
		applyStartIcon()
	}

	/** Returns whether the leading icon is visible. */
	fun isStartIconEnabled(): Boolean = mStartIconEnable

	/** Sets the drawable used for the leading (start) icon. */
	fun setStartIconDrawable(icon: Drawable?) {
		mStartIcon = icon
		applyStartIcon()
	}

	/** Returns the drawable used for the leading icon. */
	fun getStartIconDrawable(): Drawable? = mStartIcon

	/** Sets the tint applied to the leading (start) icon. */
	fun setStartIconDrawableTint(tint: ColorStateList?) {
		mStartIconTint = tint
		applyStartIcon()
	}

	/** Returns the tint applied to the leading icon. */
	fun getStartIconDrawableTint(): ColorStateList? = mStartIconTint

	/** Shows or hides the leading-icon container (background/ripple area). */
	fun setStartLayoutEnabled(enabled: Boolean) {
		mStartLayoutEnable = enabled
		applyStartLayout()
	}

	/** Returns whether the leading-icon container is visible. */
	fun isStartLayoutEnabled(): Boolean = mStartLayoutEnable

	/** Refreshes only the leading icon's drawable, tint and visibility. */
	private fun applyStartIcon() {
		viewBinding.imageStart.visibility = if (mStartIconEnable) VISIBLE else GONE
		viewBinding.imageStart.setImageDrawable(mStartIcon)
		mStartIconTint?.let { viewBinding.imageStart.imageTintList = it }
	}

	/** Refreshes only the leading-icon container's visibility. */
	private fun applyStartLayout() {
		viewBinding.start.visibility = if (mStartLayoutEnable) VISIBLE else GONE
	}

	/** Shows or hides the trailing (end) icon. */
	fun setEndIconEnabled(enabled: Boolean) {
		mEndIconEnable = enabled
		applyEndIcon()
	}

	/** Returns whether the trailing icon is visible. */
	fun isEndIconEnabled(): Boolean = mEndIconEnable

	/** Sets the drawable used for the trailing (end) icon. */
	fun setEndIconDrawable(icon: Drawable?) {
		mEndIcon = icon
		applyEndIcon()
	}

	/** Returns the drawable used for the trailing icon. */
	fun getEndIconDrawable(): Drawable? = mEndIcon

	/** Sets the tint applied to the trailing (end) icon. */
	fun setEndIconDrawableTint(tint: ColorStateList?) {
		mEndIconTint = tint
		applyEndIcon()
	}

	/** Returns the tint applied to the trailing icon. */
	fun getEndIconDrawableTint(): ColorStateList? = mEndIconTint

	/** Shows or hides the trailing-icon container (background/ripple area). */
	fun setEndLayoutEnabled(enabled: Boolean) {
		mEndLayoutEnable = enabled
		applyEndLayout()
	}

	/** Returns whether the trailing-icon container is visible. */
	fun isEndLayoutEnabled(): Boolean = mEndLayoutEnable

	/** Refreshes only the trailing icon's drawable, tint and visibility. */
	private fun applyEndIcon() {
		viewBinding.imageEnd.visibility = if (mEndIconEnable) VISIBLE else GONE
		viewBinding.imageEnd.setImageDrawable(mEndIcon)
		mEndIconTint?.let { viewBinding.imageEnd.imageTintList = it }
	}

	/** Refreshes only the trailing-icon container's visibility. */
	private fun applyEndLayout() {
		viewBinding.end.visibility = if (mEndLayoutEnable) VISIBLE else GONE
	}

	// wired directly to the target view instead of going through an apply*()
	// module.

	/**
	 * Sets the click listener for the **leading icon image** ([ImageView]
	 * inside `start`), analogous to `TextInputLayout.setStartIconOnClickListener`.
	 *
	 * @param click listener to invoke on click, or `null` to remove it (this
	 *   also makes the icon non-clickable again).
	 */
	fun setStartImageOnClickListener(click: View.OnClickListener?) {
		viewBinding.imageStart.setOnClickListener(click)
		viewBinding.imageStart.isClickable = click != null
	}

	/**
	 * Sets the click listener for the **trailing icon image** ([ImageView]
	 * inside `end`), analogous to `TextInputLayout.setEndIconOnClickListener`.
	 *
	 * @param click listener to invoke on click, or `null` to remove it (this
	 *   also makes the icon non-clickable again).
	 */
	fun setEndImageOnClickListener(click: View.OnClickListener?) {
		viewBinding.imageEnd.setOnClickListener(click)
		viewBinding.imageEnd.isClickable = click != null
	}

	/**
	 * Sets the click listener for the **leading-icon container** (`start`),
	 * i.e. the whole background/ripple area around the leading icon rather
	 * than just the icon's own bounds. Useful for enlarging the tap target.
	 *
	 * @param click listener to invoke on click, or `null` to remove it.
	 */
	fun setStartLayoutOnClickListener(click: View.OnClickListener?) {
		viewBinding.start.setOnClickListener(click)
		viewBinding.start.isClickable = click != null
	}

	/**
	 * Sets the click listener for the **trailing-icon container** (`end`),
	 * i.e. the whole background/ripple area around the trailing icon rather
	 * than just the icon's own bounds. Useful for enlarging the tap target.
	 *
	 * @param click listener to invoke on click, or `null` to remove it.
	 */
	fun setEndLayoutOnClickListener(click: View.OnClickListener?) {
		viewBinding.end.setOnClickListener(click)
		viewBinding.end.isClickable = click != null
	}

	/**
	 * Convenience accessor for the inner [EditText]'s current text.
	 * Returns an empty string if no [EditText] child has been added yet.
	 */
	fun getText(): String = mEditText?.text.toString()

	/**
	 * Installs the border **state-list drawable** on `start`/`container`/`end`
	 * a single time, replacing the old approach of calling
	 * `setBackgroundResource()` with a different drawable resource on every
	 * focus/error/enabled change.
	 *
	 * From this point on, the visible border is switched **by the platform**,
	 * based on three plain [View] booleans:
	 * - `isActivated` → error state (checked first, takes priority)
	 * - `isEnabled` → disabled state
	 * - `isSelected` → focused state
	 *
	 * `ef_selector_component_material_text_input_layout_outline.xml` must exist under
	 * `res/drawable` with states in that priority order:
	 * ```xml
	 * <selector xmlns:android="http://schemas.android.com/apk/res/android">
	 *     <item android:state_activated="true" android:drawable="@drawable/ef_component_material_text_input_layout_outline_error" />
	 *     <item android:state_enabled="false" android:drawable="@drawable/ef_component_material_text_input_layout_outline_disabled" />
	 *     <item android:state_selected="true" android:drawable="@drawable/ef_component_material_text_input_layout_outline_focused" />
	 *     <item android:drawable="@drawable/ef_component_material_text_input_layout_outline" />
	 * </selector>
	 * ```
	 * (Provided alongside this file — add it to `res/drawable` before building.)
	 */
	private fun installBorderSelector() {
		val selector = R.drawable.ef_selector_component_material_text_input_layout_outline
		viewBinding.start.setBackgroundResource(selector)
		viewBinding.container.setBackgroundResource(selector)
		viewBinding.end.setBackgroundResource(selector)
	}

	/**
	 * Toggles the `isActivated` (error) flag on `start`/`container`/`end`,
	 * letting the state-list drawable installed by [installBorderSelector]
	 * pick the right sub-drawable natively — no drawable resource lookup runs here.
	 */
	private fun setBorderErrorState(error: Boolean) {
		viewBinding.start.isActivated = error
		viewBinding.container.isActivated = error
		viewBinding.end.isActivated = error
	}

	/**
	 * Toggles the `isSelected` (focused-look) flag on `start`/`container`/`end`.
	 * Ignored while in an error state, since error already takes visual
	 * priority in the selector.
	 */
	private fun setBorderFocusedState(focused: Boolean) {
		viewBinding.start.isSelected = focused
		viewBinding.container.isSelected = focused
		viewBinding.end.isSelected = focused
	}

	/** Applies a precomputed [color] to the divider — a flat color set, not a resource lookup. */
	private fun applyDividerColor(color: ColorStateList) {
		viewBinding.divider.setDividerColor(color)
	}

	/**
	 * Intercepts children declared in XML to allow **only a single
	 * [EditText]** (or subclass, such as [AutoCompleteTextView]), applying
	 * margins, focus listeners and, if applicable, dropdown behavior to it.
	 *
	 * @throws RuntimeException if the child isn't an [EditText], or if an
	 *   [EditText] child has already been added (this container only
	 *   supports one, just like `TextInputLayout`).
	 */
	override fun addView(child: View, index: Int, params: ViewGroup.LayoutParams?) {
		if (inflating) {
			super.addView(child, index, params)
			return
		}

		if (child !is EditText)
			throw RuntimeException("You can set only EditText, AutoCompleteTextView or MultiAutoCompleteTextView as child of ComponentMaterialTextInputLayout")

		if (mEditText != null)
			throw RuntimeException("ComponentMaterialTextInputLayout only supports a single EditText child")

		mEditText = child

		// The floating label (setupFloatingHintLabel) and this view's own
		// layoutParams below constrain against `child.id`. If the caller's
		// XML didn't declare android:id on the EditText, child.id is
		// View.NO_ID (-1), which ConstraintLayout silently treats as "no
		// constraint" — the label then had no valid vertical anchor and
		// floated to the top of the container instead of centering on the
		// field. Guarantee a real id here regardless of what the caller did.
		if (child.id == View.NO_ID) {
			child.id = View.generateViewId()
		}

		child.setBackgroundColor(context.getColor(android.R.color.transparent))

		if (child is AutoCompleteTextView) {
			viewBinding.divider.visibility = VISIBLE
			viewBinding.dropdown.visibility = VISIBLE

			viewBinding.dropdown.setOnClickListener {
				child.showDropDown()
			}
		}

		val height = params?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT

		child.layoutParams = LayoutParams(0, height).apply {
			startToEnd = viewBinding.prefix.id
			endToStart = viewBinding.suffix.id
			topToTop = viewBinding.container.id
			bottomToBottom = viewBinding.container.id

			marginEnd = 10.dp
			marginStart = 10.dp
		}

		child.onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
			// Border/divider color only reflects focus while not in an error
			// state — error() already restores the correct one otherwise.
			if (!mInErrorState) {
				setBorderFocusedState(hasFocus)
				applyDividerColor(if (hasFocus) mColorPrimary else mColorOutline)
			}
			updateFloatingHintLabel(child, animate = true)
		}

		viewBinding.container.addView(child, index, child.layoutParams)

		// These modules depend on the EditText, so they only run now that it exists.
		setupFloatingHintLabel(child)
		applyHint()
		setupCounterTextWatcher(child)
		refreshCounterText()
		installAccessibilityDelegate(child)
		child.doOnTextChanged { _, _, _, _ -> updateFloatingHintLabel(child, animate = true) }
	}

	/**
	 * Exposes the current error message on the [EditText]'s accessibility
	 * node (`AccessibilityNodeInfo.setError(...)`), so TalkBack announces it
	 * as an invalid-entry field the same way it would for a native
	 * `TextInputLayout` in an error state — instead of silently relying on
	 * the visual-only color change in [error].
	 */
	private fun installAccessibilityDelegate(target: EditText) {
		ViewCompat.setAccessibilityDelegate(target, object : AccessibilityDelegateCompat() {
			override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
				super.onInitializeAccessibilityNodeInfo(host, info)
				if (mInErrorState) info.error = mErrorText
			}
		})
	}

	/**
	 * Cancels the running hint animation and detaches the layout listener
	 * used by [setupFloatingHintLabel], so neither keeps a reference back
	 * into this view (and its [Context]) once it's removed from the window.
	 */
	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		mHintAnimator?.cancel()
		mHintAnimator = null
		mHintLayoutListener?.let { listener -> mEditText?.removeOnLayoutChangeListener(listener) }
	}

	override fun setEnabled(enabled: Boolean) {
		super.setEnabled(enabled)
		mEditText?.isEnabled = isEnabled
		viewBinding.container.isEnabled = isEnabled
		viewBinding.prefix.isEnabled = isEnabled
		viewBinding.suffix.isEnabled = isEnabled
		viewBinding.start.isEnabled = isEnabled
		viewBinding.end.isEnabled = isEnabled
		viewBinding.imageStart.isEnabled = isEnabled
		viewBinding.imageEnd.isEnabled = isEnabled

		// No setBackgroundResource() call needed: state_enabled="false" is
		// already part of the selector installed by installBorderSelector(),
		// and the isEnabled assignments above make the platform pick it up
		// automatically via refreshDrawableState().
		applyDividerColor(if (isEnabled) mColorOutline else mColorDisabledDivider)
	}
}