package ua.in.badparking.ui.fragments;

import android.app.ProgressDialog;
import android.content.ContentValues;
import android.database.Cursor;
import android.graphics.Color;
import android.hardware.Camera;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.openalpr.Alpr;
import org.openalpr.app.AlprResult;
import org.openalpr.app.AlprResultItem;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.ExecutionException;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import ua.in.badparking.CameraWrapper;
import ua.in.badparking.Constants;
import ua.in.badparking.R;
import ua.in.badparking.services.ClaimState;
import ua.in.badparking.ui.activities.MainActivity;
import ua.in.badparking.ui.adapters.PhotoAdapter;

/**
 * @author Dima Kovalenko
 * @author Vadik Kovalsky
 * @author Volodymyr Dranyk
 */
@SuppressWarnings("deprecation")
public class CaptureFragment extends BaseFragment implements View.OnClickListener, PhotoAdapter.PhotosUpdatedListener{

    private static final String TAG = CaptureFragment.class.getName();

    @BindView(R.id.surface_container)
    protected FrameLayout surfaceContainer;
    private SurfaceView surfaceView;
    @BindView(R.id.recyclerView)
    protected RecyclerView recyclerView;
    private PhotoAdapter photoAdapter;
    @BindView(R.id.message)
    protected TextView messageView;
    @BindView(R.id.snap)
    protected View snapButton;
    @BindView(R.id.next_button)
    protected View nextButton;
    @BindView(R.id.upLine)
    protected FrameLayout upRedLine;
    @BindView(R.id.bottomLine)
    protected FrameLayout bottomRedLine;
    @BindView(R.id.plateTextView)
    protected TextView plate;
    private Unbinder unbinder;
    private CameraWrapper cameraWrapper;
    private AlprTask alprTask;


    public static CaptureFragment newInstance() {
        return new CaptureFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_capture, container, false);
        unbinder = ButterKnife.bind(this, rootView);
        surfaceView = new SurfaceView(inflater.getContext());
        return rootView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        snapButton.setOnClickListener(this);
        nextButton.setOnClickListener(this);

        LinearLayoutManager layoutManager = new LinearLayoutManager(getActivity());
        layoutManager.setOrientation(LinearLayoutManager.HORIZONTAL);
        recyclerView.setLayoutManager(layoutManager);

        photoAdapter = new PhotoAdapter(getActivity());
        photoAdapter.setListener(this);
        recyclerView.setAdapter(photoAdapter);
        recyclerView.setHasFixedSize(true);
        onPhotosUpdated();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        unbinder.unbind();
    }

    @Override
    public void onPhotosUpdated() {
        int photosTaken = ClaimState.INST.getClaim().getPhotoFiles().size();
        nextButton.setVisibility(photosTaken > 1 ? View.VISIBLE : View.GONE);
        snapButton.setVisibility(photosTaken > 1 ? View.GONE : View.VISIBLE);
        if (photosTaken == 0) {
            messageView.setText(R.string.capture_claim);
            upRedLine.setBackgroundColor(Color.TRANSPARENT);
            upRedLine.setAlpha(0f);
            bottomRedLine.setBackgroundColor(Color.TRANSPARENT);
            bottomRedLine.setAlpha(0f);
        } else if (photosTaken == 1) {
            messageView.setText(R.string.capture_plates);
            upRedLine.setBackgroundColor(getResources().getColor(R.color.red));
            upRedLine.setAlpha(0.2f);
            bottomRedLine.setBackgroundColor(getResources().getColor(R.color.red));
            bottomRedLine.setAlpha(0.2f);
        } else {
            messageView.setText("");
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        if (surfaceView != null) {
            surfaceView.getHolder().removeCallback(cameraWrapper.getSurfaceCamCallback());
            cameraWrapper.setSurfaceCamCallback(null);
        }

        if (cameraWrapper.getCamera() != null) {
            cameraWrapper.getCamera().setPreviewCallback(null);
            surfaceContainer.removeView(surfaceView);
        }

        releaseCamera();
    }

    private void releaseCamera() {
        if (cameraWrapper.getCamera() != null) {
            cameraWrapper.getCamera().release();
            cameraWrapper.setCamera(null);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (surfaceView != null) {
            cameraWrapper = new CameraWrapper(getActivity());
            if (cameraWrapper.getCamera() != null) {

                SurfaceHolder surfaceHolder = surfaceView.getHolder();
                surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
                surfaceHolder.addCallback(cameraWrapper.getSurfaceCamCallback());

                Camera.Size containerSize = cameraWrapper.getCameraContainerSize();
                surfaceContainer.addView(surfaceView,
                        new ViewGroup.LayoutParams(new ViewGroup.LayoutParams(
                                containerSize.height,containerSize.width)));
            }
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.snap:
                cameraWrapper.getCamera().takePicture(null, null, jpegCallback);
                break;
            case R.id.next_button:
                ((MainActivity)getActivity()).moveToNext();
                break;
        }
    }

    private void onImageFileCreated(String photoPath) {
        ClaimState.INST.getClaim().addPhoto(photoPath);
        int numberOfPhotosTaken = ClaimState.INST.getClaim().getPhotoFiles().size();
        snapButton.setVisibility(numberOfPhotosTaken >= 2 ? View.GONE : View.VISIBLE);

        if(numberOfPhotosTaken == 2){
            String openAlprConfFile = Constants.ANDROID_DATA_DIR + File.separatorChar +
                    Constants.RUNTIME_DATA_DIR_ASSET + File.separatorChar +Constants.OPENALPR_CONF_FILE;

            alprTask = new AlprTask();
            String parameters[] = {Constants.REGION, "", photoPath, openAlprConfFile, "1"};
            alprTask.execute(parameters);
        }

        photoAdapter.notifyDataSetChanged();
        onPhotosUpdated();
    }

    public Camera.PictureCallback jpegCallback = new Camera.PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            SaveImageTask saveImageTask = (SaveImageTask) new SaveImageTask().execute(data);

            try {
                onImageFileCreated(saveImageTask.get());
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
            camera.startPreview();
        }
    };

    private class SaveImageTask extends AsyncTask<byte[], Void, String> {

        @Override
        protected String doInBackground(byte[]... data) {
            Uri imageFileUri = getActivity().getContentResolver().insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new ContentValues());
            try {
                OutputStream imageFileOS = null;
                if (imageFileUri != null) {
                    imageFileOS = getActivity().getContentResolver().openOutputStream(
                            imageFileUri);
                }

                if (imageFileOS != null) {
                    imageFileOS.write(data[0]);
                    imageFileOS.flush();
                    imageFileOS.close();
                }
            } catch (IOException e){
                e.printStackTrace();
            }
            return getPathFromUri(imageFileUri);
        }
    }

    private String getPathFromUri(Uri uri) {
        String selected = null;
        String[] filePathColumn = {MediaStore.Images.Media.DATA};
        Cursor cursor = getActivity().getContentResolver().query(uri, filePathColumn, null, null, null);
        if (cursor != null) {
            cursor.moveToFirst();
            int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
            selected = cursor.getString(columnIndex);
            cursor.close();
        }
        return selected;
    }


    class AlprTask extends AsyncTask<String, Void, AlprResult> {

        private Alpr alpr;
        private ProgressDialog progressDialog;
        @Override
        protected void onPreExecute() {
            if(alpr == null){
                alpr = Alpr.Factory.create();
            }
        }

        @Override
        protected void onProgressUpdate(Void... values) {
            if(progressDialog == null){
              //  prepareProgressDialog();
            }
        }

        @Override
        protected AlprResult doInBackground(String... parameter) {
            if(parameter.length == 5) {
                String country = parameter[0];
                String region = parameter[1];
                String filePath = parameter[2];
                String confFile = parameter[3];
                String stopN = parameter[4];

                int topN = Integer.parseInt(stopN);

                String result = alpr.recognizeWithCountryRegionNConfig(country, region, filePath,
                        confFile, topN);
                AlprResult alprResult = processJsonResult(result);

                Log.i(TAG, "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!"+result);
                ///deleteImageFile(filePath);

                return alprResult;
            }

//            AlprResult alprResult = new AlprResult();
//            alprResult.addCandidate(new AlprCandidate());
            return new AlprResult();
        }

        private AlprResult processJsonResult(String result) {
            AlprResult alprResult = new AlprResult();
            try {
                JSONObject jsonObject = new JSONObject(result);
                addResult(jsonObject, alprResult);
            } catch (JSONException e) {
                Log.e(TAG, "Exception parsing JSON result", e);
                alprResult.setRecognized(false);
            }
            return alprResult;
        }

        private void addResult(JSONObject jsonObject, AlprResult alprResult) throws JSONException {
            JSONArray resultArray = jsonObject.getJSONArray(Constants.JSON_RESULT_ARRAY_NAME);
            alprResult.setProcessingTime(jsonObject.getLong("processing_time_ms"));
            AlprResultItem alprResultItem = null;

            for (int i = 0; i < resultArray.length(); i++) {
                JSONObject resultObject = resultArray.getJSONObject(i);
                alprResultItem = new AlprResultItem();
                alprResultItem.setPlate(resultObject.getString("plate"));
                alprResultItem.setProcessingTime(resultObject.getLong("processing_time_ms"));
                alprResultItem.setConfidence(resultObject.getDouble("confidence"));
                alprResult.addResultItem(alprResultItem);
            }
        }

        @Override
        public void onPostExecute(AlprResult alprResult) {
            if(alprResult.isRecognized()) {
                List<AlprResultItem> resultItems = alprResult.getResultItems();
                if (resultItems.size() > 0) {
                    AlprResultItem resultItem = resultItems.get(0);
                    String plateNumber = resultItem.getPlate();
                    plate.setText(plateNumber);
                    ClaimState.INST.getClaim().addPhoto(plateNumber);
                }
               // cleanUpProgressDialog();
            }else {
                plate.setText("Роспізнати не вдалось");
               // cleanUpProgressDialog();
            }
        }

        private void prepareProgressDialog(){
            progressDialog = new ProgressDialog(getActivity());
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.setMessage("Роспізнавання...");
            progressDialog.show();
        }

//        private void cleanUpProgressDialog() {
//            progressDialog.dismiss();
//            progressDialog = null;
//        }

//        private void deleteImageFile(final String filePath){
//            File file = new File(filePath);
//            if(file.exists()){
//                file.delete();
//               // Log.i(LOG_TAG, String.format("Deleted file %s", filePath));
//            }
//        }
    }
}