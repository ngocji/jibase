# CLAUDE.md — jibase Android Library

## Tài liệu đầy đủ

- **API Reference toàn bộ:** → [`jibase.md`](jibase.md) — tất cả hàm, signature, ví dụ chi tiết
- **FlexibleAdapter (RecyclerView):** → [`FLEXIBLE_ADAPTER_GUIDE.md`](flexible_adapter.md)

---

## Project Overview

**jibase** là Android library (AAR) dùng chung cho các dự án Android của `ngocji`.  
2 module: `:jibase` (thư viện chính) · `:app` (sample demo)

**Tech stack:** Kotlin · Coroutines/Flow · Hilt DI · ViewBinding · Navigation Component · Paging 3 · Glide · Gson  
**Min SDK:** 21 · **Target SDK:** 35 · **JVM:** 17 · **Kotlin:** 2.0.0

### Module Structure

```
jibase/src/main/java/com/jibase/
├── di/           # Hilt DI modules + @DefaultPrefName qualifier
├── extensions/   # Kotlin extensions (View, Fragment, Activity, Flow, LiveData…)
├── flow/         # ResultWrapper + safeApiCall + safeFlow + DataCacheManager
├── helper/       # GsonManager, SessionHelper, BitmapUtils, KeyboardHelper…
├── iflexible/    # FlexibleAdapter ecosystem (RecyclerView)
├── permission/   # PermissionsHelper (runtime permissions)
├── pref/         # SharePref (SharedPreferences wrapper)
├── retriever/    # BaseDataRetriever + BaseFileDataRetriever + BaseAssetDataRetriever
├── ui/           # BaseDialog, BaseBottomDialog, ConfirmDialog
├── utils/        # Log, FileUtils, FragmentUtils, IntentUtils, HighlightUtils, Utils…
└── view/         # StateNetworkView, OptionItemView, ISnackBar
```

---

## Core Pattern

### ResultWrapper

```kotlin
sealed class ResultWrapper<out T> {
    data class Success<T>(val value: T) : ResultWrapper<T>()
    data object Loading  : ResultWrapper<Nothing>()
    data class Error(val throwable: Throwable? = null) : ResultWrapper<Nothing>()
    data object Empty    : ResultWrapper<Nothing>()
    data object None     : ResultWrapper<Nothing>()
}
```

### ViewModel chuẩn

```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(private val repo: MyRepository) : ViewModel() {
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

---

## Quy tắc quan trọng

- **Flow collection:** `collect` cho UI state (StateFlow), `collectOne` cho one-shot events
- **SharePref:** mọi primitive lưu dưới dạng String — thiết kế cố ý, không thay đổi
- **SessionHelper:** in-memory only — mất khi process bị kill
- **DataCacheManager:** dùng coroutine scope của caller — không tự manage lifecycle
- **safeFlow():** dùng `mapLatest` — chỉ lấy emission cuối nếu upstream nhanh
- **FlexibleAdapter items:** bắt buộc override `equals` + `hashCode` để DiffUtil hoạt động
- **Hilt:** bắt buộc trong mọi project sử dụng jibase, cần provide `@DefaultPrefName`

---

## Build & Distribution

```kotlin
// AAR output: jibase-<ddMMyy>.aar
// BuildConfig.VERSION = "ddMMyy"

// Thêm vào app:
implementation(files("libs/jibase-XXXXXX.aar"))
// Hoặc qua jitpack:
implementation("com.ngocji:jibase:4.3.3")
```

**Yêu cầu bắt buộc trong app sử dụng:** Hilt · ViewBinding

---

## File Quick Reference (top dùng nhiều nhất)

| Cần làm | File |
|---------|------|
| Gọi API / wrap result | `flow/ApiExtensions.kt` + `flow/ResultWrapper.kt` |
| Collect Flow | `extensions/LifecycleExt.kt` |
| ViewModel helpers | `extensions/ViewModelExt.kt` |
| Navigate (NavComponent) | `extensions/NavControllerExtension.kt` + `FragmentNavExtensions.kt` |
| Quản lý Fragment thủ công | `utils/FragmentUtils.kt` |
| ViewBinding delegate | `extensions/ViewBindingDelegate.kt` |
| SharedPrefs | `pref/SharePref.kt` |
| In-memory cache | `helper/SessionHelper.kt` |
| JSON | `helper/GsonManager.kt` |
| Permissions | `permission/PermissionsHelper.kt` |
| State view | `view/StateNetworkView.kt` |
| Confirm dialog | `ui/confirmdialog/ConfirmDialog.kt` |
| RecyclerView adapter | → `FLEXIBLE_ADAPTER_GUIDE.md` |

> **Chi tiết đầy đủ tất cả API:** xem [`jibase.md`](jibase.md)
