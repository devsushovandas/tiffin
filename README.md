# Tiffin

Take-home assignment for the Android Developer Intern role at Propel. Tiffin is a practice app for ordering a daily tiffin from home kitchens: a kitchen list, a kitchen detail screen with the weekly menu, and a paywall with a fake ₹1 trial purchase.

- Debug APK: https://drive.google.com/file/d/1h49OOvuu7asUtrZsiCLLJT1elSWOZN_l/view?usp=drive_link
- Screen recording (under 90 seconds): https://drive.google.com/file/d/1L3TPGNmDx2slg4vKAN3VgSEnKD4bJkCW/view?usp=drive_link

## What I cut and why

| Cut | Why |
|---|---|
| Kitchen photos and image loading | The brief asks for name, cuisine, price, veg/non-veg and rating. Text rows carry all of it, and an image library adds a dependency and failure cases for no required feature. |
| Search, filter and sort | Not in the brief, and the list has 12 kitchens. |
| Dependency-injection framework | One module and three screens. A hand-written `AppContainer` is easier to read and review. |
| Automated tests | I spent the time on manual tests of the state rules (rotation, process death, force-stop), because that is where this app can go wrong. The manual test list is in Task 3. |
| Custom theme and animations | Default Material 3. Visual polish is not what the brief measures. |
| Network layer | The brief says no backend. Data is a bundled JSON asset read off the main thread. |

Known gaps I left in on purpose:

- The charge date is always shown on a 12-hour clock, even if the device is set to 24-hour.
- If the process dies between saving the purchase and logging it, the purchase is saved but `purchase_completed` is not logged. Fixing this needs a persisted event queue, which is too much for a Logcat logger.
- Commit history: I set up Git late, so the first build (data, list, detail, persistence, paywall, analytics) is one commit. Changes after that are one commit each.

## Assumptions

I asked three questions on the email thread. I had no reply before submitting, so these are the assumptions I built on.

1. **Third launch.** The paywall opens by itself once, on exactly the third launch. If the user closes it, it does not open by itself again. Any Subscribe tap opens it until the user has paid.
2. **What a launch is.** A fresh start of the app. Rotation and restoring after process death do not count. Pressing Back out of the app and opening it again does count.
3. **Error and empty states.** A bundled file does not fail by itself, so debug builds have an overflow menu on the list screen ("Simulate: normal / empty / error"). Release builds always load normally.
4. **After 24 hours.** No real charge happens. The app stores the promised charge time when the user pays, shows "Trial active. ₹249 will be charged on <date and time>" until then, and "Plan active since <date>" after it.

## How to build and run

Requirements: JDK 17, Android SDK 35. Min SDK is 24. Package: `in.lendan.tiffin`.

```
git clone https://github.com/devsushovandas/tiffin.git
cd tiffin
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Useful commands while testing:

```
adb logcat -s Tiffin TiffinAnalytics        # launch count and analytics events
adb shell pm clear in.lendan.tiffin         # reset to a first launch
adb shell am force-stop in.lendan.tiffin    # force-stop
adb shell am kill in.lendan.tiffin          # process death (press Home first)
```

To see the third-launch paywall: clear the app data, then open and force-stop the app until the log shows `launchCount=3`.

## How state survives

- **Paid flag, purchase time, promised charge time, launch count** are stored in DataStore, so they survive force-stop and reopening. Payment data is written in one DataStore write so the values cannot disagree.
- **Which kitchen is open** comes from the navigation arguments through `SavedStateHandle`, so the detail screen comes back after process death.
- **The date shown on the paywall** is computed once when the paywall opens and kept in `SavedStateHandle`, so rotation does not change it.
- Screens wait for DataStore before drawing the Subscribe button or the paywall, so a paid user never sees the paywall flash.

## Analytics: the four moments

`analytics/analytics.kt` has one interface and one Logcat implementation (tag `TiffinAnalytics`).

| Event | When | Properties |
|---|---|---|
| `subscribe_tapped` | The user taps Subscribe on a kitchen | `kitchenId` |
| `paywall_shown` | The paywall content appears | `source` (`third_launch` or `subscribe_tap`), `launchCount` |
| `paywall_dismissed` | The user closes the paywall without paying | `source` |
| `purchase_completed` | The fake purchase is saved | `source`, `kitchenId` if there is one |

I picked these because the business is a ₹1 trial that becomes ₹249 a month, so the numbers that matter are the steps to payment: intent, paywall view, drop-off and purchase. With these four you can compare the third-launch paywall against the subscribe-tap paywall, and see which kitchens lead to a purchase. Each event is guarded so it fires once per real occurrence, not again on rotation.

## Task 2: fix what the agent wrote

The four problems:

1. **The list is not Compose state.** `items` is a plain list that is recreated on every recomposition, and adding to it does not trigger a redraw. The user sees an empty list even when the data arrives.
2. **Blocking network call on the main thread.** `URL(...).readText()` runs inside `LaunchedEffect`, which runs on the main thread. Android throws `NetworkOnMainThreadException` and the app crashes when the screen opens. This is the one-star review.
3. **A live secret key is hardcoded in the app and sent in the URL.** Anyone can read `sk_live_...` from the APK or from network logs and use it. This is the bill. The key must be revoked, and it should only ever live on our server.
4. **`LaunchedEffect(items)` uses the list as its key.** The list is a new object on each recomposition, so the effect can restart and fetch again, and it also refetches on every rotation. That means repeated requests, wasted data and battery, and more billable calls.

Also: there is no error handling and no loading or error state, and the `vm` parameter is never used. Offline, the user gets a crash or a blank screen.

Corrected version:

```kotlin
sealed interface KitchenListUiState {
    data object Loading : KitchenListUiState
    data object Empty : KitchenListUiState
    data class Error(val message: String) : KitchenListUiState
    data class Success(val kitchens: List<Kitchen>) : KitchenListUiState
}

// No secret in the app: the client calls our own backend,
// which holds the provider key server-side.
class KitchenRepository(private val kitchensUrl: String) {
    suspend fun getKitchens(): Result<List<Kitchen>> = withContext(Dispatchers.IO) {
        try {
            Result.success(parse(URL(kitchensUrl).readText()))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

class KitchenViewModel(private val repository: KitchenRepository) : ViewModel() {
    private val _state = MutableStateFlow<KitchenListUiState>(KitchenListUiState.Loading)
    val state: StateFlow<KitchenListUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = KitchenListUiState.Loading
        viewModelScope.launch {
            _state.value = repository.getKitchens().fold(
                onSuccess = { kitchens ->
                    if (kitchens.isEmpty()) KitchenListUiState.Empty
                    else KitchenListUiState.Success(kitchens)
                },
                onFailure = {
                    KitchenListUiState.Error("Couldn't load kitchens. Check your connection and try again.")
                }
            )
        }
    }
}

@Composable
fun KitchenList(vm: KitchenViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    when (val s = state) {
        KitchenListUiState.Loading -> CircularProgressIndicator()
        KitchenListUiState.Empty -> Text("No kitchens near you yet.")
        is KitchenListUiState.Error -> Column {
            Text(s.message)
            Button(onClick = vm::load) { Text("Retry") }
        }
        is KitchenListUiState.Success -> LazyColumn {
            items(s.kitchens, key = { it.id }) { KitchenRow(it) }
        }
    }
}
```

What changed: state lives in the ViewModel as a `StateFlow` (fixes 1 and survives rotation), the network call runs on `Dispatchers.IO` (fixes 2), the key is removed from the client (fixes 3), and loading happens once in the ViewModel instead of in an effect tied to redraws (fixes 4). The list screen in this app uses the same pattern.

## Task 3: release note

**What I would test by hand before publishing, and on which devices**

- First launch through third launch: the paywall opens by itself on launch 3 only.
- Subscribe tap, pay, and the status line shows the same date and time the paywall promised.
- After paying: force-stop and reopen, rotate, and kill the process from the background. No paywall anywhere, status still correct.
- Rotate on every screen, including the paywall while it is open.
- Empty and error states, and Retry.
- Large font size, dark mode, a 24-hour clock setting, and a non-English device language (the date text changes).

Devices: one Android 7 phone (API 24, the min SDK, small screen and low memory), one mid-range phone on Android 13 or 14 from Xiaomi, Realme or Samsung (their battery managers kill background apps aggressively, which is a real process-death test), and one phone on the newest Android version for edge-to-edge layout.

What I actually tested for this submission: a Genymotion emulator on Android 13 (API 33) only. I did not test on a physical phone.

**The one thing most likely to break in production, and how I would know**

The paid state. Everything depends on a locally stored flag and the device clock. If that read fails or returns the default, a paying user sees the paywall again, which is the worst bug this app can have. A user who changes the device clock can also move the trial end.

How I would find out without a user telling me:

- An alert on `paywall_shown` arriving from an install that has already sent `purchase_completed`. That count should be zero.
- An alert on the `paywall_shown` to `purchase_completed` rate dropping suddenly after a release.
- Crash reporting and Play Console vitals for crash and ANR rate on the new version.

This needs the Logcat implementation replaced by a real analytics backend behind the same interface.

**If it broke at 11pm, the rollback**

- Release with a staged rollout (5 to 10 percent first), so most users never get a bad build.
- Halt the rollout in Play Console. That stops new installs of the bad version.
- Play cannot pull a build back from users who already updated, so ship the last good code again with a higher version code as a hotfix.
- Stored data stays readable by the older code, because the saved keys were only added to, never renamed.
- For the next version I would put the paywall trigger behind a remote flag, so it can be switched off without a release.

**The next three things I would build, in order**

1. Real billing with server-side verification, including a way to cancel. The ₹1 to ₹249 change must be decided by the server, not by the device clock, and the paywall already promises "until you cancel".
2. Real analytics and crash reporting behind the existing `Analytics` interface, with a funnel dashboard and the alerts above.
3. A real kitchen API with location ("near you") and caching, plus automated tests for the launch-count and paywall rules.

## Task 4: how I used AI

**Tools and share.** Claude Code wrote about 95 percent of the code. I used Claude chat to plan the scope, draft the step-by-step prompts and review the agent's report after each step. I tested by hand on the emulator and edited the sample data myself. My Android background is Java and XML, and my recent work is Flutter, so this was my first Compose app. I gave the agent one small step at a time so I could read each piece.

**My best prompt, exactly as sent:**

```
Step 5: paywall.
- PaywallScreen with a PaywallViewModel. The copy must state in plain words: "₹1 will be charged now." and "₹249 will be charged on <date and time>, 24 hours from now, and then every month until you cancel." The date is now + 24 hours, formatted in the device's locale and time zone like "24 Nov 2026, 3:10 PM". Compute it once when the paywall opens and keep it in SavedStateHandle so it does not change on rotation.
- A primary button "Pay ₹1 and start trial" and a close button. System back also dismisses.
- Paying is fake: save paid = true and purchaseTimeMillis = now in SubscriptionStore, then go back. Guard against double taps.
- Show rules: (a) any Subscribe tap opens the paywall while paid is false; (b) on exactly the third launch (launchCount == 3), if not paid and autoPaywallShown is false, open the paywall automatically once and set autoPaywallShown = true. Never show the paywall when paid is true.
- When paid, the detail screen replaces the Subscribe button with a status line: within 24 hours of purchase, "Trial active. ₹249 will be charged on <date and time>."; after 24 hours, "Plan active since <date>."
- Everything must be correct after rotation, process death, and force-stop + reopen. Do not wait on DataStore with runBlocking; handle the brief loading moment properly so the paywall never flashes for a paid user.
```

**One thing the agent got wrong in my build.** The paywall and the trial status showed two different charge times. The paywall fixed its date when it opened, but the status line used purchase time plus 24 hours. A user who waited ten minutes before paying would be promised one time and then shown another. The code compiled and looked right. I caught it by reading the "things I wasn't sure about" list I made the agent write at the end of every step, where it mentioned the two times in passing. Since the paywall's whole job is to name the charge date, I treated it as a bug: the promised charge time is now saved in the same write as the payment, and the status line and the trial-to-active switch both read that value.

A second one: the agent reported the persistence step as done, but its own rotation check on the emulator had logged nothing before the emulator crashed. I did not accept "builds successfully" as verified, and ran the rotation, process-death and force-stop tests by hand.

**One thing I did by hand.** The sample data. The agent gave the Bengali kitchen a Hindi name and priced a tiffin with mutton and fish at ₹65, cheaper than every vegetarian kitchen. Spotting and fixing those two lines myself was faster than explaining them to the agent.
