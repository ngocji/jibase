# jibase — Android Library Reference

**jibase** là Android library (AAR) dùng chung cho các dự án Android của `ngocji`.

**Tech stack:** Kotlin · Coroutines/Flow · Hilt DI · ViewBinding · Navigation Component · Paging 3 · Glide · Gson  
**Min SDK:** 21 · **Target SDK:** 35 · **JVM:** 17 · **Kotlin:** 2.0.0

---

## Mục lục

1. [ResultWrapper — Bọc kết quả async](#1-resultwrapper)
2. [API Extensions — Gọi API an toàn](#2-api-extensions)
3. [ViewModel Extensions](#3-viewmodel-extensions)
4. [DataCacheManager — Cache Flow](#4-datacachemanager)
5. [LifecycleExt — Collect Flow trong Fragment/Activity](#5-lifecycleext)
6. [CoroutineExt — Chạy coroutine](#6-coroutineext)
7. [ViewBindingDelegate](#7-viewbindingdelegate)
8. [Navigation Extensions](#8-navigation-extensions)
9. [FragmentUtils — Quản lý Fragment thủ công](#9-fragmentutils)
10. [View Extensions](#10-view-extensions)
11. [Context Extensions](#11-context-extensions)
12. [Activity Extensions](#12-activity-extensions)
13. [Keyboard Extensions](#13-keyboard-extensions)
14. [AnimateKeyboardHelper](#14-animatekeyboardhelper)
15. [Image Extensions (Glide)](#15-image-extensions-glide)
16. [LiveData Extensions](#16-livedata-extensions)
17. [TimerExt — Đồng hồ đếm](#17-timerext)
18. [RectExt — Geometry helpers](#18-rectext)
19. [Exts — Tiện ích chung](#19-exts)
20. [SharePref — SharedPreferences wrapper](#20-sharepref)
21. [SessionHelper — In-memory cache](#21-sessionhelper)
22. [GsonManager — JSON serialize/deserialize](#22-gsonmanager)
23. [EdittextDebounceExt — Debounce search](#23-edittextdebounceext)
24. [Log — Logging](#24-log)
25. [PermissionsHelper — Runtime permission](#25-permissionshelper)
26. [BaseDialog / BaseBottomDialog](#26-basedialog--basebottomdialog)
27. [ConfirmDialog — Builder pattern](#27-confirmdialog)
28. [StateNetworkView](#28-statenetworkview)
29. [OptionItemView](#29-optionitemview)
30. [ISnackBar — Builder](#30-isnackbar)
31. [SimpleViewPagerAdapter](#31-simpleviewpageradapter)
32. [BitmapUtils](#32-bitmaputils)
33. [MediaStoreHelper](#33-mediastorehelper)
34. [DownloadImageHelper](#34-downloadimagehelper)
35. [FileUtils](#35-fileutils)
36. [FileBackupHelper](#36-filebackuphelper)
37. [AssetsUtils](#37-assetsutils)
38. [UriUtils](#38-uriutils)
39. [IntentUtils](#39-intentutils)
40. [HighlightUtils](#40-highlightutils)
41. [MathUtils](#41-mathutils)
42. [Utils](#42-utils)
43. [CenterItemRecyclerViewUtils](#43-centeritemrecyclerviewutils)
44. [Data Retriever Pattern](#44-data-retriever-pattern)
45. [Hilt DI](#45-hilt-di)
46. [Common Patterns — ViewModel & Fragment chuẩn](#46-common-patterns)
47. [File Quick Reference](#47-file-quick-reference)

---

## 1. ResultWrapper

**File:** `com.jibase.flow.ResultWrapper`

Sealed class bọc tất cả kết quả async.

```kotlin
sealed class ResultWrapper<out T> {
    data class Success<T>(val value: T) : ResultWrapper<T>()
    data object Empty   : ResultWrapper<Nothing>()
    data class Error(val throwable: Throwable? = null) : ResultWrapper<Nothing>()
    data object None    : ResultWrapper<Nothing>()
    data object Loading : ResultWrapper<Nothing>()
}
```

### Methods trên instance

```kotlin
// Lấy value, throw nếu không phải Success (throw Error.throwable hoặc Throwable mặc định)
wrapper.takeValueOrThrow(): T

// Lấy value an toàn, trả null nếu không phải Success
wrapper.safeTakeValue(): T?
```

### Extension functions

**File:** `com.jibase.flow.ApiExtensions`

```kotlin
// Side-effect khi Success, bỏ qua các state khác, trả lại chính wrapper
wrapper.applyWhenSuccess { value -> doSomething(value) }

// Transform value khi Success, giữ nguyên Error/Loading/None
val mapped: ResultWrapper<R> = wrapper.mapWhenSuccess { value -> transform(value) }
```

---

## 2. API Extensions

**File:** `com.jibase.flow.ApiExtensions`

### `safeFlowCall` — Gọi suspend, trả null khi lỗi

```kotlin
// Phiên bản cơ bản: trả null khi exception
val result: T? = safeFlowCall { api.getData() }

// Phiên bản với fallback khi lỗi
val result: T? = safeFlowCall(
    error = { cachedData },     // suspend lambda, gọi khi exception
    action = { api.getData() }
)
```

Chạy trên `Dispatchers.IO`. Log lỗi tự động.

### `safeApiCall` — Gọi suspend, trả ResultWrapper

```kotlin
// Mặc định: lỗi → ResultWrapper.Error(throwable)
val result: ResultWrapper<T> = safeApiCall { api.getData() }

// Custom error handler
val result = safeApiCall(
    error = { throwable -> ResultWrapper.Error(throwable) }
) { api.getData() }
```

Chạy trên `Dispatchers.IO`. Kết quả: `Success(data)` hoặc `Error(throwable)`.

### `safeFlow` — Wrap Flow thành Flow<ResultWrapper>

```kotlin
val wrappedFlow: Flow<ResultWrapper<T>> = safeFlow(roomDao.observeAll())
// emit Success(item) cho mỗi item, catch → emit Error(throwable)
// Dùng mapLatest nên chỉ lấy emission cuối nếu upstream nhanh
```

---

## 3. ViewModel Extensions

**File:** `com.jibase.extensions.ViewModelExt`

### `resultApiCall` — Tự động emit Loading → Success/Error

```kotlin
// Trong ViewModel:
fun fetchData(): Flow<ResultWrapper<MyData>> = resultApiCall {
    safeApiCall { api.getData() }
}
// Flow phát ra: Loading, sau đó Success(data) hoặc Error(throwable)
// catch exception → emit Error
```

### `resultFlow` — Wrap Flow với Loading đầu

```kotlin
fun observeData(): Flow<ResultWrapper<List<Item>>> = resultFlow {
    roomDao.observeAll().map { ResultWrapper.Success(it) }
}
// Phát Loading ngay lập tức, sau đó flatMapLatest sang flow của flowCreation
```

---

## 4. DataCacheManager

**File:** `com.jibase.flow.cache.DataCacheManager`

Cache StateFlow theo key, tránh tạo nhiều collector trùng lặp.

```kotlin
// Khởi tạo — thường trong ViewModel
private val cacheManager = DataCacheManager(viewModelScope)
```

### `getFlow` — Cache flow không có params

```kotlin
fun observeItems(): Flow<List<Item>> = cacheManager.getFlow(
    cacheKey = "items",
    initialValue = emptyList(),
    context = Dispatchers.IO,   // mặc định IO
    forceRefresh = false        // nếu true: tạo lại collector dù đã có cache
) {
    roomDao.observeAll()
}
// Trả Flow<T>. Nếu đã có cache và !forceRefresh: trả lại StateFlow cũ (không tạo Job mới)
// Nếu forceRefresh: cancel Job cũ, tạo Job mới
```

### `getData` — Cache flow có params

```kotlin
fun getUserData(userId: String): StateFlow<User?> = cacheManager.getData(
    cacheKey = "user_$userId",
    params = userId,
    initialValue = null,
    context = Dispatchers.IO,
    forceRefresh = false
) { id ->
    userRepository.observeUser(id)
}
// Trả StateFlow<T>. Re-collect khi params thay đổi hoặc forceRefresh = true
```

### Xóa cache

```kotlin
cacheManager.clearCache("items")    // xóa 1 key + cancel job
cacheManager.clearAllCache()        // xóa tất cả
```

---

## 5. LifecycleExt

**File:** `com.jibase.extensions.LifecycleExt`

### Fragment

```kotlin
// Collect StateFlow theo lifecycle STARTED — dùng collectLatest (drop emission cũ)
collect(viewModel.stateFlow) { data -> updateUI(data) }

// Collect tất cả emissions, không drop — dùng collect thông thường
// Dùng cho one-shot events (Channel / SharedFlow)
collectOne(viewModel.eventFlow) { event -> handleEvent(event) }

// Chỉ collect khi value != null
collectNotNull(viewModel.nullableFlow) { data -> bind(data) }

// Collect khi lifecycle ở RESUMED (dùng collectLatest)
collectWhenResume(viewModel.flow) { }

// Collect Channel (lặp for..in)
collect(channel) { event -> }

// Chạy flow nhưng không cần nhận value (trigger side effect)
run(viewModel.triggerFlow)
```

### Activity (FragmentActivity)

```kotlin
// Tương tự Fragment nhưng dùng lifecycle của Activity
activity.collect(flow) { }
activity.collectOne(flow) { }
activity.collectNotNull(flow) { }
activity.collectWhenResume(flow) { }
activity.collect(channel) { }
activity.run(flow)
```

**Quy tắc:** `collect` cho UI state (StateFlow), `collectOne` cho one-shot events.

---

## 6. CoroutineExt

**File:** `com.jibase.extensions.CoroutineExt`

### `runAsync` — Chạy song song nhiều task, await all

```kotlin
// Trên Fragment / Activity / ViewModel / CoroutineScope
runAsync(
    context = Dispatchers.IO,   // mặc định IO
    onComplete = { isCompleted ->
        // callback trên coroutine context (không tự switch Main)
        // isCompleted = true nếu tất cả thành công, false nếu có exception
    }
) {
    /* task 1 */
},
{
    /* task 2 */
}
```

Dùng `async + awaitAll`. Nếu bất kỳ task nào throw, `isCompleted = false` nhưng callback vẫn được gọi (trong `finally`).

### `runCoroutine` — Chạy đơn một coroutine

```kotlin
runCoroutine(context = Dispatchers.IO) {
    loadData()
}

// Trên ViewModel (dùng viewModelScope)
viewModel.runCoroutine { fetchRemote() }

// Trên Fragment (dùng viewLifecycleOwner.lifecycleScope)
fragment.runCoroutine { processImage() }
```

---

## 7. ViewBindingDelegate

**File:** `com.jibase.extensions.ViewBindingDelegate`

### Fragment — delegate tự clear khi view destroyed

```kotlin
class MyFragment : Fragment(R.layout.my_fragment) {
    private val binding by viewBinding(MyFragmentBinding::bind)
    // binding tự null khi ON_DESTROY, throw khi truy cập sau destroy
}
```

### Activity — lazy delegate

```kotlin
class MyActivity : AppCompatActivity() {
    private val binding by viewBinding(MyActivityBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
    }
}
```

### ViewGroup — inflate trực tiếp

```kotlin
// Trong custom view / adapter
val binding: MyViewBinding = viewGroup.viewBinding(
    MyViewBinding::inflate,
    isConnectedToParent = false   // mặc định false
)
```

### Context — inflate từ context

```kotlin
val binding: MyViewBinding = context.viewBinding(MyViewBinding::inflate)
```

---

## 8. Navigation Extensions

### NavController extensions

**File:** `com.jibase.extensions.NavControllerExtension`

```kotlin
// Navigate an toàn — không crash khi gọi sai state, trả false nếu lỗi
navController.safeNavigate(R.id.action_to_detail)           // Boolean
navController.safeNavigate(directions)                       // NavDirections
navController.safeNavigate(R.id.action, bundle)              // với Bundle
navController.safeNavigate(R.id.action, bundle, navOptions)  // với NavOptions
navController.safeNavigate(uri)                              // DeepLink Uri
navController.safeNavigate(uri, navOptions)                  // DeepLink với options

// Kiểm tra back stack
navController.hasBackStack(destinationId)       // Boolean — có entry này trong stack không
navController.isDestinationVisible(destinationId) // Boolean — đang ở destination này không

// Lấy NavController theo view ID
fragment.findNavController(R.id.nav_host_fragment)

// Lấy NavController của fragment cha
fragment.findParentNavController(step = 1)  // step = số cấp cha cần leo lên
```

### Fragment navigation helpers

**File:** `com.jibase.extensions.FragmentNavExtensions`

```kotlin
// Pop back stack
fragment.navigateUp()                                   // navigateUp()
fragment.popBack()                                      // popBackStack()
fragment.popBackTo(R.id.destination, inclusive = false) // popBackStack(id, inclusive)

// Override nút back
fragment.onBackPressedOverride { /* xử lý */ }
activity.onBackPressedOverride { /* xử lý */ }

// Lifecycle hooks
fragment.observeOnDestroy { cleanup() }
fragment.observeOnResume { refresh() }
fragment.observeOnPause { pause() }

// Truyền kết quả giữa fragments qua SavedStateHandle
// Gửi về previous fragment
fragment.setNavigationResult(data, key = "result")
fragment.setPreviousNavigationResult(key = "result", result = data)

// Gửi về destination cụ thể (ví dụ: dialog gửi về parent)
fragment.setNavigationResult(data, destinationId = R.id.parentFragment, key = "result")
fragment.setCurrentNavigationResult(key = "result", result = data)

// Nhận (one-shot, tự xóa sau khi nhận)
observeNavigationResultOnce<MyData>(key = "result") { data -> }
observeDialogNavigationResultOnce<MyData>(destinationId = R.id.parent) { data -> }

// Đọc LiveData thủ công (không one-shot)
val liveData: LiveData<T>? = fragment.getNavigationResult<T>(key = "result")
val liveData: LiveData<T>? = fragment.getPreviousNavigationResult<T>(key = "result")
val liveData: LiveData<T>? = fragment.getCurrentNavigationResult<T>(key = "result")

// Xóa thủ công
fragment.removeNavigationResult<T>(key = "result")
fragment.removePreviousNavigationResult<T>(key = "result")
fragment.removeCurrentNavigationResult<T>(key = "result")

// Lấy Fragment hiện tại trong NavHost (từ Activity)
activity.getFragment(MyFragment::class.java): MyFragment?
```

---

## 9. FragmentUtils

**File:** `com.jibase.utils.FragmentUtils`

Quản lý Fragment **không dùng Navigation Component** (back stack thủ công).

```kotlin
// Add fragment (hide fragment trước đó, thêm vào stack)
FragmentUtils.add(
    FragmentUtils.ReplaceOption.with(activity)    // hoặc fragment (dùng childFragmentManager)
        .setContainerId(R.id.container)
        .setFragment(MyFragment())
        .addToBackStack(true)                     // mặc định true
        .setPopWhenExists(false)                  // nếu true: pop về fragment nếu đã tồn tại
        .setCommitNow(false)                      // mặc định false
        .setAnimation(
            enter = R.anim.slide_in_right,
            exit = R.anim.slide_out_left,
            popEnter = R.anim.slide_in_left,
            popExit = R.anim.slide_out_right
        )
)

// Replace fragment
FragmentUtils.replace(option)

// Pop back stack
FragmentUtils.popBack(activity)
FragmentUtils.popBackTo(activity, tagName, inclusive = false)

// Queries
FragmentUtils.getCurrentFragment(activity): Fragment?
FragmentUtils.getPreviousFragment(activity): Fragment?
FragmentUtils.getFragmentByTag(activity, tagName): Fragment?
FragmentUtils.isFirstFragment(activity): Boolean
FragmentUtils.getCountOfBackStack(activity): Int

// Xử lý back press tự động
FragmentUtils.handleBackPress(activity) { stackCount ->
    // return true để tự xử lý, false để dùng logic mặc định (popBack hoặc finish)
    false
}
```

> `target` có thể là `FragmentActivity`, `Fragment`, hoặc `FragmentManager`.

---

## 10. View Extensions

**File:** `com.jibase.extensions.ViewExtensions`

```kotlin
view.visible()                    // VISIBLE
view.gone()                       // GONE
view.invisible()                  // INVISIBLE
view.visible(isUserAnimation = true)    // VISIBLE với delayed transition animation
view.gone(isUserAnimation = true)       // GONE với transition
view.invisible(isUserAnimation = true)  // INVISIBLE với transition

view.makeTransition(trans = null) // beginDelayedTransition trên rootView (trans = null = AutoTransition)

view.isState(View.VISIBLE)        // Boolean

view.toBitmap(): Bitmap           // render view thành Bitmap

view.changeElevation(4f)
view.getCurrentElevation(): Float
view.changeBackground(drawable)

// Đo từ dimen resource
view.getDimen(R.dimen.size): Float
view.getDimensionPixelOffset(R.dimen.size): Int

// Thay đổi layoutParams — chỉ truyền param cần thay, dùng Int.MAX_VALUE để bỏ qua
view.setLayoutParams(width = 200)                         // chỉ đổi width
view.setLayoutParams(height = ViewGroup.LayoutParams.WRAP_CONTENT) // chỉ đổi height
view.setLayoutParams(width = 200, height = 300)
```

---

## 11. Context Extensions

**File:** `com.jibase.extensions.ContextExtensions`

```kotlin
context.isOnline(): Boolean       // kiểm tra kết nối mạng (cần ACCESS_NETWORK_STATE)

context.checkAppInstalled("com.package.name"): Boolean

// Đổi đơn vị
context.spToPx(14f): Int
context.dpToPx(16): Int
context.pxToDp(48): Int

// Màu sắc
context.getColorByAttr(R.attr.colorPrimary): Int  // từ theme attribute
context.getColor(R.color.primary): Int            // từ color resource

// Dimen
context.getDimen(R.dimen.size): Float
context.getDimensionPixelOffset(R.dimen.size): Int

// Toast
context.showToast("message")             // LENGTH_SHORT
context.showToast(R.string.message)
context.showLongToast("message")         // LENGTH_LONG
context.showLongToast(R.string.message)
```

---

## 12. Activity Extensions

**File:** `com.jibase.extensions.ActivityExt`

```kotlin
// Điều hướng sang Activity khác
activity.navigate<TargetActivity>()
activity.navigate<TargetActivity>(bundle = Bundle().apply { putString("key", "value") })

// Điều hướng lấy kết quả
activity.navigateResult<TargetActivity>(launcher)
activity.navigateResult<TargetActivity>(launcher, bundle)
```

---

## 13. Keyboard Extensions

**File:** `com.jibase.extensions.KeyboardExtensions`

```kotlin
// EditText
editText.showKeyboard()   // requestFocus + showSoftInput
editText.hideKeyboard()   // hideSoftInputFromWindow

// Fragment
fragment.hideKeyboard()   // ẩn keyboard từ currentFocus

// Activity
activity.hideKeyboard()   // ẩn keyboard từ currentFocus
```

**Object helper** — `com.jibase.helper.KeyboardHelper`

```kotlin
KeyboardHelper.showKeyboard(view)           // showSoftInput trên view
KeyboardHelper.hideKeyboard(view)           // view
KeyboardHelper.hideKeyboard(fragment)       // fragment.activity.currentFocus
KeyboardHelper.hideKeyboard(activity)       // activity.currentFocus
```

---

## 14. AnimateKeyboardHelper

**File:** `com.jibase.helper.AnimateKeyboardHelper`

Animate view theo keyboard IME animation (API 30+ dùng WindowInsetsAnimation, cũ hơn fallback).

```kotlin
val keyboardHelper = AnimateKeyboardHelper(
    activity = this,
    rootView = binding.root,
    subFeatureContent = binding.panelContainer,   // view cần ẩn/hiện theo keyboard
    needUpdateSubFeatureContentSizeByKeyboard = false, // nếu true: resize subFeatureContent = keyboardHeight
    binding.inputToolbar   // các view animate theo keyboard
)

keyboardHelper.setCallback(object : AnimateKeyboardHelper.Callback {
    override fun onInsetsChanged(insets: WindowInsetsCompat) {}
    override fun onChangeKeyboardSize(keyboardHeight: Int) {}
    override fun onStartAnimate(pendingAnimation: Boolean, showKeyboard: Boolean) {}
    override fun onEndAnimate(showKeyboard: Boolean) {}
})

// Hiện/ẩn keyboard
keyboardHelper.showKeyboard(editText, pendingAnimation = false)
keyboardHelper.hideKeyboard(pendingAnimation = false)

// Kiểm tra trạng thái
keyboardHelper.isKeyboardShowed: Boolean
keyboardHelper.getLastWindowInsets(): WindowInsetsCompat?
```

**Lưu ý:** Cần gọi trước `setContentView` hoặc đảm bảo `WindowCompat.setDecorFitsSystemWindows(window, false)` đã được set.

---

## 15. Image Extensions (Glide)

**File:** `com.jibase.extensions.ImageExtensions`

```kotlin
// Load URL/File/Uri/resource ID
imageView.load(url)
imageView.load(url, placeHolder = R.drawable.placeholder, error = R.drawable.error)

// Load + transform bitmap trước khi set
imageView.load(url, placeHolder = R.drawable.ph, error = R.drawable.err) { bitmap ->
    // transform bitmap, trả về Bitmap mới
    rotateBitmap(bitmap, 90f)
}

// Load với RequestListener (Glide)
imageView.load(url, listener = object : RequestListener<Drawable> { ... })

// Load với RequestOptions tùy chỉnh
imageView.load(url, requestOptions = RequestOptions().circleCrop())
```

---

## 16. LiveData Extensions

**File:** `com.jibase.extensions.LiveDataExtensions`

```kotlin
// Fragment
fragment.observe(viewModel.liveData) { value -> }
fragment.onceObserve(viewModel.liveData) { value ->
    // tự removeObserver sau lần đầu tiên nhận giá trị
}

// Activity
activity.observe(viewModel.liveData) { value -> }

// Set giá trị ban đầu cho MutableLiveData
val liveData = MutableLiveData<String>().default("initial")
```

---

## 17. TimerExt

**File:** `com.jibase.extensions.TimerExt`

```kotlin
// Tạo Flow timer
val timerFlow = timer(
    period = 1000L,         // interval (ms)
    max = 60_000L,          // dừng sau bao lâu, -1 = chạy mãi (mặc định -1)
    initialDelay = 0L       // delay trước lần đầu (mặc định 0)
) { total, isFinished ->
    // callback mỗi tick, chạy trong coroutine context của collector
    // total = tổng thời gian đã trôi qua (ms)
    // isFinished = true khi total >= max
    updateCountdown(total)
}

// Sử dụng
timerFlow.launchIn(lifecycleScope)
// hoặc
lifecycleScope.launch { timerFlow.collect { /* emit Unit mỗi tick */ } }
```

---

## 18. RectExt

**File:** `com.jibase.extensions.RectExt`

```kotlin
// Tạo RectF từ Int
val rectF: RectF = makeRectF(left = 0, top = 0, right = 100, bottom = 200)

// Tạo Rect từ Float
val rect: Rect = makeRect(left = 0f, top = 0f, right = 100f, bottom = 200f)

// Chuyển đổi
val rect: Rect = rectF.toRect()

// Tính bounding rect của danh sách điểm
val points: List<PointF> = listOf(PointF(0f, 0f), PointF(100f, 50f))
val bounds: RectF = points.getBoundRect()
```

---

## 19. Exts

**File:** `com.jibase.extensions.Exts`

```kotlin
// Kiểm tra index an toàn
list hasPosition 5   // infix — true nếu 5 in list.indices

// Format duration từ milliseconds
1000L.formatMinSecDuration()    // "00:01"
61_000L.formatMinSecDuration()  // "01:01"
3_661_000L.formatFullDuration() // "01:01:01"

// Format kích thước file
1024L.formatSize()      // "1.00 KB"
1_048_576L.formatSize() // "1.00 MB"
1.5.formatSize()        // "1.50 B" (Double version)
```

---

## 20. SharePref

**File:** `com.jibase.pref.SharePref`

**Lưu ý quan trọng:** Tất cả primitive đều lưu dưới dạng String để tránh ClassCastException khi đổi type.

```kotlin
val pref = SharePref(context, "pref_name")
// Hoặc inject qua Hilt (xem phần Hilt DI)
```

### Đọc

```kotlin
pref.getString("key", "default"): String
pref.getInt("key", 0): Int
pref.getLong("key", 0L): Long
pref.getFloat("key", 0f): Float
pref.getDouble("key", 0.0): Double
pref.getBoolean("key", false): Boolean

// Object — deserialize từ JSON
pref.getObject<MyClass>("key", MyClass::class.java): MyClass?
pref.getObject<List<MyClass>>("key", getTypeToken<List<MyClass>>()): List<MyClass>?

// Kiểm tra tồn tại
pref.contains("key"): Boolean
```

### Ghi

```kotlin
pref.putString("key", "value")
pref.putInt("key", 42)
pref.putLong("key", 100L)
pref.putFloat("key", 1.5f)
pref.putDouble("key", 3.14)
pref.putBoolean("key", true)

// Object — serialize sang JSON
pref.putObject("key", myObject)    // null → xóa key

// Generic — tự detect type (Boolean/Int/Long/Float/String/Double → String, else → JSON)
pref.put("key", value)
```

### Xóa & Listen

```kotlin
pref.remove("key1", "key2")    // vararg

pref.registerChange(listener)   // OnSharedPreferenceChangeListener
pref.unregisterChange(listener)
```

---

## 21. SessionHelper

**File:** `com.jibase.helper.SessionHelper`

In-memory cache, **mất khi process bị kill**.

```kotlin
SessionHelper.put("key", data)                        // lưu bất kỳ Any

val data: MyType? = SessionHelper.get<MyType>("key")  // null nếu không tồn tại

// Không null — ném NullPointerException nếu null
val data: MyType = SessionHelper.getNotNull<MyType>("key")

// Không null — fallback nếu null (tự set default vào map)
val data: MyType = SessionHelper.getNotNull("key", defaultValue)

SessionHelper.clear("key")    // xóa 1 key
SessionHelper.clear()         // xóa tất cả

SessionHelper.containKey("key"): Boolean
```

---

## 22. GsonManager

**File:** `com.jibase.helper.GsonManager`

```kotlin
// Serialize
val json: String = GsonManager.toJson(myObject)
val json: String = GsonManager.toJson(myObject, "custom_gson_key")

// Deserialize — inline (tự infer type)
val obj: MyClass? = GsonManager.fromJson<MyClass>(json)

// Deserialize — tường minh type
val obj: MyClass? = GsonManager.fromJson(json, MyClass::class.java)
val list: List<MyClass>? = GsonManager.fromJson(json, getTypeToken<List<MyClass>>())

// Deserialize với custom Gson
val obj: MyClass? = GsonManager.fromJson(json, getTypeToken<MyClass>(), "custom_key")

// Đăng ký Gson instance tùy chỉnh
GsonManager.createGson("custom_key", myGson)
```

### Extension functions (top-level)

```kotlin
// inline — tự infer generic
val obj: MyClass? = fromJson<MyClass>(json)
val list: List<MyClass>? = fromJson<List<MyClass>>(json)

// Lấy Type token (dùng cho generics)
val type: Type = getTypeToken<List<MyClass>>()
```

---

## 23. EdittextDebounceExt

**File:** `com.jibase.helper.EdittextDebounceExt`

```kotlin
// EditText — emit ngay giá trị hiện tại khi subscribe (onStart)
editText.toTextChangeFlow()
    .debounce(300)
    .onEach { text -> search(text.toString()) }
    .launchIn(lifecycleScope)

// SearchView — emit query hiện tại khi subscribe
searchView.toTextChangeFlow()
    .debounce(300)
    .onEach { query -> filter(query.toString()) }
    .launchIn(lifecycleScope)
```

---

## 24. Log

**File:** `com.jibase.utils.Log`

```kotlin
// Setup (thường trong Application.onCreate)
Log.setup(prefix = "MY_APP", isEnable = BuildConfig.DEBUG)

// Mặc định: isEnable = BuildConfig.DEBUG của jibase library

Log.d("message")                    // DEBUG
Log.d("message", "CUSTOM_TAG")      // DEBUG với custom tag
Log.e("message")                    // ERROR
Log.e("message", "CUSTOM_TAG")      // ERROR với custom tag
```

Output format:
```
********************************
[VERSION] Class: ClassName (methodName : lineNumber)
message
********************************
```

Log chỉ ghi khi `isEnable = true`. Tự động thêm class name, method name, line number.

---

## 25. PermissionsHelper

**File:** `com.jibase.permission.PermissionsHelper`

```kotlin
PermissionsHelper.with(fragment)   // hoặc with(activity: FragmentActivity)
    .request(Manifest.permission.CAMERA, Manifest.permission.READ_MEDIA_IMAGES)
    .onGrant { permissions: List<Permission> ->
        // tất cả permission được cấp
    }
    .onDeny { permissions: List<Permission> ->
        // bị từ chối (shouldShowRequestPermissionRationale = true)
    }
    .onRevoke { permissions: List<Permission> ->
        // bị revoke bởi device policy
    }
    .execute()
```

### Kiểm tra trước khi request

```kotlin
val helper = PermissionsHelper.with(fragment)
helper.isGranted(Manifest.permission.CAMERA): Boolean   // true nếu đã có
helper.isRevoked(Manifest.permission.CAMERA): Boolean   // true nếu revoked bởi policy
helper.isRuntimeRequestPermission(): Boolean            // true nếu SDK >= 23
```

### Permission data class

```kotlin
data class Permission(
    val name: String,           // tên permission (Manifest.permission.*)
    val granted: Boolean,
    val shouldShowRequestPermissionRationale: Boolean
)
```

**Lưu ý:** `PermissionFragment` được tự động add/remove vào FragmentManager trong quá trình request.

---

## 26. BaseDialog / BaseBottomDialog

**File:** `com.jibase.ui.dialog.BaseDialog` / `BaseBottomDialog`

### BaseDialog (DialogFragment)

```kotlin
class MyDialog : BaseDialog(
    layoutId = R.layout.dialog_my,
    theme = 0    // optional, style theme cho ContextThemeWrapper
) {
    override fun onViewReady(savedInstanceState: Bundle?) {
        // binding available, setup UI
    }

    override fun initStyle(): Int = R.style.style_dialog_90    // mặc định
    override fun isShowFullDialog(): Boolean = false           // MATCH_PARENT nếu true
    override fun doOnWindow(window: Window) {}                 // customize Window
}

// Show an toàn — không crash, không show 2 lần
val showed: Boolean = myDialog.show(parentFragmentManager)
```

### BaseBottomDialog (BottomSheetDialogFragment)

```kotlin
class MyBottomDialog : BaseBottomDialog(R.layout.dialog_my) {
    override fun onViewReady(savedInstanceState: Bundle?) {}

    override fun initStyle(): Int = R.style.style_transparent_bottom_sheet  // mặc định
    override fun isShowFullDialog(): Boolean = false  // STATE_EXPANDED nếu true
    override fun isDraggable(): Boolean = true        // BottomSheetBehavior.isDraggable
    override fun doOnWindow(window: Window) {}
    override fun doOnSheetBehavior(behavior: BottomSheetBehavior<View>) {
        super.doOnSheetBehavior(behavior)   // gọi super để apply isShowFullDialog + isDraggable
    }
}

val showed: Boolean = myBottomDialog.show(parentFragmentManager)
```

---

## 27. ConfirmDialog

**File:** `com.jibase.ui.confirmdialog.ConfirmDialog`

Builder pattern. Default style: `style_dialog_80`.

```kotlin
ConfirmDialog.newBuilder(context, R.style.AppTheme)
    // === Dialog config ===
    .setCancelable(false)                          // mặc định: true
    .setDismissWhenClick(true)                     // mặc định: true — dismiss khi nhấn nút
    .setBackground(R.color.background)             // @ColorRes
    .setBackground(drawable)                       // Drawable
    .setBackgroundColor(Color.WHITE)               // @ColorInt
    .setBackgroundResource(R.drawable.bg)          // @DrawableRes

    // === Icon ===
    .setIcon(R.drawable.ic_warning)                // @DrawableRes
    .setIcon(bitmap)                               // Bitmap
    .setIconSize(96)                               // px
    .setIconGravity(Gravity.CENTER)
    .setIconTintColor(Color.RED)                   // @ColorInt
    .setIconTintRes(R.color.red)                   // @ColorRes
    .setCloseIcon(R.drawable.ic_close)             // nút close (X) ở góc

    // === Texts ===
    .setTitle("Xác nhận")
    .setTitle(R.string.confirm)
    .setTitleColor(Color.BLACK)
    .setTitleColorRes(R.color.text_primary)
    .setTitleStyle(R.style.TextStyle)
    .setTitleGravity(Gravity.CENTER)

    .setSubTitle("Subtitle")                       // subtitle (mặc định ẩn)
    .setSubTitleColor(...)
    // setSubTitle*, setSubTitleColor*, setSubTitleStyle*, setSubTitleGravity* tương tự title

    .setMessage("Bạn có chắc không?")
    .setMessageColor(Color.GRAY)
    // setMessage*, setMessageColor*, setMessageStyle*, setMessageGravity* tương tự

    // === Confirm button ===
    .setConfirmText("Đồng ý")
    .setConfirmText(R.string.ok)
    .setConfirmTextColor(Color.WHITE)
    .setConfirmBackground(R.color.blue)            // @ColorRes
    .setConfirmBackgroundColor(Color.BLUE)         // @ColorInt
    .setConfirmBackgroundResource(R.drawable.btn)  // @DrawableRes
    .setConfirmBackground(drawable)
    .setConfirmIcon(R.drawable.ic_check)
    .setConfirmIconTintColor(Color.WHITE)
    .setConfirmTextStyle(R.style.ButtonText)
    .setConfirmTextAllCaps(false)
    .setShowConfirmButton(true)                    // mặc định: true

    // === Cancel button ===
    .setCancelText("Hủy")
    // setCancelText*, setCancelTextColor*, setCancelBackground*, setCancelIcon* tương tự confirm

    // === Callback ===
    .setCallBack(object : ConfirmDialog.CallBack {
        override fun onConfirmClicked(dialog: ConfirmDialog?) {}
        override fun onCancelClicked(dialog: ConfirmDialog) {}
        override fun onDismiss() {}                // chỉ gọi khi dismiss MÀ KHÔNG nhấn confirm
        override fun doOnWindow(window: Window) {}
    })

    .setDoOnWindowCallback { window -> }           // callback Window

    .build()
    .show(parentFragmentManager)
```

---

## 28. StateNetworkView

**File:** `com.jibase.view.StateNetworkView`

Custom view hiển thị loading / error / empty / content.

### XML

```xml
<com.jibase.view.StateNetworkView
    android:id="@+id/stateView"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:state_enable_pull_to_refresh="true"
    app:state_empty_text="Không có dữ liệu"
    app:state_empty_icon="@drawable/ic_empty"
    app:state_progress_layout="@layout/my_loading"   <!-- thay layout loading mặc định -->
    app:state_error_layout="@layout/my_error"
    app:state_empty_layout="@layout/my_empty" />

<!-- Views content đặt bên trong StateNetworkView -->
<RecyclerView ... />
```

### Kotlin

```kotlin
// Chuyển trạng thái
stateView.updateUI(StateNetworkView.NetworkType.LOADING)
stateView.updateUI(StateNetworkView.NetworkType.SUCCESS)
stateView.updateUI(StateNetworkView.NetworkType.ERROR)
stateView.updateUI(StateNetworkView.NetworkType.EMPTY)

// Callback khi retry / swipe-refresh
stateView.setCallBack { networkType: NetworkType ->
    // networkType = trạng thái trước đó khi swipe, hoặc ERROR/EMPTY khi nhấn retry button
    loadData()
}

// Thay thế layout cho một state
stateView.replaceView(NetworkType.ERROR, R.layout.custom_error)
stateView.replaceView(NetworkType.LOADING, R.layout.custom_loading)

// Thêm custom tag (state tùy chỉnh)
stateView.addTag("no_internet", R.layout.layout_no_internet)
stateView.updateUI("no_internet")   // chuỗi bất kỳ làm tag

// Trạng thái hiện tại
stateView.currentState: NetworkType
```

**Lưu ý retry button:** View trong layout error/empty cần có `android:tag="@string/tag_button"` để được auto-wired.

---

## 29. OptionItemView

**File:** `com.jibase.view.OptionItemView`

Custom view cho settings item với icon, title, description và end widget.

### XML attrs

```xml
<com.jibase.view.OptionItemView
    app:ot_title="Thông báo"
    app:ot_title_color="@color/text_primary"
    app:ot_title_text_appearance="@style/TextBody"
    app:ot_desc="Bật/tắt thông báo push"
    app:ot_desc_color="@color/text_secondary"
    app:ot_icon="@drawable/ic_notify"
    app:ot_icon_size="24dp"
    app:ot_icon_gravity="0"        <!-- 0=top, 1=center, 2=center_ll -->
    app:ot_icon_tint="@color/icon_tint"
    app:ot_end_icon="@drawable/ic_arrow"
    app:ot_end_icon_size="16dp"
    app:ot_end_icon_tint="@color/icon"
    app:ot_switch_button="@drawable/custom_switch"
    app:ot_type="switch"           <!-- none | switch | text | image -->
    app:ot_show_line="true"
    app:ot_line_margin="16dp"
    app:ot_line_fill_width="false"
    app:ot_line_color="@color/divider" />
```

### Kotlin

```kotlin
optionItem.setTitle("Title")
optionItem.setDesc("Description")
optionItem.setIcon(R.drawable.ic_icon)
optionItem.setIconSize(48)              // px
optionItem.setIconTint(Color.BLUE)
optionItem.setIconTint(colorStateList)
optionItem.setEndIcon(R.drawable.ic_arrow)
optionItem.setEndIconSize(32)
optionItem.setEndIconTint(Color.GRAY)

optionItem.setType(OptionItemView.Type.SWITCH)  // NONE | SWITCH | TEXT | IMAGE
optionItem.setChecked(true)
optionItem.isChecked(): Boolean
optionItem.setSwitchEnable(false)
optionItem.setOnCheckedChanged { buttonView, isChecked -> }

optionItem.setTextData("End text")    // khi type = TEXT
optionItem.setImageData(R.drawable.img) // khi type = IMAGE

optionItem.updateLineUI(
    visible = true,
    margin = 16,    // px, margin left của divider
    fillWidth = false,
    tintColor = Color.LTGRAY
)
```

---

## 30. ISnackBar

**File:** `com.jibase.view.ISnackBar`

```kotlin
ISnackBar()
    .of(rootView)                          // bắt buộc gọi đầu tiên
    .withMessage("Đã lưu thành công")
    .withMessage(R.string.saved)           // hoặc dùng string resource
    .withDuration(Snackbar.LENGTH_SHORT)   // mặc định LENGTH_SHORT
    .withActionName("Hoàn tác")
    .withActionName(R.string.undo)
    .setAction { view -> undoAction() }
    .setTextColor(Color.WHITE)             // màu message text
    .setActionColor(Color.YELLOW)          // màu action button text
    .setBackgroundColor(Color.BLACK)       // màu nền snackbar
    .show(): Snackbar                      // show và trả Snackbar

// Hoặc chỉ tạo mà chưa show
val snackbar: Snackbar = ISnackBar().of(view).withMessage("test").create()
```

---

## 31. SimpleViewPagerAdapter

**File:** `com.jibase.helper.SimpleViewPagerAdapter`

Adapter đơn giản cho ViewPager2.

```kotlin
// Extension trên Fragment
val adapter: FragmentStateAdapter = fragment.newSimpleViewPager(
    items = listOf(tab1Data, tab2Data, tab3Data)
) { item ->
    MyTabFragment.newInstance(item)
}
viewPager.adapter = adapter

// Extension trên Activity
val adapter = activity.newSimpleViewPager(items) { item ->
    MyTabFragment.newInstance(item)
}

// Hoặc khởi tạo trực tiếp
val adapter = SimpleViewPagerAdapter(
    fragmentManager = childFragmentManager,
    lifecycle = viewLifecycleOwner.lifecycle,
    items = items,
    onCreateItem = { item -> MyTabFragment.newInstance(item) }
)
```

---

## 32. BitmapUtils

**File:** `com.jibase.helper.BitmapUtils`

```kotlin
// Decode bitmap từ file path / asset path / URI string, auto-rotate theo EXIF
BitmapUtils.decodeBitmap(context, path): Bitmap?
BitmapUtils.decodeBitmap(context, path, reqWidth = 800, reqHeight = 600): Bitmap?

// Decode drawable resource
BitmapUtils.decodeBitmap(context, R.drawable.image, reqWidth, reqHeight): Bitmap?

// Decode theo kích thước màn hình
BitmapUtils.decodeBitmapByScreenSize(context, path): Bitmap?

// Decode + scale chính xác về width x height
BitmapUtils.decodeAndScaleBitmap(context, path, width = 200, height = 200): Bitmap?

// Lưu bitmap vào file
BitmapUtils.saveBitmapToFile(file, bitmap, Bitmap.CompressFormat.JPEG): File

// Lưu bitmap vào OutputStream
BitmapUtils.saveBitmapTo(outputStream, bitmap, Bitmap.CompressFormat.PNG)

// Bitmap → ByteArray (PNG)
BitmapUtils.bitmapToArray(bitmap): ByteArray
```

### Extension functions trên Bitmap

```kotlin
bitmap.safeRecycle()   // recycle nếu chưa recycled

// Cắt các cạnh transparent
bitmap.trim(color = Color.TRANSPARENT): Bitmap

// Kiểm tra toàn transparent
bitmap.isTransparent(): Boolean

// Chuyển thành NinePatchDrawable (nếu có ninePatch chunk)
bitmap.toNinePathDrawable(context): Drawable?
```

---

## 33. MediaStoreHelper

**File:** `com.jibase.helper.MediaStoreHelper`

### Insert file vào MediaStore

```kotlin
// Phiên bản với lambda tự ghi
val uri: Uri? = MediaStoreHelper.insert(
    context = context,
    name = "image.jpg",
    mimeType = "image/jpeg",              // tự detect từ extension nếu để trống
    relativeFolder = Environment.DIRECTORY_PICTURES + "/MyApp",
    fileToExportBeforeAndroidQ = null     // chỉ dùng khi API < 29
) { outputStream ->
    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
}

// Phiên bản Data class — copy file có sẵn
val uri: Uri? = MediaStoreHelper.insert(
    MediaStoreHelper.Data(
        context = context,
        name = "video.mp4",
        file = sourceFile,
        mimeType = "video/mp4",
        relativeSaveFolder = Environment.DIRECTORY_MOVIES + "/MyApp",
        copyToNewPath = true,          // copy file vào MediaStore
        deleteFileAfterCopy = false,   // xóa source sau khi copy
        fileToExportBeforeAndroidQ = null
    )
)
```

### Pick media

```kotlin
// Pick image (API >= 21, dùng ActivityResultLauncher)
MediaStoreHelper.pickImage(activity, launcher) { deniedPermissions -> }

// Pick file theo mimeType
MediaStoreHelper.pickFile(activity, launcher, mimeType = "*/*") { denied -> }

// Pick camera
MediaStoreHelper.pickCamera(
    target = activity,
    launcher = takePictureLauncher,
    uri = null,               // null = tự tạo Uri mới
    onCreatedUri = { uri -> },
    onDeny = { denied -> }
)
```

### Xóa / Rename / Request permission

```kotlin
// Xóa media
MediaStoreHelper.deleteMedia(context, uri): Boolean

// Rename
MediaStoreHelper.rename(context, uri, "newName.jpg"): Boolean

// Request delete permission (Android 11+)
MediaStoreHelper.requestDelete(context, launcher, uris): Boolean
// returns false nếu API >= 30 (launcher đã được gọi), true nếu API < 30 (xóa ngay)

// Request write permission (Android 11+)
MediaStoreHelper.requestWrite(context, launcher, uris): Boolean

// Kiểm tra cần xin storage permission
MediaStoreHelper.needRequestStoragePermission(): Boolean  // true nếu SDK < 31
MediaStoreHelper.isUserRelativePath(): Boolean            // true nếu SDK >= 29
```

---

## 34. DownloadImageHelper

**File:** `com.jibase.helper.DownloadImageHelper`

Download và cache file qua Glide (không load vào ImageView).

```kotlin
// suspend, chạy trên Dispatchers.IO
val file: File = DownloadImageHelper.download(
    context = context,
    url = "https://...",
    cacheKey = "unique_cache_key"
)
// Trả File đã được Glide cache vào disk cache
```

---

## 35. FileUtils

**File:** `com.jibase.utils.FileUtils`

```kotlin
// Copy file
copyFile(sourceFile: File, destFile: File): Boolean
copyFile(sourceStream: InputStream, destFile: File): Boolean
copyFile(file: File, fileOutputStream: OutputStream)   // không trả Boolean

// Ghi vào file
writeToFile(fromData: String, toFile: File): Boolean     // tự mkdirs
writeToFile(fromStream: InputStream?, toFile: File): Boolean

// Đọc text
readTextFromInputStream(inputStream: InputStream): String
readTextFromAsset(context: Context, path: String): String  // fallback = "{\"data\":[]}"

// Stream
getStreamFromFile(context: Context, path: String): InputStream?
// path có thể là: asset path (file:///android_asset/...), absolute path, hoặc URI string

isAssetFilePath(path: String): Boolean   // kiểm tra path có prefix asset

// MIME type
getMimeType(file: File): String?
getMimeType(ext: String?): String?

// Extension
File.isValidData(): Boolean    // exists() && canRead() && length() > 0

const val prefixAsset = "file:///android_asset/"
```

---

## 36. FileBackupHelper

**File:** `com.jibase.helper.FileBackupHelper`

Tự động backup file trước khi ghi để phòng crash.

```kotlin
val helper = FileBackupHelper(file)

// Ghi an toàn — tự backup → ghi → restore nếu lỗi
val success: Boolean = helper.write { file ->
    writeToFile(json, file)  // trả Boolean
}

// Đọc — tự restore từ backup nếu cần
val content: String = helper.read { file ->
    readTextFromInputStream(file.inputStream())
}

helper.exist(): Boolean   // true nếu file hoặc backupFile tồn tại
helper.delete()           // xóa cả file và backupFile
helper.syncBackup()       // restore backupFile → file nếu backup tồn tại

// File backup có extension ".bak"
val backupFile: File = makeBackupFile(file)
```

---

## 37. AssetsUtils

**File:** `com.jibase.helper.AssetsUtils`

```kotlin
// List tất cả item trong thư mục asset
AssetsUtils.getListItemFromAsset(context, "data/sounds"): List<String>

// Đọc text từ asset
AssetsUtils.readTextFromAsset(context, "data/config.json"): String
// fallback = "{\"data\":[]}" nếu lỗi

// Format tên file asset thành display name
AssetsUtils.formatAssetsName("my_audio_file.mp3"): String  // → "My audio file.mp3"
```

---

## 38. UriUtils

**File:** `com.jibase.helper.UriUtils`

```kotlin
// Kiểm tra Uri có scheme hợp lệ không (không null, không "null")
uri.isValidScheme(): Boolean

// Tạo Uri từ file path hoặc URI string
getUriFromPath("/storage/emulated/0/file.mp3"): Uri
// Nếu File tồn tại → File.toUri(), ngược lại → Uri.parse(path)
```

---

## 39. IntentUtils

**File:** `com.jibase.utils.IntentUtils`

```kotlin
// Mở trang app trên Play Store
IntentUtils.goToStore(context, "com.package.name")

// Share app (link Play Store hoặc custom text)
IntentUtils.shareApp(context, "com.package.name")
IntentUtils.shareApp(context, "com.package.name", text = "Custom share message")

// Mở app khác (fallback về Store nếu chưa cài)
IntentUtils.goToApps(context, "com.package.name", error = "App chưa được cài")

// Mở URL trong browser/app
IntentUtils.goToUrl(context, "https://...")
IntentUtils.goToUrl(context, "https://...", packageName = "com.android.chrome")

// Share text
IntentUtils.share(context, mess = "Nội dung chia sẻ")
IntentUtils.share(context, mess = "...", subject = "Tiêu đề", packageName = "")

// Share file qua URI
IntentUtils.share(
    context = context,
    uri = fileUri,
    mimeType = "image/jpeg",
    error = "Không có app để chia sẻ",
    packageName = ""
)

// Gửi email
IntentUtils.sentMail(
    context = context,
    subject = "Tiêu đề",
    message = "Nội dung",
    mailTo = "support@example.com",
    error = ""
)
```

---

## 40. HighlightUtils

**File:** `com.jibase.utils.HighlightUtils`

Highlight text trong TextView với màu và bold.

```kotlin
// Highlight chuỗi đầu tiên tìm được
HighlightUtils.highlightText(
    view = textView,
    constraint = "từ cần tô",
    hasBold = true,                           // mặc định true
    originalText = textView.text.toString(),  // mặc định lấy từ view
    color = Color.RED                         // mặc định: colorAccent từ theme
)

// Highlight từng từ trong constraint (split theo dấu , và space)
HighlightUtils.highlightWords(
    view = textView,
    constraint = "từ, cần tô",
    hasBold = true,
    originalText = textView.text.toString(),
    color = Color.BLUE
)
```

Case-insensitive. Highlight tất cả occurrence.

---

## 41. MathUtils

**File:** `com.jibase.utils.MathUtils`

```kotlin
// Quy đổi progress (0-100) sang giá trị trong khoảng [min, max]
progressToValue(progress = 50f, min = 0f, max = 200f): Float   // → 100f

// Quy đổi ngược lại
valueToProgress(value = 100f, min = 0f, max = 200f): Float     // → 50f
```

---

## 42. Utils

**File:** `com.jibase.utils.Utils`

### Status bar / Window

```kotlin
Utils.setStatusBarColor(activity, R.color.primary)      // @ColorRes
Utils.setStatusBarColorInt(activity, Color.BLACK)        // @ColorInt
Utils.setAppearanceLightStatusBar(activity, enable = true)  // icon sáng/tối

// Ẩn/hiện system bars
Utils.setSystemBarsVisibility(
    activity = activity,
    visible = false,
    type = WindowInsetsCompat.Type.statusBars()  // hoặc navigationBars(), systemBars()
)
Utils.setSystemBarsVisibility(window, visible, type)  // overload Window

// Bỏ giới hạn layout (vẽ dưới status/navigation bar)
Utils.setWindowNoLimit(activity)
Utils.setWindowNoLimit(window)
Utils.clearWindowNoLimit(activity)
Utils.clearWindowNoLimit(window)
```

### Tint / Drawable

```kotlin
Utils.tintDrawable(Color.RED, drawable1, drawable2)        // vararg
Utils.tintStrokeDrawable(Color.BLUE, gradientDrawable)     // chỉ stroke

// Menu item tint
Utils.tintMenu(Color.WHITE, menu)             // tất cả item
Utils.tintMenu(Color.WHITE, menu, R.id.item)  // item cụ thể

Utils.changeMenuText(menu, R.id.item, R.string.text)
Utils.changeMenuVisible(menu, visible = false, R.id.item1, R.id.item2)
Utils.changeMenuIcon(menu, Color.WHITE, R.id.item, R.drawable.icon)
```

### Khác

```kotlin
Utils.strikeThought(textView, enable = true)   // gạch ngang text
Utils.getColorByAttr(context, R.attr.colorAccent): Int
Utils.getLibVersion(): String   // BuildConfig.VERSION của jibase
```

---

## 43. CenterItemRecyclerViewUtils

**File:** `com.jibase.helper.CenterItemRecyclerViewUtils`

Scroll RecyclerView để item ở giữa màn hình.

```kotlin
// Dùng dimen resources
CenterItemRecyclerViewUtils.post(
    recyclerView = recyclerView,
    position = 5,
    widthItemDimens = R.dimen.item_width,
    marginItemDimens = R.dimen.item_margin,   // mặc định 0
    prefixDimens = R.dimen.header_width,      // offset từ đầu (mặc định 0)
    suffixDimens = 0                          // offset từ cuối (mặc định 0)
)

// Dùng giá trị Float (px)
CenterItemRecyclerViewUtils.post(
    recyclerView = recyclerView,
    position = 5,
    widthItem = 120f,
    marginItem = 8f,
    prefix = 0f,
    suffix = 0f
)
```

Chỉ hoạt động với `LinearLayoutManager`. Dùng `scrollToPositionWithOffset`.

---

## 44. Data Retriever Pattern

### BaseDataRetriever — Remote + Local + TTL

**File:** `com.jibase.retriever.BaseDataRetriever`

```kotlin
class MyRetriever(pref: SharePref) : BaseDataRetriever<List<Item>>(
    prefLastRefreshTime = "last_refresh_items",  // key lưu timestamp trong SharePref
    refreshInterval = 30 * 60 * 1000L,           // 30 phút (ms)
    sharePref = pref
) {
    override suspend fun getRemote(): List<Item>? = api.getItems()
    override suspend fun getLocal(): List<Item>? = db.getAll()
    override suspend fun saveToLocal(data: List<Item>) = db.insertAll(data)
}

// Sử dụng
val data: List<Item>? = retriever.getData()
// Logic: nếu in-memory cache có → trả ngay
//        nếu chưa có và cần refresh → getRemote() + saveToLocal(), fallback getLocal() nếu remote null
//        nếu chưa có và không cần refresh → getLocal()

retriever.isAvailableData(): Boolean   // có in-memory cache không

// Đọc từ asset (helper trong BaseDataRetriever)
val data: T? = retriever.getCacheAsset(context, "data/items.json", getTypeToken<List<Item>>())
```

### BaseFileDataRetriever — Local file + TTL

**File:** `com.jibase.retriever.BaseFileDataRetriever`

```kotlin
class MyFileRetriever(pref: SharePref) : BaseFileDataRetriever<MyData>(
    prefLastRefreshTime = "last_refresh",
    refreshInterval = 3_600_000L,
    sharePref = pref,
    file = File(context.filesDir, "data.json"),
    type = getTypeToken<MyData>()
) {
    override suspend fun getRemote(): MyData? = api.getData()
    // getLocal() và saveToLocal() đã được implement tự động qua FileBackupHelper
}
```

### BaseAssetDataRetriever — Chỉ đọc từ Assets

**File:** `com.jibase.retriever.BaseAssetDataRetriever`

```kotlin
class MyAssetRetriever(context: Context) : BaseAssetDataRetriever<List<Item>>(
    context = context,
    type = getTypeToken<List<Item>>(),
    path = "data/items.json"   // relative path trong assets/
)

val data: List<Item>? = retriever.getData()
// Cache in-memory sau lần đọc đầu tiên
// Override getLocal() nếu cần custom logic đọc
```

---

## 45. Hilt DI

**File:** `com.jibase.di.BaseModule`

Module cung cấp sẵn:

```kotlin
// Cần provide @DefaultPrefName qualifier trong app module
@Provides @Singleton
fun providerDefaultSharePrefHelper(
    @ApplicationContext ctx: Context,
    @DefaultPrefName name: String
): SharePref
```

Trong app module:

```kotlin
@Module @InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @DefaultPrefName                    // qualifier từ com.jibase.di.Qualifier
    fun providePrefName() = "app_pref"
}
```

---

## 46. Common Patterns

### ViewModel chuẩn

```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    private val repo: MyRepository
) : ViewModel() {

    private val _state = MutableStateFlow<ResultWrapper<MyData>>(ResultWrapper.None)
    val state = _state.asStateFlow()

    fun loadData() {
        viewModelScope.launch {
            _state.value = ResultWrapper.Loading
            _state.value = safeApiCall { repo.getData() }
        }
    }

    // Hoặc dùng resultApiCall (tự emit Loading)
    fun fetchData() = resultApiCall { safeApiCall { repo.getData() } }
}
```

### Fragment chuẩn

```kotlin
@AndroidEntryPoint
class MyFragment : Fragment(R.layout.fragment_my) {
    private val binding by viewBinding(FragmentMyBinding::bind)
    private val viewModel: MyViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        collect(viewModel.state) { result ->
            when (result) {
                is ResultWrapper.Loading -> stateView.updateUI(NetworkType.LOADING)
                is ResultWrapper.Success -> {
                    stateView.updateUI(NetworkType.SUCCESS)
                    bindData(result.value)
                }
                is ResultWrapper.Error -> stateView.updateUI(NetworkType.ERROR)
                else -> {}
            }
        }
    }
}
```

### Build config

```kotlin
// AAR output: jibase-<ddMMyy>.aar
// BuildConfig.VERSION = "ddMMyy"
// Thêm dependency:
implementation(files("libs/jibase-XXXXXX.aar"))
// Hoặc via jitpack:
implementation("com.ngocji:jibase:4.3.3")
```

---

## 47. File Quick Reference

| Cần làm | File |
|---------|------|
| Gọi API an toàn | `flow/ApiExtensions.kt` |
| Wrapping kết quả | `flow/ResultWrapper.kt` |
| Cache flow | `flow/cache/DataCacheManager.kt` |
| Collect flow trong Fragment | `extensions/LifecycleExt.kt` |
| ViewModel API call | `extensions/ViewModelExt.kt` |
| Coroutine helpers | `extensions/CoroutineExt.kt` |
| Navigate (Navigation Component) | `extensions/NavControllerExtension.kt` + `FragmentNavExtensions.kt` |
| Quản lý Fragment thủ công | `utils/FragmentUtils.kt` |
| ViewBinding | `extensions/ViewBindingDelegate.kt` |
| View show/hide/transition | `extensions/ViewExtensions.kt` |
| Context utils | `extensions/ContextExtensions.kt` |
| Activity navigate | `extensions/ActivityExt.kt` |
| Keyboard show/hide (extension) | `extensions/KeyboardExtensions.kt` |
| Keyboard show/hide (object) | `helper/KeyboardHelper.kt` |
| Animate keyboard | `helper/AnimateKeyboardHelper.kt` |
| Load image (Glide) | `extensions/ImageExtensions.kt` |
| LiveData | `extensions/LiveDataExtensions.kt` |
| Timer flow | `extensions/TimerExt.kt` |
| Geometry helpers | `extensions/RectExt.kt` |
| Duration / size format | `extensions/Exts.kt` |
| SharedPrefs | `pref/SharePref.kt` |
| In-memory store | `helper/SessionHelper.kt` |
| JSON | `helper/GsonManager.kt` |
| Debounce search | `helper/EdittextDebounceExt.kt` |
| Logging | `utils/Log.kt` |
| Permissions | `permission/PermissionsHelper.kt` |
| Dialog | `ui/dialog/BaseDialog.kt` |
| Bottom sheet | `ui/dialog/BaseBottomDialog.kt` |
| Confirm dialog | `ui/confirmdialog/ConfirmDialog.kt` |
| State view | `view/StateNetworkView.kt` |
| Option item | `view/OptionItemView.kt` |
| Snackbar | `view/ISnackBar.kt` |
| ViewPager2 adapter | `helper/SimpleViewPagerAdapter.kt` |
| Bitmap decode/rotate | `helper/BitmapUtils.kt` |
| MediaStore insert/pick | `helper/MediaStoreHelper.kt` |
| Download image to file | `helper/DownloadImageHelper.kt` |
| File copy/write/read | `utils/FileUtils.kt` |
| File backup | `helper/FileBackupHelper.kt` |
| Assets read | `helper/AssetsUtils.kt` |
| Uri helpers | `helper/UriUtils.kt` |
| Intent (share/store/mail) | `utils/IntentUtils.kt` |
| Highlight text | `utils/HighlightUtils.kt` |
| Math (progress↔value) | `utils/MathUtils.kt` |
| Status bar / system UI | `utils/Utils.kt` |
| Center scroll RecyclerView | `helper/CenterItemRecyclerViewUtils.kt` |
| Data retriever + TTL | `retriever/BaseDataRetriever.kt` |
| File data retriever | `retriever/BaseFileDataRetriever.kt` |
| Asset data retriever | `retriever/BaseAssetDataRetriever.kt` |
| Hilt DI module | `di/BaseModule.kt` |
| RecyclerView adapter | → xem `FLEXIBLE_ADAPTER_GUIDE.md` |
