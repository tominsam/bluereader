package org.movieos.feeder.fragment;

import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import okhttp3.Credentials;
import org.movieos.feeder.R;
import org.movieos.feeder.Settings;
import org.movieos.feeder.api.Feedbin;
import org.movieos.feeder.databinding.LoginFragmentBinding;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import timber.log.Timber;

public class LoginFragment extends DataBindingFragment<LoginFragmentBinding> {
    @NonNull
    @Override
    protected LoginFragmentBinding createBinding(LayoutInflater inflater, ViewGroup container) {
        LoginFragmentBinding binding = LoginFragmentBinding.inflate(inflater, container, false);

        binding.button.setOnClickListener(v -> {
            Timber.i("here with %s", mBinding);

            if (mBinding == null) {
                return;
            }
            String credentials = Credentials.basic(mBinding.email.getText().toString(), mBinding.password.getText().toString());
            Timber.i("creds are %s", credentials);

            mBinding.button.setEnabled(false);
            mBinding.passwordWrapper.setError(null);

            Feedbin.authenticate(getActivity(), credentials, new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        Timber.i("response: %s", response);
                        if (response.code() == 200) {
                            Settings.saveCredentials(getActivity(), credentials);
                            getFragmentManager().beginTransaction()
                                .replace(R.id.main_content, new SyncFragment())
                                .commitNow();

                        } else {
                            mBinding.passwordWrapper.setError("Authentication failed");
                            mBinding.button.setEnabled(true);
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        mBinding.passwordWrapper.setError("Network error: " + t.getLocalizedMessage());
                        mBinding.button.setEnabled(true);
                    }
                });
        });
        return binding;
    }


}
