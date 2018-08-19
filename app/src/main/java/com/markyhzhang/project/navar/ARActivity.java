package com.markyhzhang.project.navar;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.ar.core.Frame;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableException;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.rendering.ViewRenderable;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import uk.co.appoly.arcorelocation.LocationMarker;
import uk.co.appoly.arcorelocation.LocationScene;
import uk.co.appoly.arcorelocation.utils.ARLocationPermissionHelper;


public class ARActivity extends AppCompatActivity implements View.OnClickListener{

    private boolean installRequested;
    private boolean hasFinishedLoading = false;

    private ArSceneView arSceneView;

    private Snackbar loadingMessageSnackbar = null;

    private LocationScene locationScene;

    private WaypointManager waypointManager;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ar);
        arSceneView = findViewById(R.id.ar_scene_view);

        waypointManager = (WaypointManager) getApplication();

        waypointManager.init(this, getSharedPreferences("waypoints", MODE_PRIVATE));

        //TODO add waypoint feature
//        waypointManager.preloadAdd("Home",R.layout.arobject_rectangular,-79.445093, 43.915302);

        Collection<Waypoint> waypoints = waypointManager.getWaypoints();

        CompletableFuture<ViewRenderable>[] layouts = waypointManager.getLayouts();

        CompletableFuture.allOf(layouts)
                .handle(
                        (notUsed, throwable) -> {
                            // When you build a Renderable, Sceneform loads its resources in the background while
                            // returning a CompletableFuture. Call handle(), thenAccept(), or check isDone()
                            // before calling get().

                            if (throwable != null) {
                                Utils.displayError(this, "Unable to load renderables", throwable);
                                return null;
                            }

                            try {
                                int i = 0;
                                for (Waypoint waypoint : waypoints)
                                    waypoint.setLayoutRenderable(layouts[i++].get());
                                hasFinishedLoading = true;

                            } catch (InterruptedException | ExecutionException ex) {
                                Utils.displayError(this, "Unable to load renderables", ex);
                            }

                            return null;
                        });

        arSceneView.getScene().setOnUpdateListener(frameTime -> {
            if (!hasFinishedLoading) return;
            if (locationScene == null) {
                locationScene = new LocationScene(this, this, arSceneView);

                for (Waypoint waypoint : waypoints) {
                    locationScene.mLocationMarkers.add(waypoint.getLocationMarker());
                }
            }


            //TODO make algorithm that remove/add waypoints based on their distance from user location with respect to the radius-limit
            while (!waypointManager.removalQueue().isEmpty()) {
                LocationMarker lm = waypointManager.removalQueue().poll();
                if (lm.anchorNode != null) {
                    lm.anchorNode.getAnchor().detach();
                    lm.anchorNode.setEnabled(false);
                    lm.anchorNode = null;
                }
                locationScene.mLocationMarkers.remove(lm);
                Log.d("FUCK", "removed from queue");
            }

            while (!waypointManager.additionQueue().isEmpty()){
                Waypoint waypoint = waypointManager.additionQueue().peek();
                if (waypoint.hasFinishedLoading())
                    locationScene.mLocationMarkers.add(waypointManager.additionQueue().poll().getLocationMarker());
            }




            Frame frame = arSceneView.getArFrame();
            if (frame == null || frame.getCamera().getTrackingState() != TrackingState.TRACKING)
                return;

            if (locationScene != null) locationScene.processFrame(frame);


            if (loadingMessageSnackbar != null) {
                for (Plane plane : frame.getUpdatedTrackables(Plane.class)) {
                    if (plane.getTrackingState() == TrackingState.TRACKING) {
                        hideLoadingMessage();
                    }
                }
            }

        });

        ARLocationPermissionHelper.requestPermission(this);

        findViewById(R.id.mapButton).setOnClickListener(this::onClick);

    }

    /**
     * Make sure we call locationScene.resume();
     */
    @Override
    protected void onResume() {
        super.onResume();

        if (locationScene != null) {
            locationScene.resume();
        }

        if (arSceneView.getSession() == null) {
            // If the session wasn't created yet, don't resume rendering.
            // This can happen if ARCore needs to be updated or permissions are not granted yet.
            try {
                Session session = Utils.createArSession(this, installRequested);
                if (session == null) {
                    installRequested = ARLocationPermissionHelper.hasPermission(this);
                    return;
                } else {
                    arSceneView.setupSession(session);
                }
            } catch (UnavailableException e) {
                Utils.handleSessionException(this, e);
            }
        }

        try {
            arSceneView.resume();
        } catch (CameraNotAvailableException ex) {
            Utils.displayError(this, "Unable to get camera", ex);
            finish();
            return;
        }

        if (arSceneView.getSession() != null) {
            showLoadingMessage();
        }
    }

    /**
     * Make sure we call locationScene.pause();
     */
    @Override
    public void onPause() {
        super.onPause();

        if (locationScene != null) {
            locationScene.pause();
        }

        arSceneView.pause();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        arSceneView.destroy();
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        if (!ARLocationPermissionHelper.hasPermission(this)) {
            if (!ARLocationPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                ARLocationPermissionHelper.launchPermissionSettings(this);
            } else {
                Toast.makeText(
                        this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                        .show();
            }
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // Standard Android full-screen functionality.
            getWindow()
                    .getDecorView()
                    .setSystemUiVisibility(
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void showLoadingMessage() {
        if (loadingMessageSnackbar != null && loadingMessageSnackbar.isShownOrQueued()) {
            return;
        }

        loadingMessageSnackbar =
                Snackbar.make(
                        ARActivity.this.findViewById(android.R.id.content),
                        "Detecting Plane/Surface",
                        Snackbar.LENGTH_INDEFINITE);
        loadingMessageSnackbar.getView().setBackgroundColor(0xbf323232);
        loadingMessageSnackbar.show();
    }

    private void hideLoadingMessage() {
        if (loadingMessageSnackbar == null) {
            return;
        }

        loadingMessageSnackbar.dismiss();
        loadingMessageSnackbar = null;
    }

    public WaypointManager getWaypointManager() {
        return waypointManager;
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.mapButton){
            Intent mapIntent = new Intent(this, MapActivity.class);
            mapIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            startActivity(mapIntent);
        }
    }
}
