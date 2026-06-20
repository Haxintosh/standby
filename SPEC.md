# Plugin spec
A plugin file is a zip file with 3 main files inside.  
It contains the following:  
1. `plugin.html` This file contains the HTML, CSS and JS for the plugin
2. `plugin_manifest.json` This file contains the metadata for the plugin, detailed below
3. `customization.json` This file contains the customization options for the plugin
4. `assets/` This folder contains the assets the HMTL page can access, such as images, fonts, audio files, etc.
## plugin.html
This is what the WebView will load when the plugin is loaded. 
This file is fully compatible with CSS/JS.  
Mind the use of JS, use `requestAnimationFrame()` instead of `setInterval()` for animations to sync up with the phone's refresh rate, especially useful when the refresh rate is limited.  
The JS has access to most things a typical web browser has access to, this includes the Web Storage API or Web Audio API.  
For network requests, you need to whitelist the domain inside `plugin_manifest.json`, otherwise it will be blocked.  
Phone sensors can be accessed using `window.AndroidSensors`, which is a bridge between phone sensors and the WebView. You can find details of the bridge implementation under `SensorBridge.kt`.  
Note that phone sensor access needs permission to be defined in the `plugin_manifest.json` on a plugin to plugin basis. Otherwise, an attempt to read the data will fail.  

## plugin_manifest.json
This file details the specifics of the plugin.  
```json 
{
  "id": "uuid4",
  "name": "string (human-readable name)",
  "description": "string (brief explanation of functionality)",
  "author": "string (name of the creator)",
  "version": "string (semantic version, e.g., '1.0.0')",
  "size": "half/full (size of the plugin, whether it handles 2 at the same time or just a single one)",
  "permissions": [
    "string (requested capability, e.g., 'battery', 'gyro', 'health', 'gps')"
  ],
  "network_whitelist": [
    "string (allowed domain, e.g., 'api.example.com')"
  ],
  "min_app_version": "integer (minimum versionCode required, e.g., 1)"
}
```

## customization.json
This file should have all the variables you want to be able to use/read in the `plugin.html`.  
This file will be used to create the customization portion of the UI for the plugin.  
```json
{
  "variable 1": {
    "type": "string, bool, etc",
    "default": "yellow"
  },
  "variable 2": {
    "type": "string, bool, etc",
    "default": "true"
  }
}
```