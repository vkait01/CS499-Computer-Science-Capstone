package com.zybooks.cs360project2updated

import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.zybooks.cs360project2updated.navigation.NavigationState
import com.zybooks.cs360project2updated.ui.theme.CS360Project2UpdatedTheme
import java.util.Calendar
import android.Manifest
import java.security.MessageDigest
import androidx.core.app.ActivityCompat


// UPDATED!!
// Include "id" so each entry uses its unique database ID
data class WeightEntry(val id: Int, val date: String, val weight: Float)

class MainActivity : ComponentActivity() {

    // These helper classes are assumed to be implemented in your project.
    private lateinit var smsHandler: SmsHandler
    private lateinit var dbHelper: DatabaseHelper

    // UPDATED!!
    // Holds the ID of the user who logs in.
    // Replaces the old hard-coded "userId = 1" so entries link to the right account
    private var currentUserId: Int? = null

    // UPDATED!!
    // Goal weight is now tied to the logged-in user instead of being hard-coded.
    // Default is 154.3 until replaced at login.
    private var goalWeight: Double = 154.3

    // Hashes password using SHA-256 for secure storage and comparison
    fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize helper objects.
        smsHandler = SmsHandler(this)
        dbHelper = DatabaseHelper(this)

        setContent {
            CS360Project2UpdatedTheme {

                // Added: State for navigation
                // Start at login screen instead of grid
                var navigationState: NavigationState by remember { mutableStateOf(NavigationState.Login) }
                // Added: State for weight list
                var weightList by remember { mutableStateOf(loadWeightEntriesFromDB()) }
                // Added: State for edit dialog
                var showEditDialog by remember { mutableStateOf(false) }
                var selectedEntry by remember { mutableStateOf<WeightEntry?>(null) }

                when (navigationState) {
                    NavigationState.Grid -> {
                        GridScreen(
                            goalWeight = goalWeight,
                            weightList = weightList,
                            onAddDataClick = { navigationState = NavigationState.AddWeight },

                            onDeleteClick = { weightEntry ->
                                // UPDATED!!
                                // Delete from database by ID
                                dbHelper.deleteWeightEntry(weightEntry.id)

                                // UPDATED!!
                                // Refresh grid from database so UI stays in sync
                                refreshGrid { updatedList ->
                                    weightList = updatedList
                                }
                            },

                            onEditClick = { updatedEntry ->
                                // Store selected entry and trigger Compose-safe dialog
                                selectedEntry = updatedEntry
                                showEditDialog = true
                            },

                            onSmsClick = { /* SMS handling here if needed */ }
                        )

                        // Show Compose-native Edit dialog when triggered
                        if (showEditDialog && selectedEntry != null) {
                            EditWeightDialog(
                                weightEntry = selectedEntry!!,
                                onDismiss = { showEditDialog = false },
                                onSave = { updatedEntry ->
                                    // UPDATED!! Save to DB by ID
                                    val rowsUpdated = dbHelper.updateWeightEntry(
                                        updatedEntry.id,
                                        updatedEntry.date,
                                        updatedEntry.weight
                                    )
                                    if (rowsUpdated > 0) {
                                        // UPDATED!! Refresh grid
                                        val refreshedList = loadWeightEntriesFromDB()
                                        weightList = refreshedList
                                        Toast.makeText(
                                            this,
                                            "Entry updated",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            this,
                                            "Update failed",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                    showEditDialog = false
                                }
                            )
                        }
                    }

                    NavigationState.AddWeight -> {
                        AddWeightScreen(
                            goalWeight = goalWeight,
                            weightList = weightList,
                            onAddComplete = { newList ->
                                weightList = newList
                                navigationState = NavigationState.Grid
                            },
                            smsHandler = smsHandler
                        )
                    }

                    NavigationState.SMS -> TODO()
                    NavigationState.Login -> {
                        LoginScreen(
                            onSignUpClick = { navigationState = NavigationState.SignUp },
                            onSignInClick = {
                                // After login succeeds, go to the grid
                                navigationState = NavigationState.Grid
                            },
                            // Hash password, set user, then refresh list
                            loginFunction = { username, password, context ->
                                val hashedInput = hashPassword(password)
                                val db = dbHelper.readableDatabase
                                val cursor = db.rawQuery(
                                    "SELECT id, goal_weight FROM users WHERE username=? AND password=?",
                                    arrayOf(username, hashedInput)
                                )
                                val isValid = cursor.moveToFirst()
                                if (isValid) {
                                    // Save logged-in user ID and goal weight
                                    currentUserId = cursor.getInt(cursor.getColumnIndexOrThrow("id"))
                                    goalWeight = cursor.getDouble(cursor.getColumnIndexOrThrow("goal_weight"))

                                    // FIX: Refresh the list immediately after setting userId
                                    refreshGrid { updatedList ->
                                        weightList = updatedList
                                    }
                                }
                                cursor.close()
                                db.close()
                                isValid
                            }
                        )
                    }
                    NavigationState.SignUp -> {
                        SignUpScreen(
                            onSignUpComplete = { navigationState = NavigationState.Login },
                            registrationFunction = { username, password, context ->
                                register(username, password)
                            }
                        )
                    }
                }
            }
        }
    }

    // UPDATED!!
    // Registration function
    private fun register(username: String, password: String): Boolean {
        // Open a readable database.
        val db = dbHelper.readableDatabase

        // Check if the username already exists
        val cursor = db.rawQuery("SELECT * FROM users WHERE username=?", arrayOf(username))
        if (cursor.moveToFirst()) {
            cursor.close()
            db.close()
            // Username already exists.
            return false
        }
        cursor.close()

        // UPDATED!!
        // Hash password before saving to database
        val hashedPassword = hashPassword(password)

        // Insert the new user into the database.
        val writableDb = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("username", username)
            put("password", hashedPassword)
            // UPDATED!! Insert default goal weight for new user
            put("goal_weight", 154.3)
        }
        val newRowId = writableDb.insert("users", null, values)
        writableDb.close()
        return newRowId != -1L
    }

    // UPDATED!!
// Reads weight entries from database and returns list of WeightEntry objects.
    private fun loadWeightEntriesFromDB(): List<WeightEntry> {
        // Create a new list to hold entries
        val newList = mutableListOf<WeightEntry>()

        // UPDATED!!
        // Only load entries for the currently logged-in user.
        // If no user is logged in yet, return an empty list.
        if (currentUserId == null) return newList

        // Query database for this user's entries (already sorted by date in DBHelper)
        val cursor = dbHelper.getAllWeightEntries(currentUserId!!)

        // If results exist, pull out the columns
        if (cursor != null && cursor.moveToFirst()) {
            // Column index for ID
            val idIndex = cursor.getColumnIndex("id")
            // Column index for date
            val dateIndex = cursor.getColumnIndex("date")
            // Column index for weight
            val weightIndex = cursor.getColumnIndex("weight")

            // Loop through rows and build WeightEntry objects
            do {
                // Get entry ID
                val id = cursor.getInt(idIndex)
                // Get entry date (stored as ISO, converted later if needed)
                val date = cursor.getString(dateIndex)
                // Get entry weight
                val weight = cursor.getFloat(weightIndex)
                // Add entry to list
                newList.add(WeightEntry(id, date, weight))
            } while (cursor.moveToNext())
        }

        // Close cursor to free resources
        cursor?.close()

        // Return the list of entries
        return newList
    }

    // Refreshes grid by loading updated data from database.
    private fun refreshGrid(onDataLoaded: (List<WeightEntry>) -> Unit) {
        val newList = loadWeightEntriesFromDB()
        onDataLoaded(newList)
    }

    private fun checkSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    @Composable
    fun LoginScreen(
        onSignUpClick: () -> Unit,
        // UPDATED!!
        // Refresh grid right after login so old entries show immediately
        onSignInClick: () -> Unit,
        loginFunction: (String, String, Context) -> Boolean
    ) {
        val context = LocalContext.current
        var username by remember { mutableStateOf(TextFieldValue("")) }
        var password by remember { mutableStateOf(TextFieldValue("")) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Weight-Tracking App", style = MaterialTheme.typography.headlineLarge)
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (loginFunction(username.text, password.text, context))
                        onSignInClick()
                    else
                        Toast.makeText(context, "Invalid username or password", Toast.LENGTH_SHORT)
                            .show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign In")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onSignUpClick, modifier = Modifier.fillMaxWidth()) {
                Text("Sign Up")
            }
        }
    }

    @Composable
    fun SignUpScreen(
        onSignUpComplete: () -> Unit,
        registrationFunction: (String, String, Context) -> Boolean
    ) {
        val context = LocalContext.current
        var newUsername by remember { mutableStateOf(TextFieldValue("")) }
        var newPassword by remember { mutableStateOf(TextFieldValue("")) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "Create Account", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = newUsername,
                onValueChange = { newUsername = it },
                label = { Text("New Username") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = newPassword,
                onValueChange = { newPassword = it },
                label = { Text("New Password") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (registrationFunction(newUsername.text, newPassword.text, context)) {
                        Toast.makeText(context, "Registration successful", Toast.LENGTH_SHORT)
                            .show()
                        onSignUpComplete()
                    } else {
                        Toast.makeText(context, "Username already exists", Toast.LENGTH_SHORT)
                            .show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Sign Up")
            }
        }
    }

    @Composable
    fun GridScreen(
        goalWeight: Double,
        weightList: List<WeightEntry>,
        onAddDataClick: () -> Unit,
        onDeleteClick: (WeightEntry) -> Unit,
        onEditClick: (WeightEntry) -> Unit,
        onSmsClick: () -> Unit
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(text = "Weight Log", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Goal Weight: %.1f lbs".format(goalWeight),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Date",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Weight",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Actions",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
            }
            Divider(modifier = Modifier.padding(vertical = 4.dp))
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(weightList) { weightEntry ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = weightEntry.date, modifier = Modifier.weight(1f))
                        Text(
                            text = String.format("%.1f", weightEntry.weight),
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onEditClick(weightEntry) },
                            modifier = Modifier.wrapContentWidth()
                        ) { Text("Edit") }
                        Spacer(modifier = Modifier.width(4.dp))
                        Button(
                            onClick = { onDeleteClick(weightEntry) },
                            modifier = Modifier.wrapContentWidth()
                        ) { Text("Delete") }
                    }
                    Divider()
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = onAddDataClick, modifier = Modifier.weight(1f)) {
                    Text("Add Weight")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onSmsClick, modifier = Modifier.weight(1f)) {
                    Text("SMS Permission")
                }
            }
        }
    }

    @Composable
    fun AddWeightScreen(
        goalWeight: Double,
        weightList: List<WeightEntry>,
        onAddComplete: (List<WeightEntry>) -> Unit,
        smsHandler: SmsHandler
    ) {
        var date by remember { mutableStateOf("") }
        var weightText by remember { mutableStateOf(TextFieldValue("")) }
        val context = LocalContext.current

        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "Add Weight Entry", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val calendar = Calendar.getInstance()
                    val year = calendar.get(Calendar.YEAR)
                    val month = calendar.get(Calendar.MONTH)
                    val day = calendar.get(Calendar.DAY_OF_MONTH)
                    DatePickerDialog(
                        context,
                        { _, selectedYear, selectedMonth, selectedDay ->
                            date = "${selectedMonth + 1}/$selectedDay/$selectedYear"
                        },
                        year,
                        month,
                        day
                    ).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = if (date.isEmpty()) "Select Date" else date)
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = weightText,
                onValueChange = { weightText = it },
                label = { Text("Weight (lbs)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val weightValue = weightText.text.toDoubleOrNull()
                    if (weightValue != null && date.isNotEmpty()) {
                        // UPDATED!!
                        // Save to database using the actual logged-in user ID instead of hard-coded "1"
                        val mainActivity = context as MainActivity
                        mainActivity.dbHelper.addWeightEntry(
                            userId = mainActivity.currentUserId ?: return@Button, // fail-safe if null
                            date = date,
                            weight = weightValue
                        )

                        // UPDATED!!
                        // Refresh grid from DB for this user
                        val updatedList = mainActivity.loadWeightEntriesFromDB()

                        // Return to grid with updated list
                        onAddComplete(updatedList)

                        // UPDATED!!
                        // Send SMS if permission granted, otherwise request it
                        if (mainActivity.checkSmsPermission()) {
                            val notificationMessage = if (weightValue == goalWeight)
                                "Congratulations! You've reached your goal weight of $goalWeight lbs."
                            else
                                "Weight added: $weightValue lbs. Keep up the good work!"
                            smsHandler.sendNotification("4124124122", notificationMessage)
                        } else {
                            ActivityCompat.requestPermissions(
                                mainActivity,
                                arrayOf(Manifest.permission.SEND_SMS),
                                1001 // request code
                            )
                        }
                    } else {
                        Toast.makeText(
                            context,
                            "Please enter a valid date and weight",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Add")
            }
        }
    }

    // UPDATED!!
    @Composable
    fun EditWeightDialog(
        weightEntry: WeightEntry,
        onDismiss: () -> Unit,
        onSave: (WeightEntry) -> Unit
    ) {
        // Local state for editable fields
        var date by remember { mutableStateOf(weightEntry.date) }
        var weightText by remember { mutableStateOf(weightEntry.weight.toString()) }

        // Compose-native AlertDialog
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Edit Weight Entry") },

            // Dialog content, two input fields
            text = {
                Column {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { weightText = it },
                        label = { Text("Weight (lbs)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },

            // Confirm button validates and sends updated entry
            confirmButton = {
                Button(onClick = {
                    val weightValue = weightText.toFloatOrNull()
                    if (weightValue != null) {
                        onSave(WeightEntry(weightEntry.id, date, weightValue))
                        onDismiss()
                    } else {
                        // Added: Simple validation message
                        // (UI stays open so user can correct the value)
                    }
                }) {
                    Text("Save")
                }
            },

            // Cancel button closes the dialog
            dismissButton = {
                Button(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}