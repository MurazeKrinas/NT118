package com.example.finalproject;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.OnBackPressedDispatcher;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.example.finalproject.Model.Point;
import com.example.finalproject.Utils.ApiService;
import com.example.finalproject.Utils.CustomMarkerView;
import com.example.finalproject.Utils.DateTimePickerFragment;
import com.example.finalproject.Utils.TimestampAxisValueFormatter;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.google.android.material.textfield.TextInputLayout;
import com.tencent.mmkv.MMKV;

import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Response;

public class frag_chart extends Fragment implements DateTimePickerFragment.OnDateTimeSetListener {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private ApiService apiService = null;
    private MMKV kv = null;
    private TextView startTimeTextView = null;
    private LineChart lineChart = null;
    private TextView endTimeTextView = null;
    private Button generateChartButton = null;
    private Calendar startDateTime = null;
    private Calendar endDateTime = null;
    private TextInputLayout textInputLayout = null;
    private String selectedMetrics = null;
    private String accessToken = null;

    public frag_chart() {}

    public static frag_chart newInstance() {
        frag_chart fragment = new frag_chart();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @SuppressLint("MissingInflatedId")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.frag_chart, container, false);

        kv = MMKV.defaultMMKV();
        accessToken = kv.decodeString("AccessToken");

        AutoCompleteTextView autoCompleteTextView = view.findViewById(R.id.autoComplete);

        HomeActivity homeActivity = (HomeActivity) getActivity();
        apiService = homeActivity.getAPIService();

        startTimeTextView = view.findViewById(R.id.startTimeTextView);
        endTimeTextView = view.findViewById(R.id.endTimeTextView);
        generateChartButton = view.findViewById(R.id.generateChartButton);
        lineChart = view.findViewById(R.id.lineChart);
        textInputLayout = view.findViewById(R.id.testss);
        startTimeTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDateTimePickerDialog(true);
            }
        });

        endTimeTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showDateTimePickerDialog(false);
            }
        });


        generateChartButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setupLineChart();
                generateChart();
            }
        });
        autoCompleteTextView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                selectedMetrics = parent.getItemAtPosition(position).toString();
            }
        });

        //backPressed
        OnBackPressedDispatcher onBackPressedDispatcher = requireActivity().getOnBackPressedDispatcher();
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                FragmentTransaction transaction = requireActivity().getSupportFragmentManager().beginTransaction();
                transaction.replace(R.id.frame_layout, new frag_home());
                transaction.commit();
            }
        };
        onBackPressedDispatcher.addCallback(getViewLifecycleOwner(), callback);
        return view;
    }

    private void setupLineChart() {
        lineChart.getDescription().setEnabled(false);
        lineChart.setDragEnabled(true);
        lineChart.setScaleEnabled(true);
        lineChart.setTouchEnabled(true);
        lineChart.setBackgroundColor(Color.TRANSPARENT);
        lineChart.getDescription().setTextColor(Color.WHITE);
        lineChart.getAxisRight().setEnabled(false);
        lineChart.getLegend().setEnabled(false);
        lineChart.setDoubleTapToZoomEnabled(false);

        CustomMarkerView markerView = new CustomMarkerView(requireContext(), R.layout.custom_marker_view);
        lineChart.setMarker(markerView);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setValueFormatter(new TimestampAxisValueFormatter());
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setTextColor(Color.WHITE);
        xAxis.setLabelRotationAngle(45f);

        YAxis leftAxis = lineChart.getAxisLeft();
        leftAxis.setTextColor(Color.WHITE);
    }

    private void populateChart(List<Point> points, String Label) {
        ArrayList<Entry> entries = new ArrayList<>();
        for (Point point : points) {
            entries.add(new Entry(point.getX(), point.getY()));
        }
        LineDataSet dataSet = new LineDataSet(entries, Label);

        dataSet.setDrawValues(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setLineWidth(2.0f);
        dataSet.setColor(Color.argb(255,14,226,255));
        dataSet.setCircleColor(Color.argb(255,14,226,255));
        dataSet.setCircleRadius(5f);

        LineData lineData = new LineData(dataSet);
        lineChart.setData(lineData);
        lineChart.notifyDataSetChanged();
        lineChart.invalidate();
    }

    private void showDateTimePickerDialog(boolean isStartTime) {
        DateTimePickerFragment dateTimePickerFragment = new DateTimePickerFragment(this, isStartTime);
        dateTimePickerFragment.show(requireActivity().getSupportFragmentManager(), null);
    }
    private void generateChart() {
        if (textInputLayout != null && startDateTime != null && endDateTime != null) {
            ExecutorService networkExecutor = Executors.newSingleThreadExecutor();
            networkExecutor.execute(() -> {
                try {
                    selectedMetrics = selectedMetrics.toLowerCase().split(" ")[0];

                    long startTimestamp = startDateTime.getTime().getTime();
                    long endTimestamp = endDateTime.getTime().getTime();

                    JSONObject Mess = new JSONObject();
                    Mess.put("type","lttb");
                    Mess.put("fromTimestamp", startTimestamp);
                    Mess.put("toTimestamp", endTimestamp);
                    Mess.put("amountOfPoints", 100);

                    RequestBody requestBody = RequestBody.create(JSON, Mess.toString());

                    Call<List<Point>> GetChart = apiService.GetChart("Bearer " + accessToken,
                                                                     selectedMetrics,
                                                                     requestBody);
                    Response<List<Point>> ChartResponse = GetChart.execute();
                    List<Point> listPoint = ChartResponse.body();
                    if (!listPoint.isEmpty())
                        populateChart(listPoint, selectedMetrics);

                } catch (Exception e){
                    Log.d("Chart", e.toString());
                }
            });
        }
    }

    @Override
    public void onDateTimeSet(Calendar dateTime, boolean isStartTime) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm dd-MM-yyyy", Locale.getDefault());
        String formattedDateTime = dateFormat.format(dateTime.getTime());
        if (isStartTime){
            startDateTime = dateTime;
            startTimeTextView.setText(formattedDateTime);
        }
        else {
            endDateTime = dateTime;
            endTimeTextView.setText(formattedDateTime);
        }
    }
}

