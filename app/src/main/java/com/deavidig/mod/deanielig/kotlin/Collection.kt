package com.deavidig.mod.deanielig.kotlin

import androidx.collection.ArraySet

/**
 * Returns a new [ArrayList] containing all elements of this [ArraySet].
 *
 * Creates a copy of the elements while preserving their iteration order.
 *
 * @param E The type of elements contained in the set.
 * @return A new [ArrayList] containing all elements of this [ArraySet].
 */
public inline fun <E> ArraySet<E>.toArrayList(): ArrayList<E> = ArrayList<E>(this)
