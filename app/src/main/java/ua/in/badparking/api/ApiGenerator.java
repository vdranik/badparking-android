package ua.in.badparking.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;


import com.squareup.okhttp.OkHttpClient;

import java.util.concurrent.TimeUnit;

import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.client.OkClient;
import retrofit.converter.GsonConverter;
import ua.in.badparking.BuildConfig;


public enum ApiGenerator {
    INST;

    private static final boolean DEBUG = BuildConfig.DEBUG;

    private <S> S buildApi(Class<S> apiClass, RestAdapter.Builder builder) {
        RestAdapter adapter = builder.build();
        adapter.setLogLevel(RestAdapter.LogLevel.FULL);
        return adapter.create(apiClass);
    }

    public <S> S createApi(Class<S> apiClass, String baseUrl, final String token) {
        RestAdapter.Builder builder;
        OkHttpClient client = new OkHttpClient();

        client.setConnectTimeout(30, TimeUnit.SECONDS);
        client.setReadTimeout(300, TimeUnit.SECONDS);

        RequestInterceptor requestInterceptor = new RequestInterceptor() {
            @Override
            public void intercept(RequestFacade request) {
                request.addHeader("Authorization", "JWT " + token);
            }
        };

        GsonBuilder gsonBuilder = new GsonBuilder();
        Gson gson = gsonBuilder.create();

        builder = new RestAdapter.Builder()
                .setConverter(new GsonConverter(gson))
                .setEndpoint(baseUrl)
                .setLogLevel(DEBUG ? RestAdapter.LogLevel.FULL : RestAdapter.LogLevel.NONE)
                .setClient(new OkClient(client));
        if (token != null) {
            builder.setRequestInterceptor(requestInterceptor);
        }

        return buildApi(apiClass, builder);
    }

}
