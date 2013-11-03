/*
Copyright (c) 2012, Apps4Av Inc. (apps4av.com) 
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
    *     * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
    *
    *     THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.apps4av.avarehelper.gdl90;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;

import com.apps4av.avarehelper.nmea.NMEA;
import com.apps4av.avarehelper.nmea.Ownship;
import com.ds.avare.IHelper;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

/**
 * 
 * @author zkhan
 *
 */
public class BlueToothConnection {

    private static BluetoothAdapter mBtAdapter = null;
    private static BluetoothSocket mBtSocket = null;
    private static InputStream mStream = null;
    private static boolean mRunning = false;
    
    private static BlueToothConnection mConnection;
    
    private static AdsbStatus mAdsbStatus;
    private static IHelper mHelper;

    /*
     *  Well known SPP UUID
     */
    private static final UUID MY_UUID =
            UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    /**
     * 
     */
    private BlueToothConnection() {
    }

    
    /**
     * 
     * @return
     */
    public static BlueToothConnection getInstance() {

        if(null == mConnection) {
            mConnection = new BlueToothConnection();
            mBtAdapter = BluetoothAdapter.getDefaultAdapter();
            mAdsbStatus = new AdsbStatus();
            mAdsbStatus.setState(AdsbStatus.DISCONNECTED);
        }
        return mConnection;
    }

    /**
     * 
     */
    public void stop() {
        if(mAdsbStatus.getState() != AdsbStatus.CONNECTED) {
            return;
        }
        mRunning = false;
    }

    /**
     * 
     */
    public void start() {
        
        if(mAdsbStatus.getState() != AdsbStatus.CONNECTED) {
            return;
        }
        
        mRunning = true;
        
        /*
         * Thread that reads BT
         */
        Thread thread = new Thread() {
            @Override
            public void run() {
                
                byte[] buffer = new byte[32768];
                DataBuffer dbuffer = new DataBuffer(32768);
                Decode decode = new Decode();
                NMEA nmea = new NMEA();
                Ownship nmeaOwnship = new Ownship();
                
                
                /*
                 * This state machine will keep trying to connect to 
                 * ADBS receiver
                 */
                while(mRunning) {
                    
                    int red = 0;
                    
                    /*
                     * Read.
                     */
                    red = read(buffer);
                    if(red <= 0) {
                        try {
                            Thread.sleep(100);
                        } catch (Exception e) {
                            
                        }
                        continue;
                    }

                    /*
                     * See if it is GPS NMEA data, and not ADS-B sequence
                     */
                    if(NMEA.isNMEA(buffer, red)) {
                        byte buff[] = new byte[red];
                        System.arraycopy(buffer, 0, buff, 0, red);
                        com.apps4av.avarehelper.nmea.Message m = nmea.decode(buff);
                        if(nmeaOwnship.addMessage(m)) {
                                
                            /*
                             * Make a GPS locaiton message from ADSB ownship message.
                             */
                            JSONObject object = new JSONObject();
                            Ownship om = nmeaOwnship;
                            try {
                                object.put("type", "ownship");
                                object.put("longitude", (double)om.mLon);
                                object.put("latitude", (double)om.mLat);
                                object.put("speed", (double)(om.mHorizontalVelocity));
                                object.put("bearing", (double)om.mDirection);
                                object.put("altitude", (double)((double)om.mAltitude));
                                object.put("time", (long)om.getTime());
                            } catch (JSONException e1) {
                                return;
                            }
                            
                            if(mHelper != null) {
                                try {
                                    mHelper.sendDataText(object.toString());
                                } catch (Exception e) {
                                }
                            }

                        }
                        continue;
                    }
                     
                    dbuffer.put(buffer, red);
                 
                    byte[] buf;
                    while(null != (buf = dbuffer.get())) {

                        /*
                         * Get packets, decode
                         */
                        com.apps4av.avarehelper.gdl90.Message m = decode.decode(buf);
                        /*
                         * Post on UI thread.
                         */
                        
                        if(m instanceof UplinkMessage) {
                            /*
                             * Send an uplink nexrad message
                             */
                            LinkedList<Product> pds = ((UplinkMessage) m).getFis().getProducts();
                            for(Product p : pds) {
                                if(p instanceof Id6364Product) {
                                    Id6364Product pn = (Id6364Product)p;
                                }
                            }
                        }
                        else if(m instanceof OwnshipMessage) {
                            
                            /*
                             * Make a GPS locaiton message from ADSB ownship message.
                             */
                            JSONObject object = new JSONObject();
                            OwnshipMessage om = (OwnshipMessage)m;
                            try {
                                object.put("type", "ownship");
                                object.put("longitude", (double)om.mLon);
                                object.put("latitude", (double)om.mLat);
                                object.put("speed", (double)(om.mHorizontalVelocity));
                                object.put("bearing", (double)om.mDirection);
                                object.put("altitude", (double)((double)om.mAltitude));
                                object.put("time", (long)om.getTime());
                            } catch (JSONException e1) {
                                return;
                            }
                            
                            if(mHelper != null) {
                                try {
                                    mHelper.sendDataText(object.toString());
                                } catch (Exception e) {
                                }
                            }
                        }
                    }
                }
                
            }
        };
        thread.start();
    }
    
    /**
     * 
     * @param state
     */
    private void setState(int state) {
        mAdsbStatus.setState(state);
    }
    
    /**
     * 
     * @return
     */
    public List<String> getDevices() {
        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();
        List<String> list = new ArrayList<String>();
        
        /*
         * Find devices
         */
        if(null == pairedDevices) {            
            return null;
        }
        for(BluetoothDevice bt : pairedDevices) {
            list.add((String)bt.getName());
        }
        
        return list;
    }
    
    /**
     * 
     * A device name devNameMatch, will connect to first device whose
     * name matched this string.
     * @return
     */
    public boolean connect(String devNameMatch) {
        /*
         * Only when not connected, connect
         */
        if(mAdsbStatus.getState() != AdsbStatus.DISCONNECTED) {
            return false;
        }
        setState(AdsbStatus.CONNECTING);
        if(null == mBtAdapter) {
            setState(AdsbStatus.DISCONNECTED);
            return false;
        }
        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();

        /*
         * Find device
         */
        if(null == pairedDevices) {            
            setState(AdsbStatus.DISCONNECTED);
            return false;
        }
        BluetoothDevice device = null;
        for(BluetoothDevice bt : pairedDevices) {
           if(bt.getName().equals(devNameMatch)) {
               device = bt;
           }
        }
   
        /*
         * Stop discovery
         */
        mBtAdapter.cancelDiscovery();
 
        if(null == device) {
            setState(AdsbStatus.DISCONNECTED);
            return false;
        }
        
        /*
         * Make socket
         */
        try {
            mBtSocket = device.createRfcommSocketToServiceRecord(MY_UUID);
        } 
        catch(Exception e) {
            setState(AdsbStatus.DISCONNECTED);
            return false;
        }
    
        /*
         * Establish the connection.  This will block until it connects.
         */
        try {
            mBtSocket.connect();
        } 
        catch(Exception e) {
            try {
                mBtSocket.close();
            } 
            catch(Exception e2) {
            }
            setState(AdsbStatus.DISCONNECTED);
            return false;
        } 

        try {
            mStream = mBtSocket.getInputStream();
        } 
        catch (Exception e) {
            try {
                mBtSocket.close();
            } 
            catch(Exception e2) {
            }
            setState(AdsbStatus.DISCONNECTED);
        } 

        setState(AdsbStatus.CONNECTED);

        return true;
    }
    
    /**
     * 
     */
    public void disconnect() {
        /*
         * Exit
         */
        try {
            mStream.close();
        } 
        catch(Exception e2) {
        }
        
        try {
            mBtSocket.close();
        } 
        catch(Exception e2) {
        }    
        setState(AdsbStatus.DISCONNECTED);
    }
    
    /**
     * 
     * @return
     */
    private int read(byte[] buffer) {
        int red = -1;
        try {
            red = mStream.read(buffer, 0, buffer.length);
        } 
        catch(Exception e) {
            red = -1;
        }
        return red;
    }

    /**
     * 
     * @return
     */
    public boolean isConnected() {
        return mAdsbStatus.getState() == AdsbStatus.CONNECTED;
    }

    /**
     * 
     * @return
     */
    public boolean isConnectedOrConnecting() {
        return mAdsbStatus.getState() == AdsbStatus.CONNECTED ||
                mAdsbStatus.getState() == AdsbStatus.CONNECTING;
    }

    /**
     * 
     * @param helper
     */
    public void setHelper(IHelper helper) {
        mHelper = helper;
    }

}