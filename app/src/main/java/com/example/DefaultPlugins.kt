package com.example

import android.content.SharedPreferences

object DefaultPlugins {
    fun getBuiltInClockPlugin(prefs: SharedPreferences): PluginModel {
        val clockColor = prefs.getString("builtin_customization_com.example.builtin.clock_clockColor", "#D0BCFF") ?: "#D0BCFF"
        
        return PluginModel(
            localId = "com.example.builtin.clock",
            manifestId = "com.example.builtin.clock",
            name = "Default Clock",
            description = "Immersive digital clock with battery indicator",
            author = "System",
            version = "1.0.0",
            permissions = listOf("battery"),
            networkWhitelist = emptyList(),
            minAppVersion = 1,
            directoryPath = null,
            htmlContent = BUILT_IN_CLOCK_PLUGIN,
            customizations = mapOf(
                "clockColor" to CustomizationOption(
                    type = "color",
                    default = "#D0BCFF",
                    target = "css",
                    value = clockColor
                )
            ),
            isBuiltIn = true
        )
    }

    private val BUILT_IN_CLOCK_PLUGIN = """
<!DOCTYPE html>
<html>
<head>
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <style>
    /* styling */
    * {
      box-sizing: border-box;
    }
    html, body {
      margin: 0;
      padding: 0;
      height: 100%;
      width: 100%;
      background-color: #0a0a0a;
      color: #E6E1E5;
      font-family: system-ui, -apple-system, sans-serif;
      overflow: hidden;
    }
    body {
      display: flex;
      flex-direction: column;
      justify-content: center;
      align-items: center;
    }
    body::after {
      content: "";
      position: fixed;
      inset: 0;
      pointer-events: none;
      opacity: 0.03;
      background-image: radial-gradient(#ffffff 1px, transparent 1px);
      background-size: 4px 4px;
      z-index: 9999;
    }
    .plugin-container {
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      justify-content: center;
      transition: all 0.5s ease;
      width: 90%;
      max-width: 800px;
    }
    #time {
      font-size: 30vh;
      font-weight: 200;
      line-height: 1.1;
      letter-spacing: -0.05em;
      color: #E6E1E5;
      margin: 0;
      /* two-tone clock styling */
      background: linear-gradient(to bottom, #E6E1E5 40%, var(--clockColor, #D0BCFF) 60%);
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
      text-align: right;
    }
    .info {
      font-size: 5vh;
      color: #938F99;
      font-weight: 600;
      text-transform: uppercase;
      letter-spacing: 0.2rem;
      align-self: flex-end;
      margin-top: 1vh;
    }
    #battery-bar {
      width: 100%;
      height: 1vh;
      min-height: 4px;
      background: #49454F;
      border-radius: 0.5vh;
      margin-top: 2vh;
      overflow: hidden;
    }
    #battery-fill {
      height: 100%;
      background: var(--clockColor, #D0BCFF);
      width: 0%;
      transition: width 1s ease-in-out;
    }
    .low-battery #battery-fill {
      background: #f87171;
    }
  </style>
</head>
<body>
  <!-- clock container -->
  <div class="plugin-container" id="root">
    <div id="time">00:00</div>
    <div class="info" id="battery-text">Battery: --%</div>
    <div id="battery-bar"><div id="battery-fill"></div></div>
  </div>

  <script>
    // data update
    function updateData() {
      const timeEl = document.getElementById('time');
      const batteryTextEl = document.getElementById('battery-text');
      const batteryFillEl = document.getElementById('battery-fill');

      if (window.AndroidSensors) {
        // get sensor data
        timeEl.innerText = window.AndroidSensors.getFormattedTime("HH:mm");

        let batteryLvl = 50;
        try {
          const batteryLevelRes = window.AndroidSensors.getBatteryLevel();
          if (typeof batteryLevelRes === 'string') {
            const batteryLevelObj = JSON.parse(batteryLevelRes);
            batteryLvl = (batteryLevelObj && typeof batteryLevelObj.level !== 'undefined') ? batteryLevelObj.level : batteryLevelRes;
          } else {
            batteryLvl = batteryLevelRes;
          }
        } catch (e) {
          console.error("Failed to parse battery level JSON", e);
        }

        batteryTextEl.innerText = 'Battery ' + batteryLvl + '%';
        batteryFillEl.style.width = batteryLvl + '%';

        if (batteryLvl <= 20) {
          document.body.classList.add("low-battery");
        } else {
          document.body.classList.remove("low-battery");
        }
      } else {
        timeEl.innerText = new Date().toLocaleTimeString('en-US', { hour12: false, hour: '2-digit', minute: '2-digit' });
        batteryFillEl.style.width = '50%';
      }
    }

    // update loop
    setInterval(updateData, 1000);
    updateData(); // initial update
  </script>
</body>
</html>
""".trimIndent()
}
