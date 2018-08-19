package com.markyhzhang.project.navar;

import android.Manifest;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.Objects;
import java.util.UUID;

/**
 * An activity that displays a Google map with a marker (pin) to indicate a particular location.
 */
public class MapActivity extends AppCompatActivity
        implements OnMapReadyCallback, LocationListener {

    private GoogleMap googleMap;

    private LocationManager locationManager;

    public WaypointManager waypointManagerInstance;

    private Snackbar markerInfo;

    private Marker currentMarker;

    private SharedPreferences preLocationStorage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        waypointManagerInstance = (WaypointManager) getApplication();

        preLocationStorage = getSharedPreferences("location", MODE_PRIVATE);

//        requestWindowFeature(Window.FEATURE_NO_TITLE);
        Objects.requireNonNull(getSupportActionBar()).hide();
        // Retrieve the content view that renders the map.
        setContentView(R.layout.activity_map);

        markerInfo = Snackbar.make(findViewById(R.id.snackbar_markerinfolayout), "", Snackbar.LENGTH_LONG);
        markerInfo.setAction(new StringBuffer("Delete"), v -> {
            if (currentMarker != null) {
                Log.d("FUCK", "onCreated");
                waypointManagerInstance.remove((String) currentMarker.getTag());
                currentMarker.remove();
            }
        });


        // Get the SupportMapFragment and request notification
        // when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
//
//        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//            return;
//        }
//        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10, this); //You can also use LocationManager.GPS_PROVIDER and LocationManager.PASSIVE_PROVIDER

    }

    /**
     * Manipulates the map when it's available.
     * The API invokes this callback when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user receives a prompt to install
     * Play services inside the SupportMapFragment. The API invokes this method after the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {

        this.googleMap = googleMap;

        googleMap.getUiSettings().setMapToolbarEnabled(false);
        googleMap.getUiSettings().setMyLocationButtonEnabled(true);
        googleMap.getUiSettings().setCompassEnabled(true);
        googleMap.getUiSettings().setIndoorLevelPickerEnabled(true);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        googleMap.setMyLocationEnabled(true);

        LatLng preCamLoc = new LatLng(Double.parseDouble(preLocationStorage.getString("latitude","43.470877")), Double.parseDouble(preLocationStorage.getString("longitude","-80.541899")));
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(preCamLoc, preLocationStorage.getFloat("zoom",7));
        googleMap.animateCamera(cameraUpdate);

        for (Waypoint waypoint : waypointManagerInstance.getWaypoints()) {
            googleMap.addMarker(new MarkerOptions().position(new LatLng(waypoint.getLatitude(),waypoint.getLongitude())).title(waypoint.getName())).setTag(waypoint.getId());
        }

        googleMap.setOnMapLongClickListener(latLng -> {

            AlertDialog.Builder waypointSettingBuilder = new AlertDialog.Builder(this);
            View waypointSettingView = getLayoutInflater().inflate(R.layout.dialog_waypointsetting, null);

            EditText waypointName = waypointSettingView.findViewById(R.id.waypointName);
            Button saveButton = waypointSettingView.findViewById(R.id.saveButton);

            waypointSettingBuilder.setView(waypointSettingView);
            AlertDialog dialog = waypointSettingBuilder.create();

            saveButton.setOnClickListener(view -> {
                if (!waypointName.getText().toString().isEmpty()){

                    String id = UUID.randomUUID().toString();

                    googleMap.addMarker(new MarkerOptions().position(latLng).title(waypointName.getText().toString())).setTag(id);

                    waypointManagerInstance.add(id, waypointName.getText().toString(), R.layout.arobject_rectangular, Math.round(latLng.longitude*1000000.0)/1000000.0, Math.round(latLng.latitude*1000000.0)/1000000.0);

                    dialog.hide();
                }
            });


            dialog.show();

        });

        googleMap.setOnMarkerClickListener(marker -> {

            if (marker==null||marker.getTag()==null) return false;
            currentMarker = marker;
            markerInfo.setText(new StringBuffer(marker.getTitle())).show();

            return false;
        });

//        Utils.displayMsg(this, "Locating your position...");

        // Add a marker in Sydney, Australia,
        // and move the map's camera to the same location.
//        LatLng sydney = new LatLng(-33.852, 151.211);
//        googleMap.addMarker(new MarkerOptions().position(sydney)
//                .title("Marker in Sydney"));
//        googleMap.moveCamera();
    }

    @Override
    protected void onPause() {
        SharedPreferences.Editor editor = preLocationStorage.edit();
        CameraPosition pos = googleMap.getCameraPosition();
        editor.putString("longitude", pos.target.longitude+"");
        editor.putString("latitude", pos.target.latitude+"");
        editor.putFloat("zoom", pos.zoom);
        editor.apply();
        super.onPause();

    }

    @Override
    public void onLocationChanged(Location location) {
//        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
//        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 15);
//        googleMap.animateCamera(cameraUpdate);
//        googleMap.addMarker(new MarkerOptions().position(new LatLng(location.getLatitude(), location.getLongitude())));


        locationManager.removeUpdates(this);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    public void onBackPressed() {
        Intent backIntent = new Intent(this, ARActivity.class);
        backIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(backIntent);
    }
}