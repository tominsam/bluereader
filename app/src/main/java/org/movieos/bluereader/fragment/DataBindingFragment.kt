package org.movieos.bluereader.fragment

import android.arch.lifecycle.LifecycleFragment
import android.databinding.ViewDataBinding
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

abstract class DataBindingFragment<B : ViewDataBinding> : LifecycleFragment() {

    var binding: B? = null
        protected set

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = createBinding(inflater, container)
        return binding?.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding?.unbind()
        binding = null
    }

    protected abstract fun createBinding(inflater: LayoutInflater, container: ViewGroup?): B
}
