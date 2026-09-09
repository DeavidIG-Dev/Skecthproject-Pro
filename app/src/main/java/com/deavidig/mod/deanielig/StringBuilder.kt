package com.deavidig.mod.deanielig

fun StringBuilder.newline(): StringBuilder {
	this.append('\n')
	return this
}

fun StringBuilder.tab(): StringBuilder {
	this.append('\t')
	return this
}