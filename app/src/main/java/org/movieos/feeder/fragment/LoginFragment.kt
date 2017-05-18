package org.movieos.feeder.fragment

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import okhttp3.Credentials
import org.movieos.feeder.R
import org.movieos.feeder.api.Feedbin
import org.movieos.feeder.databinding.LoginFragmentBinding
import org.movieos.feeder.utilities.Settings
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginFragment : DataBindingFragment<LoginFragmentBinding>() {
    override fun createBinding(inflater: LayoutInflater, container: ViewGroup?): LoginFragmentBinding {
        val binding = LoginFragmentBinding.inflate(inflater, container, false)

        binding.button.setOnClickListener { v: View ->
            val credentials = Credentials.basic(binding.email.text.toString(), binding.password.text.toString())

            binding.button.isEnabled = false
            binding.passwordWrapper.error = null

            Feedbin.authenticate(activity, credentials, object : Callback<Void> {
                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                    if (response.code() == 200) {
                        Settings.saveCredentials(activity, credentials)
                        fragmentManager.beginTransaction()
                                .replace(R.id.main_content, EntriesFragment())
                                .commit()

                    } else {
                        binding.passwordWrapper.error = "Authentication failed"
                        binding.button.isEnabled = true
                    }
                }

                override fun onFailure(call: Call<Void>, t: Throwable) {
                    binding.passwordWrapper.error = "Network error: " + t.localizedMessage
                    binding.button.isEnabled = true
                }
            })
        }
        return binding
    }


}
