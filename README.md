# WatchWitch

![WatchWitch icon](img/banner.png)

WatchWitch is an Android app that allows you to communicate with your Apple Watch. It requires a jailbroken iPhone and a rooted Android phone to set up.

## Features

* The WatchWitch app provides internet access to the watch using the SHOES protocol. Statistics on contacted hosts and data transferred are displayed in the *Network Stats* view.
* Send text message notifications to the watch using the *Chat* view.
* Browse health data collected by the watch in the *Health* view, including ECG charts and GPS tracks.
* View a list of open apps, alarms set, and the ringer state of the watch (only available after sent by the watch).
* Receive and download screenshots taken on the watch and communication transcripts in the *Files* view.

## Screenshots

![Screenshots 1](img/screenshots-1.png)

![Screenshots 2](img/screenshots-2.png)

## Usage

To get your watch to connect to your Android phone, you'll need the [WatchWitch iOS app](https://github.com/rec0de/watchwitch-ios-companion) and the [WatchWitch iOS tweak](https://github.com/rec0de/watchwitch-ios). You'll also have to grant the WatchWitch Android app root privileges, which are required to set up the IPSec tunnel networking with the watch.

Once you have everything installed, open the WatchWitch apps on both the iPhone and the Android phone. Make sure all devices are connected to the same WiFi network.

Toggle the *listen for connections* switch on the Android phone. The app should should now show the status *"listening on [localip]:5000"*. Enter the local IP of the Android phone in the iOS app and tap *set target IP address*. Then, tap *send keys* to extract cryptographic keys and share them with the Android phone. Before you do so, you may set a custom transit encryption key for these secrets in the iOS app. If you do so, enter the same key in the Android app by tapping *set key*. The Android phone should confirm receipt of the key material in the debug log (*"got keys!"*).

Now, enable the *override WiFi address updates* toggle on the iPhone. The iPhone will now set up the Android phone's IP address with the watch whenever the devices reconnect. If the watch is currently connected, you may tap *force update WiFi address* to perform an update on the spot.

This completes the initial setup. Whenever you want to connect the watch to the Android phone, proceed as follows:

Make sure the devices are still in the same network and the Android app is open and listening. Connect the watch to the iPhone over Bluetooth as usual (i.e. turn on the watch, enable Bluetooth on both the iPhone and the watch). After a few seconds, disable Bluetooth completely on the iPhone (the quick settings tile is not sufficient to do this).

The watch will now connect to the Android phone *when it chooses to*. To force the watch to reconnect, you may trigger a "find my" ping from the watch's quick settings view, which will cause it to realize that the Bluetooth link is down and attempt a WiFi connection. You should see output in the debug log of the Android app confirming that a connection is being established.

For a successful connection, you should see several *SetupChannel* messages in the debug log. Once connected, you should be able to use all features of the app.

**Note:** The watch is a moody creature. Taking over the connection from an iPhone stretches the intended use case of the involved protocols a fair bit, and can get the watch into weird, half-functional states. Especially when re-connecting to the iPhone after being connected to the Android phone, you can expect various features to not work as intended. For best results, reboot the watch, restart the WatchWitch app, and try again.