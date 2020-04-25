package com.example.slaveapp;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getSimpleName() ;
    Button btnSet,btnSend, btnAddImage;
    EditText etMessage;
    TextView tvName,tvDetails, tvImageDetails;
    Socket socketGlobal = null;
    private int SocketServerPort=9000;
    private static final String REQUEST_CONNECT_CLIENT = "request-connect-client";
    private List<String> clientIPs;
    public int flag=0;
    String clientIPAddress;
    public int started=0;
    int registrationStarted=0;
    ArrayList<Uri> imageList = new ArrayList<>();
    Intent intent, intentData;


    private String SERVICE_NAME= "";
    private String SERVICE_TYPE = "_gemineye._tcp";

    private InetAddress hostAddress;
    private int hostPort;
    private NsdManager mNsdManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnSet=findViewById(R.id.btnSet);
        tvName=findViewById(R.id.tvName);
        tvDetails=findViewById(R.id.tvDetails);
        etMessage=findViewById(R.id.etMessage);
        btnSend=findViewById(R.id.btnSend);
        btnAddImage = findViewById(R.id.btnAddImage);
        tvImageDetails = findViewById(R.id.tvImageDetails);
        SERVICE_NAME = Build.MANUFACTURER.toUpperCase() + " " + Build.MODEL;
        tvName.setText(SERVICE_NAME);

        clientIPs = new ArrayList<String>();

        mNsdManager = (NsdManager) getSystemService(Context.NSD_SERVICE);

        btnSet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mNsdManager != null) {

                    if(registrationStarted==0){
                        registerService(9000);
                        registrationStarted = 1;
                        SocketServerThread socketServerThread = new SocketServerThread();
                        socketServerThread.start();
                    }
                }
            }
        });

        btnAddImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
                intent.setType("image/jpeg");
                startActivityForResult(intent,100);
            }
        });

        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String message=etMessage.getText().toString().trim();
                if(started == 1) {
                    if ((message.equals(""))&&(imageList == null)) {
                        Toast.makeText(MainActivity.this, "Please enter all fields", Toast.LENGTH_SHORT).show();
                    } else {
                        SendMessageTask sendMessageTask = new SendMessageTask(imageList,message,socketGlobal,intentData);
                        sendMessageTask.start();
                        imageList = new ArrayList<>();
                        etMessage.setText("");
                    }
                }
                else
                {
                    Toast.makeText(MainActivity.this, "No ip found (not registered)", Toast.LENGTH_SHORT).show();
                }
            }
        });

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode == 100)
        {
            if(resultCode == RESULT_OK)
            {
                intentData = data;
                if(data.getClipData() != null)
                {
                    int selectedSize = data.getClipData().getItemCount();
                    tvImageDetails.setText("" + data.getClipData().getItemCount() + " images selected");
                    for(int i=0;i<selectedSize;++i)
                    {
                        Uri uri = data.getClipData().getItemAt(i).getUri();
                        imageList.add(uri);
                    }
                }
                else
                {
                    Uri uri = data.getData();
                    imageList.add(uri);
                    tvImageDetails.setText("1 image selected");

                }
            }
            else
            {
                Toast.makeText(this, "please select something...", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private class SocketServerThread extends Thread {

        @Override
        public void run() {

            Socket socket = null;
            DataInputStream dataInputStream = null;
            DataOutputStream dataOutputStream = null;

            try {
                Log.i(TAG, "Creating server socket");
                ServerSocket serverSocket = new ServerSocket(9000);

                while (true) {

                    socket = serverSocket.accept();
                    dataInputStream = new DataInputStream(socket.getInputStream());
                    dataOutputStream = new DataOutputStream(socket.getOutputStream());


                    String messageFromClient, messageToClient, request;


                    messageFromClient = dataInputStream.readUTF();

                    final JSONObject jsondata;

                    jsondata = new JSONObject(messageFromClient);

                    try {
                        request = jsondata.getString("request");

                        if (request.equals(REQUEST_CONNECT_CLIENT)) {
                            started=1;
                            clientIPAddress = jsondata.getString("localip");
                            SocketServerPort = jsondata.getInt("Port");
                            Log.d("slave", "master wala port" + SocketServerPort);
                            tvDetails.setText("Master IP: " + clientIPAddress);

                            // Add client IP to a list

                            clientIPs.add(clientIPAddress);

                            messageToClient = "Connection Accepted";
                            dataOutputStream.writeUTF(messageToClient);
                        }
                        else {
                            // There might be other queries.
                            dataOutputStream.flush();
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                        Log.e(TAG, "Unable to get request");
                        dataOutputStream.flush();
                    }
                }
            } catch (IOException | JSONException e) {
                e.printStackTrace();
            } finally {
                if (socket != null) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (dataInputStream != null) {
                    try {
                        dataInputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                if (dataOutputStream != null) {
                    try {
                        dataOutputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    @Override
    protected void onPause() {

        /*if (flag == 1)
            mNsdManager.unregisterService(mRegistrationListener);*/
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        if (flag == 1) {
            mNsdManager.unregisterService(mRegistrationListener);
        }
        super.onDestroy();
    }

    public void registerService(int port) {
        NsdServiceInfo serviceInfo = new NsdServiceInfo();
        serviceInfo.setServiceName(SERVICE_NAME);
        serviceInfo.setServiceType(SERVICE_TYPE);
        serviceInfo.setPort(port);

        mNsdManager.registerService(serviceInfo,
                NsdManager.PROTOCOL_DNS_SD,
                mRegistrationListener);
    }

    NsdManager.RegistrationListener mRegistrationListener = new NsdManager.RegistrationListener() {

        @Override
        public void onServiceRegistered(NsdServiceInfo NsdServiceInfo) {
            String mServiceName = NsdServiceInfo.getServiceName();
            SERVICE_NAME = mServiceName;
            flag=1;
            Log.d(TAG, "Registered name : " + mServiceName);
            Toast.makeText(MainActivity.this, "Service Registered..", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onRegistrationFailed(NsdServiceInfo serviceInfo,
                                         int errorCode) {

        }

        @Override
        public void onServiceUnregistered(NsdServiceInfo serviceInfo) {

            Log.d(TAG, "Service Unregistered : " + serviceInfo.getServiceName());
        }

        @Override
        public void onUnregistrationFailed(NsdServiceInfo serviceInfo,
                                           int errorCode) {
        }
    };

    private class SendMessageTask extends Thread{

        String message;
        Socket socket;
        ArrayList<Uri> imageList;
        int masterSize;
        Intent intentData;

        SendMessageTask(ArrayList<Uri> imageList,String message, Socket socket, Intent intentData)
        {
            this.imageList = imageList;
            this.message = message;
            this.socket = socket;
            this.masterSize = 0;
            this.intentData = intentData;
        }

        @Override
        public void run() {

            try {
                System.out.println("hello ip" + clientIPAddress);

                JSONObject jsonObject = new JSONObject();
                jsonObject.put("deviceName",Build.MODEL);

                if(!message.equals(""))
                {
                    jsonObject.put("message",Build.MODEL + ": " +message);
                }
                Log.d("slave", "connect wala port  " + SocketServerPort);
                socket = new Socket(InetAddress.getByName(clientIPAddress),SocketServerPort);
                DataOutputStream dataOutputStream =new DataOutputStream(socket.getOutputStream());

                masterSize = getImageCount(imageList).length;      //getting imageCountByte Array size.
                byte[] jsonByteArray = getJsonByteArray(jsonObject);
                masterSize += jsonByteArray.length;

                ArrayList<byte[]> imageByteArrayList = new ArrayList<>();
                ArrayList<byte[]> imageByteArraySizeArrayList = new ArrayList<>();
                for(int i=0;i<imageList.size();++i)
                {
                    imageByteArrayList.add(getImage(imageList.get(i)));
                    masterSize += imageByteArrayList.get(i).length;    //getting byte array of every image one by one.

                    imageByteArraySizeArrayList.add(getImageSize(imageByteArrayList.get(i)));
                    masterSize += imageByteArraySizeArrayList.get(i).length;   // getting byte array of every image's byteArray's size.
                }

                byte[] jsonByteArraySizeByteArray = ByteBuffer.allocate(4).putInt(jsonByteArray.length).array();
                masterSize += jsonByteArraySizeByteArray.length;

                byte[] masterByteArray = new byte[masterSize];

                int index = 0;

                System.arraycopy(getImageCount(imageList),0, masterByteArray, index, getImageCount(imageList).length);
                index += getImageCount(imageList).length;

                System.arraycopy(jsonByteArraySizeByteArray,0, masterByteArray, index, jsonByteArraySizeByteArray.length);
                index += jsonByteArraySizeByteArray.length;

                for(int i=0;i<imageByteArraySizeArrayList.size();++i)
                {
                    System.arraycopy(imageByteArraySizeArrayList.get(i),0, masterByteArray, index, imageByteArraySizeArrayList.get(i).length);
                    index += imageByteArraySizeArrayList.get(i).length;
                }

                System.arraycopy(jsonByteArray,0, masterByteArray, index, jsonByteArray.length);
                index += jsonByteArray.length;

                for(int i=0;i<imageByteArrayList.size();++i)
                {
                    System.arraycopy(imageByteArrayList.get(i),0, masterByteArray, index, imageByteArrayList.get(i).length);
                    index += imageByteArrayList.get(i).length;
                }

                dataOutputStream.write(masterByteArray,0,masterSize);
                dataOutputStream.flush();
                System.out.println("Send message task...");

                socket.close();

            } catch (IOException | JSONException e) {
                e.printStackTrace();
            }

        }

        byte[] getImageCount(ArrayList<Uri> imageList)
        {
            int size=imageList.size();
            byte[] byteSize = ByteBuffer.allocate(4).putInt(size).array();

            return byteSize;
        }

        byte[] getImageSize(byte[] bytes) {
            int n = bytes.length;
            byte[] byteSize = ByteBuffer.allocate(4).putInt(n).array();

            return byteSize;
            }

        byte[] getImage(Uri uri) {

            ImageDecoder.Source imageDecoder = ImageDecoder.createSource(getApplicationContext().getContentResolver(), uri);
            try {
                Bitmap bitmap = ImageDecoder.decodeBitmap(imageDecoder);
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
                byte[] imageByteArray = byteArrayOutputStream.toByteArray();
                return imageByteArray;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }


        byte[] getJsonByteArray(JSONObject jsonObject)
        {
            return jsonObject.toString().getBytes();
        }
    }
}