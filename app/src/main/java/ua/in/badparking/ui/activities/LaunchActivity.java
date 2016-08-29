package ua.in.badparking.ui.activities;

import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.preference.PreferenceManager;

import com.google.inject.Inject;
import com.squareup.okhttp.Call;
import com.squareup.okhttp.Callback;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import roboguice.activity.RoboActivity;
import roboguice.inject.ContentView;
import ua.in.badparking.Constants;
import ua.in.badparking.R;
import ua.in.badparking.events.TypesLoadedEvent;
import ua.in.badparking.services.ClaimState;
import ua.in.badparking.services.api.ClaimsService;

@ContentView(R.layout.activity_launch)
public class LaunchActivity extends RoboActivity {

    @Inject
    private ClaimsService mClaimsService;
    private final OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!PreferenceManager.getDefaultSharedPreferences(
                getApplicationContext())
                .getBoolean(Constants.PREF_INSTALLED_KEY, false)) {

            PreferenceManager.getDefaultSharedPreferences(
                    getApplicationContext())
                    .edit().putBoolean(Constants.PREF_INSTALLED_KEY, true).commit();

            /*PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).
                    edit().putString(Constants.RUNTIME_DATA_DIR_ASSET, Constants.ANDROID_DATA_DIR).commit();*/

            copyAssetFolder(getAssets(), Constants.RUNTIME_DATA_DIR_ASSET,
                    Constants.ANDROID_DATA_DIR + File.separatorChar + Constants.RUNTIME_DATA_DIR_ASSET);
        }

        EventBus.getDefault().register(this);
        mClaimsService.getTypes();
        String url = Constants.BASE_URL + "/profiles/login/dummy";
        get(url, new Callback() {
            @Override
            public void onFailure(Request request, IOException e) {
//                    EventBus.getDefault().post(new ClaimPostedEvent(e.getMessage()));
            }

            @Override
            public void onResponse(Response response) throws IOException {
                ClaimState.INST.setToken(response.headers().get("X-JWT"));
            }
        });
    }

    @Subscribe
    public void onTypesLoaded(TypesLoadedEvent event) {
        ClaimState.INST.setCrimeTypes(event.getCrimeTypes());
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
    Call get(String url, Callback callback) {
        Request request = new Request.Builder()
                .url(url)
                .build();
        Call call = client.newCall(request);
        call.enqueue(callback);
        return call;
    }

    private static boolean copyAssetFolder(AssetManager assetManager,
                                           String fromAssetPath, String toPath) {
        try {
            String[] files = assetManager.list(fromAssetPath);
            new File(toPath).mkdirs();
            boolean res = true;
            for (String file : files)
                if (file.contains("."))
                    res &= copyAsset(assetManager,
                            fromAssetPath + "/" + file,
                            toPath + "/" + file);
                else
                    res &= copyAssetFolder(assetManager,
                            fromAssetPath + "/" + file,
                            toPath + "/" + file);
            return res;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static boolean copyAsset(AssetManager assetManager,
                                     String fromAssetPath, String toPath) {
        InputStream in = null;
        OutputStream out = null;
        try {
            in = assetManager.open(fromAssetPath);
            new File(toPath).createNewFile();
            out = new FileOutputStream(toPath);
            copyFile(in, out);
            in.close();
            in = null;
            out.flush();
            out.close();
            out = null;
            return true;
        } catch(Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
        }
    }
}
