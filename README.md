![image of an analog clock next to a monthly calendar](img/standby_hero.png)

# Standby
> [!IMPORTANT]
> This app is still very much WIP!

**Plugins can be found under: [plugins/build/](plugins/build/)**

**Video Demo: [video link](https://drive.google.com/file/d/1LBqg1Of4_amh87YIiEeGrte-fc33Wl7u/view?usp=sharing)**

Standby is an open source, modular and extensible Android app similar to the StandBy mode found on iOS devices.  

The main difference between similar standby mode apps on the Play Store is that Standby is open source, free and extensible using HTML/CSS/JS. 


## Install
1. Download the latest [release APK](https://github.com/Haxintosh/standby/releases)   
2. Tap on the downloaded APK
3. If needed, allow installation from browser  
4. Import plugins by using the local uploader or import the plugin ZIP files  
**Plugins can be found under: [plugins/build/](plugins/build/)**

## Implemented features
- Extensible plugins using HTML/CSS/JS, check out [plugin docs](DOCS.md)
- Third party app widget support
- Night mode (schedule, dim brightness, OLED protection)
- OLED burn in protection (alternate on pixels)
- Android sensor access in plugin (using `window.AndroidSensors`)
- Providers (weather currently)
- Full/split layout for plugins 
- Customization injection (no plugin reload for customizations)
- Local upload server

## Documentation 
Docs for plugins can be found under [plugin docs](DOCS.md)   
Example plugins are provided under `plugins/`.

## Images
### Layouts
Side by side layout:
  ![Side-by-side widget layout](img/half_plugin.png)
Fullscreen layout:
  ![Full-screen widget layout](img/fullscreen_plugin.png)

### Example Widgets
Battery stats:
  ![Battery stats monitor widget](img/battery_stats.png)
Weather info:
  ![Weather and clock widget](img/clock_weather.png)
Bad Apple:
  ![Bad Apple ASCII](img/bad_apple.png)
3rd party widgets:
  ![material clock](img/pixel_clock.png)
  ![github contributions widget](img/app_widget_example.png)
Other clocks:
  ![Customizable color clock](img/colorful_clock.png)
  ![Elongated typography clock](img/elongated_clock.png)
  ![2 clocks side by side](img/side_clocks.png)

### Controls
Customization:
  ![In-app plugin customization controls](img/plugin_customization.png)
Night mode:
  ![Night mode configuration](img/night_mode_setting.png)
OLED protection
  ![Shifting subpixel protection pattern overlay](img/oled_burn_example.png)

## App permissions
- `INTERNET`: Allow web plugins to fetch online data, local upload server and update providers. 
- `VIBRATE`: Duh.
- `QUERY_ALL_PACKAGES` Needed to use third party app widgets.


## Building
1. Run the Gradle build task:
   ```bash
   ./gradlew assembleDebug
   ```
