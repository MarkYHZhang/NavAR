package com.markyhzhang.project.navar;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.ar.sceneform.rendering.ViewRenderable;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import uk.co.appoly.arcorelocation.LocationMarker;

public class WaypointManager extends Application{

//    private static ArrayList<Waypoint> waypoints = new ArrayList<>();

    private HashMap<String, Waypoint> waypointMap = new HashMap<>();

    private SharedPreferences storage;

    private Context context;

    private ArrayDeque<LocationMarker> removalQueue;
    private ArrayDeque<Waypoint> additionQueue;

    public WaypointManager(){

    }

    public void init(Context context, SharedPreferences storage){
        this.context = context;
        this.storage = storage;

        removalQueue = new ArrayDeque<>();
        additionQueue = new ArrayDeque<>();
//
//        SharedPreferences.Editor editor = storage.edit();
//        editor.clear();
//        editor.apply();

        HashMap<String, String> rawWaypoints = (HashMap<String, String>) storage.getAll();

        for (String id : rawWaypoints.keySet()) {
            String[] data = rawWaypoints.get(id).split("\\|");

            justAdd(id, data[0], Integer.parseInt( data[1]), Double.parseDouble(data[2]), Double.parseDouble(data[3]));

        }
    }

    private Waypoint justAdd(String id, String name, int layoutID, double longitude, double latitude){
        Waypoint waypoint = new Waypoint(name, ViewRenderable.builder()
                .setView(context, layoutID)
                .build(), longitude, latitude, id);
        waypointMap.put(waypoint.getId(), waypoint);
        return waypoint;
    }

    public void add(String id, String name, int layoutID, double longitude, double latitude){
        Waypoint waypoint = justAdd(id, name, layoutID, longitude, latitude);

        SharedPreferences.Editor editor = storage.edit();
        editor.putString(waypoint.getId(), name+"|"+layoutID+"|"+longitude+"|"+latitude);
        editor.apply();

        CompletableFuture.allOf(waypoint.getLayout())
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
                                waypoint.setLayoutRenderable(waypoint.getLayout().get());
                                waypoint.setFinishedLoading(true);

                            } catch (InterruptedException | ExecutionException ex) {
                                Utils.displayError(this, "Unable to load renderables", ex);
                            }

                            return null;
                        });

        additionQueue.add(waypointMap.get(waypoint.getId()));
    }

    public Waypoint getWaypoint(String id){
        return waypointMap.get(id);
    }

    public ArrayDeque<LocationMarker> removalQueue() {
        return removalQueue;
    }

    public ArrayDeque<Waypoint> additionQueue() {
        return additionQueue;
    }

    public void remove(String id){
        removalQueue.add(waypointMap.get(id).getLocationMarker());
        waypointMap.remove(id);

        SharedPreferences.Editor editor = storage.edit();
        editor.remove(id);
        editor.apply();
    }


    public Collection<Waypoint> getWaypoints() {
        return waypointMap.values();
    }

    public CompletableFuture<ViewRenderable>[] getLayouts(){
        CompletableFuture<ViewRenderable>[] layouts = new CompletableFuture[getWaypoints().size()];
        int i = 0;
        for (Waypoint waypoint : waypointMap.values()) {
            layouts[i++] = waypoint.getLayout();
        }
        return layouts;
    }
}
