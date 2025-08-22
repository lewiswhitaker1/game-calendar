package me.lewis.gcalendar// Or your package name

import android.app.AlertDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageButton // New import
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import com.kizitonwose.calendar.view.CalendarView
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Calendar as JavaCalendar

class MainActivity : AppCompatActivity() {

    // --- NEW: UI variables for the header ---
    private lateinit var monthYearText: TextView
    private lateinit var previousMonthButton: ImageButton
    private lateinit var nextMonthButton: ImageButton

    private lateinit var calendarView: CalendarView
    private var selectedDate: LocalDate? = LocalDate.now()
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    private val userColors = mapOf(
        "Lewis" to Color.BLUE,
        "Joe" to Color.GREEN,
        "Polly" to Color.RED
    )

    private val REFRESH_INTERVAL_MS: Long = 30000
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var refreshRunnable: Runnable

    private lateinit var listViewEvents: ListView
    private lateinit var textViewSelectedDate: TextView
    private lateinit var fabAddEvent: FloatingActionButton
    private var allEvents = listOf<Event>()
    private var displayedEvents = listOf<Event>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI components
        calendarView = findViewById(R.id.calendarView)
        listViewEvents = findViewById(R.id.listViewEvents)
        textViewSelectedDate = findViewById(R.id.textViewSelectedDate)
        fabAddEvent = findViewById(R.id.fabAddEvent)
        // --- NEW: Initialize header components ---
        monthYearText = findViewById(R.id.monthYearText)
        previousMonthButton = findViewById(R.id.previousMonthButton)
        nextMonthButton = findViewById(R.id.nextMonthButton)

        updateDateText()
        setupAutoRefresh()
        setupCalendar()

        fabAddEvent.setOnClickListener { showAddTimeDialog() }
        listViewEvents.setOnItemLongClickListener { _, _, position, _ ->
            val selectedEvent = displayedEvents[position]
            val prefs = PreferenceManager.getDefaultSharedPreferences(this)
            val currentUser = prefs.getString("LOGGED_IN_USER", null)
            if (currentUser == selectedEvent.user) {
                showDeleteConfirmationDialog(selectedEvent)
            } else {
                Toast.makeText(this, "You can only delete your own events.", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    private fun setupCalendar() {
        class DayViewContainer(view: View) : ViewContainer(view) {
            val textView: TextView = view.findViewById(R.id.calendarDayText)
            val dotsContainer: LinearLayout = view.findViewById(R.id.dotsContainer)
            lateinit var day: CalendarDay

            init {
                view.setOnClickListener {
                    if (selectedDate != day.date) {
                        val oldDate = selectedDate
                        selectedDate = day.date
                        calendarView.notifyDateChanged(day.date)
                        oldDate?.let { calendarView.notifyDateChanged(it) }
                        updateDateText()
                        filterEventsForDate()
                    }
                }
            }
        }

        calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)
            override fun bind(container: DayViewContainer, data: CalendarDay) {
                container.day = data
                val textView = container.textView
                val dotsContainer = container.dotsContainer

                textView.text = data.date.dayOfMonth.toString()
                dotsContainer.removeAllViews()

                if (data.date == selectedDate) {
                    textView.setBackgroundColor(Color.LTGRAY)
                } else {
                    textView.background = null
                }

                val usersForDay = allEvents.filter { it.date == data.date.format(dateFormatter) }
                    .map { it.user }.distinct()

                usersForDay.forEach { user ->
                    val dot = View(this@MainActivity).apply {
                        layoutParams = LinearLayout.LayoutParams(15, 15).also {
                            it.setMargins(4, 0, 4, 0)
                        }
                        setBackgroundColor(userColors[user] ?: Color.GRAY)
                    }
                    dotsContainer.addView(dot)
                }
            }
        }

        val currentMonth = YearMonth.now()
        val startMonth = currentMonth.minusMonths(100)
        val endMonth = currentMonth.plusMonths(100)
        val firstDayOfWeek = firstDayOfWeekFromLocale()
        calendarView.setup(startMonth, endMonth, firstDayOfWeek)
        calendarView.scrollToMonth(currentMonth)

        // --- NEW: Listen for month scrolls to update the title ---
        calendarView.monthScrollListener = { month ->
            val title = "${month.yearMonth.month.name.lowercase().replaceFirstChar { it.titlecase() }} ${month.yearMonth.year}"
            monthYearText.text = title
        }

        // --- NEW: Add click listeners for the navigation arrows ---
        nextMonthButton.setOnClickListener {
            calendarView.findFirstVisibleMonth()?.let {
                calendarView.smoothScrollToMonth(it.yearMonth.plusMonths(1))
            }
        }

        previousMonthButton.setOnClickListener {
            calendarView.findFirstVisibleMonth()?.let {
                calendarView.smoothScrollToMonth(it.yearMonth.minusMonths(1))
            }
        }
    }

    // All other functions remain the same...
    private fun fetchEvents() {
        RetrofitClient.instance.getEvents().enqueue(object : Callback<List<Event>> {
            override fun onResponse(call: Call<List<Event>>, response: Response<List<Event>>) {
                if (response.isSuccessful) {
                    val newEvents = response.body() ?: emptyList()
                    if (newEvents != allEvents) {
                        allEvents = newEvents
                        calendarView.notifyCalendarChanged()
                        filterEventsForDate()
                        Toast.makeText(this@MainActivity, "Calendar Updated!", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            override fun onFailure(call: Call<List<Event>>, t: Throwable) {
                println("Error fetching events: ${t.message}")
            }
        })
    }

    private fun filterEventsForDate() {
        val selectedDateStr = selectedDate?.format(dateFormatter) ?: ""
        displayedEvents = allEvents.filter { it.date == selectedDateStr }
        val eventStrings = displayedEvents.map { "${it.user} is free at ${it.time}" }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, eventStrings)
        listViewEvents.adapter = adapter
    }

    private fun updateDateText() {
        val dateStr = selectedDate?.format(DateTimeFormatter.ofPattern("d MMMM yyyy")) ?: "No date selected"
        textViewSelectedDate.text = "Events for $dateStr"
    }

    private fun showAddTimeDialog() {
        val cal = JavaCalendar.getInstance()
        val timeSetListener = TimePickerDialog.OnTimeSetListener { _, hour, minute ->
            val time = String.format("%02d:%02d", hour, minute)
            addNewEvent(time)
        }
        TimePickerDialog(this, timeSetListener, cal.get(JavaCalendar.HOUR_OF_DAY), cal.get(JavaCalendar.MINUTE), true).show()
    }

    private fun addNewEvent(time: String) {
        if (selectedDate == null) {
            Toast.makeText(this, "Please select a date first", Toast.LENGTH_SHORT).show()
            return
        }
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val userName = prefs.getString("LOGGED_IN_USER", "Unknown") ?: "Unknown"
        val request = AddEventRequest(user = userName, date = selectedDate!!.format(dateFormatter), time = time)

        RetrofitClient.instance.addEvent(request).enqueue(object : Callback<Event> {
            override fun onResponse(call: Call<Event>, response: Response<Event>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@MainActivity, "Event added!", Toast.LENGTH_SHORT).show()
                    fetchEvents()
                } else {
                    Toast.makeText(this@MainActivity, "Failed to add event", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<Event>, t: Throwable) {
                Toast.makeText(this@MainActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        handler.post(refreshRunnable)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(refreshRunnable)
    }

    private fun setupAutoRefresh() {
        refreshRunnable = Runnable {
            fetchEvents()
            handler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS)
        }
    }

    private fun showDeleteConfirmationDialog(event: Event) {
        AlertDialog.Builder(this)
            .setTitle("Delete Event")
            .setMessage("Are you sure you want to delete this event scheduled for ${event.time}?")
            .setPositiveButton("Delete") { _, _ -> deleteEvent(event.id) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteEvent(eventId: Long) {
        RetrofitClient.instance.deleteEvent(eventId).enqueue(object : Callback<Unit> {
            override fun onResponse(call: Call<Unit>, response: Response<Unit>) {
                if (response.isSuccessful) {
                    Toast.makeText(this@MainActivity, "Event deleted", Toast.LENGTH_SHORT).show()
                    fetchEvents()
                } else {
                    Toast.makeText(this@MainActivity, "Failed to delete event", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onFailure(call: Call<Unit>, t: Throwable) {
                Toast.makeText(this@MainActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }
}