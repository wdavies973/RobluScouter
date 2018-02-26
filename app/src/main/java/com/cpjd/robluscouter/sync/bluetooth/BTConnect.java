package com.cpjd.robluscouter.sync.bluetooth;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.util.Log;

import com.cpjd.robluscouter.io.IO;
import com.cpjd.robluscouter.models.RCheckout;
import com.cpjd.robluscouter.models.RCloudSettings;
import com.cpjd.robluscouter.models.RForm;
import com.cpjd.robluscouter.models.RSettings;
import com.cpjd.robluscouter.models.RTab;
import com.cpjd.robluscouter.models.RUI;
import com.cpjd.robluscouter.models.metrics.RGallery;
import com.cpjd.robluscouter.notifications.Notify;
import com.cpjd.robluscouter.sync.cloud.AutoCheckoutTask;
import com.cpjd.robluscouter.utils.HandoffStatus;
import com.cpjd.robluscouter.utils.Utils;

import org.codehaus.jackson.map.DeserializationConfig;
import org.codehaus.jackson.map.ObjectMapper;
import org.json.simple.JSONArray;
import org.json.simple.parser.JSONParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;

/**
 * Manages a Bluetooth connection with the server and sending data to it.
 *
 * Brief description of processes:
 * -Load the sync-list from RSettings, these are all the phones / tablets the user would like to connect
 * to and sync with.
 * -After a successful sync is achieved, the following will be transmitted, in this order:
 *      -Scouting data sent to Roblu Master
 *      -Form and UI received from Roblu Master
 *      -Scouting status and data receive from Roblu Master
 * -If this is a FIRST sync (the local device contains NO DATA), the scouter can request a full event list
 * from the client. Use the initial boolean flag in the constructor for this.
 *
 * Let's talk about physically how data is transferred.
 *
 * Roblu apps will use a identical communication protocol for transferring data. Prior to the content of a message,
 * an identification string will be sent, followed by the actual serialized content of the message.
 *
 * TAGS
 * -"form" - Received by Scouter, the following string should be processed as an RForm object
 * -"ui" - Received by Scouter, the following string should be processed as an RUI object
 * -"scoutingData" - Received by Master, the following string should be deserialized as a RCheckouts array and merged
 * -"checkouts" - Received by Scouter, the following string should be deserialized as a RCheckouts array and merged
 * -"success-<original tag>" - signifies that the device we are connected to successfully processed our request and data
 *
 * @version 1
 * @since 4.0.0
 * @author Will Davies
 */
public class BTConnect extends Thread implements Bluetooth.BluetoothListener {

    /**
     * Roblu uses a Bluetooth wrapper library to simplify connections and lessen the amount of bugs.
     * The library is available here: https://github.com/OmarAflak/Bluetooth-Library
     * This library provides easy access to searching, connecting, and sending data between
     * Bluetooth capable devices.
     */
    private Bluetooth bluetooth;

    /**
     * Contains a String array of all Bluetooth MAC addresses of devices we've acknowledged before, each one
     * will be contacted, and if available, a Bluetooth sync will occur
     */
    private ArrayList<String> bluetoothServerMACs;

    /**
     * Stores the index of the current Bluetooth device in {@link #bluetoothServerMACs} that
     * we are connected to
     */
    private int index = 0;

    /**
     * This listener will just receive some generic updates when certain events happen within BTConnect.
     * It is just some generic code for the UI to process.
     */
    private BTConnectListener listener;

    @Override
    public void deviceDiscovered(BluetoothDevice device) {

    }


    public interface BTConnectListener {
        void success();
        void errorOccurred(String message);
    }

    /**
     * Used for deserializing and serializing objects to and from strings
     */
    private ObjectMapper mapper = new ObjectMapper().configure(DeserializationConfig.Feature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private RSettings settings;

    private ProgressDialog pd;

    /**
     * Creates a BTConnect object for syncing to a Bluetooth device
     * @param bluetooth {@link #bluetooth}
     */
    public BTConnect(ProgressDialog pd, RSettings settings, Bluetooth bluetooth) {
        this.pd = pd;
        this.bluetooth = bluetooth;
        this.settings = settings;

        this.bluetooth.setListener(this);
    }

    /**
     * Starts the sync task
     */
    @Override
    public void run() {
        if(bluetooth.isEnabled()) begin();
        else bluetooth.enable();

    }

    private void begin() {

        /*
         * First, load dependencies
         */
        bluetoothServerMACs = settings.getBluetoothServerMACs();

        if(bluetoothServerMACs == null) {
            Log.d("RSBS", "No Bluetooth servers found in the sync list. Aborting BTConnect.");
            return;
        }

        //bluetooth.setCommunicationCallback(this); // tell the @Override methods in this class to listen to this Bluetooth object

        if(!connectToNextDevice()) {
            if(listener != null) listener.errorOccurred("No Bluetooth servers were found to connect to.");
        }
    }

    /**
     * Attempts to connect to the next device in {@link #bluetoothServerMACs}
     * @return false if there isn't a next device to connect to, true if a device is being connected to
     */
    private boolean connectToNextDevice() {
        if(index >= bluetoothServerMACs.size()) return false;
        Log.d("RSBS", "Attempting to connect to device: "+bluetoothServerMACs.get(index));
        bluetooth.connectToDevice(bluetoothServerMACs.get(index));
        index++;
        return true;
    }

    /**
     * This method should be called after a successful connection, it will perform the actual syncing of data
     *
     * This code is loosely mirrored from
     * @see com.cpjd.robluscouter.sync.cloud.Service
     */
    private void transfer() {
        bluetooth.send("isActive", "noParams");

        // Send completed
        IO io = new IO(bluetooth.getActivity());
        ArrayList<RCheckout> checkouts = io.loadPendingCheckouts();
        ArrayList<RCheckout> toUpload = new ArrayList<>();
        if(checkouts != null) {
            for(RCheckout checkout : checkouts) {
                if(checkout.getStatus() == HandoffStatus.COMPLETED && checkout.getTeam().getLastEdit() > 0) {
                    for(RTab t : checkout.getTeam().getTabs()) {
                        LinkedHashMap<String, Long> edits = t.getEdits();
                        if(edits == null) edits = new LinkedHashMap<>();
                        edits.put(settings.getName(), System.currentTimeMillis());
                        t.setEdits(edits);
                    }
                }
                    /*
                     * Pack images
                     */
                for(RTab tab : checkout.getTeam().getTabs()) {
                    for(int i = 0; tab.getMetrics() != null && i < tab.getMetrics().size(); i++) {
                        if(!(tab.getMetrics().get(i) instanceof RGallery)) continue;

                        ((RGallery)tab.getMetrics().get(i)).setImages(new ArrayList<byte[]>());
                        for(int j = 0; ((RGallery)tab.getMetrics().get(i)).getPictureIDs() != null && j < ((RGallery)tab.getMetrics().get(i)).getPictureIDs().size(); j++) {
                            ((RGallery)tab.getMetrics().get(i)).getImages().add(io.loadPicture(((RGallery)tab.getMetrics().get(i)).getPictureIDs().get(j)));
                        }
                    }
                }
                if(checkout.getStatus() == HandoffStatus.COMPLETED) {
                    toUpload.add(checkout);
                }

                try {
                    bluetooth.send("SCOUTING_DATA", mapper.writeValueAsString(toUpload));
                    Notify.notifyNoAction(bluetooth.getActivity(), "Sent checkouts successfully", "Successfully sent "+checkouts.size()+" checkouts to target device over Bluetooth.");
                } catch(Exception e) {
                    Log.d("RSBS", "Failed to send completed checkouts.");
                }
            }
        }

        bluetooth.send("requestForm", "noParams");
        bluetooth.send("requestUI", "noParams");
        bluetooth.send("requestCheckouts", "time:"+new IO(bluetooth.getActivity()).loadCloudSettings().getLastCheckoutSync());
        bluetooth.send("requestNumber", "noParams");
        bluetooth.send("requestEventName", "noParams");
        bluetooth.send("DONE", "noParams");
    }

    @Override
    public void messageReceived(String header, String message) {
        IO io = new IO(bluetooth.getActivity());

        if(header.equals("FORM")) {
            try {
                RForm form = mapper.readValue(message, RForm.class);
                io.saveForm(form);
            } catch(Exception e) {
                Log.d("RSBS", "Failed to deserialized RForm from Bluetooth.");
            }
        }
        else if(header.equals("UI")) {
            try {
                RUI ui = mapper.readValue(message, RUI.class);
                settings.setRui(ui);
                io.saveSettings(settings);
            } catch(Exception e) {
                Log.d("RSBS", "Failed to deserialized UI from Bluetooth.");
            }
        }
        else if(header.equals("CHECKOUTS")) {
            try {
                JSONParser parser = new JSONParser();
                JSONArray array = (JSONArray)parser.parse(message);
                ArrayList<RCheckout> refList = new ArrayList<>();
                for(int i = 0; i < array.size(); i++) {
                    String s = array.get(i).toString();
                    RCheckout checkout = mapper.readValue(s, RCheckout.class);
                    // Unpack images
                    /*
                     * Unpack images
                     */
                    for(RTab tab : checkout.getTeam().getTabs()) {
                        for(int l = 0; tab.getMetrics() != null && l < tab.getMetrics().size(); l++) {
                            if(!(tab.getMetrics().get(l) instanceof RGallery)) continue;

                            ((RGallery)tab.getMetrics().get(i)).setPictureIDs(new ArrayList<Integer>());
                            for(int j = 0; ((RGallery)tab.getMetrics().get(l)).getImages() != null && j < ((RGallery)tab.getMetrics().get(l)).getImages().size(); j++) {
                                ((RGallery)tab.getMetrics().get(l)).getPictureIDs().add(io.savePicture(((RGallery)tab.getMetrics().get(l)).getImages().get(j)));
                            }
                            // Set metrics to null
                            ((RGallery)tab.getMetrics().get(l)).setImages(null);
                        }
                    }

                    refList.add(checkout);
                    io.saveCheckout(checkout);
                }



                /*
                 * Run the auto-assignment checkout task
                 */
                new AutoCheckoutTask(null, io, settings, refList).start();

                RCloudSettings cloudSettings = io.loadCloudSettings();
                cloudSettings.setLastCheckoutSync(System.currentTimeMillis());
                io.saveCloudSettings(cloudSettings);

                Utils.requestUIRefresh(bluetooth.getActivity(), false, true);
                Notify.notifyNoAction(bluetooth.getActivity(), "Successfully pulled "+refList.size()+" checkouts.", "Roblu Scouter successfully pulled "+refList.size()+" checkouts from" +
                        " a Bluetooth connection at "+Utils.convertTime(System.currentTimeMillis()));

                pd.dismiss();
            } catch(Exception e) {
                Log.d("RSBS", "Failed to process checkouts received over Bluetooth.");
            }
        }
        else if(header.equals("NUMBER")) {
            RCloudSettings cloudSettings = io.loadCloudSettings();
            cloudSettings.setTeamNumber(Integer.parseInt(message));
            io.saveCloudSettings(cloudSettings);
        }
        else if(header.equals("EVENT_NAME")) {
            RCloudSettings cloudSettings = io.loadCloudSettings();
            cloudSettings.setEventName(message);
            io.saveCloudSettings(cloudSettings);
        }
        else if(header.equals("ACTIVE")) {
            if(!Boolean.parseBoolean(message)) {
                // Stop the thread
                interrupt();
                pd.dismiss();
            }
        }
        else if(header.equals("DONE")) {

        }
    }

    @Override
    public void deviceConnected(BluetoothDevice device) {
        Log.d("RSBS", "Connected to "+device.getName());

        transfer();
    }

    @Override
    public void deviceDisconnected(BluetoothDevice device, String reason) {

    }

    @Override
    public void errorOccurred(String message) {
        Log.d("RSBS", "Error occurred: "+message);
    }

    @Override
    public void stateChanged(int state) {
        if(state == BluetoothAdapter.STATE_ON) {
            begin();
        }
    }

    @Override
    public void discoveryStopped() {

    }

}