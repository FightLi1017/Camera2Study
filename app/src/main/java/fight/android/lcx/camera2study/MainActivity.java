package fight.android.lcx.camera2study;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.widget.Button;
import android.widget.ImageView;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    ///为了使照片竖直显示
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }
    @Bind(R.id.tv_textview)
    TextureView mTextureView;
    @Bind(R.id.iv_Thumbnail)
    ImageView ivThumbnail;
    @Bind(R.id.btn_takepic)
    Button btnTakepic;

    private HandlerThread mBackgroundThread;
    private  Handler mBackgroundHandler;
    private TextureView.SurfaceTextureListener mSurfaceTextlistener;
    CameraCaptureSession mCaptureSession;

    CameraCharacteristics mCameraCharacteristics;
    //很关键的一个类  首先可以设置返回图片的格式
    private ImageReader mImageReader;

    private CameraDevice mCameraDevice;

    private Size mPreViewSize;

    private CaptureRequest.Builder mPreviewRequestBuilder;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        InitCamera2Listener();
    }

    private void InitCamera2Listener() {
        mSurfaceTextlistener=new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                OpenCamera();

        }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                return false;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        };
    }

    private void OpenCamera() {
        CameraManager manager= (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        String Cameraid= CameraCharacteristics.LENS_FACING_FRONT+"";
        try {
            mCameraCharacteristics=manager.getCameraCharacteristics(Cameraid);
            StreamConfigurationMap map = mCameraCharacteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size largest = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CompareSizesByArea());
            mPreViewSize = map.getOutputSizes(SurfaceTexture.class)[0];
            mImageReader = ImageReader.newInstance(largest.getWidth(), largest.getHeight(),
                    ImageFormat.JPEG, /*maxImages*/2);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);
            manager.openCamera(Cameraid, mStateCallback,mBackgroundHandler);
        }catch (Exception e){
            e.printStackTrace();
        }
    }


    /**
     *  由于Camera2没有想Camera那样 拍照一个接口 预览一个接口 也就是说 这个ImageReader 我们即可以当作拍照使用 同时我们也可以做
        实时获取图像信息的东西 只要我们在mPreviewRequestBuilder的时候 addTarget进去 这个时候预览的数据就会回调过来了 其实也就是我们说的录像么
        说的明白一点  要拍照的话 预览不要加入Imagereader 在拍照的时候 加Imagereader 要录像的话 预览就加入Imagereader
        具体就是  拍照 mPreviewRequestBuilder.addTarget(surface);
                  预览  mPreviewRequestBuilder.addTarget(surface);
                        mPreviewRequestBuilder.addTarget(mImagereader.getSurface());
                        此时摄像头的数据就会没有一针图像 就会回调OnImageAvailableListener接口了
      说实话  Camera2真实tmd麻烦
     *
     */
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
           // Log.d("LCX",reader.toString());
            Image image = reader.acquireNextImage();
            /**
             *  因为Camera2并没有Camera1的Priview回调！！！所以该怎么能到预览图像的byte[]呢？就是在这里了！！！我找了好久的办法！！！
             **/
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);//由缓冲区存入字节数组
            final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap != null) {
                mBackgroundHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        ivThumbnail.setImageBitmap(bitmap);
                    }
                });

            }
            image.close();

        }

    };


    @Override
    protected void onResume() {
        super.onResume();
        StartBackBround();
       if (mTextureView.isAvailable()){
           OpenCamera();
       }else{
           mTextureView.setSurfaceTextureListener(mSurfaceTextlistener);
       }
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }

    private void closeCamera() {
        if (null != mCaptureSession) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        if (null != mImageReader) {
            mImageReader.close();
            mImageReader = null;
        }
    }
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    private void StartBackBround() {
        mBackgroundThread = new HandlerThread("Ceamera3");
        mBackgroundThread.start();
        mBackgroundHandler=new Handler(mBackgroundThread.getLooper());
    }

    @OnClick(R.id.btn_takepic)
    public void onViewClicked() {
        TakePicture();
    }

    private void TakePicture() {
        try {

            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());
            // 自动对焦
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
            // 自动曝光
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
            int rotation = this.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));
            mCaptureSession.capture(captureBuilder.build(), null,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    //美有一针的画面 就要回调一次 这里可以采取数据
    private final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            cameraDevice.close();
            mCameraDevice = null;

        }

    };

    private CameraCaptureSession.StateCallback mSessionStateCallBack = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
            try {
                mCaptureSession = cameraCaptureSession;
                // 自动对焦
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                // 打开闪光灯
                //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), null, mBackgroundHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();

            }
        }

        @Override
        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {

        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();
        CloseSession();
    }

    private void CloseSession() {
        if (mCaptureSession != null) {
            mCaptureSession.getDevice().close();
            mCaptureSession.close();
        }
    }

    private void createCameraPreviewSession() {
        try {
            Surface surface=InitSurface();
            mPreviewRequestBuilder=mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),mSessionStateCallBack,mBackgroundHandler);
        }catch (Exception e){
            e.printStackTrace();
        }

    }

    @NonNull
    private Surface InitSurface() {
        SurfaceTexture texture = mTextureView.getSurfaceTexture();

        // We configure the size of default buffer to be the size of camera preview we want.
        texture.setDefaultBufferSize(mPreViewSize.getWidth(), mPreViewSize.getHeight());

        // This is the output Surface we need to start preview.
        return new Surface(texture);
    }
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }
}
