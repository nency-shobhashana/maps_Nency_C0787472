package com.nency.maps;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.maps.android.PolyUtil;
import com.google.maps.android.SphericalUtil;
import com.google.maps.android.ui.IconGenerator;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleMap.OnMapLongClickListener, GoogleMap.OnMarkerClickListener {

    private GoogleMap mMap;

    private static final String TAG = "MapsActivity";
    public static final int REQUEST_CODE = 1;

    private Marker homeMarker;
    private Location userLocation;

    Polygon shape;
    private List<Polyline> mPolylines = new ArrayList<>();
    private static final int POLYGON_SIDES = 4;
    List<Marker> markers = new ArrayList<>();
    Map<Marker, Circle> mcircles= new HashMap<>();

    private boolean isMarkerDrag = false;

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

    // on map ready set all listener
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
        mMap.setOnMapLongClickListener(this);

        // apply marker click listener
        mMap.setOnMarkerClickListener(this);

        mMap.setOnMarkerDragListener(new GoogleMap.OnMarkerDragListener() {
            @Override
            public void onMarkerDragStart(Marker marker) {
                isMarkerDrag = true;
                shape.remove();
                markers.remove(marker);
            }

            @Override
            public void onMarkerDrag(Marker marker) {

            }

            @Override
            public void onMarkerDragEnd(Marker marker) {
                isMarkerDrag = false;
                marker.remove();
                setMarker(marker.getPosition());
            }
        });

        // Total distance of quadrilateral(A-B-C-D)
        mMap.setOnPolygonClickListener(new GoogleMap.OnPolygonClickListener() {
            @Override
            public void onPolygonClick(Polygon polygon) {
                diplayTotalDistance();
            }
        });

        mMap.setOnPolylineClickListener(new GoogleMap.OnPolylineClickListener() {
            @Override
            public void onPolylineClick(Polyline polyline) {
                double distance = SphericalUtil.computeDistanceBetween(polyline.getPoints().get(0), polyline.getPoints().get(1));
                Toast.makeText(MapsActivity.this, String.format("Distance between to points : %.2f kms", distance / 1000) , Toast.LENGTH_LONG).show();
            }
        });
    }

    // on map long click
    @Override
    public void onMapLongClick(LatLng latLng) {
        // if marker is dragging then return
        if(isMarkerDrag) return;

        // if long press near city (invisible circle) then remove marker
        for (Marker marker : mcircles.keySet()) {
            Circle mcircle = mcircles.get(marker);
            if (SphericalUtil.computeDistanceBetween(mcircle.getCenter(), latLng) < mcircle.getRadius()){
                marker.remove();
                mcircle.remove();
                mcircles.remove(marker);
                markers.remove(marker);
                reloadShape();
                addTitle();
                return;
            }
        }

        // if add marker more then POLYGON_SIDES clear map
        if (markers.size() >= POLYGON_SIDES){
            clearMap();
        }
        // add marker
        if(latLng != null) {
            setMarker(latLng);
        }
    }

    // on marker click display distance on snipet and toast address
    @Override
    public boolean onMarkerClick(Marker marker) {
        // if marker is my location then do nothing
        if("myLocation".equals(marker.getTag())){
            return false;
        }
        // Show address in Toast on click of markers
        Geocoder geocoder;
        List<Address> addresses;
        LatLng latLng = marker.getPosition();
        geocoder = new Geocoder(MapsActivity.this, Locale.getDefault());
        try {
            addresses = geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
            Address address = addresses.get(0);
            Log.i(TAG, "address:" + address.toString());
            Toast.makeText(MapsActivity.this, address.getAddressLine(0),Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // add snippet with calculate latest distance with user location
        if(userLocation != null){
            Location location = new Location(LocationManager.GPS_PROVIDER);
            location.setLatitude( latLng.latitude);
            location.setLongitude(latLng.longitude);
            float distance = userLocation.distanceTo(location);
            marker.setSnippet(String.format("Distance from user :  %.2f kms", distance/1000 ));
        }
        return false;
    }

    // Calculate total distance and display in toast
    private void diplayTotalDistance(){
        float distance = 0;
        Location prvLocation = null;
        for (int i = 0; i < markers.size(); i++) {
            Location location = new Location(LocationManager.GPS_PROVIDER);
            location.setLongitude(markers.get(i).getPosition().longitude);
            location.setLatitude(markers.get(i).getPosition().latitude);
            if(prvLocation != null){
                distance += prvLocation.distanceTo(location);
            }
            prvLocation = location;
        }
        Toast.makeText(this, String.format("Total Distance : %.2f kms", distance / 1000), Toast.LENGTH_LONG).show();
    }

    // Set marker for polygon
    private void setMarker(LatLng latLng) {
        MarkerOptions options = new MarkerOptions()
                .position(latLng)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE));
        Marker marker = mMap.addMarker(options);
        marker.setDraggable(true);

        CircleOptions circleOption = new CircleOptions().center(latLng).radius(4000).strokeWidth(0);
        mcircles.put(marker, mMap.addCircle(circleOption));

        // for quadrilateral
        adjustPolygonWithRespectTo(marker);
        reloadShape();
        addTitle();
    }

    // reload shape based on markers list
    private void reloadShape(){
        // remove marker
        if (shape != null) {
            shape.remove();
            shape = null;
        }

        PolygonOptions polygonOptions = null;

        // adding points in polygon
        for (int i = 0; i < markers.size(); i++) {
            if (i == 0) {
                polygonOptions = new PolygonOptions().add(markers.get(0).getPosition());
            } else {
                polygonOptions.add(markers.get(i).getPosition());
            }

        }
        // set color, width of stroke
        if (polygonOptions != null) {
            polygonOptions.strokeColor(Color.RED);
            polygonOptions.strokeWidth(7f);
            polygonOptions.fillColor(0x5900FF00);
            shape = mMap.addPolygon(polygonOptions);
            shape.setClickable(true);
        }

        // added invisble polyline to get click events
        for (Polyline mPolyline : mPolylines) {
            mPolyline.remove();
        }
        mPolylines.clear();
        for (int i = 0; i < markers.size() - 1; i++) {
            PolylineOptions polylineOption = new PolylineOptions()
                    .color(Color.TRANSPARENT)
                    .width(10)
                    .zIndex(5)
                    .clickable(true)
                    .add(markers.get(i).getPosition(), markers.get(i + 1).getPosition());
            mPolylines.add(mMap.addPolyline(polylineOption));
        }
        if(markers.size() > 0) {
            PolylineOptions polylineOption = new PolylineOptions()
                    .color(Color.TRANSPARENT)
                    .width(10)
                    .zIndex(5)
                    .clickable(true)
                    .add(markers.get(0).getPosition(), markers.get(markers.size() - 1).getPosition());
            mPolylines.add(mMap.addPolyline(polylineOption));
        }
    }

    // Set marker title with custom marker
    private void addTitle(){
        IconGenerator iconGenerator = new IconGenerator(this);
        iconGenerator.setStyle(IconGenerator.STYLE_ORANGE);
        for (int i = 0; i < markers.size(); i++) {
            String title = i == 0 ? "A" : i == 1 ? "B" : i == 2 ? "C" : "D";
            Marker marker = markers.get(i);
            marker.setTitle(title);

           marker.setIcon(BitmapDescriptorFactory.fromBitmap(iconGenerator.makeIcon(title)));
        }

    }

    // quadrilateral - adjusting point of polygon
    private void adjustPolygonWithRespectTo(Marker marker) {
        double minDistance = 0;

        if (markers.size() > 2) {
            distancesFromMidPointsOfPolygonEdges.clear();
            //midPointsOfPolygonEdges?.removeAll()

            for (int i = 0; i < markers.size(); i++) {
                // 1. Find the mid points of the edges of polygon
                List<Marker> list = new ArrayList<>();

                if (i == (markers.size() - 1)) {
                    list.add(markers.get(markers.size() - 1));
                    list.add(markers.get(0));
                } else {
                    list.add((markers.get(i)));
                    list.add((markers.get(i + 1)));
                }

                LatLng midPoint = computeCentroid(list);

                // 2. Calculate the nearest coordinate by finding distance between mid point of each edge and the coordinate to be drawn
                Location startPoint = new Location("");
                startPoint.setLatitude(marker.getPosition().latitude);
                startPoint.setLongitude(marker.getPosition().longitude);
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
            int shiftByNumber = (markers.size() - position - 1);

            if (shiftByNumber != markers.size()) {
                markers = rotate(markers, shiftByNumber);
            }
        }

        // 5. Now add coordinated to be drawn
        markers.add(marker);
    }

    private List<Marker> rotate(List<Marker> markerList, int shiftByNumber) {
        if (markerList.size() == 0)
            return markerList;
        Marker element = null;
        for (int i = 0; i < shiftByNumber; i++) {
            // remove last element, add it to front of the ArrayList
            element = markerList.remove(markerList.size() - 1);
            markerList.add(0, element);
        }
        return markerList;
    }

    public static int minIndex(List<Double> list) {
        return list.indexOf(Collections.min(list));
    }

    private LatLng computeCentroid(List<Marker> points) {
        double latitude = 0;
        double longitude = 0;
        int n = points.size();

        for (Marker point : points) {
            latitude += point.getPosition().latitude;
            longitude += point.getPosition().longitude;
        }

        return new LatLng(latitude / n, longitude / n);
    }

    // To remove markers and polygon from map
    private void clearMap() {
        for (Marker marker : markers)
            marker.remove();

        markers.clear();
        for (Circle mcircle : mcircles.values()) {
            mcircle.remove();
        }
        mcircles.clear();
        if(shape != null) {
            shape.remove();
            shape = null;
        }
    }

    // add user location update listener
    private void startUpdateLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 50, locationListener);
    }


    // ask for location permission
    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE);
    }


    // check location permission is there
    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }


    // set custom home marker
    private void setHomeMarker(Location location) {
        userLocation = location;
        LatLng userLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        MarkerOptions options = new MarkerOptions().position(userLatLng)
                .title("You are here")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET))
                .snippet("User Location");
        homeMarker = mMap.addMarker(options);
        homeMarker.setTag("myLocation");
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 9));
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