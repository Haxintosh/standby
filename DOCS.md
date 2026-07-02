# Standby Plugin Development Guide

This documentation details how to create and customize plugins for the Standby Mode application. Plugins are lightweight web applications running inside an isolated Android WebView container with sandboxed access to native sensors, providers, and real-time user customization settings.

---

## 1. Plugin Directory & ZIP Structure

Every plugin is packaged as a standard `.zip` file containing the following structure:

```text
plugin.zip/
├── plugin_manifest.json     # Required: Metadata, permissions, and sizing
├── plugin.html              # Required: Main HTML entry point
├── customization.json       # Optional: Configures user-customizable options
└── assets/                  # Optional: Images, fonts, styles, or scripts
    ├── sunset.png
    ├── style.css
    └── font.ttf
```

> [!IMPORTANT]
> **Use Forward Slashes (`/`) in Paths:**
> Always use forward slashes for path separators in HTML imports and inside zip file headers. Android is Linux-based; zip archives generated using Windows-style backward slashes (`\`) inside file names will fail to unpack/resolve subdirectories correctly.

### Reference assets in `plugin.html`:
```html
<link rel="stylesheet" href="assets/style.css">
<img src="assets/sunset.png" alt="Sunset image">
```

---

## 2. Manifest Configuration (`plugin_manifest.json`)

The manifest contains metadata and declares permissions, providers, and sandbox whitelist controls:

```json
{
  "id": "com.example.clock",
  "name": "Standard Clock Widget",
  "description": "Displays current local time with customizable styles.",
  "author": "honk honk",
  "version": "1.0.0",
  "size": "half",
  "permissions": [
    "alarms"
  ],
  "providers": [
    "weather"
  ],
  "network_whitelist": [
    "api.open-meteo.com"
  ],
  "min_app_version": 1
}
```

### Property Reference

| Property | Type | Description |
| :--- | :--- | :--- |
| `id` | String | A unique package-style identifier (e.g., `"com.example.clock"`). |
| `name` | String | User-facing title displayed in the widget selector. |
| `description` | String | Brief description of the widget. |
| `author` | String | Developer name or organization. |
| `version` | String | Semantic version string (e.g., `"1.0.0"`). |
| `size` | String | Widget width layout: `"half"` (takes up half the screen alongside another widget) or `"full"` (takes up the entire screen). |
| `permissions` | Array | Declares requested hardware permissions (see [Sensors](#5-native-sensor-bridge-windowandroidsensors)). |
| `providers` | Array | Declares requested external data providers (see [Providers](#6-native-provider-bridge-windowandroidproviders)). |
| `network_whitelist` | Array | Allowed domains. External HTTP requests to domains not in this list are automatically blocked. |
| `min_app_version` | Integer | Minimum Android client app versionCode needed to support the plugin. |

---

## 3. Customizations Schema (`customization.json`)

To let users customize colors, toggles, or numerical limits directly from the Android settings dialog, include a `customization.json` file in your root folder:

```json
{
  "bg-color": {
    "type": "color",
    "default": "#000000",
    "target": "css"
  },
  "clock-color": {
    "type": "color",
    "default": "#D0BCFF",
    "target": "css"
  },
  "show-seconds": {
    "type": "boolean",
    "default": "false",
    "target": "js"
  },
  "max-items": {
    "type": "number",
    "default": "10",
    "target": "js"
  }
}
```

### Property Schema

* **`type`**: Determines the input widget rendered in the Android settings UI. Common values include `"color"`, `"string"`, `"bool"`, `"boolean"`, or `"number"`.
* **`default`**: The default fallback value (always specified as a string).
* **`target`**: The destination target of the variable:
  * `"css"`: Injects the value as a CSS custom property (variable) on the document root.
  * `"js"`: Injects the value directly as a global variable on the `window` object.
* **`value`** *(Populated at runtime)*: When a user configures settings, the host Android app updates and writes the new choice to the `value` field in this file locally.

---

## 4. Variable Injection Mechanism

The host app automatically parses and injects variables when the plugin is loaded, and dynamically updates them in real-time when the user makes changes in settings.

### Value Conversion Rules
Before injection, types are parsed and converted to native types in the WebView:
* `"bool"` or `"boolean"` values are converted to standard JS boolean literals (`true` or `false`).
* `"number"` values are converted to JS float literals (defaulting to `0` if invalid).
* Other types (e.g. `"color"`, `"string"`) are escaped and wrapped as double-quoted string literals.

### CSS Target Injection
CSS-targeted customizations are added to the document root:
```javascript
document.documentElement.style.setProperty('--variableName', escapedValue);
```
Inside your `plugin.html` or styling assets, reference them natively:
```css
body {
  background-color: var(--bg-color);
  color: var(--clock-color);
}
```

### JS Target Injection
JS-targeted customizations are attached to the `window` object:
```javascript
window.variableName = escapedValue;
```
Inside your JavaScript, access them directly:
```javascript
if (window.show-seconds) {
  // Render seconds
}
```

### Real-Time Update Callback
When a user updates customizations, the app updates the styles or variables on the fly without reloading the page. You can listen for these changes dynamically by implementing:

```javascript
window.onCustomizationChanged = function(updatedValues) {
  // updatedValues is a JSON object mapping customization names to their new values
  console.log("Updated values received:", updatedValues);
  
  if (updatedValues["show-seconds"] !== undefined) {
    toggleSecondsDisplay(updatedValues["show-seconds"]);
  }
};
```

---

## 5. Native Sensor Bridge (`window.AndroidSensors`)

The native sensor bridge is registered as a global Javascript interface under `window.AndroidSensors`. Because values cross process boundaries, **all complex objects are returned as serialized JSON strings**. Make sure to parse them using `JSON.parse()`.

Always verify that `window.AndroidSensors` and the desired method exist before invoking them to avoid errors during desktop browser previews.

### A. `getBatteryLevel()`
Returns current battery charge percentage.
* **Required Manifest Permission**: `"battery"` or `"battery_level"`
* **Response Format**: `{"level": Int}`
* **Example**:
  ```javascript
  const levelObj = JSON.parse(window.AndroidSensors.getBatteryLevel());
  console.log(`Battery: ${levelObj.level}%`); // "Battery: 85%"
  ```

### B. `getChargingStatus()`
Returns charging status, temperature, and remaining charge time estimation.
* **Required Manifest Permission**: `"battery"` or `"battery_charging"`
* **Response Format**:
  ```json
  {
    "isCharging": Boolean,
    "source": String,        // "AC", "USB", "Wireless", "None", or "Unknown"
    "temp": Int,            // Temperature in °C
    "timeRemaining": Long   // Estimated remaining charge time in ms (or -1 if unavailable)
  }
  ```

### C. `getBatteryStats()`
Returns real-time power metrics and battery health ratings.
* **Required Manifest Permission**: `"battery"` or `"battery_stats"`
* **Response Format**:
  ```json
  {
    "current": Int,      // Active charge (+) or discharge (-) current in mA
    "voltage": Int,      // Battery voltage in mV
    "health": Int,       // Health status code (see below)
    "chargeCount": Int   // Remaining charge counter in mAh
  }
  ```
  * *Health Status Codes:* `1` = Unknown, `2` = Good, `3` = Overheat, `4` = Dead, `5` = Over Voltage, `6` = Unspecified Failure, `7` = Cold.
    
### D. `getAmbientLight()`
Reads the ambient light sensor.
* **Required Manifest Permission**: `"sensors"` or `"ambient_light"`
* **Response Format**: `{"light": Int}` (illumination in Lux)

### E. `getProximity()`
Reads the physical proximity sensor.
* **Required Manifest Permission**: `"sensors"` or `"proximity"`
* **Response Format**: `{"proximity": Int}` (distance value in cm, usually `0` for close and `5` for far)

### F. `getNextAlarm()`
Retrieves details about the next scheduled system alarm.
* **Required Manifest Permission**: `"alarms"`
* **Response Format**:
  ```json
  {
    "hasAlarm": Boolean,
    "triggerTime": Long,       // Epoch millisecond timestamp of next alarm
    "creatorPackage": String?, // Package name of the app that set the alarm
    "formattedTime": String    // Simple formatted time representation (e.g. "07:30 AM")
  }
  ```

### G. `getCurrentTime()`
Returns current local time formatted as a simple 24-hour string.
* **Required Manifest Permission**: None (always available)
* **Response Format**: String (format: `"HH:mm:ss"`)

### H. `getFormattedTime(formatString)`
Returns current local time formatted according to the given Java `SimpleDateFormat` string.
* **Required Manifest Permission**: None (always available)
* **Parameters**: `formatString` (String, e.g. `"yyyy-MM-dd"`, `"hh:mm a"`, or `"E, MMM d"`)
* **Response Format**: String (formatted date/time)

### I. `getCustomizations()`
Returns the raw JSON string containing active customization options.
* **Required Manifest Permission**: None (always available)
* **Response Format**: JSON String

---

## 6. Native Provider Bridge (`window.AndroidProviders`)

 the provider bridge is registered as a global Javascript interface under `window.AndroidProviders`. Like the sensor bridge, **all objects are returned as serialized JSON strings**.

### A. `getCurrentWeather()`
Returns parsed, aggregated weather condition values matching the current hour.
* **Required Manifest Provider**: `"weather"`
* **Response Format**:
  ```json
  {
    "city": String,                    // City name (e.g. "Berlin")
    "latitude": Double,
    "longitude": Double,
    "temp": Double,                    // Current temperature in °C
    "tempMin": Double,                 // Minimum temperature forecast for today
    "tempMax": Double,                 // Maximum temperature forecast for today
    "weatherCode": Int,                // WMO interpretation code (0 to 99)
    "description": String,             // Human-readable condition description (e.g. "Partly Cloudy")
    "isDay": Int,                      // Day indicator: 1 = day, 0 = night
    "humidity": Int,                   // Relative Humidity percentage
    "dewPoint": Double,                // Dew point in °C
    "apparentTemperature": Double,     // Apparent temperature in °C
    "visibility": Double,              // Visibility distance in meters
    "uvIndex": Double,                 // UV index
    "precipitation": Double,           // Current precipitation level in mm
    "precipitationProbability": Int,   // Chance of precipitation percentage (0 to 100)
    "icon": String                     // Data URL base64 image string (or fallback keywords like "sunny", "rainy", etc.)
  }
  ```

### B. `getWeatherData()`
Returns the raw cached JSON weather forecast payload (containing hourly lists). This is ideal for rendering weather forecast charts.
* **Required Manifest Provider**: `"weather"`
* **Response Format**: Raw weather JSON forecast structure from open-meteo.

---

## 7. Basic Plugin Boilerplate

```html
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <style>
    :root {
      --bg-color: #000000;
      --text-main: #ffffff;
    }
    body {
      background-color: var(--bg-color);
      color: var(--text-main);
      font-family: sans-serif;
      display: flex;
      flex-direction: column;
      align-items: center;
      justify-content: center;
      height: 100vh;
      margin: 0;
    }
  </style>
</head>
<body>
  <div id="time">00:00</div>
  <div id="weather">Loading weather...</div>

  <script>
    function updateWidget() {
      // 1. Update Time
      // you can use the AndroidSensors or the default JS API to access real-time data
      if (window.AndroidSensors) {
        document.getElementById('time').innerText = window.AndroidSensors.getFormattedTime("hh:mm a");
      } else {
        const now = new Date();
        document.getElementById('time').innerText = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
      }

      // 2. Update Weather
      if (window.AndroidProviders && typeof window.AndroidProviders.getCurrentWeather === 'function') {
        try {
          const weather = JSON.parse(window.AndroidProviders.getCurrentWeather());
          if (weather && weather.temp !== undefined) {
            document.getElementById('weather').innerText = `${weather.city}: ${weather.temp}°C, ${weather.description}`;
          }
        } catch (e) {
          console.error("Failed to parse weather data:", e);
        }
      }
    }

    // Handle real-time customization updates
    window.onCustomizationChanged = function(changed) {
      console.log("Customizations dynamically updated:", changed);
    };

    updateWidget();
    // for smoother animations, use requestAnimationFrame() instead
    setInterval(updateWidget, 1000);
  </script>
</body>
</html>
```
