package org.movieos.bluereader.fragment

import android.app.Activity
import android.content.Intent
import android.view.LayoutInflater
import android.view.ViewGroup
import com.google.android.gms.auth.api.Auth
import com.google.android.gms.auth.api.credentials.Credential
import com.google.android.gms.auth.api.credentials.CredentialRequest
import com.google.android.gms.common.api.Status
import okhttp3.Credentials
import org.movieos.bluereader.MainActivity
import org.movieos.bluereader.R
import org.movieos.bluereader.api.Feedbin
import org.movieos.bluereader.databinding.LoginFragmentBinding
import org.movieos.bluereader.utilities.Settings
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import timber.log.Timber


class LoginFragment : DataBindingFragment<LoginFragmentBinding>() {

    override fun createBinding(inflater: LayoutInflater, container: ViewGroup?): LoginFragmentBinding {
        val binding = LoginFragmentBinding.inflate(inflater, container, false)

        binding.button.setOnClickListener {
            val credentials = Credentials.basic(binding.email.text.toString(), binding.password.text.toString())

            binding.button.isEnabled = false
            binding.passwordWrapper.error = null

            Feedbin.authenticate(activity, credentials, object : Callback<Void> {
                override fun onResponse(call: Call<Void>, response: Response<Void>) {
                    if (response.code() == 200) {
                        saveSmartLock(binding.email.text.toString(), binding.password.text.toString())
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


        val request = CredentialRequest.Builder()
                .setPasswordLoginSupported(true)
                .build()

        Auth.CredentialsApi.request((activity as MainActivity).client, request).setResultCallback { result ->
            if (result.status.isSuccess) {
                // See "Handle successful credential requests"
                onCredentialRetrieved(result.credential)
            } else {
                // See "Handle unsuccessful and incomplete credential requests"
                resolveResult(result.status)
            }
        }

        return binding
    }

    private fun resolveResult(status: Status) {
        if (status.hasResolution()) {
            Timber.i("status has resolution")
            status.startResolutionForResult(activity, 990)
        } else {
            Timber.i("nothing to do")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == 990 && resultCode == Activity.RESULT_OK) {
            val credential: Credential? = data?.getParcelableExtra(Credential.EXTRA_KEY)
            if (credential != null) {
                onCredentialRetrieved(credential)
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    fun onCredentialRetrieved(credential: Credential) {
        binding?.email?.setText(credential.id)
        binding?.password?.setText(credential.password)
    }

    private fun saveSmartLock(email: String, password: String) {
        // blind save
        val activity = activity as MainActivity
        val credential = Credential.Builder(email).setPassword(password).build()
        Auth.CredentialsApi.save(activity.client, credential).setResultCallback { result ->
            if (result.status.hasResolution()) {
                result.status.startResolutionForResult(activity, 0)
            }
        }
    }


}
