# Artifact Appendix

Paper title: WatchWitch: Interoperability, Privacy, and Autonomy for the Apple Watch

Artifacts HotCRP Id: #13

Requested Badge: **Available**, **Functional**

## Description
The WatchWitch Android app is our open reimplementation of the Apple Watch protocol stack that allows Android devices to interoperate with the Apple Watch and implements a privacy-preserving, user-first approach for using modern smartwatches.

### Security/Privacy Issues and Ethical Concerns (All badges)
Running WatchWitch in a complete setup requires rooted/jailbroken devices, which disables some security mechanisms of the devices. Using WatchWitch with a real Apple Watch will also access private key material used to communicate with the watch and cause the watch to synchronize private health information with the app.

Running the Android WatchWitch in a simulated setup without a real Apple Watch does not require root access and does not pose any security or ethical concerns.

## Basic Requirements

### Hardware Requirements

A full setup would require an Apple Watch running watchOS 8 or earlier, a jailbroken iPhone running iOS with a root-ful jailbreak, and a rooted Android phone running Android 13 or later.  
For ease of review, we include an Apple Watch simulator in our Android App. If using the simulator, only an Android phone or emulator for Android 13 or later is required. Root access is not necessary in this case.

### Software Requirements

Build instructions are based on the Standard VM running Ubuntu 22.04. Using an emulated Android device requires Android Studio on the host machine.

### Estimated Time and Storage Consumption

An APK build from scratch on the Standard VM, following the commands below, takes less than 10 minutes and requires about ~4GB of storage for all installed build tools / dependencies. Installing Android Studio and the Android emulator might require more time and storage (~10GB).

## Environment 

### Accessibility

The git repository for the Android app is available at [github.com/seemoo-lab/watchwitch/tree/pets](https://github.com/seemoo-lab/watchwitch/tree/pets). The tag submitted for evaluation is `v1.2.0`. Other components are linked from the readme but not required for evaluation using a simulated Apple Watch.

### Set up the environment

For a plain build of the WatchWitch APK (on a fresh Standard VM, Ubuntu 22.04):

```bash
sudo apt install openjdk-17-jdk android-sdk sdkmanager git
export ANDROID_HOME=/home/[username]/.android # set Android SDK location
sdkmanager --licenses # accept Android SDK licenses
git clone https://github.com/seemoo-lab/watchwitch.git
```

or using Android Studio with the built-in emulator for evaluation without a physical Android 13+ device (native Ubuntu 22.04 with GUI):

```bash
sudo apt install git
git clone https://github.com/seemoo-lab/watchwitch.git
# download the latest Android Studio from https://developer.android.com/studio
# unpack downloaded archive to installation location /usr/local
sudo tar -xf android-studio-2024.1.2.13-linux.tar.gz -C /usr/local
cd /usr/local/android-studio/
bin/studio.sh
```

This should launch the Android Studio install wizard. Complete the wizard with default settings. Once completed, choose "open project" and select the dowloaded copy of the watchwitch repository. Wait for the gradle sync to finish and launch the app on a virtual Android device from the top right corner of the window.  

*Note that the virtual device will fail to start if Android Studio itself was installed in a virtual machine. If you'd like to run the build in an untrusted VM, follow the above plain build instructions and copy the `app-debug.apk` file to your host machine. Install Android Studio on the host, open the device manager from the right sidebar and launch the virtual device. Once the virtual device is booted, you can drag-and-drop the APK file into the virtual device to install it.*

### Testing the Environment

Gradle performs large parts of the dependency and build tool installation and testing automatically. To veryify your environment is set up correctly, start a gradle build using the provided wrapper:

```bash
cd watchwitch
./gradlew build
cd app/build/outputs/apk/debug # generated APK location
```

The gradle build should finish with `BUILD SUCCESSFUL in Xm XXs`.

## Artifact Evaluation

### Main Results and Claims

#### Main Result 1: Android Interoperability for the Apple Watch

We show that interoperability between the Apple Watch and Android devices is possible despite Apple's claims. To demonstrate feasibility, we implement support for several core smartwatch features (health data sync, internet sharing, watch state information, instant messaging) in our WatchWitch Android app. See Section 5 and Experiment 1.

#### Main Result 2: User-controlled Firewall

We implement a fine-grained firewall building on top of our reimplementation of Shoes that allows users to block connections to certain hosts, as well as connections from certain processes. This allows users to keep certain watch apps entirely offline and limit tracking functionality while retaining internet access for desired features and apps (see Section 5.3.2, Experiment 2).

### Experiments 

#### Experiment 1: General Interoperability

*should take ~3 minutes and no significant disk space*

Start the watchwitch app on your physical or emulated Android device. Tap the 'simulate watch' button at the bottom of the screen. You should observe some debug output being printed to the screen. Wait for ~10 seconds for the setup to complete. You can then view the watch state, synchronized health data, and outgoing network connections from the watch by tapping the 'watch', 'health', and 'firewall' buttons, respectively.

In the watch state view, you should see some alarms being set as well as a list of apps running on the watch. This list will change every 10 seconds as a new state update is received.

In the health view, you should see heart rate and step count samples accumulating. New samples should arrive every ~45 seconds, but will only be shown when you exit and re-enter the view. 

In the firewall view, you should see network requests to various example domains from different processes, with some data being transferred.

This shows support for core smartwatch features as discussed in Section 5 of our paper.

#### Experiment 2: User-controlled Firewall

*should take ~3 minutes and no significant disk space*

Start the app as before and enable the watch simulation. Tap on the 'Firewall' button on the main screen, then wait for ~10 seconds and tap 'refresh'. You should see some requests from different processes to various example domains showing up with non-zero amounts of data transferred. Pick some of the example domains and disable the 'allow?' switch. Continue refreshing the view occasionally (every ~10-30 seconds). You should be able to observe the packet counter (left) for the disabled hosts to continue increasing as the watch attempts to make connections, but the amount of data transferred should stay the same, as new connections are now blocked. For allowed hosts, data should continue to be transferred.

This shows that we can make fine-grained decisions on which connections our watch is allowed to establish and that we can keep certain processes (such as the simulated `com.watchwitch.spyware`) completely offline (see Section 5.3.2 in the paper).

## Limitations

All claims made in our paper are reproducible with the provided software. The hardware setup required to do so, however, is quite complex and requires specific software versions to work. The provided simulator replaces the watch at the TCP level and performs all the required communication but is, by its nature, only a limited replacement for the much more complex real watch.

For a demo video with a real hardware setup, see [youtube.com/watch?v=dHz8NHMhtLY](https://www.youtube.com/watch?v=dHz8NHMhtLY).

## Notes on Reusability 

WatchWitch provides the infrastructure to implement further services communicating with the Apple Watch using Alloy. To add a new service, developers can implement the [AlloyService](https://github.com/seemoo-lab/watchwitch/blob/main/app/src/main/java/net/rec0de/android/watchwitch/alloy/AlloyService.kt) interface and register their service with the [AlloyController](https://github.com/seemoo-lab/watchwitch/blob/main/app/src/main/java/net/rec0de/android/watchwitch/alloy/AlloyController.kt).