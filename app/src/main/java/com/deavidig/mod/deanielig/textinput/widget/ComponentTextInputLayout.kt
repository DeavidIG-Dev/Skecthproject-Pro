package com.deavidig.mod.deanielig.textinput.widget

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.ImageView
import android.widget.ListView
import android.widget.PopupWindow
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.appcompat.view.ContextThemeWrapper
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.withStyledAttributes
import androidx.core.graphics.ColorUtils
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.widget.doOnTextChanged
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.deavidig.sketchprojectpro.R
import com.deavidig.sketchprojectpro.databinding.ComponentTextInputLayoutBinding
import com.google.android.material.textview.MaterialTextView
import kotlin.math.min

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
 *   translated up) once focused or filled. Its horizontal alignment
 *   ([HintGravity]) and how far it may visually stretch over the prefix/
 *   suffix/icons ([HintStartAnchor], [HintEndAnchor]) are both configurable,
 *   independently per side.
 * - A **header**, a title-like label rendered above the whole field —
 *   useful to group several fields under one heading.
 * - Selection lifecycle listeners ([OnItemActionListener],
 *   [OnItemClickChangedListener]) for the prefix/suffix dropdown, covering
 *   select, reselect, unselect and "value actually changed" events.
 *
 * ### Basic usage (XML)
 * ```xml
 * <com.deavidig.sketchprojectpro.ComponentTextInputLayout
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
 * </com.deavidig.sketchprojectpro.ComponentTextInputLayout>
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
 * declared in `R.styleable.ComponentTextInputLayout`.
 * @param context source context; it is wrapped in a [ContextThemeWrapper]
 *   using the `Theme.MaterialComponents.DayNight` theme so that M3 resources
 *   (error color, etc.) resolve correctly even if the hosting
 *   Activity/Fragment uses a different theme.
 * @param attrs XML attributes for the component.
 *
 * @author DeanielIG, DeavidIG
 */
class ComponentTextInputLayout(
	context: Context,
	attrs: AttributeSet? = null
) : ConstraintLayout(
	ContextThemeWrapper(
		context,
		com.google.android.material.R.style.Theme_MaterialComponents_DayNight
	),
	attrs
) {

	private companion object {
		/** Duration of the floating-label float-up/float-down transition. */
		const val HINT_ANIMATION_DURATION_MS = 200L

		/** Scale applied to the floating label once it's "floated" (focused or filled). */
		const val HINT_FLOATING_SCALE = 0.75f

		/**
		 * Extra gap, in dp, kept between the floated label's own top edge and
		 * the outlined border above it — on top of the label's own height —
		 * so the border never crosses through the text. Also drives how much
		 * extra top margin [reserveSpaceForFloatingHint] adds.
		 */
		const val HINT_TOP_CLEARANCE_DP = 6

		/**
		 * How long the ghost placeholder takes to fade **in** once the field
		 * gains focus while empty — the moment it takes over the resting spot
		 * the hint deliberately leaves inactive (see [updateFloatingHintLabel]).
		 */
		const val PLACEHOLDER_APPEAR_FADE_DURATION_MS = 400L

		/**
		 * How long the ghost placeholder takes to fade out while what's typed
		 * still matches it — a deliberate, "melting away" pace.
		 */
		const val PLACEHOLDER_MATCH_FADE_DURATION_MS = 400L

		/**
		 * How long the ghost placeholder takes to fade out once what's typed
		 * no longer matches it — near-instant, so a stale suggestion never
		 * lingers over unrelated text.
		 */
		const val PLACEHOLDER_MISMATCH_FADE_DURATION_MS = 80L

		/** Alpha (0-255) applied to [mHintTextColor] as the placeholder's default color when none is set explicitly. */
		const val PLACEHOLDER_FALLBACK_ALPHA = 140
	}

	/**
	 * Defines the horizontal alignment of the floating hint label relative to
	 * the [EditText]'s bounds.
	 *
	 * Set this value using [setHintGravity] or the `hintGravity` XML attribute.
	 * [START] is used by default.
	 */
	enum class HintGravity {

		/** Aligns the floating hint label with the start of the [EditText]. */
		START,

		/** Centers the floating hint label within the [EditText]. */
		CENTER,

		/** Aligns the floating hint label with the end of the [EditText]. */
		END
	}

	/**
	 * Defines the view used as the start anchor for the floating hint label
	 * when the label extends beyond the [EditText]'s bounds.
	 *
	 * Set this value using [setHintStartAnchor] or the `hintStartAnchor` XML
	 * attribute. When unset (`auto`), the [EditText]'s start is used by default.
	 */
	enum class HintStartAnchor {

		/**
		 * Aligns with the start of the leading icon, covering the icon and
		 * prefix area.
		 */
		START_ICON,

		/**
		 * Aligns with the start of the leading container, covering both the
		 * icon and prefix areas.
		 */
		START_LAYOUT,

		/** Aligns with the start of the prefix text. */
		PREFIX
	}

	/**
	 * Defines the view used as the end anchor for the floating hint label
	 * when the label extends beyond the [EditText]'s bounds.
	 *
	 * Set this value using [setHintEndAnchor] or the `hintEndAnchor` XML
	 * attribute. When unset (`auto`), the [EditText]'s end is used by default.
	 */
	enum class HintEndAnchor {

		/**
		 * Aligns with the end of the trailing icon, covering the icon and
		 * suffix area.
		 */
		END_ICON,

		/**
		 * Aligns with the end of the trailing container, covering both the
		 * icon and suffix areas.
		 */
		END_LAYOUT,

		/** Aligns with the end of the suffix text. */
		SUFFIX
	}

	/**
	 * Defines the layout style used by the button.
	 */
	enum class ButtonLayoutStyle {

		/** Displays the button using a three-dot layout. */
		THREE_DOT,

		/** Displays the button using the normal layout. */
		NORMAL,

		/** Displays the button using the custom layout. */
		// CUSTOM
	}

	/**
	 * Holds a popup row's [itemView], the same idea as
	 * `RecyclerView.ViewHolder` — cache child-view lookups once per row
	 * instead of re-running `findViewById` on every bind. Implemented from
	 * scratch: this component has no dependency on the RecyclerView library.
	 *
	 * Subclass it to expose whatever child views a custom row layout needs:
	 * ```kotlin
	 * class CountryHolder(itemView: View) : ComponentTextInputLayout.PopupItemViewHolder(itemView) {
	 *     val flag: ImageView = itemView.findViewById(R.id.flag)
	 *     val label: TextView = itemView.findViewById(R.id.label)
	 * }
	 * ```
	 */
	abstract class PopupItemViewHolder(val itemView: View)

	/**
	 * Adapter contract for fully customizing how a prefix/suffix dropdown's
	 * rows are created and populated — modeled on `RecyclerView.Adapter`'s
	 * create/bind/count split, reimplemented independently (no RecyclerView
	 * dependency at all; internally it's bridged onto a plain [ListView] by
	 * [PopupItemAdapterBridge]).
	 *
	 * The adapter is expected to hold its own dataset (of *any* type, not
	 * just [String]) and look it up by `position` itself — [onBindViewHolder]
	 * doesn't hand you the row's data, exactly like real `RecyclerView.Adapter`.
	 * The one difference from the real thing: [onBindViewHolder] **returns**
	 * that row's text value, which the component uses to update the
	 * prefix/suffix box, accessibility descriptions, and the selection
	 * listeners — no separate method needed for that.
	 *
	 * ```kotlin
	 * class CountryAdapter(private val countries: List<Country>) :
	 *     ComponentTextInputLayout.PopupItemAdapter() {
	 *
	 *     override fun onCreateViewHolder(inflater: LayoutInflater, parent: ViewGroup): PopupItemViewHolder {
	 *         val view = inflater.inflate(R.layout.row_country, parent, false)
	 *         return CountryHolder(view)
	 *     }
	 *
	 *     override fun onBindViewHolder(holder: PopupItemViewHolder, position: Int): String {
	 *         holder as CountryHolder
	 *         val country = countries[position]
	 *         holder.flag.setImageResource(country.flagRes)
	 *         holder.label.text = country.name
	 *         return country.dialCode // what shows in the prefix/suffix box once picked
	 *     }
	 *
	 *     override fun getItemCount() = countries.size
	 *
	 *     fun addCountry(country: Country) {
	 *         countries.add(country)
	 *         notifyDataSetChanged() // refreshes an open popup, and the prefix/suffix box
	 *     }
	 * }
	 *
	 * textInputLayout.setPrefixItemAdapter(CountryAdapter(countryList))
	 * ```
	 *
	 * Register one via [setPrefixItemAdapter] / [setSuffixItemAdapter] for
	 * full control over the popup's look. [setPrefixTextList] /
	 * [setSuffixTextList] remain available as a shortcut that builds a
	 * plain-text [DefaultPopupItemAdapter] under the hood, for when full
	 * customization isn't needed.
	 */
	abstract class PopupItemAdapter<T : PopupItemViewHolder> {
		/** Inflates/builds a fresh row's view. Called only as often as new rows are actually needed. */
		abstract fun onCreateViewHolder(
			inflater: LayoutInflater,
			parent: ViewGroup
		): T

		/**
		 * Populates [holder] for [position] — look up your own data via
		 * `position`, exactly like `RecyclerView.Adapter` — and returns that
		 * row's text value, used for the prefix/suffix box's displayed text,
		 * accessibility descriptions, and the selection listeners.
		 */
		abstract fun onBindViewHolder(holder: T, position: Int): String

		/** Total number of selectable options. */
		abstract fun getItemCount(): Int

		/** Bridge currently displaying this adapter in an open popup, if any. Set by [showOptionsPopup]. */
		private var registeredBridge: PopupItemAdapterBridge? = null

		/** Set by [setPrefixItemAdapter] / [setSuffixItemAdapter] so [notifyDataSetChanged] can also refresh the box's displayed text, not just an open popup. */
		internal var onDataSetChanged: (() -> Unit)? = null

		/**
		 * Call after the backing dataset changes in any way — matching
		 * `RecyclerView.Adapter.notifyDataSetChanged()`. Refreshes an open
		 * popup immediately (if this adapter is currently shown in one) and
		 * the prefix/suffix box's displayed text right away, without
		 * needing the popup to be reopened.
		 */
		fun notifyDataSetChanged() {
			registeredBridge?.notifyDataSetChanged()
			onDataSetChanged?.invoke()
		}

		/**
		 * Convenience aliases for [notifyDataSetChanged], matching
		 * `RecyclerView.Adapter`'s naming for single-item changes. A plain
		 * [ListView]-backed popup has no notion of partial/animated updates
		 * the way `RecyclerView` does, so all three just trigger the same
		 * full refresh — they exist for a familiar, drop-in-compatible API.
		 */
		fun notifyItemChanged(position: Int) = notifyDataSetChanged()

		fun notifyItemInserted(position: Int) = notifyDataSetChanged()
		fun notifyItemRemoved(position: Int) = notifyDataSetChanged()

		internal fun attachBridge(bridge: PopupItemAdapterBridge) {
			registeredBridge = bridge
		}

		internal fun detachBridge() {
			registeredBridge = null
		}
	}

	/**
	 * The plain-text adapter built automatically by [setPrefixTextList] /
	 * [setSuffixTextList] when no custom [PopupItemAdapter] is supplied —
	 * a single [MaterialTextView] per row, matching the component's
	 * original look.
	 */
	private inner class DefaultPopupItemAdapter(private val items: List<String>) :
		PopupItemAdapter<PopupItemViewHolder>() {
		override fun onCreateViewHolder(
			inflater: LayoutInflater,
			parent: ViewGroup
		): PopupItemViewHolder {
			val textView = MaterialTextView(context).apply {
				setPadding(16.dp, 12.dp, 16.dp, 12.dp)
			}
			return object : PopupItemViewHolder(textView) {}
		}

		override fun onBindViewHolder(holder: PopupItemViewHolder, position: Int): String {
			val item = items[position]
			(holder.itemView as MaterialTextView).text = item
			return item
		}

		override fun getItemCount(): Int = items.size
	}

	/**
	 * Bridges a [PopupItemAdapter] onto the platform's [ListView] +
	 * [android.widget.BaseAdapter] — which is what the popup uses under the
	 * hood for scrolling/positioning — with basic view-recycling via
	 * `convertView.tag`, the same idea `RecyclerView` itself uses. This is
	 * the *only* place that touches [android.widget.BaseAdapter]; the
	 * developer-facing [PopupItemAdapter] API has no notion of it.
	 *
	 * Also caches each row's text value (the [PopupItemAdapter.onBindViewHolder]
	 * return) as it's bound, so [showOptionsPopup] can read back what was
	 * shown for a tapped position without binding it a second time.
	 * `notifyDataSetChanged()` is inherited directly from [android.widget.BaseAdapter]
	 * — it's what actually makes an open [ListView] re-query and re-render.
	 */
	internal class PopupItemAdapterBridge(
		private val inflater: LayoutInflater,
		private val adapter: PopupItemAdapter<PopupItemViewHolder>
	) : android.widget.BaseAdapter() {
		private val labelCache = HashMap<Int, String>()

		override fun getCount(): Int = adapter.getItemCount()
		override fun getItem(position: Int): String? = labelCache[position]
		override fun getItemId(position: Int): Long = position.toLong()

		override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
			val holder = (convertView?.tag as? PopupItemViewHolder)
				?: adapter.onCreateViewHolder(inflater, parent).also { it.itemView.tag = it }
			labelCache[position] = adapter.onBindViewHolder(holder, position)
			return holder.itemView
		}

		/** The text value bound for [position], if that row has been rendered at least once. */
		fun labelAt(position: Int): String? = labelCache[position]

		override fun notifyDataSetChanged() {
			labelCache.clear()
			super.notifyDataSetChanged()
		}
	}

	/** Binding for the private layout (container, prefix/suffix, icons, counter, etc.). */
	private val view_binding: ComponentTextInputLayoutBinding

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

	/**
	 * Backing field for the prefix dropdown's data + row rendering. `null`
	 * means "no dropdown, [mPrefixText] is shown as plain static text".
	 * Built automatically by [setPrefixTextList] (as a [DefaultPopupItemAdapter]),
	 * or supplied directly via [setPrefixItemAdapter] for full control over
	 * how each row looks.
	 */
	private var mPrefixItemAdapter: PopupItemAdapter<PopupItemViewHolder>? = null

	private var mSuffixText: String? = null
	private var mSuffixEnable: Boolean = false
	private var mSuffixTextColor: ColorStateList? = null

	/** Same idea as [mPrefixItemAdapter], for the suffix dropdown. */
	private var mSuffixItemAdapter: PopupItemAdapter<PopupItemViewHolder>? = null

	private var mHintText: String? = null
	private var mHintEnable: Boolean = false
	private var mHintTextColor: ColorStateList? = null

	/** Backing field for [setHintGravity]. Defaults to [HintGravity.START]. */
	private var mHintGravity: HintGravity = HintGravity.START

	/**
	 * Backing field for [setHintStartAnchor]. `null` (the default) means
	 * "match the [EditText]'s own start edge" — the typed text always
	 * begins right after the prefix, that isn't configurable. A non-null
	 * value lets the floating label's start edge extend further left,
	 * independently of where typing actually starts, so the hint can
	 * visually stretch over the prefix and/or the leading icon.
	 */
	private var mHintStartAnchor: HintStartAnchor? = null

	/**
	 * Backing field for [setHintEndAnchor]. `null` (the default) means
	 * "match the [EditText]'s own end edge" — the typed text always ends
	 * right before the suffix, that isn't configurable. A non-null value
	 * lets the floating label's end edge extend further right,
	 * independently of where typing actually ends, so the hint can
	 * visually stretch over the suffix and/or the trailing icon.
	 */
	private var mHintEndAnchor: HintEndAnchor? = null

	private var mStartButtonLayoutStyle: ButtonLayoutStyle = ButtonLayoutStyle.NORMAL
	private var mEndButtonLayoutStyle: ButtonLayoutStyle = ButtonLayoutStyle.NORMAL

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
	 * Ghost example text (e.g. "juan@correo.com") shown inline over the
	 * [EditText] once the hint has floated out of the way and there's
	 * nothing typed yet — separate from [mHintText], which is the *label*,
	 * not an example value. See [setPlaceholderText].
	 */
	private var mPlaceholderText: String? = null

	/** Whether the placeholder is shown at all. See [setPlaceholderEnabled]. */
	private var mPlaceholderEnable: Boolean = false

	/** Text color for the placeholder. Defaults to a dimmed [mHintTextColor] if left unset. */
	private var mPlaceholderTextColor: ColorStateList? = null

	/**
	 * Backing field for the ghost placeholder label. Created lazily the
	 * first time an [EditText] child is added, mirroring
	 * [mFloatingHintLabel]. See [setupPlaceholderLabel].
	 */
	private var mPlaceholderLabel: MaterialTextView? = null

	/**
	 * Drives the placeholder's fade-out, at one of two very different
	 * speeds depending on whether what's typed still matches it — see
	 * [updatePlaceholderVisibility]. Cancelled in [onDetachedFromWindow]
	 * like [mHintAnimator].
	 */
	private var mPlaceholderAnimator: ValueAnimator? = null

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
		view_binding = ComponentTextInputLayoutBinding.inflate(
			LayoutInflater.from(context),
			this,
			true
		)
		inflating = false

		context.withStyledAttributes(attrs, R.styleable.ComponentTextInputLayout) {

			mPrefixText = getString(R.styleable.ComponentTextInputLayout_prefixText)
			mPrefixEnable =
				getBoolean(R.styleable.ComponentTextInputLayout_prefixEnable, false)
			mPrefixTextColor =
				getColorStateList(R.styleable.ComponentTextInputLayout_prefixTextColor)
					?: run {
						val typedValue = TypedValue()
						context.theme.resolveAttribute(
							android.R.attr.textColorPrimary,
							typedValue,
							true
						)
						context.getColorStateList(typedValue.resourceId)
							.takeIf { typedValue.resourceId != 0 }
							?: ColorStateList.valueOf(context.getColor(typedValue.data))
					}

			mPlaceholderText =
				getString(R.styleable.ComponentTextInputLayout_placeholderText)
			mPlaceholderEnable =
				getBoolean(R.styleable.ComponentTextInputLayout_placeholderEnable, false)
			mPlaceholderTextColor =
				getColorStateList(R.styleable.ComponentTextInputLayout_placeholderTextColor)

			mHeaderText = getString(R.styleable.ComponentTextInputLayout_headerText)
			mHeaderEnable =
				getBoolean(R.styleable.ComponentTextInputLayout_headerEnable, false)
			mHeaderTextColor =
				getColorStateList(R.styleable.ComponentTextInputLayout_headerTextColor)
					?: run {
						val typedValue = TypedValue()
						context.theme.resolveAttribute(
							android.R.attr.textColorPrimary,
							typedValue,
							true
						)
						context.getColorStateList(typedValue.resourceId)
							.takeIf { typedValue.resourceId != 0 }
							?: ColorStateList.valueOf(context.getColor(typedValue.data))
					}
			mHeaderTextSize = getDimension(
				R.styleable.ComponentTextInputLayout_headerTextSize,
				24.dp.toFloat()
			)

			mCounterEnable =
				getBoolean(R.styleable.ComponentTextInputLayout_counterEnable, false)
			mOutLimitTextLimit =
				getBoolean(R.styleable.ComponentTextInputLayout_counterLimit, false)
			mCounterTextColor =
				getColorStateList(R.styleable.ComponentTextInputLayout_counterTextColor)
					?: run {
						val typedValue = TypedValue()
						context.theme.resolveAttribute(
							android.R.attr.textColorPrimary,
							typedValue,
							true
						)
						context.getColorStateList(typedValue.resourceId)
							.takeIf { typedValue.resourceId != 0 }
							?: ColorStateList.valueOf(context.getColor(typedValue.data))
					}
			mMaxLimit =
				getInteger(R.styleable.ComponentTextInputLayout_counterMaxLength, -1)

			mHelperText = getString(R.styleable.ComponentTextInputLayout_helperText)
			mHelperEnable =
				getBoolean(R.styleable.ComponentTextInputLayout_helperEnable, false)
			mHelperTextColor =
				getColorStateList(R.styleable.ComponentTextInputLayout_helperTextColor)
					?: run {
						val typedValue = TypedValue()
						context.theme.resolveAttribute(
							android.R.attr.textColorPrimary,
							typedValue,
							true
						)
						context.getColorStateList(typedValue.resourceId)
							.takeIf { typedValue.resourceId != 0 }
							?: ColorStateList.valueOf(context.getColor(typedValue.data))
					}

			mStartIconEnable =
				getBoolean(R.styleable.ComponentTextInputLayout_startIconEnable, false)
			mStartIcon = getDrawable(R.styleable.ComponentTextInputLayout_startIconDrawable)
			mStartIconTint =
				getColorStateList(R.styleable.ComponentTextInputLayout_startIconTint)
					?: run {
						val typedValue = TypedValue()
						context.theme.resolveAttribute(
							com.google.android.material.R.attr.colorOutline,
							typedValue,
							true
						)
						context.getColorStateList(typedValue.resourceId)
							.takeIf { typedValue.resourceId != 0 }
							?: ColorStateList.valueOf(context.getColor(typedValue.data))
					}
			mStartLayoutEnable =
				getBoolean(R.styleable.ComponentTextInputLayout_startLayoutEnable, false)
			mStartButtonLayoutStyle =
				when (getInt(R.styleable.ComponentTextInputLayout_startLayoutStyle, 1)) {
					0 -> ButtonLayoutStyle.THREE_DOT
					else -> ButtonLayoutStyle.NORMAL
				}

			mEndIconEnable =
				getBoolean(R.styleable.ComponentTextInputLayout_endIconEnable, false)
			mEndIcon = getDrawable(R.styleable.ComponentTextInputLayout_endIconDrawable)
			mEndIconTint =
				getColorStateList(R.styleable.ComponentTextInputLayout_endIconTint) ?: run {
					val typedValue = TypedValue()
					context.theme.resolveAttribute(
						com.google.android.material.R.attr.colorOutline,
						typedValue,
						true
					)
					context.getColorStateList(typedValue.resourceId)
						.takeIf { typedValue.resourceId != 0 }
						?: ColorStateList.valueOf(context.getColor(typedValue.data))
				}
			mEndLayoutEnable =
				getBoolean(R.styleable.ComponentTextInputLayout_endLayoutEnable, false)
			mEndButtonLayoutStyle =
				when (getInt(R.styleable.ComponentTextInputLayout_endLayoutStyle, 1)) {
					0 -> ButtonLayoutStyle.THREE_DOT
					else -> ButtonLayoutStyle.NORMAL
				}

			mSuffixText = getString(R.styleable.ComponentTextInputLayout_suffixText)
			mSuffixEnable =
				getBoolean(R.styleable.ComponentTextInputLayout_suffixEnable, false)
			mSuffixTextColor =
				getColorStateList(R.styleable.ComponentTextInputLayout_suffixTextColor)
					?: run {
						val typedValue = TypedValue()
						context.theme.resolveAttribute(
							android.R.attr.textColorPrimary,
							typedValue,
							true
						)
						context.getColorStateList(typedValue.resourceId)
							.takeIf { typedValue.resourceId != 0 }
							?: ColorStateList.valueOf(context.getColor(typedValue.data))
					}

			mHintText = getString(R.styleable.ComponentTextInputLayout_hintText)
			mHintEnable = getBoolean(R.styleable.ComponentTextInputLayout_hintEnable, false)
			mHintTextColor =
				getColorStateList(R.styleable.ComponentTextInputLayout_hintTextColor)
					?: run {
						val typedValue = TypedValue()
						context.theme.resolveAttribute(
							com.google.android.material.R.attr.colorOutline,
							typedValue,
							true
						)
						context.getColorStateList(typedValue.resourceId)
							.takeIf { typedValue.resourceId != 0 }
							?: ColorStateList.valueOf(context.getColor(typedValue.data))
					}
			mHintGravity =
				when (getInt(R.styleable.ComponentTextInputLayout_hintGravity, 0)) {
					1 -> HintGravity.CENTER
					2 -> HintGravity.END
					else -> HintGravity.START
				}

			mHintStartAnchor =
				when (getInt(R.styleable.ComponentTextInputLayout_hintStartAnchor, 0)) {
					1 -> HintStartAnchor.START_ICON
					2 -> HintStartAnchor.START_LAYOUT
					3 -> HintStartAnchor.PREFIX
					else -> if (mPrefixEnable) HintStartAnchor.PREFIX else if (mStartIconEnable) HintStartAnchor.START_ICON else HintStartAnchor.START_LAYOUT
				}
			mHintEndAnchor =
				when (getInt(R.styleable.ComponentTextInputLayout_hintEndAnchor, 0)) {
					1 -> HintEndAnchor.END_ICON
					2 -> HintEndAnchor.END_LAYOUT
					3 -> HintEndAnchor.SUFFIX
					else -> if (mSuffixEnable) HintEndAnchor.SUFFIX else if (mEndIconEnable) HintEndAnchor.END_ICON else HintEndAnchor.END_LAYOUT
				}

			mErrorText = getString(R.styleable.ComponentTextInputLayout_errorText) ?: ""
			mErrorTextColor =
				getColorStateList(R.styleable.ComponentTextInputLayout_errorTextColor)
					?: ColorStateList.valueOf(Color.RED)
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
		view_binding.message.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE

		// Fixes the header/message row getting an unwanted symmetric gap
		// (see normalizeVerticalTextLayout's KDoc for why).
		normalizeVerticalTextLayout(view_binding.header)
		normalizeVerticalTextLayout(view_binding.message)
	}

	/**
	 * Forces a text row to hug its **top**, instead of being vertically
	 * centered inside whatever height its `ConstraintLayout.LayoutParams`
	 * ended up with.
	 *
	 * The symptom this fixes: if `header` or `message` sits in a row taller
	 * than its own text (its `layout_height` is `0dp`/`match_constraint`
	 * spanning between two fixed anchors, or a chain gives it more room than
	 * it needs), ConstraintLayout's *default* vertical bias (`0.5`, i.e.
	 * centered) splits that extra height evenly — half above the text, half
	 * below. So a row with `24dp` of slack above a `24dp`-tall text ends up
	 * with `24dp` of empty space *above* the text too, on top of its normal
	 * height, exactly as described: `24dp + 24dp + normal_height`.
	 *
	 * Setting the height to `WRAP_CONTENT` and the vertical bias to `0`
	 * (top-aligned) removes that slack outright — the row is exactly as
	 * tall as its own text, with nothing above it.
	 *
	 * This is a defensive, code-only fix since the exact row/constraint
	 * setup lives in the XML layout, which isn't available here; if the
	 * gap persists after this, the row's *own* margin (not its height/bias)
	 * is the more likely culprit and would need the layout file itself.
	 */
	@Deprecated("The error is fixed in the XML-Binding.")
	private fun normalizeVerticalTextLayout(view: View) {
		val params = view.layoutParams as? LayoutParams ?: return
		if (params.height != LayoutParams.WRAP_CONTENT || params.verticalBias != 0f) {
			params.height = LayoutParams.WRAP_CONTENT
			params.verticalBias = 0f
			view.layoutParams = params
		}
	}

	/** Converts `dp` to pixels using the current device density. */
	private val Int.dp: Int
		get(): Int =
			TypedValue.applyDimension(
				TypedValue.COMPLEX_UNIT_DIP,
				this.toFloat(),
				resources.displayMetrics
			).toInt()

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
		return context.getColorStateList(typedValue.resourceId)
			.takeIf { typedValue.resourceId != 0 }
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
		setHeaderTextSize(
			TypedValue.applyDimension(
				TypedValue.COMPLEX_UNIT_SP,
				sizeSp,
				resources.displayMetrics
			)
		)
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
		val header = view_binding.header
		header.text = mHeaderText
		mHeaderTextColor?.let { header.setTextColor(it) }
		if (mHeaderTextSize > 0f) header.setTextSize(TypedValue.COMPLEX_UNIT_PX, mHeaderTextSize)
		header.visibility = if (mHeaderEnable) VISIBLE else GONE
		ViewCompat.setAccessibilityHeading(header, mHeaderEnable)
	}

	/**
	 * Sets the static prefix text, shown when there's no active dropdown
	 * (see [getPrefixItemAdapter]).
	 *
	 * @throws IllegalStateException if a prefix dropdown is currently active
	 *   (set via [setPrefixTextList] or [setPrefixItemAdapter]). A static
	 *   text and a dropdown are mutually exclusive: call `setPrefixTextList()`
	 *   with no arguments, or `setPrefixItemAdapter(null)`, before setting a
	 *   static value.
	 */
	fun setPrefixText(text: String?) {
		check(mPrefixItemAdapter == null) {
			"Cannot call setPrefixText() while a prefix dropdown is active " +
					"(set via setPrefixTextList() or setPrefixItemAdapter()). " +
					"Call setPrefixTextList() with no arguments, or setPrefixItemAdapter(null), to clear it first."
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
	 * (e.g. country codes), rendered as plain text rows via a
	 * [DefaultPopupItemAdapter]. The first entry is displayed by default;
	 * tapping the prefix opens the popup when there's more than one option.
	 * For custom row layouts, use [setPrefixItemAdapter] instead.
	 *
	 * @param texts options to show, in order. Passing none clears the dropdown.
	 */
	fun setPrefixTextList(vararg texts: String) {
		setPrefixTextList(texts.toList())
	}

	/**
	 * Sets a list of selectable **prefix** options shown as a popup menu
	 * (e.g. country codes), rendered as plain text rows via a
	 * [DefaultPopupItemAdapter]. The first entry is displayed by default;
	 * tapping the prefix opens the popup when there's more than one option.
	 * For custom row layouts, use [setPrefixItemAdapter] instead.
	 *
	 * @param texts options to show, in order. Passing an empty list clears the dropdown.
	 */
	fun setPrefixTextList(texts: List<String>) {
		attachPrefixAdapter(if (texts.isNotEmpty()) DefaultPopupItemAdapter(texts) else null)
	}

	/**
	 * Returns the current list of selectable prefix options, read back from
	 * [getPrefixItemAdapter] by binding each row once (see [labelForPosition]).
	 */
	fun getPrefixTextList(): List<String> {
		val adapter = mPrefixItemAdapter ?: return emptyList()
		return (0 until adapter.getItemCount()).mapNotNull { labelForPosition(adapter, it) }
	}

	/**
	 * Sets a fully custom [PopupItemAdapter] for the prefix dropdown,
	 * replacing whatever [setPrefixTextList] would have built — use this
	 * when the popup's rows need more than plain text (icons, multi-line
	 * layouts, custom typography, etc). Pass `null` to clear the dropdown
	 * entirely, same as calling `setPrefixTextList()` with no arguments.
	 */
	fun setPrefixItemAdapter(adapter: PopupItemAdapter<PopupItemViewHolder>?) {
		attachPrefixAdapter(adapter)
	}

	/**
	 * Assigns [adapter] as the prefix dropdown's data source and wires
	 * [PopupItemAdapter.notifyDataSetChanged] back to [applyPrefix], so
	 * calling it later refreshes the prefix box's displayed text too — not
	 * just an open popup.
	 */
	private fun attachPrefixAdapter(adapter: PopupItemAdapter<PopupItemViewHolder>?) {
		mPrefixItemAdapter = adapter
		adapter?.onDataSetChanged = { applyPrefix() }
		applyPrefix()
	}

	/** Returns the prefix dropdown's current adapter, or `null` if none is active. */
	fun getPrefixItemAdapter(): PopupItemAdapter<PopupItemViewHolder>? = mPrefixItemAdapter

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
	 * [setPrefixTextList] / [setPrefixItemAdapter] are left untouched, so
	 * the popup still offers the same choices next time it's opened.
	 */
	fun clearPrefixSelection() {
		val previous = mPrefixText
		mPrefixText = null
		applyPrefix()
		if (previous != null) mPrefixItemActionListener?.onItemUnselected(previous)
	}

	/**
	 * Resolves the text value [adapter] would show for [position], by
	 * binding a throwaway [PopupItemViewHolder] and reading
	 * [PopupItemAdapter.onBindViewHolder]'s return value — used to display
	 * the current selection in the prefix/suffix box *without* needing the
	 * popup itself to be open. A one-off `View` allocation, only run when
	 * the prefix/suffix or its adapter actually changes (never per-frame).
	 */
	private fun labelForPosition(adapter: PopupItemAdapter<PopupItemViewHolder>, position: Int): String? {
		if (position !in 0 until adapter.getItemCount()) return null
		val holder =
			adapter.onCreateViewHolder(LayoutInflater.from(context), view_binding.container)
		return adapter.onBindViewHolder(holder, position)
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
		val textView = view_binding.prefix
		val adapter = mPrefixItemAdapter
		val current = if (adapter != null && adapter.getItemCount() > 0) labelForPosition(
			adapter,
			0
		) else mPrefixText

		textView.text = current?.truncate(11)
		mPrefixTextColor?.let { textView.setTextColor(it) }
		textView.visibility = if (mPrefixEnable) VISIBLE else GONE

		if (adapter != null && adapter.getItemCount() > 1) {
			textView.contentDescription = "Prefix"
			textView.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
			ViewCompat.setStateDescription(textView, current)

			textView.setOnClickListener {
				showOptionsPopup(textView, adapter) { selected ->
					val previous = mPrefixText
					textView.text = selected.truncate(11)
					mPrefixText = selected
					ViewCompat.setStateDescription(textView, selected)

					if (selected == previous) {
						mPrefixItemActionListener?.onItemReselected(selected)
					} else {
						mPrefixItemActionListener?.onItemSelected(selected)
						if (previous != null) mPrefixItemClickChangedListener?.onItemClickChanged(
							previous,
							selected
						)
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
	 * Sets the static suffix text, shown when there's no active dropdown
	 * (see [getSuffixItemAdapter]).
	 *
	 * @throws IllegalStateException if a suffix dropdown is currently active
	 *   (set via [setSuffixTextList] or [setSuffixItemAdapter]). A static
	 *   text and a dropdown are mutually exclusive: call `setSuffixTextList()`
	 *   with no arguments, or `setSuffixItemAdapter(null)`, before setting a
	 *   static value.
	 */
	fun setSuffixText(text: String?) {
		check(mSuffixItemAdapter == null) {
			"Cannot call setSuffixText() while a suffix dropdown is active " +
					"(set via setSuffixTextList() or setSuffixItemAdapter()). " +
					"Call setSuffixTextList() with no arguments, or setSuffixItemAdapter(null), to clear it first."
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
	 * (e.g. units of measurement), rendered as plain text rows via a
	 * [DefaultPopupItemAdapter]. The first entry is displayed by default;
	 * tapping the suffix opens the popup when there's more than one option.
	 * For custom row layouts, use [setSuffixItemAdapter] instead.
	 *
	 * @param texts options to show, in order. Passing none clears the dropdown.
	 */
	fun setSuffixTextList(vararg texts: String) {
		setSuffixTextList(texts.toList())
	}

	/**
	 * Sets a list of selectable **suffix** options shown as a popup menu
	 * (e.g. units of measurement), rendered as plain text rows via a
	 * [DefaultPopupItemAdapter]. The first entry is displayed by default;
	 * tapping the suffix opens the popup when there's more than one option.
	 * For custom row layouts, use [setSuffixItemAdapter] instead.
	 *
	 * @param texts options to show, in order. Passing an empty list clears the dropdown.
	 */
	fun setSuffixTextList(texts: List<String>) {
		attachSuffixAdapter(if (texts.isNotEmpty()) DefaultPopupItemAdapter(texts) else null)
	}

	/**
	 * Returns the current list of selectable suffix options, read back from
	 * [getSuffixItemAdapter] by binding each row once (see [labelForPosition]).
	 */
	fun getSuffixTextList(): List<String> {
		val adapter = mSuffixItemAdapter ?: return emptyList()
		return (0 until adapter.getItemCount()).mapNotNull { labelForPosition(adapter, it) }
	}

	/**
	 * Sets a fully custom [PopupItemAdapter] for the suffix dropdown,
	 * replacing whatever [setSuffixTextList] would have built — use this
	 * when the popup's rows need more than plain text (icons, multi-line
	 * layouts, custom typography, etc). Pass `null` to clear the dropdown
	 * entirely, same as calling `setSuffixTextList()` with no arguments.
	 */
	fun setSuffixItemAdapter(adapter: PopupItemAdapter<PopupItemViewHolder>?) {
		attachSuffixAdapter(adapter)
	}

	/**
	 * Assigns [adapter] as the suffix dropdown's data source and wires
	 * [PopupItemAdapter.notifyDataSetChanged] back to [applySuffix], so
	 * calling it later refreshes the suffix box's displayed text too — not
	 * just an open popup.
	 */
	private fun attachSuffixAdapter(adapter: PopupItemAdapter<PopupItemViewHolder>?) {
		mSuffixItemAdapter = adapter
		adapter?.onDataSetChanged = { applySuffix() }
		applySuffix()
	}

	/** Returns the suffix dropdown's current adapter, or `null` if none is active. */
	fun getSuffixItemAdapter(): PopupItemAdapter<PopupItemViewHolder>? = mSuffixItemAdapter

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
	 * [setSuffixTextList] / [setSuffixItemAdapter] are left untouched, so
	 * the popup still offers the same choices next time it's opened.
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
		val textView = view_binding.suffix
		val adapter = mSuffixItemAdapter
		val current = if (adapter != null && adapter.getItemCount() > 0) labelForPosition(
			adapter,
			0
		) else mSuffixText

		textView.text = current?.truncate(11)
		mSuffixTextColor?.let { textView.setTextColor(it) }
		textView.visibility = if (mSuffixEnable) VISIBLE else GONE

		if (adapter != null && adapter.getItemCount() > 1) {
			textView.contentDescription = "Suffix"
			textView.accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
			ViewCompat.setStateDescription(textView, current)

			textView.setOnClickListener {
				showOptionsPopup(textView, adapter) { selected ->
					val previous = mSuffixText
					textView.text = selected.truncate(11)
					mSuffixText = selected
					ViewCompat.setStateDescription(textView, selected)

					if (selected == previous) {
						mSuffixItemActionListener?.onItemReselected(selected)
					} else {
						mSuffixItemActionListener?.onItemSelected(selected)
						if (previous != null) mSuffixItemClickChangedListener?.onItemClickChanged(
							previous,
							selected
						)
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
	 * [applySuffix] so the dropdown behavior lives in a single place. The
	 * actual row views come from [adapter] via [PopupItemAdapterBridge] —
	 * this function only owns the [PopupWindow]/[ListView] plumbing
	 * (positioning, sizing, dismiss-on-select), never the row's look.
	 *
	 * The adapter is attached to its bridge for the popup's lifetime, so
	 * [PopupItemAdapter.notifyDataSetChanged] reaches the live [ListView]
	 * while it's open, and detached again on dismiss.
	 *
	 * @param anchor view the popup is anchored to.
	 * @param adapter supplies the row count, row views, and (via
	 *   [PopupItemAdapter.onBindViewHolder]'s return value) each row's text.
	 * @param onSelected called with the tapped row's text value.
	 */
	private fun showOptionsPopup(
		anchor: View,
		adapter: PopupItemAdapter<PopupItemViewHolder>,
		onSelected: (String) -> Unit
	) {
		val popup = PopupWindow(this.context)
		val inflater = LayoutInflater.from(this.context)
		val bridge = PopupItemAdapterBridge(inflater, adapter)
		adapter.attachBridge(bridge)

		val listView = ListView(this.context).apply {
			this.adapter = bridge
			dividerHeight = 0
		}

		popup.contentView = listView
		popup.width = 150.dp
		popup.height = WindowManager.LayoutParams.WRAP_CONTENT
		popup.isFocusable = true
		popup.setBackgroundDrawable(
			ContextCompat.getDrawable(this.context, R.drawable.bg_popup_background)
		)
		popup.setOnDismissListener { adapter.detachBridge() }

		listView.setOnItemClickListener { _, _, position, _ ->
			bridge.labelAt(position)?.let(onSelected)
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
	 * Sets the horizontal alignment of the floating hint label ([HintGravity]).
	 * Takes effect immediately if an [EditText] child has already been added.
	 */
	fun setHintGravity(gravity: HintGravity) {
		mHintGravity = gravity
		mEditText?.let { applyHintGravity(it) }
	}

	/** Returns the current hint gravity. */
	fun getHintGravity(): HintGravity = mHintGravity

	/**
	 * Refreshes only the floating label's horizontal constraints to match
	 * [mHintGravity] plus [mHintStartAnchor] / [mHintEndAnchor], re-centering
	 * its scale pivot to match (so it shrinks toward the edge it's anchored
	 * to, instead of always around its own center regardless of alignment).
	 *
	 * The label's start/end edges align to [mHintStartAnchor] /
	 * [mHintEndAnchor] when set, or fall back to the [EditText]'s own start/
	 * end (`target.id`) when `null` — see [setHintStartAnchor] /
	 * [setHintEndAnchor] for what each option visually does.
	 */
	private fun applyHintGravity(target: EditText) {
		val label = mFloatingHintLabel ?: return
		val params = label.layoutParams as? ConstraintLayout.LayoutParams ?: return

		val startId = mHintStartAnchor?.alignedStartViewId() ?: target.id
		val endId = mHintEndAnchor?.alignedEndViewId() ?: target.id

		params.startToStart = ConstraintLayout.LayoutParams.UNSET
		params.endToEnd = ConstraintLayout.LayoutParams.UNSET
		params.horizontalBias = 0f

		when (mHintGravity) {
			HintGravity.START -> params.startToEnd = startId
			HintGravity.CENTER -> {
				params.startToEnd = startId
				params.endToStart = endId
				params.horizontalBias = 0.5f
			}

			HintGravity.END -> params.endToStart = endId
		}

		label.layoutParams = params
		updateFloatingHintLabel(target, animate = false)
	}

	/**
	 * Controls how far left the **floating hint label** — not the typed
	 * text itself, which always begins right after the prefix — is allowed
	 * to extend on its start side, independently of where typed text
	 * actually begins:
	 *
	 * - `null` (default): the hint's start edge matches the [EditText]'s
	 *   own start edge exactly — the original, unchanged behavior.
	 * - [HintStartAnchor.PREFIX]: the hint's start edge aligns with the
	 *   **start** of the prefix (not after it, like typed text does), so
	 *   the resting hint visually stretches leftward over the prefix.
	 * - [HintStartAnchor.START_LAYOUT] / [HintStartAnchor.START_ICON]:
	 *   stretches further still, over the leading-icon container too — the
	 *   most "complete" coverage on this side.
	 *
	 * This is independent from [setHintEndAnchor], so one side can be left
	 * at its minimal default while the other is set to fully cover its
	 * icon/prefix or suffix/icon, and vice versa.
	 */
	fun setHintStartAnchor(anchor: HintStartAnchor?) {
		mHintStartAnchor = anchor
		mEditText?.let { applyHintGravity(it) }
	}

	/** Returns the current hint start anchor (`null` = matches the [EditText]'s own start). */
	fun getHintStartAnchor(): HintStartAnchor? = mHintStartAnchor

	/**
	 * Controls how far right the **floating hint label** — not the typed
	 * text itself, which always ends right before the suffix — is allowed
	 * to extend on its end side, independently of where typed text actually
	 * ends:
	 *
	 * - `null` (default): the hint's end edge matches the [EditText]'s own
	 *   end edge exactly — the original, unchanged behavior.
	 * - [HintEndAnchor.SUFFIX]: the hint's end edge aligns with the
	 *   **end** of the suffix (not before it, like typed text does), so the
	 *   resting hint visually stretches rightward over the suffix.
	 * - [HintEndAnchor.END_LAYOUT] / [HintEndAnchor.END_ICON]:
	 *   stretches further still, over the trailing-icon container too — the
	 *   most "complete" coverage on this side.
	 *
	 * This is independent from [setHintStartAnchor], so one side can be
	 * left at its minimal default while the other is set to fully cover its
	 * prefix/icon or icon/suffix, and vice versa.
	 */
	fun setHintEndAnchor(anchor: HintEndAnchor?) {
		mHintEndAnchor = anchor
		mEditText?.let { applyHintGravity(it) }
	}

	/** Returns the current hint end anchor (`null` = matches the [EditText]'s own end). */
	fun getHintEndAnchor(): HintEndAnchor? = mHintEndAnchor

// =======================================================================
// Placeholder — ghost example text with a two-speed fade
// =======================================================================

	/**
	 * Sets the ghost example text (e.g. `"juan@correo.com"`) shown inline
	 * over the [EditText] once the hint has floated out of the way. This is
	 * a *placeholder*, distinct from [setHintText] (the label): the hint
	 * describes the field, the placeholder shows what a valid value looks
	 * like.
	 */
	fun setPlaceholderText(text: String?) {
		mPlaceholderText = text
		mEditText?.let { updatePlaceholderText(it) }
	}

	/** Returns the current placeholder text. */
	fun getPlaceholderText(): String? = mPlaceholderText

	/** Shows or hides the placeholder entirely. */
	fun setPlaceholderEnabled(enabled: Boolean) {
		mPlaceholderEnable = enabled
		mEditText?.let { updatePlaceholderVisibility(it, animate = false) }
	}

	/** Returns whether the placeholder is enabled. */
	fun isPlaceholderEnabled(): Boolean = mPlaceholderEnable

	/** Sets the placeholder's text color. Falls back to a dimmed hint color when unset. */
	fun setPlaceholderTextColor(color: ColorStateList?) {
		mPlaceholderTextColor = color
		mPlaceholderLabel?.let { label -> resolvePlaceholderTextColor()?.let { label.setTextColor(it) } }
	}

	/** Returns the placeholder's text color, if explicitly set. */
	fun getPlaceholderTextColor(): ColorStateList? = mPlaceholderTextColor

	/** Resolves the color the placeholder should render in: its own color, or a dimmed hint color as a fallback. */
	private fun resolvePlaceholderTextColor(): ColorStateList? {
		mPlaceholderTextColor?.let { return it }
		val baseColor = mHintTextColor?.defaultColor ?: return null
		return ColorStateList.valueOf(
			ColorUtils.setAlphaComponent(
				baseColor,
				PLACEHOLDER_FALLBACK_ALPHA
			)
		)
	}

	/**
	 * Creates the ghost placeholder label, mirroring [setupFloatingHintLabel]:
	 * a plain [MaterialTextView] positioned exactly over `target`'s own
	 * bounds, purely decorative (not read by TalkBack — the real
	 * accessibility hint is already carried by the floating label via
	 * `labelFor`, and a ghost example value would just be noise there).
	 */
	private fun setupPlaceholderLabel(target: EditText) {
		val label = MaterialTextView(context).apply {
			id = View.generateViewId()
			setTextSize(TypedValue.COMPLEX_UNIT_PX, target.textSize)
			resolvePlaceholderTextColor()?.let { setTextColor(it) }
			isClickable = false
			isFocusable = false
			importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
			alpha = 0f
		}

		view_binding.container.addView(label)
		label.layoutParams = ConstraintLayout.LayoutParams(
			ConstraintLayout.LayoutParams.WRAP_CONTENT,
			ConstraintLayout.LayoutParams.WRAP_CONTENT
		).apply {
			startToStart = target.id
			topToTop = target.id
			bottomToBottom = target.id
		}

		mPlaceholderLabel = label
		updatePlaceholderText(target)
	}

	/** Refreshes only the placeholder's text content, then re-syncs its visibility. */
	private fun updatePlaceholderText(target: EditText) {
		mPlaceholderLabel?.text = mPlaceholderText
		updatePlaceholderVisibility(target, animate = false)
	}

	/**
	 * Shows, hides or fades the ghost placeholder based on focus and what's
	 * actually typed in [target] — three distinct speeds:
	 *
	 * - Focused **and** empty → placeholder fades **in**, taking over the
	 *   resting spot the hint deliberately leaves inactive while empty (see
	 *   [updateFloatingHintLabel]), over [PLACEHOLDER_APPEAR_FADE_DURATION_MS].
	 * - Not focused (regardless of text) → hidden. A placeholder shown while
	 *   unfocused would just be noise, since nothing invites the user to
	 *   compare their input against it right then.
	 * - Focused, with something typed that's a **prefix of the placeholder**
	 *   (the user is typing the example value itself) → fades out over
	 *   [PLACEHOLDER_MATCH_FADE_DURATION_MS], a deliberate, "melting away" pace.
	 * - Focused, with something typed that **doesn't match** → fades out over
	 *   [PLACEHOLDER_MISMATCH_FADE_DURATION_MS] instead — near-instant, so a
	 *   stale ghost example never lingers over unrelated text.
	 *
	 * @param animate `false` for the initial sync (no previous state to
	 *   animate from); `true` for real focus/text changes.
	 */
	private fun updatePlaceholderVisibility(target: EditText, animate: Boolean) {
		val label = mPlaceholderLabel ?: return
		val placeholder = mPlaceholderText

		mPlaceholderAnimator?.cancel()

		if (!mPlaceholderEnable || placeholder.isNullOrEmpty()) {
			label.visibility = GONE
			return
		}
		label.visibility = VISIBLE

		val typed = target.text?.toString().orEmpty()

		val targetAlpha: Float
		val duration: Long
		when {
			!target.isFocused -> {
				targetAlpha = 0f
				duration = PLACEHOLDER_MISMATCH_FADE_DURATION_MS
			}

			typed.isEmpty() -> {
				targetAlpha = 1f
				duration = PLACEHOLDER_APPEAR_FADE_DURATION_MS
			}

			placeholder.startsWith(typed) -> {
				targetAlpha = 0f
				duration = PLACEHOLDER_MATCH_FADE_DURATION_MS
			}

			else -> {
				targetAlpha = 0f
				duration = PLACEHOLDER_MISMATCH_FADE_DURATION_MS
			}
		}

		if (!animate) {
			label.alpha = targetAlpha
			return
		}

		val startAlpha = label.alpha
		mPlaceholderAnimator = ValueAnimator.ofFloat(startAlpha, targetAlpha).apply {
			this.duration = duration
			interpolator =
				if (targetAlpha < startAlpha) FastOutSlowInInterpolator() else android.view.animation.LinearInterpolator()
			addUpdateListener { label.alpha = it.animatedValue as Float }
			start()
		}
	}

	/**
	 * Resolves a [HintStartAnchor] to the view the hint label should align
	 * its start edge *with* (`startToStart`), letting it reach further left
	 * than where typing actually starts.
	 */
	private fun HintStartAnchor.alignedStartViewId(): Int = when (this) {
		HintStartAnchor.START_ICON -> view_binding.imageStart.id
		HintStartAnchor.START_LAYOUT -> view_binding.start.id
		HintStartAnchor.PREFIX -> view_binding.prefix.id
	}

	/**
	 * Resolves a [HintEndAnchor] to the view the hint label should align
	 * its end edge *with* (`endToEnd`), letting it reach further right than
	 * where typing actually ends.
	 */
	private fun HintEndAnchor.alignedEndViewId(): Int = when (this) {
		HintEndAnchor.END_ICON -> view_binding.imageEnd.id
		HintEndAnchor.END_LAYOUT -> view_binding.end.id
		HintEndAnchor.SUFFIX -> view_binding.suffix.id
	}

	/**
	 * Reserves enough space above [view_binding.container] for the floated
	 * hint label to sit fully above the outlined border, instead of
	 * overlapping it — which is what used to make the border visually cut
	 * through the label's text once it floated up.
	 *
	 * The reserved amount is added as a **top margin on `container` itself**
	 * (not padding on `this`), so it becomes part of this component's own
	 * measured height instead of spilling into whatever the caller placed
	 * above it. `clipChildren`/`clipToPadding` are also disabled on
	 * `container` and its parent as a safety net, in case the reserved
	 * amount ends up slightly short on some device/font combination.
	 */
	private fun reserveSpaceForFloatingHint(target: EditText) {
		view_binding.container.clipChildren = false
		view_binding.container.clipToPadding = false
		(view_binding.container.parent as? ViewGroup)?.apply {
			clipChildren = false
			clipToPadding = false
		}

		if (!mHintEnable) return

		val params = view_binding.container.layoutParams as? ConstraintLayout.LayoutParams ?: return
		val floatedLabelHeight = target.textSize * HINT_FLOATING_SCALE
		val reserved = (floatedLabelHeight + HINT_TOP_CLEARANCE_DP.dp).toInt()

		if (params.topMargin < reserved) {
			params.topMargin = reserved
			view_binding.container.layoutParams = params
		}
	}

	/**
	 * Creates the floating label used for the M3 hint animation and adds it
	 * to [view_binding.container], constrained to overlap the [EditText]
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
			id = generateViewId()
			text = mHintText
			mHintTextColor?.let { setTextColor(it) }
			setTextSize(TypedValue.COMPLEX_UNIT_PX, target.textSize)
			isClickable = false
			isFocusable = false
			labelFor = target.id
			importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
		}

		view_binding.container.addView(label)
		label.layoutParams = LayoutParams(
			LayoutParams.WRAP_CONTENT,
			LayoutParams.WRAP_CONTENT
		).apply {
			topToTop = target.id
			bottomToBottom = target.id
		}

		mFloatingHintLabel = label
		reserveSpaceForFloatingHint(target)
		applyHintGravity(target)

		// Re-sync the resting/floated position on every layout pass — not
		// just the first one — so it never drifts after a rotation or a
		// keyboard open/close, both of which resize `target` in place
		// without going through setupFloatingHintLabel() again. A one-shot
		// `target.post { ... }` (the previous approach) only fixed the very
		// first frame and then silently went stale.
		val listener = OnLayoutChangeListener { _, _, top, _, bottom, _, oldTop, _, oldBottom ->
			if (bottom - top != oldBottom - oldTop) {
				updateFloatingHintLabel(target, animate = false)
			}
		}
		mHintLayoutListener = listener
		target.addOnLayoutChangeListener(listener)
	}

	/**
	 * Applies the M3 "float up" motion: **only once there's actual text** —
	 * the label scales down and moves above the field; otherwise (empty,
	 * whether focused or not) it stays "inactive", resting directly over the
	 * field exactly like a normal hint. This is deliberately *not* tied to
	 * focus alone: while focused with nothing typed yet, the ghost
	 * [mPlaceholderLabel] takes over that resting spot instead (see
	 * [updatePlaceholderVisibility]) — floating the hint on focus alone
	 * would fight the placeholder for the same space. Also fades the label
	 * in/out based on [mHintEnable].
	 *
	 * The scale pivot is recomputed on every call to match [mHintGravity]:
	 * a `START`-aligned label shrinks from its left edge, an `END`-aligned
	 * one from its right edge, and a `CENTER`-aligned one from its middle —
	 * matching whichever edge (or point) it's actually anchored to, instead
	 * of always pivoting around its own center regardless of alignment
	 * (which used to make the label visibly drift sideways while animating).
	 *
	 * Driven by a single reusable [ValueAnimator] ([mHintAnimator]) instead
	 * of a fire-and-forget [View.animate] call: any in-flight run is
	 * cancelled before starting a new one (so rapid focus changes don't pile
	 * up competing animations), and [onDetachedFromWindow] cancels it too,
	 * so nothing keeps animating — or holding a reference to this view's
	 * [Context] — after the field leaves the screen. The motion itself uses
	 * [FastOutSlowInInterpolator], Material's standard "emphasized" easing
	 * curve, instead of a generic ease-in-ease-out.
	 *
	 * @param animate `false` on first layout to avoid an unwanted entrance
	 *   animation, and on every re-sync triggered by [mHintLayoutListener];
	 *   `true` for real focus/text changes.
	 */
	private fun updateFloatingHintLabel(target: EditText, animate: Boolean) {
		val label = mFloatingHintLabel ?: return

		label.pivotY = label.height / 2f
		label.pivotX = when (mHintGravity) {
			HintGravity.START -> 0f
			HintGravity.CENTER -> label.width / 2f
			HintGravity.END -> label.width.toFloat()
		}

		val isFloating = !target.text.isNullOrEmpty() || target.isFocused || animate
		val targetScale = if (isFloating) HINT_FLOATING_SCALE else 1f
		val targetTranslationY = if (isFloating) {
			// Move the label from its resting position (vertically centered
			// on `target`) up past `container`'s own top edge — where the
			// border actually is — by the label's own floated height plus a
			// small clearance gap, instead of just clearing `target`'s top
			// edge (which sits *inside* the bordered box and used to leave
			// the label overlapping the border itself, clipping its text).
			val restingCenterY = target.top + target.height / 2f
			val floatedLabelHalfHeight = (target.textSize * HINT_FLOATING_SCALE) / 2f
			-restingCenterY - floatedLabelHalfHeight - HINT_TOP_CLEARANCE_DP.dp
		} else {
			0f
		}
		val targetAlpha = if (mHintEnable) 1f else 0f

		mHintAnimator?.cancel()

		if (!isFloating) {
			when (mHintStartAnchor) {
				HintStartAnchor.START_LAYOUT -> {
					view_binding.prefix.alpha = 0f
					view_binding.imageStart.alpha = 0f
				}

				HintStartAnchor.START_ICON -> {
					view_binding.prefix.alpha = 0f
				}

				else -> Unit
			}
			when (mHintEndAnchor) {
				HintEndAnchor.END_LAYOUT -> {
					view_binding.suffix.alpha = 0f
					view_binding.imageEnd.alpha = 0f
				}

				HintEndAnchor.END_ICON -> {
					view_binding.prefix.alpha = 0f
				}

				else -> Unit
			}
		}

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
		val startPrefixAlpha = view_binding.prefix.alpha
		val startImageStartAlpha = view_binding.imageStart.alpha
		val startSuffixAlpha = view_binding.suffix.alpha
		val startImageEndAlpha = view_binding.imageEnd.alpha

		mHintAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
			duration = HINT_ANIMATION_DURATION_MS
			interpolator = FastOutSlowInInterpolator()
			addUpdateListener { animator ->
				val fraction = animator.animatedValue as Float
				label.scaleX = startScale + fraction * (targetScale - startScale)
				label.scaleY = label.scaleX
				label.translationY =
					startTranslationY + fraction * (targetTranslationY - startTranslationY)
				label.alpha = startAlpha + fraction * (targetAlpha - startAlpha)
				when (mHintStartAnchor) {
					HintStartAnchor.START_LAYOUT -> {
						view_binding.prefix.alpha = startPrefixAlpha + fraction * (targetAlpha - startPrefixAlpha)
						view_binding.imageStart.alpha = startImageStartAlpha + fraction * (targetAlpha - startImageStartAlpha)
					}

					HintStartAnchor.START_ICON -> {
						view_binding.prefix.alpha = startPrefixAlpha + fraction * (targetAlpha - startPrefixAlpha)
					}

					else -> Unit
				}
				when (mHintEndAnchor) {
					HintEndAnchor.END_LAYOUT -> {
						view_binding.suffix.alpha = startSuffixAlpha + fraction * (targetAlpha - startSuffixAlpha)
						view_binding.imageEnd.alpha = startImageEndAlpha + fraction * (targetAlpha - startImageEndAlpha)
					}

					HintEndAnchor.END_ICON -> {
						view_binding.suffix.alpha = startSuffixAlpha + fraction * (targetAlpha - startSuffixAlpha)
					}

					else -> Unit
				}
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
		mCounterTextColor?.let { view_binding.counter.setTextColor(it) }
		view_binding.counter.visibility = if (mCounterEnable) VISIBLE else GONE
	}

	/**
	 * Refreshes only the counter's `count/limit` text based on the current
	 * [EditText] content. No-ops until an [EditText] child has been added.
	 */
	private fun refreshCounterText() {
		val target = mEditText ?: return
		if (mMaxLimit == -1) {
			view_binding.counter.text = ""
			return
		}
		val count = target.text?.length ?: 0
		view_binding.counter.text = "${minOf(count, mMaxLimit)}/${mMaxLimit}"
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
			view_binding.message.text = mErrorText
			view_binding.message.visibility = VISIBLE
			view_binding.message.setTextColor(mColorError)
			view_binding.counter.setTextColor(mColorError)
			view_binding.prefix.setTextColor(mColorError)
			view_binding.suffix.setTextColor(mColorError)
			view_binding.start.foregroundTintList = ColorStateList.valueOf(mColorError)
			view_binding.end.foregroundTintList = ColorStateList.valueOf(mColorError)
		} else {
			val focused = mEditText?.isFocused == true
			applyDividerColor(if (focused) mColorPrimary else mColorOutline)
			view_binding.message.text = mHelperText
			view_binding.message.visibility = if (mHelperEnable) VISIBLE else GONE
			mHelperTextColor?.let { view_binding.message.setTextColor(it) }
			mCounterTextColor?.let { view_binding.counter.setTextColor(it) }
			mPrefixTextColor?.let { view_binding.prefix.setTextColor(it) }
			mSuffixTextColor?.let { view_binding.suffix.setTextColor(it) }
			view_binding.start.foregroundTintList = mStartIconTint
			view_binding.end.foregroundTintList = mEndIconTint
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

	/**
	 * Sets the visual style of the leading-icon container's background
	 * ([ButtonLayoutStyle]): a three-dot "more" glyph, or the normal
	 * outlined look.
	 */
	fun setStartButtonLayoutStyle(style: ButtonLayoutStyle) {
		mStartButtonLayoutStyle = style
		applyStartLayout()
	}

	/** Returns the leading-icon container's current button layout style. */
	fun getStartButtonLayoutStyle(): ButtonLayoutStyle = mStartButtonLayoutStyle

	/** Refreshes only the leading icon's drawable, tint and visibility. */
	private fun applyStartIcon() {
		view_binding.imageStart.visibility = if (mStartIconEnable) VISIBLE else GONE
		view_binding.imageStart.setImageDrawable(mStartIcon)
		mStartIconTint?.let { view_binding.imageStart.imageTintList = it }
	}

	/**
	 * Refreshes only the leading-icon container's visibility and background.
	 *
	 * The background is entirely owned by [mStartButtonLayoutStyle] — it no
	 * longer participates in the shared focus/error state-list drawable
	 * installed by [installBorderSelector] (see that function's KDoc for
	 * why `start`/`end` were pulled out of it).
	 */
	private fun applyStartLayout() {
		view_binding.start.visibility = if (mStartLayoutEnable) VISIBLE else GONE
		(view_binding.container.layoutParams as LayoutParams).apply {
			marginStart = if (mStartLayoutEnable) 5.dp else 0
		}
		view_binding.start.background =
			if (mStartButtonLayoutStyle == ButtonLayoutStyle.THREE_DOT) {
				ThreeDotsDrawable(mColorOutline)
			} else {
				ContextCompat.getDrawable(
					context,
					R.drawable.ef_component_material_text_input_layout_outline
				)
			}
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

	/**
	 * Sets the visual style of the trailing-icon container's background
	 * ([ButtonLayoutStyle]): a three-dot "more" glyph, or the normal
	 * outlined look.
	 */
	fun setEndButtonLayoutStyle(style: ButtonLayoutStyle) {
		mEndButtonLayoutStyle = style
		applyEndLayout()
	}

	/** Returns the trailing-icon container's current button layout style. */
	fun getEndButtonLayoutStyle(): ButtonLayoutStyle = mEndButtonLayoutStyle

	/** Refreshes only the trailing icon's drawable, tint and visibility. */
	private fun applyEndIcon() {
		view_binding.imageEnd.visibility = if (mEndIconEnable) VISIBLE else GONE
		view_binding.imageEnd.setImageDrawable(mEndIcon)
		mEndIconTint?.let { view_binding.imageEnd.imageTintList = it }
	}

	/**
	 * Refreshes only the trailing-icon container's visibility and
	 * background — same deal as [applyStartLayout]: fully owned by
	 * [mEndButtonLayoutStyle], independent of the shared border selector.
	 */
	private fun applyEndLayout() {
		view_binding.end.visibility = if (mEndLayoutEnable) VISIBLE else GONE
		(view_binding.container.layoutParams as LayoutParams).apply {
			marginEnd = if (mEndLayoutEnable) 5.dp else 0
		}
		view_binding.end.background = if (mEndButtonLayoutStyle == ButtonLayoutStyle.THREE_DOT) {
			ThreeDotsDrawable(mColorOutline)
		} else {
			ContextCompat.getDrawable(
				context,
				R.drawable.ef_component_material_text_input_layout_outline
			)
		}
	}

	// wired directly to the target view instead of going through an apply*()
	// module.

	/**
	 * Sets the long-click listener for the **leading icon image** ([ImageView]
	 * inside `start`), analogous to `TextInputLayout.setStartIconLongOnClickListener`.
	 *
	 * @param click listener to invoke on long click, or `null` to remove it (this
	 *   also makes the icon non-clickable again).
	 */
	fun setStartImageOnLongClickListener(click: View.OnLongClickListener?) {
		view_binding.imageStart.setOnLongClickListener(click)
		view_binding.imageStart.isClickable = click != null
	}
	// module.

	/**
	 * Sets the click listener for the **leading icon image** ([ImageView]
	 * inside `start`), analogous to `TextInputLayout.setStartIconOnClickListener`.
	 *
	 * @param click listener to invoke on click, or `null` to remove it (this
	 *   also makes the icon non-clickable again).
	 */
	fun setStartImageOnClickListener(click: View.OnClickListener?) {
		view_binding.imageStart.setOnClickListener(click)
		view_binding.imageStart.isClickable = click != null
	}

	/**
	 * Sets the click listener for the **trailing icon image** ([ImageView]
	 * inside `end`), analogous to `TextInputLayout.setEndIconOnClickListener`.
	 *
	 * @param click listener to invoke on click, or `null` to remove it (this
	 *   also makes the icon non-clickable again).
	 */
	fun setEndImageOnClickListener(click: View.OnClickListener?) {
		view_binding.imageEnd.setOnClickListener(click)
		view_binding.imageEnd.isClickable = click != null
	}

	/**
	 * Sets the click listener for the **leading-icon container** (`start`),
	 * i.e. the whole background/ripple area around the leading icon rather
	 * than just the icon's own bounds. Useful for enlarging the tap target.
	 *
	 * @param click listener to invoke on click, or `null` to remove it.
	 */
	fun setStartLayoutOnClickListener(click: View.OnClickListener?) {
		view_binding.start.setOnClickListener(click)
		view_binding.start.isClickable = click != null
	}

	/**
	 * Sets the click listener for the **trailing-icon container** (`end`),
	 * i.e. the whole background/ripple area around the trailing icon rather
	 * than just the icon's own bounds. Useful for enlarging the tap target.
	 *
	 * @param click listener to invoke on click, or `null` to remove it.
	 */
	fun setEndLayoutOnClickListener(click: View.OnClickListener?) {
		view_binding.end.setOnClickListener(click)
		view_binding.end.isClickable = click != null
	}

	/**
	 * Convenience accessor for the inner [EditText]'s current text.
	 * Returns an empty string if no [EditText] child has been added yet.
	 */
	fun getText(): String = mEditText?.text.toString()

	/**
	 * Installs the border **state-list drawable** on `container` a single
	 * time, replacing the old approach of calling `setBackgroundResource()`
	 * with a different drawable resource on every focus/error/enabled change.
	 *
	 * `start`/`end` (the icon containers) are **not** part of this — their
	 * background is owned entirely by [mStartButtonLayoutStyle] /
	 * [mEndButtonLayoutStyle] (see [applyStartLayout] / [applyEndLayout]),
	 * since a three-dot "more" button doesn't make sense reacting to the
	 * field's own focus/error look. Installing the selector there too would
	 * just get immediately overwritten by those functions anyway.
	 *
	 * From this point on, `container`'s visible border is switched **by the
	 * platform**, based on two plain [View] booleans:
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
		view_binding.container.setBackgroundResource(selector)
	}

	/**
	 * Toggles the `isActivated` (error) flag on `container`, letting the
	 * state-list drawable installed by [installBorderSelector] pick the
	 * right sub-drawable natively — no drawable resource lookup runs here.
	 */
	private fun setBorderErrorState(error: Boolean) {
		view_binding.container.isActivated = error
	}

	/**
	 * Toggles the `isSelected` (focused-look) flag on `container`. Ignored
	 * while in an error state, since error already takes visual priority in
	 * the selector.
	 */
	private fun setBorderFocusedState(focused: Boolean) {
		view_binding.container.isSelected = focused
	}

	/** Applies a precomputed [color] to the divider — a flat color set, not a resource lookup. */
	private fun applyDividerColor(color: ColorStateList) {
		view_binding.divider.setDividerColor(color)
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
			throw RuntimeException("You can set only EditText, AutoCompleteTextView or MultiAutoCompleteTextView as child of ComponentTextInputLayout")

		if (mEditText != null)
			throw RuntimeException("ComponentTextInputLayout only supports a single EditText child")

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
			view_binding.divider.visibility = VISIBLE
			view_binding.dropdown.visibility = VISIBLE

			view_binding.dropdown.setOnClickListener {
				child.showDropDown()
			}
		}

		val height = params?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT

		child.layoutParams = LayoutParams(0, height).apply {
			startToEnd = view_binding.prefix.id
			endToStart = view_binding.suffix.id
			topToTop = view_binding.container.id
			bottomToBottom = view_binding.container.id

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
			updateFloatingHintLabel(child, animate = hasFocus)
			updatePlaceholderVisibility(child, animate = hasFocus)
		}

		view_binding.container.addView(child, index, child.layoutParams)

		// These modules depend on the EditText, so they only run now that it exists.
		setupFloatingHintLabel(child)
		setupPlaceholderLabel(child)
		applyHint()
		setupCounterTextWatcher(child)
		refreshCounterText()
		installAccessibilityDelegate(child)
		child.doOnTextChanged { _, _, _, _ ->
			updateFloatingHintLabel(child, animate = true)
			updatePlaceholderVisibility(child, animate = true)
		}
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
			override fun onInitializeAccessibilityNodeInfo(
				host: View,
				info: AccessibilityNodeInfoCompat
			) {
				super.onInitializeAccessibilityNodeInfo(host, info)
				if (mInErrorState) info.error = mErrorText
			}
		})
	}

	/**
	 * Cancels running animations and detaches the layout listener used by
	 * [setupFloatingHintLabel], so neither keeps a reference back into this
	 * view (and its [Context]) once it's removed from the window.
	 */
	override fun onDetachedFromWindow() {
		super.onDetachedFromWindow()
		mHintAnimator?.cancel()
		mHintAnimator = null
		mPlaceholderAnimator?.cancel()
		mPlaceholderAnimator = null
		mHintLayoutListener?.let { listener -> mEditText?.removeOnLayoutChangeListener(listener) }
	}

	override fun setEnabled(enabled: Boolean) {
		super.setEnabled(enabled)
		mEditText?.isEnabled = isEnabled
		view_binding.container.isEnabled = isEnabled
		view_binding.prefix.isEnabled = isEnabled
		view_binding.suffix.isEnabled = isEnabled
		view_binding.start.isEnabled = isEnabled
		view_binding.end.isEnabled = isEnabled
		view_binding.imageStart.isEnabled = isEnabled
		view_binding.imageEnd.isEnabled = isEnabled

// No setBackgroundResource() call needed: state_enabled="false" is
// already part of the selector installed by installBorderSelector(),
// and the isEnabled assignments above make the platform pick it up
// automatically via refreshDrawableState().
		applyDividerColor(if (isEnabled) mColorOutline else mColorDisabledDivider)
	}

	fun String.truncate(maxChars: Int): String {
		return if (this.length > maxChars) {
			"${this.take(maxChars)}..."
		} else {
			this
		}
	}

	/**
	 * A drawable that renders three equally sized dots horizontally.
	 *
	 * The dots are centered within the drawable bounds and their size is
	 * calculated relative to the smaller dimension of the bounds.
	 *
	 * @param color the color used to draw the dots.
	 */
	private class ThreeDotsDrawable(@ColorInt private val color: Int) : Drawable() {

		constructor(color: ColorStateList) : this(color.defaultColor)

		/**
		 * Paint used to render the dots.
		 *
		 * Antialiasing is enabled to produce smooth circle edges.
		 */
		private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			this.color = this@ThreeDotsDrawable.color
			style = Paint.Style.FILL
		}

		/**
		 * Draws the three dots centered within the drawable bounds.
		 *
		 * The dot radius is calculated from the smaller dimension of the
		 * drawable bounds. The horizontal spacing between the dots is
		 * derived from the calculated radius.
		 *
		 * @param canvas canvas on which the drawable is rendered.
		 */
		override fun draw(canvas: Canvas) {
			val bounds = bounds
			val centerY = bounds.exactCenterY()
			val radius = min(bounds.width(), bounds.height()) / 5f
			val spacing = radius * 3.9f
			val centerX = bounds.exactCenterX()

			canvas.drawCircle(centerX, centerY - spacing, radius, paint)
			canvas.drawCircle(centerX, centerY, radius, paint)
			canvas.drawCircle(centerX, centerY + spacing, radius, paint)
		}

		/**
		 * Sets the opacity of the dots.
		 *
		 * @param alpha alpha value applied to the drawable, from 0 to 255.
		 */
		override fun setAlpha(alpha: Int) {
			paint.alpha = alpha
		}

		/**
		 * Applies a color filter to the dots.
		 *
		 * @param colorFilter color filter applied when rendering the drawable,
		 * or `null` to remove the current filter.
		 */
		override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
			paint.colorFilter = colorFilter
		}

		/**
		 * Returns the opacity of this drawable.
		 *
		 * @return [PixelFormat.TRANSLUCENT], since the drawable supports
		 * transparency through its alpha value.
		 */
		override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
	}
}