package com.kontakt.sample.ui.activity;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v7.widget.Toolbar;
import android.widget.ListView;
import android.widget.Toast;

import com.kontakt.sample.R;
import com.kontakt.sample.adapter.BeaconBaseAdapter;
import com.kontakt.sample.dialog.PasswordDialogFragment;
import com.kontakt.sample.util.Utils;
import com.kontakt.sdk.android.configuration.BeaconActivityCheckConfiguration;
import com.kontakt.sdk.android.configuration.ForceScanConfiguration;
import com.kontakt.sdk.android.connection.OnServiceBoundListener;
import com.kontakt.sdk.android.data.RssiCalculators;
import com.kontakt.sdk.android.device.BeaconDevice;
import com.kontakt.sdk.android.device.Region;
import com.kontakt.sdk.android.factory.AdvertisingPackage;
import com.kontakt.sdk.android.factory.Filters;
import com.kontakt.sdk.android.manager.BeaconManager;
import com.kontakt.sdk.core.interfaces.BiConsumer;

import java.util.List;

import butterknife.ButterKnife;
import butterknife.InjectView;
import butterknife.OnItemClick;

public class BeaconRangeActivity extends BaseActivity {

    private static final int REQUEST_CODE_ENABLE_BLUETOOTH = 1;

    private static final int REQUEST_CODE_CONNECT_TO_DEVICE = 2;

    @InjectView(R.id.toolbar)
    Toolbar toolbar;

    @InjectView(R.id.device_list)
    ListView deviceList;

    private BeaconBaseAdapter adapter;
    private BeaconManager beaconManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.beacon_range_activity);
        ButterKnife.inject(this);
        setUpActionBar(toolbar);
        setUpActionBarTitle(getString(R.string.achievements));
        adapter = new BeaconBaseAdapter(this);

        beaconManager = BeaconManager.newInstance(this);

        beaconManager.setScanMode(BeaconManager.SCAN_MODE_BALANCED); // Works only for Android L OS version

        beaconManager.setRssiCalculator(RssiCalculators.newLimitedMeanRssiCalculator(5)); //Calculate rssi value basing on arithmethic mean basing on 5 last notified values

        //accept beacons from distance 1m at most
        beaconManager.addFilter(new Filters.CustomFilter() {
            public Boolean apply(AdvertisingPackage advPackage) {
                return advPackage.getAccuracy() < 1;
            }
        });


/*
        beaconManager.setRssiCalculator(new RssiCalculators.CustomRssiCalculator() { //Provide your own Rssi Calculator to estimate manipulate Rssi value
            @Override                                                                  //and thus Proximity from Beacon device
            public double calculateRssi(int beaconHashCode, int rssiValue) {
                return rssiValue;
            }

            @Override
            public void clear() {

            }
        });
*/

        //beaconManager.addFilter(Filters.newProximityUUIDFilter(BeaconManager.DEFAULT_KONTAKT_BEACON_PROXIMITY_UUID)); //accept Beacons with default Proximity UUID only
        //(f7826da6-4fa2-4e98-8024-bc5b71e0893e)
/*
        beaconManager.addFilter(Filters.newAddressFilter("00:00:00:00:00:00")); //accept Beacons with specified MAC address only
        beaconManager.addFilter(Filters.newBeaconUniqueIdFilter("myID"));         //accept Beacons with specified Unique Id only
        beaconManager.addFilter(Filters.newDeviceNameFilter("my_beacon_name"));   //accept Beacons with specified name only
        beaconManager.addFilter(Filters.newFirmwareFilter(26));                   //accept Beacons with specified Firmware version only
        beaconManager.addFilter(Filters.newMajorFilter(666));                     //accept Beacons with specified Major only
        beaconManager.addFilter(Filters.newMinorFilter(333));                     //accept Beacons with specified Minor only
        beaconManager.addFilter(Filters.newMultiFilterBuilder()                   //accept Beacon matching constraints specified in MultiFilter
                                        .setBeaconUniqueId("Boom")
                                        .setDeviceName("device_name")
                                        .setAddress("00:00:00:00:00:00")
                                        .setFirmware(26)
                                        .setProximityUUID(UUID.randomUUID())
                                        .build());
        beaconManager.addFilter(new Filters.CustomFilter() {                      //create your customized filter
            @Override
            public boolean filter(AdvertisingPackage advertisingPackage) {
                return advertisingPackage.getAccuracy() < 5;                     //accept beacons from distance 5m at most
            }
        });
*/

        beaconManager.setBeaconActivityCheckConfiguration(BeaconActivityCheckConfiguration.DEFAULT);

        beaconManager.setForceScanConfiguration(ForceScanConfiguration.DEFAULT);

        beaconManager.registerRangingListener(new BeaconManager.RangingListener() {
            @Override
            public void onBeaconsDiscovered(final Region region, final List<BeaconDevice> beacons) {
                BeaconRangeActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        adapter.replaceWith(beacons);
                    }
                });
            }
        });

        deviceList.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if(! beaconManager.isBluetoothEnabled()){
            final Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, REQUEST_CODE_ENABLE_BLUETOOTH);
        } else if(beaconManager.isConnected()) {
            startRanging();
        } else {
            connect();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(beaconManager.isConnected()) {
            beaconManager.stopRanging();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        beaconManager.disconnect();
        beaconManager = null;
        ButterKnife.reset(this);
    }

    @OnItemClick(R.id.device_list)
    void onListItemClick(final int position) {
        final BeaconDevice beacon = (BeaconDevice) adapter.getItem(position);
        if(beacon != null) {
            PasswordDialogFragment.newInstance(getString(R.string.format_connect, beacon.getAddress()),
                    getString(R.string.password),
                    getString(R.string.connect),
                    new BiConsumer<DialogInterface, String>() {
                        @Override
                        public void accept(DialogInterface dialogInterface, String password) {

                            beacon.setPassword(password.getBytes());

                            final Intent intent = new Intent(BeaconRangeActivity.this, BeaconManagementActivity.class);
                            intent.putExtra(BeaconManagementActivity.EXTRA_BEACON_DEVICE, beacon);

                            startActivityForResult(intent, REQUEST_CODE_CONNECT_TO_DEVICE);
                        }
                    }).show(getFragmentManager(), "dialog");
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == REQUEST_CODE_ENABLE_BLUETOOTH) {
            if(resultCode != Activity.RESULT_OK) {
                final String bluetoothNotEnabledInfo = getString(R.string.bluetooth_not_enabled);
                Toast.makeText(BeaconRangeActivity.this, bluetoothNotEnabledInfo, Toast.LENGTH_LONG).show();
            }
            return;
        }  else if(requestCode == REQUEST_CODE_CONNECT_TO_DEVICE) {
            if(resultCode == Activity.RESULT_CANCELED) {
                Toast.makeText(this,
                        String.format("Beacon authentication failure: %s", data.getExtras().getString(BeaconManagementActivity.EXTRA_FAILURE_MESSAGE, "")),
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void startRanging() {
        try {
            beaconManager.startRanging();
        } catch (RemoteException e) {
            Utils.showToast(this, e.getMessage());
        }
    }

    private void connect() {
        try {
            beaconManager.connect(new OnServiceBoundListener() {
                @Override
                public void onServiceBound() throws RemoteException {
                    beaconManager.startRanging();
                }
            });
        } catch (RemoteException e) {
            Toast.makeText(BeaconRangeActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
