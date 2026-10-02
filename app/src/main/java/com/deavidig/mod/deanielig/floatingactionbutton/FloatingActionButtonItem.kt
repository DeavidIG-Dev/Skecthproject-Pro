package com.deavidig.mod.deanielig.floatingactionbutton

import android.content.Context
import android.util.AttributeSet
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

/**
 * A single item of a [FloatingActionButtonGroup].
 *
 * Deliberately has no custom API of its own — it inherits the full XML attribute surface
 * of [ExtendedFloatingActionButton] as-is (`app:icon`, `android:text`, `app:iconTint`,
 * `app:backgroundTint`, `android:textColor`, `app:cornerRadius`, `app:elevation`, motion
 * specs, etc.). Selected appearance ("state_selected") is handled through `ColorStateList`
 * resources passed to `iconTint`/`backgroundTint`/`textColor` — no separate "Selected"
 * properties are needed, since [FloatingActionButtonGroup] flips [isSelected] on the item
 * directly and the state list resolves the right color automatically.
 *
 * @constructor Creates a new [FloatingActionButtonItem].
 * @param context the view's [Context]
 * @param attrs XML attribute set, if inflated from layout
 * @param defStyleAttr default style attribute, defaults to [ExtendedFloatingActionButton]'s own style
 * @author DeanielIG, DeavidIG
 */
class FloatingActionButtonItem @JvmOverloads constructor(
	context: Context,
	attrs: AttributeSet? = null,
	defStyleAttr: Int = com.google.android.material.R.attr.extendedFloatingActionButtonStyle
) : ExtendedFloatingActionButton(context, attrs, defStyleAttr)