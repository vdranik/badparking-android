package ua.in.badparking.ui.fragments;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;

import ua.in.badparking.model.Claim;

import static ua.in.badparking.utils.Constants.CLAIM_STATE;

/**
 * Created by Dima Kovalenko on 7/4/16.
 */
public class BaseFragment extends Fragment {

    private InputMethodManager inputManager;
    protected boolean isTablet;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        inputManager = (InputMethodManager)getContext()
                .getSystemService(Context.INPUT_METHOD_SERVICE);

        boolean large = ((getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_LARGE);
        boolean xlarge = ((getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) == 4);
        isTablet = large || xlarge;
    }

    public void removePhoneKeypad() {
        IBinder binder = getView().getWindowToken();
        inputManager.hideSoftInputFromWindow(binder,
                InputMethodManager.HIDE_NOT_ALWAYS);
    }

    public void logging(Class clazz, Claim claim){
        Log.d(CLAIM_STATE, "Fragment: " + this.getClass().getSimpleName());
        Log.d(CLAIM_STATE, "plates - " + claim.getLicensePlates());
        Log.d(CLAIM_STATE, "crimetypes - " + claim.getCrimetypes());
        Log.d(CLAIM_STATE, "latitude - " + claim.getLatitude());
        Log.d(CLAIM_STATE, "longitude - " + claim.getLongitude());
        Log.d(CLAIM_STATE, "city - " + claim.getCity());
        Log.d(CLAIM_STATE, "adress - " + claim.getAddress());
        Log.d(CLAIM_STATE, "photoFiles - " + claim.getPhotoFiles().size());
        Log.d(CLAIM_STATE, "====================================================================");
    }
}
