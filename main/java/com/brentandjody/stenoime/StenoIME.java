package com.brentandjody.stenoime;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.inputmethodservice.InputMethodService;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.brentandjody.stenoime.Input.NKeyRolloverMachine;
import com.brentandjody.stenoime.Input.StenoMachine;
import com.brentandjody.stenoime.Input.TXBoltMachine;
import com.brentandjody.stenoime.Input.TouchLayer;
import com.brentandjody.stenoime.Translator.Dictionary;
import com.brentandjody.stenoime.Translator.RawStrokeTranslator;
import com.brentandjody.stenoime.Translator.SimpleTranslator;
import com.brentandjody.stenoime.Translator.Stroke;
import com.brentandjody.stenoime.Translator.TranslationResult;
import com.brentandjody.stenoime.Translator.Translator;

import java.util.Set;

/**
 * Created by brent on 30/11/13.
 * Replacement Keyboard
 */
public class StenoIME extends InputMethodService implements TouchLayer.OnStrokeListener,
        StenoMachine.OnStrokeListener, Dictionary.OnDictionaryLoadedListener {

    private static final String TAG = "StenoIME";
    private static final String ACTION_USB_PERMISSION = "com.brentandjody.USB_PERMISSION";

    private static boolean KEYBOARD_CONNECTED=false;
    private static boolean TXBOLT_CONNECTED=false;

    private StenoApp App; // to make it easier to access the Application class
    private SharedPreferences prefs;
    private boolean inline_preview;
    private Translator mTranslator;
    //TXBOLT:private PendingIntent mPermissionIntent;

    //layout vars
    private LinearLayout mKeyboard;
    private LinearLayout preview_overlay;
    private TextView preview;
    private TextView debug_text;

    private int preview_length = 0;
    private boolean redo_space;


    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");
        super.onCreate();
        App = ((StenoApp) getApplication());
        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false); //load default values
        //TXBOLT:mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        KEYBOARD_CONNECTED = isKeyboardConnected(getResources().getConfiguration());
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.d(TAG, "onConfigurationChanged()");
        KEYBOARD_CONNECTED = isKeyboardConnected(newConfig);
        if (KEYBOARD_CONNECTED) {
            setMachineType(StenoMachine.TYPE.KEYBOARD);
        } else {
            setMachineType(StenoMachine.TYPE.VIRTUAL);
        }
    }


    @Override
    public void onInitializeInterface() {
        // called before initialization of interfaces, and after config changes
        Log.d(TAG, "onInitializeInterface()");
        super.onInitializeInterface();
//TXBOLT:        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
//        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
//        registerReceiver(mUsbReceiver, filter); //listen for plugged/unplugged events
    }

    @Override
    public View onCreateInputView() {
        Log.d(TAG, "onCreateInputView()");
        mKeyboard = new LinearLayout(this);
        try {
            mKeyboard.addView(getLayoutInflater().inflate(R.layout.keyboard, null));
        } catch (NullPointerException e) {
            Log.e(TAG, "Could not load keyboard layout!");
        }
        mKeyboard.findViewById(R.id.settings_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                launchSettingsActivity();
            }
        });
        return mKeyboard;
    }

    @Override
    public View onCreateCandidatesView() {
        Log.d(TAG, "onCreateCandidatesView()");
        View view = null;
        try {
        view = getLayoutInflater().inflate(R.layout.preview, null);
        } catch (NullPointerException e) {
            Log.e(TAG, "Could not load candidates view");
            return null;
        }
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                sendText(mTranslator.submitQueue());
            }
        });
        preview = (TextView) view.findViewById(R.id.preview);
        preview_overlay = (LinearLayout) view.findViewById(R.id.preview_overlay);
        debug_text = (TextView) view.findViewById(R.id.debug);

        App.setProgressBar((ProgressBar) preview_overlay.findViewById(R.id.progressBar));
        return view;
    }


    @Override
    public void onStartInputView(EditorInfo info, boolean restarting) {
        //Called when starting input to a new field
        Log.d(TAG, "onStartInputView()");

        super.onStartInputView(info, restarting);
        if (!restarting)
           mTranslator.reset(); //clear stroke history

        initializePreview();
        initializeTranslator(App.getTranslatorType());
        if (mTranslator!=null) mTranslator.reset();

        if (App.getMachineType() == StenoMachine.TYPE.VIRTUAL) {
            launchVirtualKeyboard();
        } else {
            removeVirtualKeyboard();
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mUsbReceiver);
        mKeyboard=null;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return dispatchKeyEvent(event);
    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return dispatchKeyEvent(event);
    }

    // Implemented Interfaces

    @Override
    public void onStroke(Set<String> keys) {
        Stroke stroke = new Stroke(keys);
        processStroke(stroke);
    }

    @Override
    public void onDictionaryLoaded() {
        unlockKeyboard();
   }

    // Private methods

    private boolean isKeyboardConnected(Configuration config) {
        return (config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
                && config.keyboard == Configuration.KEYBOARD_QWERTY);
    }

    private void initializePreview() {
        inline_preview = prefs.getBoolean("pref_inline_preview", false);
        setCandidatesViewShown(!inline_preview);
        if (! inline_preview) {
            if (preview!=null) preview.setText("");
            if (debug_text !=null) debug_text.setText("");
        }
        preview_length=0;
    }

    private void initializeTranslator(Translator.TYPE t) {
        switch (t) {
            case RawStrokes: mTranslator = new RawStrokeTranslator(); break;
            case SimpleDictionary:
                mTranslator = new SimpleTranslator(getApplicationContext());
                ((SimpleTranslator) mTranslator).setDictionary(App.getDictionary(this));
                break;
        }
        if (mTranslator.usesDictionary())
            loadDictionary();
    }

    private void processStroke(Stroke stroke) {
        sendText(mTranslator.translate(stroke));
    }

    private void launchSettingsActivity() {
        Intent intent = new Intent(mKeyboard.getContext(), SettingsActivity.class);
        lockKeyboard();
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private void loadDictionary() {
        if (!App.isDictionaryLoaded()) {
            lockKeyboard();
            App.getDictionary(this);
        } else {
            unlockKeyboard();
        }
    }

    private void sendText(TranslationResult tr) {
        preview.setText(tr.getPreview());
        debug_text.setText(tr.getExtra());
        InputConnection connection = getCurrentInputConnection();
        if (connection == null) return; //short circuit
        connection.beginBatchEdit();
        //remove the preview
        if (inline_preview && preview_length>0) {
            connection.deleteSurroundingText(preview_length, 0);
            if (redo_space)
                connection.commitText(" ", 1);
        }
        // deal with backspaces
        if (tr.getBackspaces()==-1) {  // this is a special signal to remove the prior word
            smartDelete(connection);
        } else if (tr.getBackspaces() > 0) {
            connection.deleteSurroundingText(tr.getBackspaces(), 0);
        }
        connection.commitText(tr.getText(), 1);
        //draw the preview
        if (inline_preview) {
            String p = tr.getPreview();
            if (mTranslator instanceof SimpleTranslator) {
                int bs = ((SimpleTranslator) mTranslator).preview_backspaces();
                redo_space=(bs > 0);
            }
            if (redo_space)
                connection.deleteSurroundingText(1, 0);
            connection.commitText(p, 1);
            preview_length = p.length();
        }
        connection.endBatchEdit();
    }

    private void smartDelete(InputConnection connection) {
        try {
            String t = connection.getTextBeforeCursor(2, 0).toString();
            while (! (t.length()==0 || t.equals(" "))) {
                connection.deleteSurroundingText(1, 0);
                t = connection.getTextBeforeCursor(1, 0).toString();
            }
        } finally {
            connection.commitText("", 1);
        }
    }


    private void saveIntPreference(String name, int value) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(name, value);
        editor.commit();
    }

    // *** NKeyRollover Keyboard ***

    private boolean dispatchKeyEvent(KeyEvent event) {
        StenoMachine inputDevice = App.getInputDevice();
        if (inputDevice instanceof NKeyRolloverMachine) {
            ((NKeyRolloverMachine) inputDevice).handleKeys(event);
        }
        return (event.getKeyCode() != KeyEvent.KEYCODE_BACK);
    }

    // *** Virtual Keyboard ***

    private void launchVirtualKeyboard() {
        TouchLayer keyboard = (TouchLayer) mKeyboard.findViewById(R.id.keyboard);
        keyboard.setOnStrokeListener(this);
        //keyboard.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up_in));
        keyboard.setVisibility(View.VISIBLE);
        if (mTranslator.usesDictionary()) {
            if (App.getDictionary(this).isLoading())
                lockKeyboard();
            else
                unlockKeyboard();
        }
    }

    private void removeVirtualKeyboard() {
        TouchLayer keyboard = (TouchLayer) mKeyboard.findViewById(R.id.keyboard);
        if (keyboard != null) {
            //keyboard.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_down_out));
            keyboard.setVisibility(View.GONE);
        }
    }

    private void lockKeyboard() {
        View overlay;
        if (App.getMachineType() == StenoMachine.TYPE.VIRTUAL) {
            overlay = mKeyboard.findViewById(R.id.overlay);
            if (overlay != null) overlay.setVisibility(View.VISIBLE);
        }
        if (preview_overlay != null) preview_overlay.setVisibility(View.VISIBLE);
        if (mTranslator!=null) mTranslator.lock();
    }

    private void unlockKeyboard() {
        View overlay = mKeyboard.findViewById(R.id.overlay);
        if (overlay!=null) overlay.setVisibility(View.INVISIBLE);
        if (preview_overlay != null) preview_overlay.setVisibility(View.INVISIBLE);
        if (mTranslator!=null) mTranslator.unlock();
    }


    // *** Stuff to change Input Device ***

    private void setMachineType(StenoMachine.TYPE t) {
        if (t==null) t= StenoMachine.TYPE.VIRTUAL;
        if (App.getMachineType()==t) return; //short circuit
        App.setMachineType(t);
        saveIntPreference(StenoApp.KEY_MACHINE_TYPE, App.getMachineType().ordinal());
        switch (App.getMachineType()) {
            case VIRTUAL:
                App.setInputDevice(null);
                if (mKeyboard!=null) launchVirtualKeyboard();
                break;
            case KEYBOARD:
                Toast.makeText(this,"Physical Keyboard Detected",Toast.LENGTH_SHORT).show();
                if (mKeyboard!=null) removeVirtualKeyboard();
                registerMachine(new NKeyRolloverMachine());
                break;
//TXBOLT:            case TXBOLT:
//                Toast.makeText(this,"TX-Bolt Machine Detected",Toast.LENGTH_SHORT).show();
//                if (mKeyboard!=null) removeVirtualKeyboard();
//                ((UsbManager)getSystemService(Context.USB_SERVICE))
//                        .requestPermission(App.getUsbDevice(), mPermissionIntent);
//                break;
        }
    }

    private void registerMachine(StenoMachine machine) {
        if (App.getInputDevice()!=null) App.getInputDevice().stop(); //stop the prior device
        App.setInputDevice(machine);
        machine.setOnStrokeListener(this);
        machine.start();
    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                Log.d(TAG, "mUSBReceiver: received detached event");
                App.setUsbDevice(null);
                setMachineType(StenoMachine.TYPE.VIRTUAL);
            }
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                Log.d(TAG, "mUSBReceiver: received attach event");
                App.setUsbDevice((UsbDevice) intent.getParcelableExtra(UsbManager.EXTRA_DEVICE));
                setMachineType(StenoMachine.TYPE.TXBOLT);
             }
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    App.setUsbDevice(device);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            //TODO: (also add stuff to known devices list)
                            UsbManager usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
                            registerMachine(new TXBoltMachine(usbManager, device));
                        }
                    }
                    else {
                        Log.d(TAG, "permission denied for device " + device);
                    }
                }
            }
        }
    };


}
