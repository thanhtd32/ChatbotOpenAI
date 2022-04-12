package tranduythanh.com.openaichatbot;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import tranduythanh.com.adapter.MessageDataAdapter;
import tranduythanh.com.api.OpenAI;
import tranduythanh.com.model.MessageData;
import tranduythanh.com.model.OpenAIInput;
import tranduythanh.com.model.OpenAIOutput;
import tranduythanh.com.model.TypeTalking;

public class MainActivity extends AppCompatActivity{
    private static final String TAG = MainActivity.class.getSimpleName();
    RecyclerView rvMessageData;
    EditText edtChatBox;
    TextView txtOpenAIStatus;
    ImageView imgChatBoxSend;
    ImageView imgSpeaker;

    private MessageDataAdapter messageDataAdapter;
    private List<MessageData> messageList;

    private SpeechRecognizer speech = null;
    private Intent recognizerIntent;
    private String LOG_TAG = "VoiceRecognitionActivity";
    GoogleRecognitionListener googleRecognitionListener=null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        makeRequestPermission();

        addControls();
        addEvents();
    }
    private boolean checkIfAlreadyhavePermission(String permission) {
        int result = ContextCompat.checkSelfPermission(this, permission);
        if (result == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            return false;
        }
    }
    private void makeRequestPermission() {
        if (!checkIfAlreadyhavePermission(Manifest.permission.RECORD_AUDIO)) {
            ActivityCompat.requestPermissions(MainActivity.this,
                    new String[]{Manifest.permission.ACCESS_NETWORK_STATE,
                            Manifest.permission.INTERNET,
                            Manifest.permission.RECORD_AUDIO,
                            Manifest.permission.MODIFY_AUDIO_SETTINGS,
                    },
                    1);
        }

    }

    private void addControls() {
        edtChatBox=findViewById(R.id.edtChatBox);
        rvMessageData=findViewById(R.id.rvMessageData);
        txtOpenAIStatus=findViewById(R.id.txtOpenAIStatus);
        imgChatBoxSend=findViewById(R.id.imgChatBoxSend);
        imgSpeaker=findViewById(R.id.imgSpeaker);

        messageList = new ArrayList<>();

        messageDataAdapter = new MessageDataAdapter(this, messageList);
        rvMessageData.setLayoutManager(new LinearLayoutManager(this));
        rvMessageData.setAdapter(messageDataAdapter);

        googleRecognitionListener=new GoogleRecognitionListener();
        speech = SpeechRecognizer.createSpeechRecognizer(this);
        speech.setRecognitionListener(googleRecognitionListener);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);

        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,"en-US");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"en-US");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,this.getPackageName());
        //recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,this.getPackageName());

        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
    }

    private void addEvents() {
        imgChatBoxSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
               processSendMessage();
            }
        });
        imgSpeaker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                processGoogleSpeech();
            }
        });
    }
    private void processGoogleSpeech() {
        speech.stopListening();
        speech.startListening(recognizerIntent);
    }

    private void processSendMessage() {
        String message = edtChatBox.getText().toString();
        if (message.isEmpty()) {
            Toast.makeText(this, "Input the contents.", Toast.LENGTH_SHORT).show();
            return;
        }
        sendMessageByMe(message);
    }

    private void sendMessageByMe(String msg) {
        //When the user presses the send button
        //we initialize MessageData
        //and assign TypeTalking.HUMAN
        MessageData userMessage = new MessageData();
        userMessage.setUserName("Duy Thanh Tran");
        userMessage.setTypeTalking(TypeTalking.HUMAN);

        userMessage.setCreated(System.currentTimeMillis());
        userMessage.setText(msg);

        messageList.add(userMessage);
        //Update interface
        refreshMessageList();
        edtChatBox.setText("");
        //send this information to OpenAI
        getMessageByOpenAI(msg);
    }

    private void getMessageByOpenAI(String msg) {
        txtOpenAIStatus.setVisibility(View.VISIBLE);
        //use AsyncTask to create thread
        AsyncTask<OpenAIInput,Void, OpenAIOutput> task=new AsyncTask<OpenAIInput, Void, OpenAIOutput>() {
            @Override
            protected OpenAIOutput doInBackground(OpenAIInput... openAIInputs) {
                try {
                    //call HttpURLConnection
                    //and coding like below:
                    URL url = new URL(OpenAI.API);
                    HttpURLConnection conn =(HttpURLConnection) url.openConnection();
                    conn.setRequestProperty("Authorization","Bearer "+OpenAI.TOKEN);
                    conn.setRequestProperty("Content-Type",OpenAI.CONTENT_TYPE);
                    conn.setRequestMethod(OpenAI.METHOD);
                    conn.setDoOutput(true);
                    OpenAIInput aiInput=openAIInputs[0];
                    String myData=new Gson().toJson(aiInput);
                    conn.getOutputStream().write(myData.getBytes());
                    InputStream responseBody = conn.getInputStream();
                    InputStreamReader responseBodyReader =
                            new InputStreamReader(responseBody, StandardCharsets.UTF_8);
                    // get the return data as OpenAIOutput
                    OpenAIOutput data= new Gson().fromJson(responseBodyReader, OpenAIOutput.class);
                    return data;
                }
                catch (Exception ex)
                {
                    Log.e(TAG,ex.toString());
                }
                return null;
            }

            @Override
            protected void onPostExecute(OpenAIOutput openAIOutput) {
                super.onPostExecute(openAIOutput);
                if(openAIOutput!=null)
                {
                    if(openAIOutput.getChoices()!=null)
                    {
                        //if there is data, we get the first element in the choices . array
                        MessageData openAIMessage=openAIOutput.getChoices().get(0);
                        //update the name
                        openAIMessage.setUserName("OpenAI");
                        //updated OpenAI type to show box properly
                        openAIMessage.setTypeTalking(TypeTalking.OPENAI);
                        //update time to show
                        openAIMessage.setCreated(openAIOutput.getCreated());
                        messageList.add(openAIMessage);
                        //update interface
                        refreshMessageList();

                        txtOpenAIStatus.setVisibility(View.INVISIBLE);
                    }
                }
            }
        };
        //Enable thread:
        OpenAIInput input=new OpenAIInput(msg);
        task.execute(input);
    }
    private void refreshMessageList() {
        messageDataAdapter.notifyDataSetChanged();

        rvMessageData.scrollToPosition(messageList.size() - 1);
    }
    private class GoogleRecognitionListener implements RecognitionListener
    {

        @Override
        public void onReadyForSpeech(Bundle bundle) {

        }
        @Override
        public void onBeginningOfSpeech() {

        }

        @Override
        public void onRmsChanged(float rmsdB) {



        }

        @Override
        public void onBufferReceived(byte[] bytes) {

        }

        @Override
        public void onEndOfSpeech() {

        }

        @Override
        public void onError(int errorCode) {
            String errorMessage = getErrorText(errorCode);
            Toast.makeText(getApplicationContext(),errorMessage,Toast.LENGTH_LONG).show();
        }

        @Override
        public void onResults(Bundle results) {
            ArrayList<String> result = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            String text=result.get(0);
            sendMessageByMe(text);
        }

        @Override
        public void onPartialResults(Bundle bundle) {

        }

        @Override
        public void onEvent(int i, Bundle bundle) {

        }
    }
    public static String getErrorText(int errorCode) {
        String message;
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                message = "Audio recording error";
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                message = "Client side error";
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                message = "Insufficient permissions";
                break;
            case SpeechRecognizer.ERROR_NETWORK:
                message = "Network error";
                break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                message = "Network timeout";
                break;
            case SpeechRecognizer.ERROR_NO_MATCH:
                message = "No match";
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                message = "RecognitionService busy";
                break;
            case SpeechRecognizer.ERROR_SERVER:
                message = "error from server";
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                message = "No speech input";
                break;
            default:
                message = "Didn't understand, please try again.";
                break;
        }
        return message;
    }
}