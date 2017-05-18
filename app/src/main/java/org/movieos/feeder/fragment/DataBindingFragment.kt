package org.movieos.feeder.fragment

import android.databinding.ViewDataBinding
import android.os.Bundle
import android.support.v4.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

abstract class DataBindingFragment<B : ViewDataBinding> : Fragment() {

    var binding: B? = null
        protected set

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = createBinding(inflater, container)
        return binding!!.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (binding != null) {
            binding!!.unbind()
            binding = null
        }
    }

    protected abstract fun createBinding(inflater: LayoutInflater, container: ViewGroup?): B
}
