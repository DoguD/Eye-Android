package kiddo.epsilon.custodet;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class MainActivity extends AppCompatActivity implements TextToSpeech.OnInitListener {

    //VARIABLES

    //Instance
    public static MainActivity instance;
    //General
    int state; //0: no task || 1:take photo ||2: analysis || 3:translation
    //Microsoft Variables
    String microsoftApiKey;
    public VisionServiceClient visionServiceClient;
    //Google Variables
    String googleApiKey;
    //Image Variables
    Bitmap photoBitmap;
    ImageView imageTakenPhoto;
    //Text-To-Speech Variables
    String textToSpeechInputText;
    TextToSpeech tts;
    Locale turkishLocale = new Locale("tr", "TR"); // Turkish for text-to-speech
    //Taking photo
    static final int REQUEST_IMAGE_CAPTURE = 1; // (?) How many images will be taken when the camera intent is started
    String currentPhotoPath; //Path of the taken photo
    //Camera
    private Camera mCamera;


    //can be usefull
    // http://www.vogella.com/tutorials/AndroidCamera/article.html

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Setting up instance
        if (instance == null) {
            instance = this;
        }

        //Initialize state variable
        state=0;

        //Microsoft Api Set-up
        microsoftApiKey = getString(R.string.microsoft_api_key);
        if (visionServiceClient==null) {
            visionServiceClient = new VisionServiceRestClient(microsoftApiKey);
        }

        //Setting up translator
        //Google Api Key
        googleApiKey=getString(R.string.google_api_key);


        //Taking photo (initialization of image view)
        mCamera = getCameraInstance();
        imageTakenPhoto = (ImageView) findViewById(R.id.imageTakenPhoto);

        //!!! Only devices with a camera can download our app
        // (?) Does my code open front or back camera?

        //Setting up text-to-speech
        tts = new TextToSpeech(this, this); // New text-to-speech
        textToSpeechInputText = "Merhaba, göze hoşgeldiniz. İstediğiniz yere bakın ve kulaklığınızın butonuna basın. Biz sizin için görelim."; // Welcome message

        //Set the image of image view to temp photo
        photoBitmap = BitmapFactory.decodeResource(getResources(),R.drawable.fountain_pen); // Temporary bitmap
        imageTakenPhoto=(ImageView) findViewById(R.id.imageTakenPhoto);
        imageTakenPhoto.setImageBitmap(photoBitmap);

    }


    // RECOGNIZE FUNCTION OF MICROSOFT API
    //Copied from Microsoft API
    void ProcessImage(){
        state = 2; // switch the state of application to analyzing
        DoDescribe();
    }

    public void DoDescribe() {

        try {
            new DoRequest().execute();
        } catch (Exception e)
        {
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

                for (Caption caption: result.description.captions) {
                    tempStringBuilder.append(caption.text);
                }

                //Pass result to speech variable
                textToSpeechInputText = tempStringBuilder.toString();
                Log.d("Output",textToSpeechInputText);
                state=0; //stop the continious analyzing speech
                //Translate to Turkish
                TranslateToTurkish();
                Speak("Çevriliyor...");
            }
        }
    }


    // USE EARPHONE PAUSE BUTTON TO TAKE PHOTO
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK) { // Checking for which button is clicked
            MainMethod(); // Start the main method
            return true;
        }
        return super.onKeyDown(keyCode, event); // Default return
    }

    //TAKE PHOTO
    //Copied from https://developer.android.com/guide/topics/media/camera.html#custom-camera

    //Accessing camera
    public static Camera getCameraInstance() {
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
        } catch (Exception e) {
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    //Retrieving image from camera
    private Camera.PictureCallback mPicture = new Camera.PictureCallback() {

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {

            File pictureFile = getOutputMediaFile(MEDIA_TYPE_IMAGE);
            if (pictureFile == null) {
                /*Log.d(TAG, "Error creating media file, check storage permissions: " +
                        e.getMessage());*/
                return;
            }

            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                fos.write(data);
                fos.close();
            } catch (FileNotFoundException e) {
                //Log.d(TAG, "File not found: " + e.getMessage());
            } catch (IOException e) {
                //Log.d(TAG, "Error accessing file: " + e.getMessage());
            }
        }
    };

    //Take picture method
    void CaptureImage() {
        //Setting up camera
        mCamera.takePicture(null, null, mPicture); // Capture image from camera
    }

    //After the photo is taken
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Bitmap bitmap = (Bitmap) data.getExtras().get("data");
        imageTakenPhoto.setImageBitmap(bitmap);
    }

    //Release camera when you quit from application
    @Override
    protected void onPause() {
        // Release camera to prevent possible problems
        if (mCamera != null) {
            mCamera.release();
            mCamera = null;
        }
        super.onPause();
    }

    //SAVING IMAGE
    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2; // In case we use video

    // Create a file Uri for saving an image or video
    private static Uri getOutputMediaFileUri(int type) {
        return Uri.fromFile(getOutputMediaFile(type));
    }

    //Create a File for saving an image or video
    private static File getOutputMediaFile(int type) {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "CustodetArchive");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d("CustodetArchive", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_" + timeStamp + ".jpg");
        } else if (type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_" + timeStamp + ".mp4");
        } else {
            return null;
        }

        return mediaFile;
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
                        Log.d("Translated text:",translation.getTranslatedText().toString());
                        textToSpeechInputText = translation.getTranslatedText().toString();
                        Log.d("Output",textToSpeechInputText);
                        //Speak of the result
                        Speak(textToSpeechInputText);
                    }
                });
                return null;
            }
        }.execute();
    }

    // TEXT TO SPEECH
    // Speak method
    void Speak(String inputText) {
        if(tts.isSpeaking()){
            tts.stop();
        }
        tts.speak(inputText, TextToSpeech.QUEUE_FLUSH, null);
    }

    //Setting the language for text-to-speech
    @Override
    public void onInit(int status) {
        tts.setLanguage(turkishLocale); // Set speaking language to Turkish

        //Welcome Message
        Speak(textToSpeechInputText); // Speak welcome message
        textToSpeechInputText = "Test konuşması"; // Put temp variable in textToSpeechINPUTTEXT
    }

    //MAIN
    public void MainMethod() {
        ProcessImage(); // Process image and get new variable for textToSpeechInputText
        //Wait until analysis finsihes
        //while(state ==2){
            Speak("Algılanıyor...");
        //}
        Log.d("State","End of main method");
    }
}





























/** INTENT METHOD FOR TAKING PHOTO (OBSLETE)
 //Copied mostly from https://developer.android.com/training/camera/photobasics.html
 //Intent for taking photo
 private void dispatchTakePictureIntent() {
 Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
 //Ensure that there is a camera activity to handle the intent
 if (takePictureIntent.resolveActivity(getPackageManager()) != null) {

 // Create the File where the photo should go
 File photoFile = null;
 try {
 photoFile = createImageFile();
 } catch (IOException ex) {
 // Error occurred while creating the File
 }

 // Continue only if the File was successfully created
 if (photoFile != null) {
 Uri photoURI = FileProvider.getUriForFile(this,
 "kiddo.epsilon.custodet.fileprovider",
 photoFile);
 takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
 startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
 }

 }
 }

 //Creating UNIQUE file name of image
 private File createImageFile() throws IOException {
 // Create an image file name
 String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
 String imageFileName = "JPEG_" + timeStamp + "_";
 File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
 File image = File.createTempFile(
 imageFileName,  // prefix
 ".jpg",         // suffix
 storageDir      // directory
 );

 // Save a file: path for use with ACTION_VIEW intents
 currentPhotoPath = image.getAbsolutePath();
 return image;
 }

 //Alternative method
 void TakePicture() {
 Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE); // Create intent for capturing image
 startActivityForResult(intent, 0);
 }*/

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
