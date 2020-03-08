package com.skogberglabs.pics

enum class AppMode {
    Public, Private
}

data class AppColors(
    val statusBar: Int,
    val navigationBar: Int,
    val background: Int,
    val actionBar: Int
)
