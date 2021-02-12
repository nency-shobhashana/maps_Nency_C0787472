package com.nency.maps;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;

    public static final int REQUEST_CODE = 1;

    private Marker homeMarker;

    Polygon shape;
    private static final int POLYGON_SIDES = 4;
    List<LatLng> latLngList = new ArrayList<>();
    List<Double> distancesFromMidPointsOfPolygonEdges = new ArrayList<>();



    // location manager and location listener
    LocationManager locationManager;
    LocationListener locationListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                setHomeMarker(location);
            }
        };

        if (!hasLocationPermission())
            requestLocationPermission();
        else
            startUpdateLocation();

        // apply long press gesture
        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {

            @Override
            public void onMapLongClick(LatLng latLng) {
                if (latLngList.size() == POLYGON_SIDES){
                    clearMap();
                }
                if(latLng != null) {
                    setMarker(latLng);
                }
            }
        });
    }

    private void setMarker(LatLng latLng) {

        mMap.addMarker(new MarkerOptions().position(latLng).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)));
        if (shape != null) {
            shape.remove();
            shape = null;
        }
        adjustPolygonWithRespectTo(latLng);
        PolygonOptions polygonOptions = null;
        for (int i = 0; i < latLngList.size(); i++) {
            if (i == 0)
                polygonOptions = new PolygonOptions().add(latLngList.get(0));
            else
                polygonOptions.add(latLngList.get(i));
        }
        polygonOptions.strokeColor(Color.RED);
        polygonOptions.strokeWidth(5f);
        polygonOptions.fillColor(0x3300FF00);
        shape = mMap.addPolygon(polygonOptions);

    }

    private void adjustPolygonWithRespectTo(LatLng point) {
        double minDistance = 0;

        if (latLngList.size() > 2) {
            distancesFromMidPointsOfPolygonEdges.clear();
            //midPointsOfPolygonEdges?.removeAll()

            for (int i = 0; i < latLngList.size(); i++) {
                // 1. Find the mid points of the edges of polygon
                List<LatLng> list = new ArrayList<>();

                if (i == (latLngList.size() - 1)) {
                    list.add(latLngList.get(latLngList.size() - 1));
                    list.add(latLngList.get(0));
                } else {
                    list.add((latLngList.get(i)));
                    list.add((latLngList.get(i + 1)));
                }

                LatLng midPoint = computeCentroid(list);

                // 2. Calculate the nearest coordinate by finding distance between mid point of each edge and the coordinate to be drawn
                Location startPoint = new Location("");
                startPoint.setLatitude(point.latitude);
                startPoint.setLongitude(point.longitude);
                Location endPoint = new Location("");
                endPoint.setLatitude(midPoint.latitude);
                endPoint.setLongitude(midPoint.longitude);
                double distance = startPoint.distanceTo(endPoint);

                distancesFromMidPointsOfPolygonEdges.add(distance);
                if (i == 0) {
                    minDistance = distance;
                } else {

                    if (distance < minDistance) {
                        minDistance = distance;
                    }
                }
                //midPointsOfPolygonEdges?.append(midPoint)
            }

            // 3. The nearest coordinate = the edge with minimum distance from mid point to the coordinate to be drawn
            int position = minIndex(distancesFromMidPointsOfPolygonEdges);

            // 4. move the nearest coordinate at the end by shifting array right
            int shiftByNumber = (latLngList.size() - position - 1);

            if (shiftByNumber != latLngList.size()) {
                latLngList = rotate(latLngList, shiftByNumber);
            }
        }

        // 5. Now add coordinated to be drawn
        latLngList.add(point);

    }

    private List<LatLng> rotate(List<LatLng> latLngList, int shiftByNumber) {
        if (latLngList.size() == 0)
            return latLngList;
        LatLng element = null;
        for (int i = 0; i < shiftByNumber; i++) {
            // remove last element, add it to front of the ArrayList
            element = latLngList.remove(latLngList.size() - 1);
            latLngList.add(0, element);
        }
        return latLngList;
    }

    public static int minIndex(List<Double> list) {
        return list.indexOf(Collections.min(list));
    }

    private LatLng computeCentroid(List<LatLng> points) {
        double latitude = 0;
        double longitude = 0;
        int n = points.size();

        for (LatLng point : points) {
            latitude += point.latitude;
            longitude += point.longitude;
        }

        return new LatLng(latitude / n, longitude / n);
    }

    // To remove markers from map
    private void clearMap() {
//        for (Marker marker : markers)
//            marker.remove();

        latLngList.clear();
        shape.remove();
        shape = null;
    }

    private void startUpdateLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 0, locationListener);
    }


    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE);
    }


    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }


    // set custom home marker
    private void setHomeMarker(Location location) {
        LatLng userLocation = new LatLng(location.getLatitude(), location.getLongitude());
        MarkerOptions options = new MarkerOptions().position(userLocation)
                .title("You are here")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET))
                .snippet("Your Location");
        homeMarker = mMap.addMarker(options);
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 15));
    }


    // After granting a permission this method call
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        if (REQUEST_CODE == requestCode) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 0, locationListener);
            }
        }
    }
}