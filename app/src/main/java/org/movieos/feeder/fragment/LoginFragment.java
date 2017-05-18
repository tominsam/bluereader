package org.movieos.feeder.fragment;

import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.movieos.feeder.R;
import org.movieos.feeder.api.Feedbin;
import org.movieos.feeder.databinding.LoginFragmentBinding;
import org.movieos.feeder.utilities.Settings;

import okhttp3.Credentials;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginFragment extends DataBindingFragment<LoginFragmentBinding> {
    @NonNull
    @Override
    protected LoginFragmentBinding createBinding(@NotNull LayoutInflater inflater, @Nullable ViewGroup container) {
        LoginFragmentBinding binding = LoginFragmentBinding.inflate(inflater, container, false);

        binding.button.setOnClickListener(v -> {
            if (getBinding() == null) {
                return;
            }

            String credentials = Credentials.basic(getBinding().email.getText().toString(), getBinding().password.getText().toString());

            getBinding().button.setEnabled(false);
            getBinding().passwordWrapper.setError(null);

            Feedbin.Companion.authenticate(getActivity(), credentials, new Callback<Void>() {
                    @Override
                    public void onResponse(Call<Void> call, Response<Void> response) {
                        if (response.code() == 200) {
                            Settings.INSTANCE.saveCredentials(getActivity(), credentials);
                            getFragmentManager().beginTransaction()
                                .replace(R.id.main_content, new EntriesFragment())
                                .commit();

                        } else {
                            getBinding().passwordWrapper.setError("Authentication failed");
                            getBinding().button.setEnabled(true);
                        }
                    }

                    @Override
                    public void onFailure(Call<Void> call, Throwable t) {
                        getBinding().passwordWrapper.setError("Network error: " + t.getLocalizedMessage());
                        getBinding().button.setEnabled(true);
                    }
                });
        });
        return binding;
    }


}
