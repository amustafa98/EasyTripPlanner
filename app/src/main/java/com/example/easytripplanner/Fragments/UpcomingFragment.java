package com.example.easytripplanner.Fragments;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.example.easytripplanner.R;
import com.example.easytripplanner.adapters.TripRecyclerViewAdapter;
import com.example.easytripplanner.databinding.FragmentUpcomingBinding;
import com.example.easytripplanner.models.Trip;
import com.example.easytripplanner.services.AlarmReceiver;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;

import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Iterator;

import timber.log.Timber;

import static android.content.Context.ALARM_SERVICE;
import static android.content.Context.MODE_PRIVATE;

/**
 * A simple {@link Fragment} subclass.
 */
public class UpcomingFragment extends Fragment {

    public static final String TRIP_NAME = "Name";
    public static final String TRIP_LOCATION_NAME = "LOCATION NAME";
    public static final String TRIP_LOC_LONGITUDE = "LOCATION LONGITUDE";
    public static final String TRIP_LOC_LATITUDE = "LOCATION LATITUDE";
    public static final String TRIP_ID = "ID";
    public static final String TRIP_HASH_CODE = "HASH CODE";
    @SuppressLint("SimpleDateFormat")
    public static final SimpleDateFormat formatter = new SimpleDateFormat("dd MMM yyyy hh:mm aa");

    private TripRecyclerViewAdapter mAdapter;
    private ArrayList<Trip> trips;
    private ChildEventListener listener;
    private Query queryReference;
    DatabaseReference currentUserRef;

    private FragmentUpcomingBinding binding;


    public UpcomingFragment() {

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        trips = new ArrayList<>();
        initQueryAndListener();
    }

    @Override
    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        binding = FragmentUpcomingBinding.inflate(inflater, container, false);
        mAdapter = new TripRecyclerViewAdapter(getContext(), trips, true, currentUserRef);
        binding.recyclerView.setAdapter(mAdapter);

        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Timber.i("onViewCreated: ");
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            Navigation.findNavController(view).popBackStack();
            Navigation.findNavController(view).navigate(R.id.loginFragment);
        }
    }

    public void initQueryAndListener() {
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId == null)
            return;


        FirebaseDatabase database = FirebaseDatabase.getInstance();

        currentUserRef = null;
        currentUserRef = database.getReference("Users").child(userId);
        currentUserRef.keepSynced(true);

        //get upcoming trips
        queryReference = currentUserRef
                .orderByChild("status")
                .startAt(TRIP_STATUS.FORGOTTEN.name())
                .endAt(TRIP_STATUS.UPCOMING.name());

        DatabaseReference finalCurrentUserRef = currentUserRef;

        Calendar calendar = Calendar.getInstance();
        listener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                Trip trip = snapshot.getValue(Trip.class);
                if (trip != null && trip.timeInMilliSeconds != null) {
                    calendar.setTimeInMillis(trip.timeInMilliSeconds);
                    trip.setDate(formatter.format(calendar.getTime()));

                    if (trip.timeInMilliSeconds < System.currentTimeMillis()) {
                        finalCurrentUserRef.child(trip.pushId).child("status").setValue("FORGOTTEN");
                    }
                    trips.add(trip);
                    Collections.sort(trips);
                    mAdapter.notifyDataSetChanged();
                    checkAlarm(trip);
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                String id = snapshot.child("pushId").getValue(String.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    trips.removeIf(trip -> trip.pushId.equals(id));
                } else {
                    for (Iterator<Trip> iterator = trips.iterator(); iterator.hasNext(); ) {
                        if (iterator.next().pushId.equals(id)) {
                            iterator.remove();
                            break;
                        }
                    }
                }
                binding.recyclerView.getAdapter().notifyDataSetChanged();
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        };
    }

    private void checkAlarm(Trip t) {
        //save Shared Preferences
        SharedPreferences sharedPref = requireContext().getSharedPreferences("Save", MODE_PRIVATE);

        //set Alarm
        AlarmManager alarmMgr = (AlarmManager) requireActivity().getSystemService(ALARM_SERVICE);

        //first scenario if trip is forgotten then do not fire and if found in sharedPreferences delete it
        if (t.status.equalsIgnoreCase(TRIP_STATUS.FORGOTTEN.name())) {
            if (sharedPref.contains(t.pushId)) {
                //delete it from sharedPreference
                sharedPref.edit().remove(t.pushId).apply();
            }
        } else if (!sharedPref.contains(t.pushId)) {

            //save trips id and trigger time in sharedPreference
            Timber.i("checkAlarm: trip name: " + t.name + ", fire alarm");
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putLong(t.pushId, t.timeInMilliSeconds);
            editor.apply();

            final Intent intent = new Intent(getContext(), AlarmReceiver.class);

            intent.putExtra(TRIP_NAME, t.name);
            intent.putExtra(TRIP_ID, t.pushId);
            intent.putExtra(TRIP_HASH_CODE, t.pushId.hashCode());
            intent.putExtra(TRIP_LOCATION_NAME, t.locationTo.Address);
            intent.putExtra(TRIP_LOC_LONGITUDE, t.locationTo.longitude);
            intent.putExtra(TRIP_LOC_LATITUDE, t.locationTo.latitude);


            PendingIntent notifyPendingIntent = PendingIntent.getBroadcast(getContext(), t.pushId.hashCode(),
                    intent, PendingIntent.FLAG_NO_CREATE);
            if (notifyPendingIntent == null)
                notifyPendingIntent = PendingIntent.getBroadcast
                        (getContext(), t.pushId.hashCode(), intent,
                                PendingIntent.FLAG_UPDATE_CURRENT);
            if (t.repeating.equals("No Repeated")) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmMgr.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t.timeInMilliSeconds, notifyPendingIntent);
                } else {
                    alarmMgr.setExact(AlarmManager.RTC_WAKEUP, t.timeInMilliSeconds, notifyPendingIntent);
                }
            } else {
                long repeatInterval;
                long ONE_DAY = 86400000;
                switch (t.repeating) {
                    case "Repeated Daily":
                        repeatInterval = ONE_DAY;
                        break;
                    case "Repeated weekly":
                        repeatInterval = ONE_DAY * 7;
                        break;
                    case "Repeated Monthly":
                        repeatInterval = ONE_DAY * 7 * 4;
                        break;
                    default:
                        repeatInterval = 0;
                }

                alarmMgr.setRepeating(
                        AlarmManager.RTC_WAKEUP,
                        t.timeInMilliSeconds,
                        repeatInterval,
                        notifyPendingIntent);
            }
        }


    }

    public enum TRIP_STATUS {
        DONE,
        FORGOTTEN,
        CANCELED,
        UPCOMING
    }

    @Override
    public void onStart() {
        super.onStart();
        queryReference.addChildEventListener(listener);
    }

    @Override
    public void onStop() {
        super.onStop();
        queryReference.removeEventListener(listener);
        trips.clear();
        binding.recyclerView.getAdapter().notifyDataSetChanged();
    }
}