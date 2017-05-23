package kiddo.epsilon.custodet;


import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

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

public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    //VARIABLES
    //Instance
    public static MainActivity instance;
    //General
    int state; //0: no task || 1:processing
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

    private FrameLayout cameraPreviewLayout;
    private ImageView capturedImageHolder;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //Setting up instance
        if (instance == null) {
            instance = this;
        }

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
        tts = new TextToSpeech(this, this); // New text-to-speech
        textToSpeechInputText = "Merhaba, göze hoşgeldiniz. İstediğiniz yere bakın ve kulaklığınızın butonuna basın. Biz sizin için görelim."; // Welcome message

        //Taking photo (initialization of image view)
        //imageTakenPhoto = (ImageView) findViewById(R.id.imageTakenPhoto);
        //imageTakenPhoto2 = (ImageView) findViewById(R.id.imageTakenPhoto2);

        //!!! Only devices with a camera can download our app
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            //ask for authorization
            Speak("Lütfen kamera için izin veriniz");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 50);
        }
        else{ //Get camera instance
        camera = checkDeviceCamera();
        }

        //Set the image of image view to temp photo
        photoBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.fountain_pen); // Temporary bitmap

        //Taking Picture
        cameraPreviewLayout = (FrameLayout)findViewById(R.id.camera_preview);
        capturedImageHolder = (ImageView)findViewById(R.id.captured_image);

        camera = checkDeviceCamera();
        mImageSurfaceView = new ImageSurfaceView(MainActivity.this, camera);
        cameraPreviewLayout.addView(mImageSurfaceView);

        Button captureButton = (Button)findViewById(R.id.button);
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                camera.takePicture(null, null, pictureCallback);
            }
        });
    }

    //TAKING PHOTO from https://inducesmile.com/android/android-camera-api-tutorial/

    private Camera checkDeviceCamera(){
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
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            if(bitmap==null){
                Toast.makeText(MainActivity.this, "Captured image is empty", Toast.LENGTH_LONG).show();
                return;
            }
            capturedImageHolder.setImageBitmap(scaleDownBitmapImage(bitmap, 300, 200 ));
        }
    };

    private Bitmap scaleDownBitmapImage(Bitmap bitmap, int newWidth, int newHeight){
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
            Speak("Hata var.");
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
                this.e = e;    // Store error
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
                Speak("Hata var.");
                this.e = null;
            } else {
                Gson gson = new Gson();
                AnalysisResult result = gson.fromJson(data, AnalysisResult.class);

                for (Caption caption : result.description.captions) {
                    tempStringBuilder.append(caption.text);
                }

                //Pass result to speech variable
                textToSpeechInputText = tempStringBuilder.toString();
                Log.d("Output", textToSpeechInputText);
                state = 0; //stop the continious analyzing speech

                //Translate to Turkish
                TranslateToTurkish();
                Speak("Çevriliyor...");
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
                        state=0; // Turn state to not processing
                    }
                });
                return null;
            }
        }.execute();
    }

    // TEXT TO SPEECH
    void Speak(String inputText) {
        if (tts.isSpeaking()) {
            tts.stop();
        }
        tts.speak(inputText, TextToSpeech.QUEUE_FLUSH, null);
    }

    //MAIN
    public void MainMethod() {
        if(state == 0) {
            //CaptureImage(); // Start with taking photo
            Log.e("Camera:", "CaptureImage method executed");

            Speak("Fotoğraf çekiliyor...");
        }
        else{
            Speak("Bekleyiniz...");
        }
        state=1;
    }

    //Setting the language for text-to-speech (Initialize method)
    @Override
    public void onInit(int status) {
        tts.setLanguage(turkishLocale); // Set speaking language to Turkish

        //Welcome Message
        Speak(textToSpeechInputText); // Speak welcome message
        Log.e("Checkpoint","welcome message");
        textToSpeechInputText = "Hata var."; // Put temp variable in textToSpeechINPUTTEXT
    }

    //Release camera when you quit from application (Pause method)
    @Override
    protected void onPause() {
        // Release camera to prevent possible problems
        if (camera != null) {
            camera.release();
            camera = null;
        }
        super.onPause();
    }
}




















/**
    //After the photo is taken
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Bitmap bitmap = (Bitmap) data.getExtras().get("data");
        imageTakenPhoto.setImageBitmap(bitmap);
    }

    **/
/**
 * INTENT METHOD FOR TAKING PHOTO (OBSLETE)
 * //Copied mostly from https://developer.android.com/training/camera/photobasics.html
 * //Intent for taking photo
 * private void dispatchTakePictureIntent() {
 * Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
 * //Ensure that there is a camera activity to handle the intent
 * if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
 * <p>
 * // Create the File where the photo should go
 * File photoFile = null;
 * try {
 * photoFile = createImageFile();
 * } catch (IOException ex) {
 * // Error occurred while creating the File
 * }
 * <p>
 * // Continue only if the File was successfully created
 * if (photoFile != null) {
 * Uri photoURI = FileProvider.getUriForFile(this,
 * "kiddo.epsilon.custodet.fileprovider",
 * photoFile);
 * takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
 * startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
 * }
 * <p>
 * }
 * }
 * <p>
 * //Creating UNIQUE file name of image
 * private File createImageFile() throws IOException {
 * // Create an image file name
 * String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
 * String imageFileName = "JPEG_" + timeStamp + "_";
 * File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
 * File image = File.createTempFile(
 * imageFileName,  // prefix
 * ".jpg",         // suffix
 * storageDir      // directory
 * );
 * <p>
 * // Save a file: path for use with ACTION_VIEW intents
 * currentPhotoPath = image.getAbsolutePath();
 * return image;
 * }
 * <p>
 * //Alternative method
 * void TakePicture() {
 * Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE); // Create intent for capturing image
 * startActivityForResult(intent, 0);
 * }
 */

// Copied from https://www.youtube.com/watch?v=RKyl2Ko0Ag0&t=182s
    /*void ProcessImage() {
        //Image as a bitmap
        //photoBitmap = BitmapFactory.decodeResource(getResources(),R.drawable.fountain_pen);

        //Convert image to stream
        ByteArrayOutputStream imageOutputStream = new ByteArrayOutputStream();
        photoBitmap.compress(Bitmap.CompressFormat.JPEG, 100, imageOutputStream);
        final ByteArrayInputStream imageInputStream = new ByteArrayInputStream(imageOutputStream.toByteArray());

        //Main task to analyze image (?)AsyncTask)
        final AsyncTask<InputStream, String, String> visionTask = new AsyncTask<InputStream, String, String>() {
            ProgressDialog progressDialog = new ProgressDialog(MainActivity.this);

            @Override
            protected String doInBackground(InputStream... params) {
                try {
                    publishProgress("Algılanıyor");
                    String[] features = {"Açıklama"};
                    String[] details = {};

                    AnalysisResult resultOfAnalysis = visionServiceClient.analyzeImage(params[0], features, details);
                    String stringOfAnalysis = new Gson().toJson(resultOfAnalysis);
                    return stringOfAnalysis;

                } catch (Exception e) { // For every exception
                    return null;
                }
            }

            //Before executing AsyncTask
            @Override
            protected void onPreExecute() {
                progressDialog.show();
            }

            //After executing AsyncTask
            @Override
            protected void onPostExecute(String s) {
                progressDialog.dismiss();
                AnalysisResult resultOfAnalysis = new Gson().fromJson(s, AnalysisResult.class);

                //String builder
                StringBuilder resultStringBuilder = new StringBuilder();
                for (Caption caption : resultOfAnalysis.description.captions) {
                    resultStringBuilder.append(caption.text);
                }

                //Pass the builded string to the string which will be read from Text-to-Speech
                textToSpeechInputText = resultStringBuilder.toString();
            }

            //When the progress of AsyncTask is updated
            @Override
            protected void onProgressUpdate(String... values) {
                progressDialog.setMessage(values[0]);
                //Text-to-speech progress
                Speak(values[0].toString()); // (?) is toString necessary
            }

        };

        //Calling AsyncTask
        visionTask.execute(imageInputStream);
    }*/

/*private CameraDevice cameraDevice;
    private CameraDevice.StateCallback cameraDeviceStateCallBack
            = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {

        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {

        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {

        }
    };*/
//TAKING PHOTO
//Copied from https://developer.android.com/guide/topics/media/camera.html#custom-camera
    /*
    //Accessing camera
    public static Camera getCameraInstance() {
        Camera c = null;
        try {

            c = Camera.open(Camera.CameraInfo.CAMERA_FACING_BACK); // attempt to get a Camera instance
            Log.e("Camera: ","Camera opened succesfully");
        } catch (Exception e) {
            Log.e("Camera Error:","Can't open camera");
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    //Retrieving image from camera
    private PictureCallback pictureCallback = new PictureCallback() {
        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.e("Camera: ", "inside pictureCallBack onPictureTaken");
            try {
                // Replace bitmap variable with the new taken photo (transfer byte array to bitmap)
                photoBitmap = BitmapFactory.decodeByteArray(data,0,data.length);
                //Blinds can't see but for debug purposes replace image views with new photo
                imageTakenPhoto.setImageBitmap(photoBitmap);
                imageTakenPhoto2.setImageBitmap(photoBitmap);

                //Continue with Image Processing
                ProcessImage();
                Speak("Algılanıyor...");
            } catch (Exception e){
                Speak("Hata var.");
            }
        }
    };

    //Take picture method
    void CaptureImage() {
        //Re-accsess camera if it has been released somehow
        if (camera == null) {
            camera = getCameraInstance();
            Log.e("Camera: ","Camera initialized in Capture Image method");
        }

        camera.unlock();
        camera.enableShutterSound(true); // SOund for shutter
        camera.takePicture(null, null, pictureCallback); // Capture image from camera
        Log.e("Camera:", "Take picture method executed");
    }
    */