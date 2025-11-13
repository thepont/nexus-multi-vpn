# Test Apps

This directory contains test Android applications used for validating routing functionality.

## Required Test Apps

### 1. `test-app-uk.apk`
- **Package:** `com.example.testapp.uk`
- **Purpose:** Test routing to UK VPN server
- **Features:**
  - "Fetch" button (resource ID: `btn_fetch`)
  - Response text view (resource ID: `tv_response`)
  - Makes HTTP request to `http://10.1.0.2` (or `http://10.8.0.2` for conflict test)
  - Displays response text

### 2. `test-app-fr.apk`
- **Package:** `com.example.testapp.fr`
- **Purpose:** Test routing to FR VPN server
- **Features:**
  - "Fetch" button (resource ID: `btn_fetch`)
  - Response text view (resource ID: `tv_response`)
  - Makes HTTP request to `http://10.2.0.2` (or `http://10.8.0.3` for conflict test)
  - Displays response text

### 3. `test-app-dns.apk`
- **Package:** `com.example.testapp.dns`
- **Purpose:** Test DNS resolution via VPN DHCP
- **Features:**
  - "Fetch" button (resource ID: `btn_fetch`)
  - Response text view (resource ID: `tv_response`)
  - Makes HTTP request to `http://test.server.local`
  - Displays response text (should be `"DNS_TEST_PASSED"`)

## Creating Test Apps

### Option 1: Simple HTTP Client App

Create a minimal Android app with:
- Single Activity with a "Fetch" button and TextView
- HTTP client (OkHttp) to make requests
- Displays response in TextView

### Option 2: Use Existing Test Framework

You can create these apps using:
- Android Studio project template
- Minimal Kotlin code
- OkHttp for HTTP requests

### Example App Structure

```kotlin
class MainActivity : AppCompatActivity() {
    private lateinit var responseText: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        responseText = findViewById(R.id.tv_response)
        findViewById<Button>(R.id.btn_fetch).setOnClickListener {
            fetchData()
        }
    }
    
    private fun fetchData() {
        // Make HTTP request to test server
        // Display response in responseText
    }
}
```

## Installing Test Apps

Tests can install apps using:
```bash
adb install test-app-uk.apk
adb install test-app-fr.apk
adb install test-app-dns.apk
```

Or programmatically in tests using UI Automator.


