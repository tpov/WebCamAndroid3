package com.dolya.webcam.util

import androidx.lifecycle.Observer

interface NonNullObserver<T> : Observer<T> {

  fun onNonNullChanged(t: T)

  override fun onChanged(t: T?) {
    t?.let { onNonNullChanged(it) }
  }
}

fun <T> NonNullObserver(func: (T) -> Unit) = object : NonNullObserver<T> {
  override fun onNonNullChanged(t: T) = func(t)
}
