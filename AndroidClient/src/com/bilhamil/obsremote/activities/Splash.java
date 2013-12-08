package com.bilhamil.obsremote.activities;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import com.bilhamil.obsremote.OBSRemoteApplication;
import com.bilhamil.obsremote.R;
import com.bilhamil.obsremote.RemoteUpdateListener;
import com.bilhamil.obsremote.R.id;
import com.bilhamil.obsremote.R.layout;
import com.bilhamil.obsremote.WebSocketService;
import com.bilhamil.obsremote.WebSocketService.LocalBinder;
import com.bilhamil.obsremote.messages.ResponseHandler;
import com.bilhamil.obsremote.messages.requests.Authenticate;
import com.bilhamil.obsremote.messages.requests.GetAuthRequired;
import com.bilhamil.obsremote.messages.requests.GetVersion;
import com.bilhamil.obsremote.messages.responses.AuthRequiredResp;
import com.bilhamil.obsremote.messages.responses.Response;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;

public class Splash extends FragmentActivity implements RemoteUpdateListener
{
	
	protected WebSocketService mService = null;
    protected boolean mBound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) 
	{
        super.onCreate(savedInstanceState);
        
        //Remove title bar
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        setContentView(R.layout.splash);
        
        //Set font for title
        TextView headerTextView=(TextView)findViewById(R.id.splashheader);
        Typeface typeFace=Typeface.createFromAsset(getAssets(),"fonts/neometricmedium.ttf");
        headerTextView.setTypeface(typeFace);
        
        //Set hostname to saved hostname
        EditText hostnameEdit = (EditText)findViewById(R.id.hostentry);
        hostnameEdit.setText(getApp().getDefaultHostname());
    }
	
	/** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            LocalBinder binder = (LocalBinder) service;
            mService = binder.getService();
            mService.addUpdateListener(Splash.this);
            mBound  = true;
            
            if(mService.isConnected())
            {
                startAuthentication();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mService.removeUpdateListener(Splash.this);
            mService = null;
            mBound = false;
        }
    };
	
	@Override
	protected void onStart()
	{
	    super.onStart();
	    
	    Intent intent = new Intent(this, WebSocketService.class);
	    boolean bound = bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
	}
	
	@Override
	protected void onStop()
	{
	    super.onStop();
	    if(mService != null)
	        mService.removeUpdateListener(this);
	    unbindService(mConnection);
	}
	
	
	public OBSRemoteApplication getApp()
	{
	    return (OBSRemoteApplication)getApplicationContext();
	}
	
	public void connect(View view)
	{
		//Get hostname and connect
		String hostname = ((EditText)findViewById(R.id.hostentry)).getText().toString();
		getApp().setDefaultHostname(hostname);
		
		/* Get the service going */
		mService.connect();
	}

	//Called after authentication is successful
	public void authenticated()
	{
	    Toast toast = Toast.makeText(Splash.this, "Authenticated!", Toast.LENGTH_LONG);
        toast.show();
	}
	
	public void startAuthentication()
	{
	    this.startAuthentication(null);
	}
	
	public void startAuthentication(String errorMessage)
	{
	    AuthDialogFragment frag = new AuthDialogFragment();
	    frag.splash = this;
	    frag.message = errorMessage;
	    frag.show(this.getSupportFragmentManager(), OBSRemoteApplication.TAG);
	}
	
	public void authenticate(String password)
	{
	    String salted, hashed;

	    String salt = getApp().getAuthSalt();
        String challenge = getApp().getAuthChallenge();
	        
        salted = OBSRemoteApplication.sign(password, salt);
        hashed = OBSRemoteApplication.sign(salted,  challenge);
        
	    mService.sendRequest(new Authenticate(hashed), new ResponseHandler() {

            @Override
            public void handleResponse(String jsonMessage)
            {
                Response resp = getApp().getGson().fromJson(jsonMessage, Response.class);
                
                if(resp.isOk())
                {
                    authenticated();
                }
                else
                {
                    Toast toast = Toast.makeText(Splash.this, "Auth failed: " + resp.getError(), Toast.LENGTH_LONG);
                    toast.show();
                    
                    // try authenticating again
                    startAuthentication(resp.getError());
                }
            }
        
        });
	    
	}
	
	public static class AuthDialogFragment extends DialogFragment {
	    
	    public String message;
        public Splash splash;
	    
	    @Override
	    public Dialog onCreateDialog(Bundle savedInstanceState) {
	        // Use the Builder class for convenient dialog construction
	        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
	        
	        // Get the layout inflater
	        LayoutInflater inflater = getActivity().getLayoutInflater();
	        View dialogView = inflater.inflate(R.layout.password_dialog, null);
	        
	        //Set Error message
	        if(message != null)
	            ((TextView)dialogView.findViewById(R.id.authErrorView)).setText(message);
	        
	        builder.setView(dialogView);
	        
	        
	        builder.setMessage(R.string.authenticate)
	               .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
	                   public void onClick(DialogInterface dialog, int id) {
	                       String password = ((EditText)AuthDialogFragment.this.getDialog().findViewById(R.id.password)).getText().toString();
	                       boolean rememberPassword = ((CheckBox)AuthDialogFragment.this.getDialog().findViewById(R.id.rememberPassword)).isChecked();
	                       splash.authenticate(password);
	                   }
	               })
	               .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
	                   public void onClick(DialogInterface dialog, int id) {
	                       // User cancelled the dialog, shutdown everything
	                   }
	               });
	        // Create the AlertDialog object and return it
	        return builder.create();
	    }
	}
	
    @Override
    public void onConnectionOpen()
    {
        checkAuthRequired();
    }

    private void checkAuthRequired()
    {
        mService.sendRequest(new GetAuthRequired(), new ResponseHandler() {

            @Override
            public void handleResponse(String jsonMessage)
            {
                AuthRequiredResp resp = getApp().getGson().fromJson(jsonMessage, AuthRequiredResp.class);
                
                if(resp.authRequired)
                {
                    getApp().setAuthChallenge(resp.challenge);
                    getApp().setAuthSalt(resp.salt);
                    
                    startAuthentication();
                }
                else
                {
                    authenticated();
                }
            }
        
        });
    }


    @Override
    public void onConnectionClosed(int code, String reason)
    {
        
    }
}