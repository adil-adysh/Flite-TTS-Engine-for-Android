package edu.cmu.cs.speech.tts.flite;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

/* Download user-requested voice data for Flite
 * 
 */
public class DownloadVoiceData extends Activity {
	private final static String FLITE_DATA_PATH = Environment.getExternalStorageDirectory()
    + "/flite-data/";
	
	private ArrayAdapter<CharSequence> mVoiceList;
	private ArrayAdapter<CharSequence> mVoiceDescList;
	private Handler mHandler;
	ProgressDialog pd;

		
	private static ArrayList<String> availableLanguages = null;
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		try {
			availableLanguages = Utility.readLines(FLITE_DATA_PATH+"cg/voices.list");
		} catch (IOException e) {
			Log.e("Flite.DownloadVoiceData","Could not read voice list");
			abort("Could not download voice data. Please check your internet connectivity.");			
		}
		
		setContentView(R.layout.download_voice_data);
		Spinner voiceSpinner = (Spinner) findViewById(R.id.spnVoice);
		mVoiceDescList = new ArrayAdapter<CharSequence>(this,android.R.layout.simple_spinner_item);
		mVoiceList = new ArrayAdapter<CharSequence>(this,android.R.layout.simple_spinner_item);
        voiceSpinner.setAdapter(mVoiceDescList);
        mVoiceDescList.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        
        Button bDownload = (Button) findViewById(R.id.bDownload);
        bDownload.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				downloadSelectedVoice();				
			}

		});
        mHandler = new Handler();
        populateVoiceList();

	}
	
	/* Look at the voices available in flite, 
	 * and compare them to voices already installed.
	 * The remaining voices will be available for download.
	 */
	private void populateVoiceList() {
		mVoiceList.clear();
		mVoiceDescList.clear();
		for(String s: availableLanguages) {
			String[] voiceParams = s.split("-");
			if(voiceParams.length != 3) {
				Log.e("Flite.CheckVoiceData","Incorrect voicename:" + s);
				continue;
			}
			String voxdatafile = FLITE_DATA_PATH + "cg/"+voiceParams[0]+"/"+voiceParams[1]+"/"+voiceParams[2]+".cg.voxdata";
			if(!Utility.pathExists(voxdatafile)) {
				// We need to install this voice.
				Locale loc = new Locale(voiceParams[0],voiceParams[1],voiceParams[2]);
				mVoiceDescList.add(loc.getDisplayLanguage() + " (" + loc.getDisplayCountry() + ", " + loc.getDisplayVariant() + ")");
				mVoiceList.add(s);
			}
		}
		if(mVoiceList.getCount()==0) {
			TextView tv = (TextView) findViewById(R.id.txtInfo);
			tv.setText("All voices correctly installed");
			
			Button b = (Button) findViewById(R.id.bDownload);
			b.setEnabled(false);
		}
	}
	
	// Download the chosen voice from CMU server.
	private void downloadSelectedVoice() {
		Spinner sp = (Spinner) findViewById(R.id.spnVoice);
		String selectedVoice = (String) mVoiceList.getItem(sp.getSelectedItemPosition());
		
		String[] voiceParams = selectedVoice.split("-");
		if(voiceParams.length != 3) {
			Log.e("Flite.CheckVoiceData","Incorrect voicename:" + selectedVoice);
			return;
		}
		
		String datapath = FLITE_DATA_PATH + "cg/" + voiceParams[0] + "/" + voiceParams[1];
		try {
			if(!Utility.pathExists(datapath))
				if(! new File(datapath).mkdirs()) {
					abort("Could not create directory structure necessary to store the voice");
				}
		} catch (Exception e) {
			abort("Could not create directory structure necessary to store the voice");
		}

		final FileDownloader fdload = new FileDownloader();
		
		pd = new ProgressDialog(this);
		pd.setCancelable(true);
		pd.setIndeterminate(true);
		pd.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		pd.show();
		
		final Builder voicedownloadSuccessStatus = new AlertDialog.Builder(this);
		voicedownloadSuccessStatus.setPositiveButton("Ok",null);
				
		
		final String url = "http://tts.speech.cs.cmu.edu/android/general/"+selectedVoice+".voxdata";
		final String filename = datapath + "/" + voiceParams[2] + ".cg.voxdata";
    	new Thread() {
    		public void run() { 
    			fdload.saveUrlAsFile(url, filename);
    			while(fdload.totalFileLength == 0) {
    				if (fdload.finished)
    					break;
    			}
    			runOnUiThread(new Runnable() {

    				@Override
    				public void run() {
    					pd.setIndeterminate(false);
    					pd.setMax(fdload.totalFileLength);
    				}
    			});
    			while(!fdload.finished) {
    				runOnUiThread(new Runnable() {

    					@Override
    					public void run() {
    						pd.setProgress(fdload.finishedFileLength);
    					}
    				});
    			}
    			runOnUiThread(new Runnable() {
					
					@Override
					public void run() {
	
		    			pd.dismiss();

					}
				});
    			if(!fdload.success) {
    				Log.e("Flite.DownloadVoiceData", "Voice data download failed!");
    				if(fdload.abortDownload)
    					voicedownloadSuccessStatus.setMessage("Voice download aborted.");
    				else
    					voicedownloadSuccessStatus.setMessage("Voice download failed! Check your internet settings.");
    			}
    			else {
    				voicedownloadSuccessStatus.setMessage("Voice download succeeded");
    			}
    			mHandler.post(new Runnable() {
					
					@Override
					public void run() {
						voicedownloadSuccessStatus.show();
						populateVoiceList();
					}
				});
    			
    		}
    	}.start();
	}
	
	private void abort(String str) {
		Log.e("Flite.DownloadVoiceData", str);
		new AlertDialog.Builder(this)
	      .setMessage(str)
	      .setPositiveButton("Ok",new OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				finish();				
			}
		})
	      .show();
	}

}
