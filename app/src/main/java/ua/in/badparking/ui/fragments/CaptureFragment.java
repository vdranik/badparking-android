package ua.in.badparking.ui.fragments;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.cameraview.CameraView;

import org.greenrobot.eventbus.EventBus;

import java.io.File;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import ua.in.badparking.R;
import ua.in.badparking.events.ShowHeaderEvent;
import ua.in.badparking.services.ClaimService;
import ua.in.badparking.ui.activities.MainActivity;
import ua.in.badparking.ui.adapters.PhotoAdapter;
import ua.in.badparking.utils.ConfirmationDialogFragment;
import ua.in.badparking.utils.Constants;
import ua.in.badparking.utils.PhotoUtils;

import static android.app.Activity.RESULT_CANCELED;

/**
 * @author Dima Kovalenko
 * @author Vadik Kovalsky
 * @author Volodymyr Dranyk
 */
@SuppressWarnings("deprecation")
public class CaptureFragment extends BaseFragment implements View.OnClickListener, PhotoAdapter.PhotosUpdatedListener {

    private static final String TAG = CaptureFragment.class.getName();

    private static final int REQUEST_CAMERA_PERMISSION = 1;
    private static final int PHOTO_INTENT_WITH_FILENAME = 1001;
    private static final String FRAGMENT_DIALOG = "dialog";

    @BindView(R.id.camera)
    protected CameraView cameraView;
    private Handler mBackgroundHandler;

    @BindView(R.id.recyclerView)
    protected RecyclerView recyclerView;

    @BindView(R.id.message)
    protected TextView messageView;

    @BindView(R.id.platesEditText)
    protected EditText platesEditText;

    @BindView(R.id.snap)
    protected View snapButton;

    @BindView(R.id.next_button)
    protected View nextButton;

    private Unbinder unbinder;
    private PhotoAdapter photoAdapter;

    private boolean safeToTakePicture = false;
    private int orientationDegree = 0;
    private OrientationEventListener orientationEventListener;

    //used if cameraView can't start
    @BindView(R.id.cantStartCameraViewMessage)
    protected TextView cantStartCameraViewText;
    private Uri nativeCameraPhotoFileUri;

    public static CaptureFragment newInstance() {
        return new CaptureFragment();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_capture, container, false);
        unbinder = ButterKnife.bind(this, rootView);

        if (cameraView != null) {
            cameraView.addCallback(mCallback);
        }

        orientationEventListener = new OrientationEventListener(getContext()) {
            @Override
            public void onOrientationChanged(int orientation) {
                orientationDegree = roundOrientation(orientation);
            }
        };

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

        photoAdapter = new PhotoAdapter(getActivity(), false);
        photoAdapter.setListener(this);
        recyclerView.setAdapter(photoAdapter);
        recyclerView.setHasFixedSize(true);
        onPhotosUpdated();

        platesEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                int photosTaken = ClaimService.INST.getClaim().getPhotoFiles().size();
                String result = editable.toString().replaceAll(" ", "");
                if (!editable.toString().equals(result)) {
                    platesEditText.setText(result);
                    platesEditText.setSelection(result.length());
                }

                nextButton.setEnabled(photosTaken > 1 && platesEditText.getText().toString().length() >=
                        Constants.MIN_CARPLATE_LENGTH ? true : false);
            }
        });
    }

    @Override
    public void onDestroyView() {
        if (mBackgroundHandler != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mBackgroundHandler.getLooper().quitSafely();
            } else {
                mBackgroundHandler.getLooper().quit();
            }
            mBackgroundHandler = null;
        }
        unbinder.unbind();
        super.onDestroyView();
    }

    @Override
    public void onPhotosUpdated() {
        int photosTaken = ClaimService.INST.getClaim().getPhotoFiles().size();
        snapButton.setVisibility(photosTaken > 1 ? View.GONE : View.VISIBLE);

        if (photosTaken == 0) {
            messageView.setText(R.string.capture_claim);
        } else if (photosTaken == 1) {
            nextButton.setVisibility(View.GONE);
            messageView.setText(R.string.capture_plates);
            messageView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.VISIBLE);
        } else if (TextUtils.isEmpty(ClaimService.INST.getClaim().getLicensePlates())) {
            EventBus.getDefault().post(new ShowHeaderEvent(false));
            messageView.setText("Введiть номернi знаки...");
            nextButton.setEnabled(false);
            nextButton.setVisibility(View.VISIBLE);
            platesEditText.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            if (TextUtils.isEmpty(ClaimService.INST.getClaim().getLicensePlates())) {
                showCarPlateKeyboard();
            }
        } else {
            nextButton.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onPause() {

        if(cameraView != null) {
            cameraView.stop();
        }

        removePhoneKeypad();
        orientationEventListener.disable();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        orientationEventListener.enable();

        if(ClaimService.INST.getClaim().getPhotoFiles().size() > 1 && !TextUtils.isEmpty(ClaimService.INST.getClaim().getLicensePlates())){
            nextButton.setVisibility(View.VISIBLE);
        }

        if (ContextCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {

            if(cameraView != null) {
                try {
                    //throw new RuntimeException(); //test cameraView.start() crush
                    cameraView.start();
                    safeToTakePicture = true;
                } catch (RuntimeException re) {
                    cameraView = null;
                    cantStartCameraViewText.setVisibility(View.VISIBLE);
                }
            }

        } else if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(), Manifest.permission.CAMERA)) {
            ConfirmationDialogFragment
                    .newInstance(R.string.camera_permission_confirmation,
                            new String[] {Manifest.permission.CAMERA},
                            REQUEST_CAMERA_PERMISSION,
                            R.string.camera_permission_not_granted)
                    .show(getActivity().getSupportFragmentManager(), FRAGMENT_DIALOG);
        } else {
            ActivityCompat.requestPermissions(getActivity(), new String[] {Manifest.permission.CAMERA},
                    REQUEST_CAMERA_PERMISSION);
        }

        claimStateLogging();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.snap:
                if (cameraView != null && cameraView.isCameraOpened() && isSafeToTakePicture()) {
                    ClaimService.INST.getClaim().addPhoto(""); //"wait preview" mode
                    photoAdapter.notifyDataSetChanged();
                    cameraView.takePicture();
                    snapButton.setVisibility(View.GONE);
                    setSafeToTakePicture(false);
                } else if (cameraView == null){
                    File currentPhotoFile = new File(getActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES), PhotoUtils.getFileName());
                    nativeCameraPhotoFileUri = Uri.fromFile(currentPhotoFile);
                    Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, nativeCameraPhotoFileUri);
                    startActivityForResult(intent, PHOTO_INTENT_WITH_FILENAME);
                }
                break;

            case R.id.next_button:
                nextButton.setEnabled(false);
                InputMethodManager imm = (InputMethodManager)getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(view.getWindowToken(), 0);

                if(TextUtils.isEmpty(ClaimService.INST.getClaim().getLicensePlates())) {
                    ClaimService.INST.getClaim().setLicensePlates(platesEditText.getText().toString());
                }

                EventBus.getDefault().post(new ShowHeaderEvent(true));
                platesEditText.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);

                ((MainActivity)getActivity()).showPage(MainActivity.PAGE_CLAIM_TYPES);

                break;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_CANCELED) {
            return;
        }

        switch(requestCode) {
            case PHOTO_INTENT_WITH_FILENAME:
                PhotoUtils.resize(nativeCameraPhotoFileUri.getPath(), orientationDegree);
                photoAdapter.notifyDataSetChanged();
                onImageFileCreated(nativeCameraPhotoFileUri.getPath());
                break;
        }
    }

    private void onImageFileCreated(String photoPath) {
        ClaimService.INST.getClaim().addPhoto(photoPath);
        int numberOfPhotosTaken = ClaimService.INST.getClaim().getPhotoFiles().size();
        snapButton.setVisibility(numberOfPhotosTaken >= 2 ? View.GONE : View.VISIBLE);
        photoAdapter.notifyDataSetChanged();
        onPhotosUpdated();
    }

    private CameraView.Callback mCallback = new CameraView.Callback() {
        @Override
        public void onCameraOpened(CameraView cameraView) {
            Log.d(TAG, "onCameraOpened - " + cameraView.isCameraOpened());
        }

        @Override
        public void onCameraClosed(CameraView cameraView) {
            Log.d(TAG, "onCameraClosed: isopen - " + cameraView.isCameraOpened());
        }

        @Override
        public void onPictureTaken(final CameraView cameraView, final byte[] data) {
            Log.d(TAG, "onPictureTaken " + data.length);

            final File currentPhotoFile = new File(getActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES), PhotoUtils.getFileName());

                    if (data == null) {
                        setSafeToTakePicture(true);
                        return;
                    }

                    PhotoUtils.takePhoto(data, orientationDegree, currentPhotoFile);

                    setSafeToTakePicture(true);
                    PhotoUtils.shootSound(getActivity());

                    photoAdapter.notifyDataSetChanged();
                    onImageFileCreated(currentPhotoFile.getPath());
        }
    };

    public boolean isSafeToTakePicture() {
        return safeToTakePicture;
    }

    public void setSafeToTakePicture(boolean safeToTakePicture) {
        this.safeToTakePicture = safeToTakePicture;
    }

    private void showCarPlateKeyboard(){
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                InputMethodManager imm = (InputMethodManager)getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(platesEditText, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 800);
    }

    public int roundOrientation(int orientationInput) {
        int orientation = orientationInput;

        if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) {
            orientation = PhotoUtils.DEGREE_0;
        }

        orientation = orientation % 360;
        int retVal;
        if (orientation < (0 * 90) + 45) {
            retVal = PhotoUtils.DEGREE_0;
        } else if (orientation < (1 * 90) + 45) {
            retVal = PhotoUtils.DEGREE_90;
        } else if (orientation < (2 * 90) + 45) {
            retVal = PhotoUtils.DEGREE_180;
        } else if (orientation < (3 * 90) + 45) {
            retVal = PhotoUtils.DEGREE_270;
        } else {
            retVal = PhotoUtils.DEGREE_0;
        }

        return retVal;
    }
}