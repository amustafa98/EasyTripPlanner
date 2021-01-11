package com.example.easytripplanner.Fragments;

import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.example.easytripplanner.R;
import com.example.easytripplanner.models.Trip;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;

import java.util.ArrayList;

/**
 * A simple {@link Fragment} subclass.
 */
public class TripsViewFragment extends Fragment {

    private static final String LIST_VIEW_TYPE = "LIST_TYPE";
    private int listType;
    private static final String TAG = "TripsViewFragment";
    public final static String LIST_STATE_KEY = "recycler_list_state";

    private TripRecyclerViewAdapter viewAdapter;
    private ArrayList<Trip> trips;
    private RecyclerView.LayoutManager mLayoutManager;
    private RecyclerView recyclerView;
    private ChildEventListener listener;
    private Query queryReference;

    Parcelable listState;

    public TripsViewFragment() {

    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        trips = new ArrayList<>();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Log.i(TAG, "onCreateView: Called");
        if (getArguments() != null) {
            listType = getArguments().getInt(LIST_VIEW_TYPE);
        }

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_list_trip, container, false);
        // Set the adapter
        if (view instanceof RecyclerView) {
            recyclerView = (RecyclerView) view;
            viewAdapter = new TripRecyclerViewAdapter(getContext(), trips);
            recyclerView.setAdapter(viewAdapter);
            //recyclerView.setAdapter(new TripRecyclerViewAdapter(games, item -> ((Communicator) getActivity()).openGame(item)));
        }
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mLayoutManager = recyclerView.getLayoutManager();
        initQueryAndListener();
    }

    private void initQueryAndListener() {
        String userId = FirebaseAuth.getInstance().getUid();

        FirebaseDatabase database = FirebaseDatabase.getInstance();

        DatabaseReference currentUserRef = null;
        if (userId != null) {
            currentUserRef = database.getReference("Users").child(userId);
            currentUserRef.keepSynced(true);
        }


        if (listType == 0) {
            //get upcoming trips
            queryReference = currentUserRef
                    .orderByChild("status")
                    .equalTo("UPCOMING");
        } else {
            queryReference = currentUserRef
                    .orderByChild("status")
                    .startAt("CANCELED")
                    .endAt("DONE");
        }

        listener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                trips.add(snapshot.getValue(Trip.class));
                viewAdapter.notifyDataSetChanged();
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {

            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        };
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        // Save list state
        listState = mLayoutManager.onSaveInstanceState();
        outState.putParcelable(LIST_STATE_KEY, listState);
        outState.putInt(LIST_VIEW_TYPE, listType);
        queryReference.removeEventListener(listener);
        trips.clear();
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        queryReference.addChildEventListener(listener);
        // Retrieve list state and list/item positions
        if (savedInstanceState != null) {
            listState = savedInstanceState.getParcelable(LIST_STATE_KEY);
            listType = savedInstanceState.getInt(LIST_VIEW_TYPE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listState != null) {
            mLayoutManager.onRestoreInstanceState(listState);
        }
    }


    public static TripsViewFragment newInstance(int type) {
        Bundle args = new Bundle();
        args.putInt(LIST_VIEW_TYPE, type);
        TripsViewFragment fragment = new TripsViewFragment();
        fragment.setArguments(args);
        return fragment;
    }
}