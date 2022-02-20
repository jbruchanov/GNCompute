package com.scurab.gncompute.app.tools

import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

fun <T> Fragment.viewLifecycleLazy(initializer: () -> T) = lifecycleAwareLazy({ viewLifecycleOwner.lifecycle }, initializer)
fun <T> Fragment.lifecycleLazy(initializer: () -> T) = lifecycleAwareLazy({ lifecycle }, initializer)

/**
 * Handy shortcut for easier property delegation using [LifecycleAwareLazy]
 *
 * @param T
 * @param lifecycleProvider
 * @param initializer
 */
fun <T> lifecycleAwareLazy(lifecycleProvider: () -> Lifecycle, initializer: () -> T): Lazy<T> = LifecycleAwareLazy(
    lifecycleProvider,
    initializer
)

/**
 * Handy shortcut for easier property delegation using [LifecycleAwareLazy]
 *
 * @param T
 * @param lifecycleProvider
 */
fun <T> lifecycleAwareProperty(lifecycleProvider: () -> Lifecycle): ReadWriteProperty<Any, T> = LifecycleAwareLazy(
    lifecycleProvider,
    { throw IllegalStateException("LifecycleAwareLazy created as a field delegate but used as lazy") }
)

/**
 * Lazy implementation with respect of lifecycle
 *
 * @param lifecycleProvider lambda is necessary, because [Lifecycle] doesn't have to be defined at
 * class instantiation time (e.g. fragment's view lifecycle provider)
 * @param initializer
 */
private class LifecycleAwareLazy<T>(
    private val lifecycleProvider: () -> Lifecycle,
    private val initializer: () -> T,
) : Lazy<T>, ReadWriteProperty<Any, T> {
    private var _value: Any? = UNINITIALIZED_VALUE
    private var isListening = false

    private val lifecycleObserver = object : LifecycleObserver {
        @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        @Suppress("unused")
        fun onDestroy() {
            reset()
        }
    }

    //region Lazy
    override val value: T
        get() {
            if (_value === UNINITIALIZED_VALUE) {
                val provider = lifecycleProvider()
                if (provider.currentState != Lifecycle.State.DESTROYED) {
                    provider.addObserver(lifecycleObserver)
                    isListening = true
                    _value = initializer()
                } else {
                    //we are destroyed, so don't cache, potential memleak if we were caching
                    return initializer()
                }
            }
            @Suppress("UNCHECKED_CAST")
            return _value as T
        }

    override fun isInitialized(): Boolean = _value !== UNINITIALIZED_VALUE
    //endregion

    //region property
    override fun getValue(thisRef: Any, property: KProperty<*>): T = _value.takeIf { it != UNINITIALIZED_VALUE } as T

    override fun setValue(thisRef: Any, property: KProperty<*>, value: T) {
        val provider = lifecycleProvider()
        if (!isListening && provider.currentState != Lifecycle.State.DESTROYED) {
            provider.addObserver(lifecycleObserver)
            isListening = true
        }
        _value = value
    }
    //endregion property

    override fun toString(): String = if (isInitialized()) value.toString() else "Lazy value not initialized yet."

    fun reset() {
        _value = UNINITIALIZED_VALUE
        lifecycleProvider().removeObserver(lifecycleObserver)
        isListening = false
    }

    companion object {
        private val UNINITIALIZED_VALUE = Any()
    }
}
