package kiddo.epsilon.custodet;


import android.Manifest;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.google.cloud.translate.Translate;
import com.google.cloud.translate.TranslateOptions;
import com.google.cloud.translate.Translation;
import com.google.gson.Gson;
import com.microsoft.projectoxford.vision.VisionServiceClient;
import com.microsoft.projectoxford.vision.VisionServiceRestClient;
import com.microsoft.projectoxford.vision.contract.AnalysisResult;
import com.microsoft.projectoxford.vision.contract.Caption;
import com.microsoft.projectoxford.vision.rest.VisionServiceException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;

public class MainActivity extends AppCompatActivity{

    //VARIABLES
    //Instance
    public static MainActivity instance;
    //General
    int state; //0: no task || 1:processing || 2: result
    //Microsoft Variables
    String microsoftApiKey;
    public VisionServiceClient visionServiceClient;
    //Google Variables
    String googleApiKey;
    //Image Variables
    Bitmap photoBitmap;
    ImageView imageTakenPhoto;
    ImageView imageTakenPhoto2;
    //Text-To-Speech Variables
    String textToSpeechInputText;
    TextToSpeech tts;
    Locale turkishLocale = new Locale("tr", "TR"); // Turkish for text-to-speech

    // CAMERA FROM https://inducesmile.com/android/android-camera-api-tutorial/
    private ImageSurfaceView mImageSurfaceView;
    private Camera camera;

    private FrameLayout cameraPreviewLayoutLeft;
    //private FrameLayout cameraPreviewLayoutRight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Force landscape mode
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        //Initialize state variable (no process right now)
        state = 0;

        //Microsoft API Set-up
        microsoftApiKey = getString(R.string.microsoft_api_key);
        if (visionServiceClient == null) {
            visionServiceClient = new VisionServiceRestClient(microsoftApiKey);
        }

        //Google API Set-up
        googleApiKey = getString(R.string.google_api_key);

        //Setting up text-to-speech
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                Log.d("Initialization", ""+status);
                    tts.setLanguage(turkishLocale); // Set speaking language to Turkish
                    Log.d("Text-To-Speach", "Initialized");

                    // Welcome Message
                    Speak("Merhaba, göze hoşgeldiniz. İstediğiniz yere bakın ve kulaklığınızın butonuna basın. Biz sizin için görelim.");
                    // Put temp variable in textToSpeechInputText
                    textToSpeechInputText = "Hata var.";
                }
        });

        //Taking photo (initialization of image view)
        //imageTakenPhoto = (ImageView) findViewById(R.id.imageTakenPhoto);
        //imageTakenPhoto2 = (ImageView) findViewById(R.id.imageTakenPhoto2);

        //!!! Only devices with a camera can download our app
        // Check for camera permission for Android 6.0 and above
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            //ask for authorization
            Speak("Lütfen kamera için izin veriniz");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 50);
        } else { //Get camera instance
            camera = checkDeviceCamera();
        }

        //Set the image of image view to temp photo
        photoBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.fountain_pen); // Temporary bitmap

        //CAMERA VIEW (Not for blind)
        //Initialize frame layout
        cameraPreviewLayoutLeft = (FrameLayout) findViewById(R.id.camera_preview_left);
        //cameraPreviewLayoutRight = (FrameLayout) findViewById(R.id.camera_preview_right);
        //Initialize surface view
        mImageSurfaceView = new ImageSurfaceView(MainActivity.this, camera);
        //Show surface view from frame layout
        cameraPreviewLayoutLeft.addView(mImageSurfaceView);
        //cameraPreviewLayoutRight.addView(mImageSurfaceView);

    }

    //TAKING PHOTO from https://inducesmile.com/android/android-camera-api-tutorial/
    void takePicture() {
        camera.takePicture(null, null, pictureCallback);
    }

    private Camera checkDeviceCamera() {
        Camera mCamera = null;
        try {
            mCamera = Camera.open();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mCamera;
    }

    PictureCallback pictureCallback = new PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            photoBitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            if (photoBitmap == null) { // If a problem occurs with taking photo
                Speak("Fotoğraf çekilemedi... Lütfen uygulamayı yeniden başlatın.");
                state = 0;
                return;
            }
            Speak("Fotoğraf algılanıyor.");

            //Scaling Down bitmap using matrix
            Matrix transformationMatrix = new Matrix();
            transformationMatrix.setRectToRect(new RectF(0,0,photoBitmap.getWidth(),photoBitmap.getHeight()),new RectF(0, 0, 300, 200), Matrix.ScaleToFit.CENTER);
            photoBitmap = Bitmap.createBitmap(photoBitmap,0,0,photoBitmap.getWidth(),photoBitmap.getHeight(),transformationMatrix,true);
            // photoBitmap = scaleDownBitmapImage(photoBitmap, 300, 200 ); // Experimental bitmap scaling down
            ProcessImage();
        }
    };

    // Bitmap rescale method
    private Bitmap scaleDownBitmapImage(Bitmap bitmap, int newWidth, int newHeight) {
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
        return resizedBitmap;
    }

    // RECOGNIZE FUNCTION OF MICROSOFT API
    //Copied from Microsoft SDK App sample
    void ProcessImage() {
        DoDescribe();
    }

    public void DoDescribe() {
        try {
            new DoRequest().execute();
        } catch (Exception e) {
            Speak("Fotoğraf algılanamadı, internet bağlantınızı kontrol edin.");
            state = 0;
        }
    }

    private String Process() throws VisionServiceException, IOException {
        Gson gson = new Gson();

        // Put the image into an input stream for detection.
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        photoBitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
        ByteArrayInputStream inputStream = new ByteArrayInputStream(output.toByteArray());

        AnalysisResult v = this.visionServiceClient.describe(inputStream, 1);

        String result = gson.toJson(v);
        Log.d("result", result);

        return result;
    }

    private class DoRequest extends AsyncTask<String, String, String> {
        // Store error message
        private Exception e = null;

        public DoRequest() {
        }

        @Override
        protected String doInBackground(String... args) {
            try {
                return Process();
            } catch (Exception e) {
                this.e = e; // Store error
                Speak("Fotoğraf algılanamadı, internet bağlantınızı kontrol edin.");
                state = 0;
            }

            return null;
        }

        @Override
        protected void onPostExecute(String data) {
            super.onPostExecute(data);
            // Display based on error existence

            //String builder for description output
            StringBuilder tempStringBuilder;
            tempStringBuilder = new StringBuilder();

            if (e != null) {
                Speak("Fotoğraf algılanamadı, internet bağlantınızı kontrol edin.");
                this.e = null;
                state = 0;
            } else {
                Gson gson = new Gson();
                AnalysisResult result = gson.fromJson(data, AnalysisResult.class);

                for (Caption caption : result.description.captions) {
                    tempStringBuilder.append(caption.text);
                }

                //Pass result to speech variable
                textToSpeechInputText = tempStringBuilder.toString();
                Log.d("Output", textToSpeechInputText);

                // Translate to Turkish
                TranslateToTurkish();
                // Speak("Fotoğraf algılandı.");
            }
        }
    }

    // USE EARPHONE PAUSE BUTTON AS INPUT
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK) { // Checking for which button is clicked
            MainMethod(); // Start the main method
            return true;
        }
        return super.onKeyDown(keyCode, event); // Default return
    }

    //USE VOLUME UP AND DOWN BUTTONS AS INPUT
    /*
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int action = event.getAction();
        int keyCode = event.getKeyCode();
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_UP:
                if (action == KeyEvent.ACTION_DOWN) {
                    MainMethod(); // Start the main method
                }
                return true;
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                if (action == KeyEvent.ACTION_DOWN) {
                    MainMethod(); // Start the main method
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }*/

    // TRANSLATE THE OUTPUT OF API TO TURKISH
    void TranslateToTurkish() {
        final Handler textViewHandler = new Handler();

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                TranslateOptions options = TranslateOptions.newBuilder()
                        .setApiKey(googleApiKey)
                        .build();
                Translate translate = options.getService();
                final Translation translation =
                        translate.translate(textToSpeechInputText,
                                Translate.TranslateOption.targetLanguage("tr"));
                textViewHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        Log.d("Translated text:", translation.getTranslatedText().toString());
                        textToSpeechInputText = translation.getTranslatedText().toString();
                        Log.d("Output", textToSpeechInputText);
                        //Speak of the result
                        Speak(textToSpeechInputText);
                        state = 2; // Turn state to speaking the result
                    }
                });
                return null;
            }
        }.execute();
    }

    // TEXT TO SPEECH
    void Speak(final String inputText) {
            // If the result text is currently spoken wait until it is finisihed
            if (state == 2) {
                // Check momentarily if tts is finisihed
                final Handler h = new Handler();
                Runnable r = new Runnable() {

                    public void run() {
                        if (!tts.isSpeaking()) { // The speech ended
                            tts.speak(inputText, TextToSpeech.QUEUE_FLUSH, null);
                            state = 0;
                            return;
                        }
                        h.postDelayed(this, 100);
                    }
                };
                h.postDelayed(r, 100);
            }
            // Stop Previous Speech
            else if (tts.isSpeaking()) {
                tts.stop();
                tts.speak(inputText, TextToSpeech.QUEUE_FLUSH, null);
            } else {
                tts.speak(inputText, TextToSpeech.QUEUE_FLUSH, null);
            }
        }

    //MAIN
    public void MainMethod() {
        // Turn state to 0 if the resulthas ended
        Speak("");

        if (state == 0) {
            takePicture(); // Start with taking photo
            Log.e("Camera:", "CaptureImage method executed");

            Speak("Fotoğraf çekiliyor...");
            state = 1;
        } else if (state == 1){
            Speak("Bekleyiniz...");
        }
        Log.d("STATE = ",""+state);
    }

    //Release camera Method
    void ReleaseCamera(){
        if(camera!=null){
            camera.release();
            camera=null;
        }
    }

    //Execute release camera method in onPause and onDestroy
    @Override
    protected void onPause() {
        ReleaseCamera();
        super.onPause();
    }
    @Override
    protected void onDestroy() {
        ReleaseCamera();
        super.onDestroy();
    }

    @Override
    protected void onStart() {

        super.onStart();
    }

    @Override
    protected void onStop() {
        ReleaseCamera();
        super.onStop();
    }
}