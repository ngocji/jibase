# CLAUDE.md — jibase Android Library

## Project Overview

**jibase** là một Android library (AAR) dùng chung cho các dự án Android của `ngocji`. Gồm 2 module:
- `:jibase` — thư viện chính (publish AAR)
- `:app` — sample app demo

**Tech stack:** Kotlin · Coroutines/Flow · Hilt DI · ViewBinding · Navigation Component · Paging 3 · Glide · Gson · Room (optional) · Retrofit (optional)

**Min SDK:** 21 · **Target SDK:** 35 · **JVM:** 17 · **Kotlin:** 2.0.0

---

## Module Structure

```
jibase/
├── di/                     # Hilt DI modules
├── extensions/             # Kotlin extension functions
├── flow/                   # ResultWrapper + API helpers + Cache
├── helper/                 # Utility helpers
├── iflexible/              # FlexibleAdapter ecosystem (RecyclerView)
│   ├── adapter/
│   ├── common/
│   ├── entities/
│   ├── fastscroll/
│   ├── helpers/
│   ├── items/
│   ├── listener/
│   ├── utils/
│   └── viewholder/
├── permission/             # Runtime permission helper
├── pref/                   # SharedPreferences wrapper
├── retriever/              # Data retriever pattern
├── ui/                     # BaseDialog, BaseBottomDialog, ConfirmDialog
├── utils/                  # Log, FileUtils, FragmentUtils…
└── view/                   # StateNetworkView, OptionItemView, ISnackBar
```

---

## 1. Flow / API Pattern

### ResultWrapper — `com.jibase.flow.ResultWrapper`

Sealed class bọc tất cả kết quả async:

```kotlin
sealed class ResultWrapper<out T> {
    data class Success<T>(val value: T) : ResultWrapper<T>()
    data object Empty   : ResultWrapper<Nothing>()
    data class Error(val throwable: Throwable?) : ResultWrapper<Nothing>()
    data object None    : ResultWrapper<Nothing>()
    data object Loading : ResultWrapper<Nothing>()
}

// Helpers
wrapper.safeTakeValue()           // T? — không throw
wrapper.takeValueOrThrow()        // T  — throw nếu không phải Success
wrapper.applyWhenSuccess { }      // side-effect
wrapper.mapWhenSuccess { }        // transform
```

### API helpers — `com.jibase.flow.ApiExtensions`

```kotlin
// Gọi API an toàn, trả null khi lỗi
val result: T? = safeFlowCall { api.getData() }

// Gọi API trả ResultWrapper
val result = safeApiCall { api.getData() }
// → Success(data) hoặc Error(throwable)

// Wrap Flow thành Flow<ResultWrapper>
val wrappedFlow = safeFlow(roomDao.observeAll())
```

### ViewModel helpers — `com.jibase.extensions.ViewModelExt`

```kotlin
// Trong ViewModel:
fun fetchData() = resultApiCall { safeApiCall { api.getData() } }
// emit Loading rồi emit Success/Error

fun observeData() = resultFlow { roomDao.observeAll().map { ResultWrapper.Success(it) } }
```

---

## 2. Coroutine Extensions

File: `com.jibase.extensions.CoroutineExt`

```kotlin
// Chạy song song nhiều tác vụ, trả về khi tất cả xong
fragment.runAsync(
    onComplete = { isCompleted -> /* UI callback trên Main */ }
) {
    task1()
    task2()
}

// Chạy đơn một coroutine
viewModel.runCoroutine { loadData() }
fragment.runCoroutine { loadData() }
```

---

## 3. Lifecycle / Flow Collection

File: `com.jibase.extensions.LifecycleExt`

```kotlin
// Fragment — collect theo lifecycle STARTED (mặc định)
collect(viewModel.dataFlow) { data -> updateUI(data) }

// Fragment — collect tất cả emissions (không drop khi background)
collectOne(viewModel.eventFlow) { event -> handleEvent(event) }

// Chỉ collect khi không null
collectNotNull(viewModel.nullableFlow) { data -> bind(data) }

// Collect khi RESUMED
collectWhenResume(viewModel.flow) { }

// Activity equivalents — tương tự, nhưng gọi trực tiếp trên Activity
activity.collect(flow) { }
activity.collectOne(flow) { }

// Channel support
collect(channel) { event -> }

// LiveData (legacy)
observe(viewModel.liveData) { }
onceObserve(viewModel.liveData) { }
```

**Quy tắc:** Dùng `collect` cho UI state (StateFlow), `collectOne` cho one-shot events (Channel/SharedFlow).

---

## 4. ViewBinding Delegates

File: `com.jibase.extensions.ViewBindingDelegate`

```kotlin
// Fragment
class MyFragment : Fragment(R.layout.my_fragment) {
    private val binding by viewBinding(MyFragmentBinding::bind)
}

// Activity
class MyActivity : AppCompatActivity() {
    private val binding by viewBinding(MyActivityBinding::inflate)
    override fun onCreate(...) { setContentView(binding.root) }
}

// ViewGroup (trong custom view)
val binding = viewBinding(MyViewBinding::inflate)

// Context
val binding = context.viewBinding(MyViewBinding::inflate)
```

---

## 5. Navigation Extensions

File: `com.jibase.extensions.NavControllerExtension` + `FragmentNavExtensions`

```kotlin
// Safe navigate — không crash khi gọi sai state
navController.safeNavigate(R.id.action_to_detail)
navController.safeNavigate(directions)
navController.safeNavigate(deepLinkUri)

// Truyền kết quả giữa fragments (NavBackStack)
// Gửi:
setNavigationResult(myResult, key = "result")
setPreviousNavigationResult(key = "result", result = data)

// Nhận (one-shot):
observeNavigationResultOnce<MyData>(key = "result") { data -> }

// Dialog → Parent:
observeDialogNavigationResultOnce<MyData>(destinationId = R.id.parentFragment) { }

// Navigate up / pop
navigateUp()
popBack()
popBackTo(R.id.destination, inclusive = false)

// Back pressed override
onBackPressedOverride { /* handle */ }

// Lifecycle hooks trong Fragment
observeOnDestroy { cleanup() }
observeOnResume { refresh() }
```

---

## 6. View Extensions

File: `com.jibase.extensions.ViewExtensions`

```kotlin
view.visible()          // VISIBLE (optional animate)
view.gone()             // GONE
view.invisible()        // INVISIBLE
view.visible(true)      // với transition animation

view.makeTransition()   // bắt đầu delayed transition

view.setLayoutParams(width = 200, height = WRAP_CONTENT)
view.getDimen(R.dimen.size)
view.getDimensionPixelOffset(R.dimen.size)
view.toBitmap()
view.changeElevation(4f)
view.getCurrentElevation()
```

---

## 7. Image Extensions (Glide)

File: `com.jibase.extensions.ImageExtensions`

```kotlin
imageView.load(url)
imageView.load(url, placeHolder = R.drawable.placeholder, error = R.drawable.error)
imageView.load(url, action = { bitmap -> /* transform */ })
imageView.load(url, listener = glideListener)
imageView.load(url, requestOptions)
```

---

## 8. FlexibleAdapter — RecyclerView

> **Full API reference:** xem `FLEXIBLE_ADAPTER_GUIDE.md` — bao gồm toàn bộ method, ví dụ thực tế, và các edge case.


### Adapter hierarchy

```
AbstractFlexibleAdapter          ← selection, FastScroller, bound VH tracking
  └── AbstractFlexibleAnimatorAdapter  ← scroll animation
        └── FlexibleAdapter<T>         ← tất cả logic (items, headers, expand, filter…)
              └── FlexiblePagingAdapter<T>    ← Paging 3 tích hợp
```

### Item hierarchy

```
IFlexible<VH>
  └── AbstractFlexibleItem<VH>          ← item thông thường
        ├── AbstractFlexibleExpandItem<VH,S>    ← expandable (có subItems)
        ├── AbstractFlexibleHeaderItem<VH>      ← header section
        └── AbstractFlexibleSectionableItem<VH,H>  ← item thuộc về header
```

### Tạo item nhanh nhất (ViewBinding)

```kotlin
// Item đơn giản dùng BindingItem
class MyItem(val data: MyData) : BindingItem() {
    override fun getLayoutRes() = R.layout.item_my
    override fun bindViewHolder(adapter: FlexibleAdapter<*>, holder: BindingViewHolder, position: Int, payloads: List<*>) {
        holder.withBinding<ItemMyBinding> {
            tvName.text = data.name
        }
    }
    override fun createViewHolder(view: View, adapter: FlexibleAdapter<*>) =
        BindingViewHolder(ItemMyBinding.bind(view), adapter)
    override fun equals(other: Any?) = other is MyItem && other.data.id == data.id
    override fun hashCode() = data.id.hashCode()
}
```

### Setup adapter

```kotlin
val adapter = FlexibleAdapter<IFlexible<*>>(items)
    .addListener(this)  // implement OnItemClickListener

recyclerView.adapter = adapter
recyclerView.layoutManager = LinearLayoutManager(context)

// Click listener
adapter.onItemClickListener = OnItemClickListener { adapter, view, position ->
    val item = adapter.getItem(position)
    true // consume
}
```

### Data management

```kotlin
adapter.updateDataSet(newList)          // diff + animate
adapter.addItem(item)                   // thêm cuối
adapter.addItem(position, item)         // chèn vào vị trí
adapter.addItems(position, list)
adapter.removeItem(position)
adapter.removeItems(positions)
adapter.clear()
adapter.getItem(position)
adapter.getItemCount()
adapter.isEmpty
```

### Selection & Multi-select

```kotlin
adapter.setMode(FlexibleAdapter.SINGLE)  // hoặc MULTI
adapter.toggleSelection(position)
adapter.selectAll()
adapter.clearSelection()
adapter.getSelectedItems()               // List<Int> positions
adapter.getSelectedItemCount()
```

### Expandable

```kotlin
// Item expandable — kế thừa AbstractFlexibleExpandItem
class ParentItem(val subItems: List<ChildItem>) : AbstractFlexibleExpandItem<...>() {
    override fun getSubItemsCount() = subItems.size
    override fun getSubItems() = subItems
    override fun getLayoutRes() = R.layout.item_parent
    ...
}

adapter.expand(position)
adapter.collapse(position)
adapter.expandAll()
adapter.collapseAll()
```

### Section Headers

```kotlin
// Header kế thừa AbstractFlexibleHeaderItem
// Item kế thừa AbstractFlexibleSectionableItem và link header:
item.header = myHeader

adapter.showAllHeaders()
adapter.hideAllHeaders()
adapter.setStickyHeaders(true, container)  // sticky header
```

### Filter

```kotlin
adapter.setFilter("query")
adapter.filterItems(delay = 300)
adapter.resetFilter()
// Item implement IFilterable để custom filter logic
```

### Endless Scroll

```kotlin
adapter.setEndlessScrollListener(threshold = 3) { currentPage ->
    loadPage(currentPage)
}
adapter.setEndlessScrollEnabled(true)
adapter.onLoadMoreComplete(newItems)  // gọi sau khi load xong
adapter.setEndlessTargetCount(totalCount)
```

### Drag & Drop / Swipe

```kotlin
adapter.enableDragDrop(longPressDrag = true)
adapter.enableSwipe(swipeRight = true, swipeLeft = true)
adapter.onItemMoveListener = OnItemMoveListener { ... }
adapter.onItemSwipeListener = OnItemSwipeListener { ... }
// Item cần enableDrag = true / enableSwipe = true
```

### Undo (Snackbar)

```kotlin
adapter.removeItem(position)  // mark pending delete
// sau đó dùng UndoHelper:
UndoHelper(adapter)
    .withPayload(Payload.CHANGE)
    .remove(positions, snackbarRootView, "Deleted", "Undo", 5000, listener)
```

---

## 9. FlexiblePagingAdapter (Paging 3)

```kotlin
class MyPagingAdapter : FlexiblePagingAdapter<MyItem>(diffCallback) {
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) { ... }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder { ... }
}

// Trong Fragment:
viewModel.pagingFlow.collectLatest { adapter.submitData(it) }
```

---

## 10. SharedPreferences — SharePref

File: `com.jibase.pref.SharePref`

```kotlin
// Inject qua Hilt (cần provide tên pref trong @DefaultPrefName)
// Hoặc tạo trực tiếp:
val pref = SharePref(context, "my_pref")

// Đọc
pref.getString("key", "default")
pref.getInt("key", 0)
pref.getBoolean("key", false)
pref.getLong("key", 0L)
pref.getFloat("key", 0f)
pref.getDouble("key", 0.0)
pref.getObject<MyClass>("key", MyClass::class.java)
pref.getObject<List<MyClass>>("key", getTypeToken<List<MyClass>>())

// Ghi (generic — tự detect type)
pref.put("key", value)  // Boolean/Int/Long/Float/String/Object

// Hoặc typed:
pref.putString("key", "val")
pref.putInt("key", 1)
pref.putBoolean("key", true)
pref.putObject("key", myObject)

// Xóa
pref.remove("key1", "key2")

// Listen changes
pref.registerChange(listener)
pref.unregisterChange(listener)
```

**Note:** SharePref lưu mọi primitive dưới dạng String để tránh lỗi type casting.

---

## 11. SessionHelper — In-memory cache

File: `com.jibase.helper.SessionHelper`

```kotlin
SessionHelper.put("key", data)
val data: MyType? = SessionHelper.get("key")
val data: MyType = SessionHelper.getNotNull("key")          // throw nếu null
val data: MyType = SessionHelper.getNotNull("key", default) // fallback
SessionHelper.clear("key")   // xóa 1 key
SessionHelper.clear()        // xóa tất cả
SessionHelper.containKey("key")
```

---

## 12. DataCacheManager — Flow cache

File: `com.jibase.flow.cache.DataCacheManager`

```kotlin
// Trong ViewModel/Repository:
private val cacheManager = DataCacheManager(viewModelScope)

// Cache 1 flow (không có params)
fun observeData(): Flow<List<Item>> = cacheManager.getFlow(
    cacheKey = "items",
    initialValue = emptyList(),
    forceRefresh = false
) { roomDao.observeAll() }

// Cache với params
fun getUserData(userId: String): StateFlow<User?> = cacheManager.getData(
    cacheKey = "user_$userId",
    params = userId,
    initialValue = null
) { id -> userRepository.observeUser(id) }

// Xóa cache
cacheManager.clearCache("items")
cacheManager.clearAllCache()
```

---

## 13. Hilt DI

### BaseModule cung cấp sẵn:

```kotlin
// SharePref singleton — cần @DefaultPrefName qualifier
@Provides @Singleton
fun providerDefaultSharePrefHelper(@ApplicationContext ctx, @DefaultPrefName name): SharePref
```

### Định nghĩa @DefaultPrefName trong app module:

```kotlin
@Module @InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @DefaultPrefName
    fun providePrefName() = "app_pref"
}
```

---

## 14. PermissionsHelper

File: `com.jibase.permission.PermissionsHelper`

```kotlin
PermissionsHelper.with(fragment)
    .request(Manifest.permission.CAMERA, Manifest.permission.READ_MEDIA_IMAGES)
    .onGrant { permissions -> /* tất cả được cấp */ }
    .onDeny { permissions -> /* bị từ chối */ }
    .onRevoke { permissions -> /* bị revoke bởi policy */ }
    .execute()

// Check trước khi request
val helper = PermissionsHelper.with(fragment)
helper.isGranted(Manifest.permission.CAMERA)   // Boolean
helper.isRevoked(Manifest.permission.CAMERA)   // Boolean
```

---

## 15. BaseDialog / BaseBottomDialog

File: `com.jibase.ui.dialog.BaseDialog` / `BaseBottomDialog`

```kotlin
class MyDialog : BaseDialog(R.layout.dialog_my) {
    // hoặc BaseBottomDialog(R.layout.dialog_my)
    
    override fun onViewReady(savedInstanceState: Bundle?) {
        // binding available, setup UI
    }
    
    override fun isShowFullDialog() = false  // full screen?
    override fun initStyle() = R.style.style_dialog_90  // default
}

// Show (safe — không crash nếu đã show)
myDialog.show(parentFragmentManager)
```

---

## 16. ConfirmDialog (Builder pattern)

File: `com.jibase.ui.confirmdialog.ConfirmDialog`

```kotlin
ConfirmDialog.newBuilder(context, R.style.AppTheme)
    .setTitle("Xác nhận")
    .setMessage("Bạn có chắc không?")
    .setConfirmText("Đồng ý")
    .setCancelText("Hủy")
    .setCancelable(false)
    .setCallBack(object : ConfirmDialog.CallBack {
        override fun onConfirmClicked(dialog: ConfirmDialog?) { }
        override fun onCancelClicked(dialog: ConfirmDialog) { }
        override fun onDismiss() { }
    })
    .build()
    .show(parentFragmentManager)
```

---

## 17. StateNetworkView

Custom view hiển thị loading/error/empty/content. File: `com.jibase.view.StateNetworkView`

```xml
<com.jibase.view.StateNetworkView
    android:id="@+id/stateView"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:state_enable_pull_to_refresh="true"
    app:state_empty_text="Không có dữ liệu"
    app:state_empty_icon="@drawable/ic_empty" />
<!-- Content views đặt bên trong StateNetworkView -->
```

```kotlin
stateView.updateUI(StateNetworkView.NetworkType.LOADING)
stateView.updateUI(StateNetworkView.NetworkType.SUCCESS)
stateView.updateUI(StateNetworkView.NetworkType.ERROR)
stateView.updateUI(StateNetworkView.NetworkType.EMPTY)

stateView.setCallBack { networkType ->
    // gọi khi user nhấn retry hoặc swipe refresh
    loadData()
}

// Custom layout cho từng state
stateView.replaceView(NetworkType.ERROR, R.layout.my_error_layout)
stateView.addTag("custom_tag", R.layout.custom_layout)
stateView.updateUI("custom_tag")
```

---

## 18. OptionItemView

Custom view cho settings item. File: `com.jibase.view.OptionItemView`

```xml
<com.jibase.view.OptionItemView
    app:ot_title="Thông báo"
    app:ot_desc="Bật/tắt thông báo"
    app:ot_icon="@drawable/ic_notify"
    app:ot_type="switch"   <!-- none | switch | text | image -->
    app:ot_show_line="true" />
```

```kotlin
optionItem.setTitle("Title")
optionItem.setDesc("Description")
optionItem.setChecked(true)
optionItem.setOnCheckedChanged { _, isChecked -> }
optionItem.setType(OptionItemView.Type.SWITCH)
```

---

## 19. ISnackBar (Builder)

File: `com.jibase.view.ISnackBar`

```kotlin
ISnackBar()
    .of(rootView)
    .withMessage("Thành công")
    .withDuration(Snackbar.LENGTH_SHORT)
    .withActionName("Hoàn tác")
    .setAction { view -> }
    .setTextColor(Color.WHITE)
    .setBackgroundColor(Color.BLACK)
    .show()
```

---

## 20. Data Retriever Pattern

### BaseDataRetriever — fetch remote + cache local với TTL

```kotlin
class MyDataRetriever(pref: SharePref) : BaseDataRetriever<List<Item>>(
    prefLastRefreshTime = "last_refresh_items",
    refreshInterval = 30 * 60 * 1000L,  // 30 phút
    sharePref = pref
) {
    override suspend fun getRemote() = api.getItems()
    override suspend fun getLocal() = db.getAll()
    override suspend fun saveToLocal(data: List<Item>) = db.insertAll(data)
}

val data = retriever.getData()  // remote nếu hết TTL, local nếu còn
```

### BaseFileDataRetriever — local file

```kotlin
class MyFileRetriever(pref: SharePref, file: File) : BaseFileDataRetriever<MyData>(
    prefLastRefreshTime = "last_refresh",
    refreshInterval = 3600_000L,
    sharePref = pref,
    file = file,
    type = getTypeToken<MyData>()
) {
    override suspend fun getRemote() = api.getData()
}
```

### BaseAssetDataRetriever — đọc từ assets

```kotlin
class MyAssetRetriever(context: Context) : BaseAssetDataRetriever<List<Item>>(
    context = context,
    type = getTypeToken<List<Item>>(),
    path = "data/items.json"
)

val data = retriever.getData()
```

---

## 21. GsonManager

File: `com.jibase.helper.GsonManager`

```kotlin
// Serialize
val json = GsonManager.toJson(myObject)

// Deserialize
val obj: MyClass? = GsonManager.fromJson(json, MyClass::class.java)
val list: List<MyClass>? = GsonManager.fromJson(json, getTypeToken<List<MyClass>>())

// Extension function (inline)
val obj: MyClass? = fromJson<MyClass>(json)
val list: List<MyClass>? = fromJson(json)

// Custom Gson instance
GsonManager.createGson("my_type", myCustomGson)
GsonManager.fromJson(json, type, "my_type")
```

---

## 22. Logging

File: `com.jibase.utils.Log`

```kotlin
// Setup (thường trong Application.onCreate)
Log.setup(prefix = "APP_TAG", isEnable = BuildConfig.DEBUG)

Log.d("Debug message")    // DEBUG — chỉ khi isEnable = true
Log.e("Error message")    // ERROR — chỉ khi isEnable = true
Log.d("message", "CUSTOM_TAG")  // custom tag

// Log tự động thêm: class name, method name, line number
```

---

## 23. Debounce EditText / SearchView

File: `com.jibase.helper.EdittextDebounceExt`

```kotlin
// Trong Fragment/Activity:
editText.toTextChangeFlow()
    .debounce(300)
    .onEach { text -> search(text.toString()) }
    .launchIn(lifecycleScope)

searchView.toTextChangeFlow()
    .debounce(300)
    .onEach { query -> filter(query.toString()) }
    .launchIn(lifecycleScope)
```

---

## 24. Extension Utilities

### Exts.kt
```kotlin
list hasPosition position  // Boolean — safe bounds check
1000L.formatMinSecDuration()  // "16:40"
3661000L.formatFullDuration() // "01:01:01"
1048576L.formatSize()         // "1.00 MB"
```

### LiveDataExtensions.kt
```kotlin
fragment.observe(liveData) { value -> }
fragment.onceObserve(liveData) { value -> }   // auto-remove sau lần đầu
activity.observe(liveData) { value -> }
```

---

## 25. Build Configuration

- **AAR output name:** `jibase-<ddMMyy>.aar` (tự động theo ngày build)
- **BuildConfig.VERSION:** string ngày `ddMMyy`
- **Hilt:** bắt buộc trong mọi project sử dụng jibase
- **ViewBinding:** bật sẵn

### Thêm dependencies trong app sử dụng:
```kotlin
// build.gradle.kts của app
implementation(files("libs/jibase-XXXXXX.aar"))
// Hoặc qua jitpack nếu publish
implementation("com.ngocji:jibase:4.3.3")
```

---

## 26. Common Patterns & Conventions

### ViewModel pattern chuẩn
```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    private val repo: MyRepository
) : ViewModel() {

    private val _state = MutableStateFlow<ResultWrapper<MyData>>(ResultWrapper.None)
    val state = _state.asStateFlow()

    fun loadData() = viewModelScope.launch {
        _state.value = ResultWrapper.Loading
        _state.value = safeApiCall { repo.getData() }
    }
}
```

### Fragment pattern chuẩn
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

### Item type ID
`AbstractFlexibleItem.getItemViewType()` mặc định dùng `this::class.java.name.hashCode()` — mỗi class = 1 view type riêng. **Không cần override** trừ khi muốn share layout.

### equals/hashCode bắt buộc
Mọi `AbstractFlexibleItem` **phải override `equals` và `hashCode`** để DiffUtil hoạt động đúng.

---

## 27. Known Issues & Notes

- **`StickyHeaderHelper`** hiện đang có thay đổi chưa commit (thấy trong git status).
- **`fragment_list.xml`** có modification chưa commit.
- Tất cả primitive trong `SharePref` lưu dưới dạng String — đây là thiết kế cố ý để tránh ClassCastException khi đổi type.
- `SessionHelper` là in-memory chỉ — mất khi process bị kill.
- `DataCacheManager` dùng coroutine scope của caller — không tự manage lifecycle.
- `safeFlow()` dùng `mapLatest` — emit cuối cùng nếu upstream nhanh.

---

## 28. File Quick Reference

| Cần làm | File |
|---------|------|
| Gọi API an toàn | `flow/ApiExtensions.kt` |
| Wrapping kết quả | `flow/ResultWrapper.kt` |
| Cache flow | `flow/cache/DataCacheManager.kt` |
| Collect flow trong Fragment | `extensions/LifecycleExt.kt` |
| ViewModel API call | `extensions/ViewModelExt.kt` |
| Coroutine helpers | `extensions/CoroutineExt.kt` |
| Navigate | `extensions/NavControllerExtension.kt` + `FragmentNavExtensions.kt` |
| ViewBinding | `extensions/ViewBindingDelegate.kt` |
| View show/hide | `extensions/ViewExtensions.kt` |
| Load image | `extensions/ImageExtensions.kt` |
| SharedPrefs | `pref/SharePref.kt` |
| In-memory store | `helper/SessionHelper.kt` |
| JSON | `helper/GsonManager.kt` |
| Logging | `utils/Log.kt` |
| Permissions | `permission/PermissionsHelper.kt` |
| Dialog | `ui/dialog/BaseDialog.kt` |
| Bottom sheet | `ui/dialog/BaseBottomDialog.kt` |
| Confirm dialog | `ui/confirmdialog/ConfirmDialog.kt` |
| State view | `view/StateNetworkView.kt` |
| Option item | `view/OptionItemView.kt` |
| Snackbar | `view/ISnackBar.kt` |
| RecyclerView adapter | `iflexible/adapter/FlexibleAdapter.kt` |
| Paging 3 adapter | `iflexible/adapter/FlexiblePagingAdapter.kt` |
| Item đơn giản | `iflexible/viewholder/BindingItem.kt` |
| Debounce search | `helper/EdittextDebounceExt.kt` |
| Data retriever | `retriever/BaseDataRetriever.kt` |
| DI module | `di/BaseModule.kt` |
