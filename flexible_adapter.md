# FlexibleAdapter – Hướng dẫn chi tiết

> **Thuộc:** jibase Android Library (`com.ngocji:jibase`)  
> **Package gốc:** `com.jibase.iflexible`  
> **Tài liệu liên quan:** [`jibase.md`](jibase.md) (API các module khác) · [`CLAUDE.md`](CLAUDE.md) (project overview)  
> **File này:** `flexible_adapter.md`

## Mục lục

1. [Tổng quan kiến trúc](#tổng-quan-kiến-trúc)
2. [Interface cốt lõi](#interface-cốt-lõi)
3. [Abstract base classes](#abstract-base-classes)
4. [FlexibleAdapter – API đầy đủ](#flexibleadapter--api-đầy-đủ)
   - [Khởi tạo & Lifecycle](#khởi-tạo--lifecycle)
   - [Quản lý dữ liệu](#quản-lý-dữ-liệu)
   - [Thêm item](#thêm-item)
   - [Xóa item](#xóa-item)
   - [Cập nhật item](#cập-nhật-item)
   - [Di chuyển & Swap](#di-chuyển--swap)
   - [Selection – Chọn item](#selection--chọn-item)
   - [Expandable – Item có thể mở rộng](#expandable--item-có-thể-mở-rộng)
   - [Header / Section](#header--section)
   - [Scrollable Header & Footer](#scrollable-header--footer)
   - [Filter – Tìm kiếm](#filter--tìm-kiếm)
   - [Endless Scroll – Tải thêm](#endless-scroll--tải-thêm)
   - [Drag & Drop / Swipe](#drag--drop--swipe)
   - [Animation](#animation)
   - [Sticky Header](#sticky-header)
   - [Undo – Hoàn tác](#undo--hoàn-tác)
   - [Fast Scroller](#fast-scroller)
   - [Configurations](#configurations)
5. [FlexiblePagingAdapter – Paging 3](#flexiblepagingadapter--paging-3)
   - [Cơ chế hoạt động](#cơ-chế-hoạt-động)
   - [API](#api)
   - [Tính năng giữ lại & xung đột](#tính-năng-giữ-lại--xung-đột)
6. [ViewHolder](#viewholder)
7. [Helpers](#helpers)
   - [ActionModeHelper](#actionmodehelper)
   - [UndoHelper](#undohelper)
8. [Listeners](#listeners)
9. [Ví dụ sử dụng thực tế](#ví-dụ-sử-dụng-thực-tế)

---

## Tổng quan kiến trúc

```
AbstractFlexibleAdapter              ← Quản lý selection, FastScroller, bound ViewHolders, configurations
    └── AbstractFlexibleAnimatorAdapter  ← Quản lý animation khi scroll
            └── FlexibleAdapter<T>       ← Toàn bộ logic chính
                    └── FlexiblePagingAdapter<T>  ← FlexibleAdapter + Paging 3

IFlexible<VH>                   ← Interface mỗi item phải implement
    └── AbstractFlexibleItem<VH>  ← Base class cho item thông thường
            ├── AbstractFlexibleExpandItem<VH, S>    ← Item có thể expand
            ├── AbstractFlexibleSectionableItem<VH, H> ← Item thuộc section/header
            └── AbstractFlexibleHeaderItem<VH>       ← Item header

FlexibleViewHolder               ← Base ViewHolder với hỗ trợ click, drag, swipe
    └── FlexibleExpandableViewHolder ← ViewHolder cho item expandable
```

**Package gốc:** `com.jibase.iflexible`

---

## Interface cốt lõi

### `IFlexible<VH>` – `items/interfaceItems/IFlexible.kt`

Interface bắt buộc mỗi item phải implement. FlexibleAdapter gọi thẳng vào các method này (AutoMap pattern).

| Method | Mô tả |
|--------|-------|
| `isEnabled() / setEnabled(Boolean)` | Bật/tắt tương tác trên item |
| `isHidden() / setHidden(Boolean)` | Dùng nội bộ khi filter hoặc xóa có undo |
| `getSpanSize(spanCount, position): Int` | Số cột item chiếm trong GridLayoutManager |
| `shouldNotifyChange(newItem): Boolean` | Quyết định có rebind ViewHolder khi update không. Default `true` |
| `isSelectable() / setSelectable(Boolean)` | Cho phép chọn item hay không |
| `isDraggable() / setDraggable(Boolean)` | Cho phép kéo item hay không |
| `isSwipeable() / setSwipeable(Boolean)` | Cho phép vuốt item hay không |
| `getBubbleText(position): String` | Text hiển thị trên bubble của FastScroller |
| `getItemViewType(): Int` | Trả về viewType (mặc định là hashCode của class name) |
| `createViewHolder(parent, adapter): VH` | Tạo ViewHolder – được gọi bởi `onCreateViewHolder` |
| `bindViewHolder(adapter, holder, position, payloads)` | Bind dữ liệu – được gọi bởi `onBindViewHolder` |
| `unbindViewHolder(adapter, holder, position)` | Gọi khi ViewHolder bị recycle |
| `onViewAttached(adapter, holder, position)` | Gọi khi view được attach vào window |
| `onViewDetached(adapter, holder, position)` | Gọi khi view bị detach khỏi window |

### `IExpandable<VH, S>` – `items/interfaceItems/IExpandable.kt`

Interface cho item có thể expand/collapse, chứa danh sách sub-items.

| Method | Mô tả |
|--------|-------|
| `isExpanded() / setExpanded(Boolean)` | Trạng thái đang mở hay đóng |
| `getExpansionLevel(): Int` | Level trong cây đa tầng (0 = gốc) |
| `getSubItems(): List<S>` | Danh sách các item con |

### `IHeader<VH>` – `items/interfaceItems/IHeader.kt`

Marker interface cho item đóng vai trò header của một section.

### `ISectionable<VH, H>` – `items/interfaceItems/ISectionable.kt`

Interface cho item thuộc một section (có header).

| Method | Mô tả |
|--------|-------|
| `getHeader(): H?` | Lấy header của item |
| `setHeader(header: IHeader<*>?)` | Gán header cho item |

### `IFilterable` – `items/interfaceItems/IFilterable.kt`

Interface cho item có thể lọc.

| Method | Mô tả |
|--------|-------|
| `filter(constraint: String): Boolean` | Trả về `true` nếu item khớp với từ khóa tìm kiếm |

---

## Abstract base classes

### `AbstractFlexibleItem<VH>` – `items/abstractItems/AbstractFlexibleItem.kt`

Base class cho mọi item. Implement sẵn tất cả các method của `IFlexible` trừ `createViewHolder` và `bindViewHolder`.

**Các flag mặc định:**

| Flag | Mặc định | Ý nghĩa |
|------|-----------|---------|
| `enableItem` | `true` | Item có thể tương tác |
| `enableHidden` | `false` | Item đang hiển thị |
| `enableSelect` | `false` | Item KHÔNG thể chọn (phải set `true` để enable) |
| `enableDrag` | `false` | Item KHÔNG thể kéo |
| `enableSwipe` | `false` | Item KHÔNG thể vuốt |

> **Lưu ý:** Phải override `equals()` để adapter phân biệt các item, đặc biệt khi dùng Filter hoặc DiffUtil.

### `AbstractFlexibleExpandItem<VH, S>` – `items/abstractItems/AbstractFlexibleExpandItem.kt`

Base class cho item expandable. Quản lý danh sách sub-items.

**Các method quản lý sub-items:**

| Method | Mô tả |
|--------|-------|
| `setSubItems(list)` | Gán toàn bộ danh sách con |
| `addSubItem(item)` | Thêm item con vào cuối |
| `addSubItem(position, item)` | Thêm item con tại vị trí |
| `addSubItems(position, list)` | Thêm nhiều item con từ vị trí |
| `removeSubItem(item)` | Xóa item con theo object |
| `removeSubItem(position)` | Xóa item con theo vị trí |
| `removeSubItems(list)` | Xóa nhiều item con |
| `getSubItem(position): S?` | Lấy item con theo vị trí |
| `getSubItemPosition(item): Int` | Lấy vị trí item con |
| `getSubItemsCount(): Int` | Số lượng item con |
| `hasSubItems(): Boolean` | Có item con hay không |
| `contains(item): Boolean` | Kiểm tra tồn tại |

### `AbstractFlexibleSectionableItem<VH, H>` – `items/abstractItems/AbstractFlexibleSectionableItem.kt`

Base class cho item thuộc một section. Mặc định bật `enableSelect = true`.

---

## FlexibleAdapter – API đầy đủ

**Package:** `com.jibase.iflexible.adapter.FlexibleAdapter`

**Khai báo:**
```kotlin
open class FlexibleAdapter<T : IFlexible<*>>(
    var listData: MutableList<T> = mutableListOf(),
    hasStateId: Boolean = false
)
```

### Cơ chế Pending Init

Một số hàm cần RecyclerView đã được attach mới có thể thực thi (vì phụ thuộc vào `ItemTouchHelper`, `LayoutManager`, hoặc `StickyHeaderHelper`). Adapter tự xử lý điều này qua cơ chế **pending init**: nếu gọi trước khi attach, config được lưu nội bộ và **tự động apply** ngay khi `onAttachedToRecyclerView` được gọi – không cần bất kỳ xử lý thêm nào từ phía người dùng.

Các hàm hỗ trợ pending init được đánh dấu bằng **> Pending init:** trong tài liệu này.

```kotlin
// Có thể gọi theo thứ tự bất kỳ – không cần quan tâm đến thời điểm attach
val adapter = FlexibleAdapter(items)
    .setLongPressDragEnabled(true)   // pending nếu RV chưa attach
    .setSwipeEnabled(true)           // pending nếu RV chưa attach
    .setStickyHeaders(true)          // pending nếu RV chưa attach
    .setEndlessScrollThreshold(3)    // pending nếu RV chưa attach

recyclerView.adapter = adapter       // ← tại đây tất cả pending được apply tự động
```

### Selection Modes

```kotlin
FlexibleAdapter.IDLE    // 0 – không chọn gì
FlexibleAdapter.SINGLE  // 1 – chọn một item tại một thời điểm
FlexibleAdapter.MULTI   // 2 – chọn nhiều item
```

---

### Khởi tạo & Lifecycle

#### `addListener(listener: Any): FlexibleAdapter<T>`
Đăng ký listener. Tự động nhận diện loại listener qua `when(listener)`.

```kotlin
adapter.addListener(object : OnItemClickListener {
    override fun onItemClick(adapter: FlexibleAdapter<*>, view: View, position: Int): Boolean {
        // xử lý click
        return true // true = kích hoạt selection highlight
    }
})
```

**Các listener được hỗ trợ:**
- `OnItemClickListener` – click vào item
- `OnItemLongClickListener` – long click
- `OnItemMoveListener` – drag & drop
- `OnItemSwipeListener` – vuốt item
- `OnUpdateListener` – danh sách rỗng/không rỗng
- `OnFilterListener` – kết quả filter
- `OnDeleteCompleteListener` – xóa hoàn tất (dùng với Undo)
- `OnStickyHeaderChangeListener` – sticky header thay đổi

#### `removeListener(listener: Any): FlexibleAdapter<T>`
Hủy đăng ký listener. Với Click/LongClick cũng xóa luôn callback trên ViewHolder hiện có.

#### `release()`
Hủy tất cả coroutine job đang chạy. Gọi khi Fragment/Activity bị destroy nếu cần dọn dẹp thủ công (tự động gọi trong `onDetachedFromRecyclerView`).

#### `setExpandItemsAtStartUp(): FlexibleAdapter<T>`
Expand tất cả item có `isExpanded() = true` khi khởi tạo. Gọi sau khi set adapter cho RecyclerView.

```kotlin
recyclerView.adapter = adapter
adapter.setExpandItemsAtStartUp()
```

---

### Quản lý dữ liệu

#### `updateDataSet(items: List<T>, animate: Boolean = false)`
Thay thế toàn bộ dữ liệu. Nếu `animate = true`, dùng DiffUtil để tính diff và animate.

```kotlin
adapter.updateDataSet(newList)              // không animate
adapter.updateDataSet(newList, animate = true)  // có animate
```

#### `setData(items: List<T>)`
Gán dữ liệu trực tiếp, không notify. Dùng khi khởi tạo trước khi attach RecyclerView.

#### `getItem(position: Int): T?`
Lấy item tại vị trí. Trả về `null` nếu out of bounds.

#### `getItemAsClass<S>(position: Int): S?`
Lấy item và ép kiểu về class `S`. Trả về `null` nếu không cast được.

```kotlin
val myItem = adapter.getItemAsClass<MyItemType>(position)
```

#### `getItemCount(): Int`
Tổng số item (kể cả scrollable header/footer).

#### `getMainItemCount(): Int`
Số item chính (không tính scrollable header/footer và không tính filter).

#### `getItemCountOfTypes(vararg viewTypes: Int): Int`
Đếm số item theo viewType.

#### `getCurrentItems(): List<T>`
Lấy snapshot của danh sách hiện tại (unmodifiable).

#### `isEmpty(): Boolean`
Kiểm tra danh sách rỗng.

#### `contains(item: T): Boolean`
Kiểm tra item có trong danh sách không.

#### `hasPosition(position: Int): Boolean`
Kiểm tra vị trí hợp lệ.

#### Các method tìm vị trí

| Method | Trả về | Mô tả |
|--------|--------|-------|
| `getGlobalPositionOf(item)` | `Int` | Vị trí thực sự trong adapter (dùng cho mọi thao tác) |
| `getCardinalPositionOf(item)` | `Int` | Vị trí không tính scrollable headers |
| `getSameTypePositionOf(item)` | `Int` | Vị trí trong số các item cùng viewType |
| `getSubPositionOf(child)` | `Int` | Vị trí tương đối trong parent/header |

#### `calculatePositionFor(item: T, comparator: Comparator<T>): Int`
Tính toán vị trí phù hợp để chèn item dựa trên Comparator (dùng khi thêm item vào list đã được sắp xếp).

---

### Thêm item

#### `addItem(item: T): Boolean`
Thêm vào cuối danh sách.

#### `addItem(position: Int, item: T): Boolean`
Thêm vào vị trí cụ thể. Nếu vị trí vượt quá, thêm vào cuối.

#### `addItems(position: Int, items: List<T>): Boolean`
Thêm nhiều item từ vị trí cho trước. Tự động hiển thị header nếu headers đang bật.

#### `addItemWithDelay(position, item, delay, scrollToPosition)`
Thêm item sau một khoảng delay (ms). Dùng coroutine nội bộ.

```kotlin
adapter.addItemWithDelay(0, myItem, 500L, scrollToPosition = true)
```

#### `addSubItem(parentPosition, subPosition, item): Boolean`
Thêm một item con vào expandable tại `parentPosition`, ở vị trí `subPosition`.

#### `addSubItems(parentPosition, subPosition, items): Boolean`
Thêm nhiều item con.

#### `addSubItems(parentPosition, subPosition, items, expandParent, payload)`
Thêm item con với option tự động expand parent và notify.

#### `addSection(header: IHeader<*>): Int`
Thêm section header mới (vào đầu danh sách).

#### `addSection(header, comparator): Int`
Thêm section header vào đúng vị trí theo comparator.

#### `addItemToSection(sectionable, header, comparator): Int`
Thêm item vào đúng vị trí trong section (tự tính vị trí).

#### `addItemToSection(sectionable, header, index): Int`
Thêm item vào section tại vị trí tương đối `index` đã biết.

---

### Xóa item

#### `removeItem(position: Int)`
Xóa item tại vị trí. Tự động collapse nếu là expandable. Item được giữ lại cho Undo nếu `permanentDelete = false`.

#### `removeItem(position: Int, payload: Any)`
Xóa và notify parent bằng payload.

#### `removeItems(selectedPositions: List<Int>)`
Xóa nhiều item theo danh sách vị trí. Tự động gom range để tối ưu notification.

#### `removeItemWithDelay(item, delay, permanent)`
Xóa item sau delay. Nếu `permanent = true`, xóa luôn khỏi original list.

#### `removeSection(header: IHeader<*>)`
Xóa toàn bộ section: header và tất cả item thuộc section đó.

#### `removeItemsOfType(vararg viewTypes: Int)`
Xóa tất cả item theo viewType.

#### `removeAllSelectedItems(payload: Any? = null)`
Xóa tất cả item đang được chọn.

#### `removeRange(positionStart, itemCount)`
Xóa dải liên tiếp các item.

#### `clear()`
Xóa tất cả: item chính, scrollable headers và footers.

#### `clearAllBut(vararg viewTypes: Int)`
Xóa tất cả trừ các item có viewType được giữ lại.

#### `setPermanentDelete(permanentDelete: Boolean): FlexibleAdapter<T>`
- `true` (mặc định): item bị xóa hoàn toàn ngay lập tức
- `false`: item được giữ lại để có thể Undo

---

### Cập nhật item

#### `updateItem(item: T, payload: Any? = null)`
Cập nhật item tại vị trí hiện tại của nó. Payload sẽ được truyền vào `bindViewHolder`.

#### `updateItem(position: Int, item: T, payload: Any? = null)`
Cập nhật item tại vị trí cụ thể.

```kotlin
// Cập nhật không partial binding
adapter.updateItem(myItem)

// Cập nhật có partial binding (chỉ cập nhật phần thay đổi)
adapter.updateItem(myItem, payload = "PRICE_CHANGED")
```

---

### Di chuyển & Swap

#### `moveItem(fromPosition, toPosition, payload?)`
Di chuyển item từ vị trí này sang vị trí khác. Tự xử lý selection, expand/collapse.

#### `swapItems(list, fromPosition, toPosition)`
Hoán đổi hai item (dùng trong Drag & Drop để cập nhật vị trí khi kéo). Tự xử lý selection và header linkage.

---

### Selection – Chọn item

#### `setMode(mode: Int): FlexibleAdapter<T>`
Đặt chế độ chọn: `IDLE`, `SINGLE`, `MULTI`.

#### `toggleSelection(position: Int)`
Bật/tắt trạng thái chọn của item tại vị trí. Tự động xử lý logic parent/child selection.

#### `selectAll(vararg viewTypes: Int)`
Chọn tất cả item (hoặc chỉ các item theo viewType). Nếu đã có item được chọn và không truyền viewType, sẽ chọn theo viewType của item đầu tiên.

#### `clearSelection()`
Bỏ chọn tất cả.

#### `addSelection(position, hasNotify): Boolean`
Thêm vị trí vào danh sách đã chọn.

#### `removeSelection(position, hasNotify): Boolean`
Xóa vị trí khỏi danh sách đã chọn.

#### `isSelected(position): Boolean`
Kiểm tra item tại vị trí có đang được chọn không.

#### `isSelectable(position): Boolean`
Kiểm tra item có thể được chọn không.

#### `getSelectedItemCount(): Int`
Số item đang được chọn.

#### `getSelectedPositions(): List<Int>`
Danh sách vị trí đang được chọn (sorted copy).

#### `getSelectedPositionsAsSet(): Set<Int>`
Set vị trí đang được chọn.

#### `getSelectedItems(): List<T>`
Danh sách item đang được chọn.

#### `isAnyParentSelected(): Boolean`
Kiểm tra có parent nào đang được chọn không.

#### `isAnyChildSelected(): Boolean`
Kiểm tra có child nào đang được chọn không.

#### Lưu/Khôi phục state selection

```kotlin
// Lưu
override fun onSaveInstanceState(outState: Bundle) {
    super.onSaveInstanceState(outState)
    adapter.onSaveInstanceState(outState)
}

// Khôi phục
override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    savedInstanceState?.let { adapter.onRestoreInstanceState(it) }
}
```

---

### Expandable – Item có thể mở rộng

**Cấu hình:**

#### `setAutoCollapseOnExpand(collapse: Boolean): FlexibleAdapter<T>`
Tự động collapse item đang mở khi mở item khác. Default: `false`.

#### `setRecursiveCollapse(collapse: Boolean): FlexibleAdapter<T>`
Khi collapse một parent, cũng collapse các sub-expandable bên trong. Default: `false`.

#### `setAutoScrollOnExpand(scroll: Boolean): FlexibleAdapter<T>`
Tự động scroll để item expand nằm ở đầu màn hình. Default: `false`.

#### `setMinCollapsibleLevel(level: Int): FlexibleAdapter<T>`
Level tối thiểu để auto-collapse sub-expandables.

**Kiểm tra:**

#### `isExpanded(position): Boolean` / `isExpanded(item): Boolean`
Item có đang mở rộng không.

#### `isExpandableItem(item): Boolean`
Item có implement `IExpandable` không.

#### `hasSubItems(expandable): Boolean`
Expandable có chứa item con không.

**Thao tác:**

#### `expand(item, expandAll, init, notifyParent): Int`
#### `expand(position, expandAll, init, notifyParent): Int`
Mở rộng item. Trả về số sub-items được thêm vào.

```kotlin
adapter.expand(position)
adapter.expand(myExpandableItem, notifyParent = true)
```

#### `collapse(position, notifyParent): Int`
Thu gọn item. Trả về số sub-items bị xóa.

#### `expandAll(level): Int`
Mở rộng tất cả expandable từ level cho trước. Trả về số parent được expand.

#### `collapseAll(level): Int`
Thu gọn tất cả expandable.

**Tìm quan hệ parent/child:**

| Method | Mô tả |
|--------|-------|
| `getExpandableOf(position/item): IExpandable?` | Lấy parent của một child |
| `getExpandablePositionOf(child): Int` | Vị trí của parent |
| `getSubPositionOf(child): Int` | Vị trí tương đối của child trong parent |
| `getSiblingsOf(child): List<T>` | Danh sách item cùng cấp với child |
| `getExpandedItems(): List<T>` | Tất cả item đang expand |
| `getExpandedPositions(): List<Int>` | Vị trí tất cả item đang expand |
| `getCurrentChildren(expandable): List<T>` | Item con chưa bị xóa của parent |

---

### Header / Section

#### `setDisplayHeadersAtStartUp(displayHeaders: Boolean): FlexibleAdapter<T>`
Hiển thị tất cả header khi khởi tạo (không animate, load cùng item). Default: `false`.

#### `setHeadersShown(headersShown: Boolean): FlexibleAdapter<T>`
Báo cho adapter biết headers đã được thêm thủ công vào list (bỏ qua auto-insert).

#### `showAllHeaders(init: Boolean = false): FlexibleAdapter<T>`
Hiển thị tất cả header. Nếu `init = false`, animate insertion.

#### `hideAllHeaders()`
Ẩn tất cả header.

#### `setUnlinkAllItemsOnRemoveHeaders(unlink: Boolean): FlexibleAdapter<T>`
Khi xóa header, tự động unlink các item thuộc header đó. Default: `false`.

**Kiểm tra:**

| Method | Mô tả |
|--------|-------|
| `isHeader(item)` | Item có phải header không |
| `isHeader(position)` | Item tại vị trí có phải header không |
| `hasHeader(item)` | Item có header không |
| `hasSameHeader(item, header)` | Item có chung header không |
| `areHeadersShown()` | Các header có đang hiển thị không |

**Lấy dữ liệu:**

| Method | Mô tả |
|--------|-------|
| `getHeaderItems(): List<IHeader<*>>` | Tất cả header items |
| `getHeaderOf(item): IHeader<*>?` | Header của item |
| `getSectionHeader(position): IHeader<*>?` | Header của section tại vị trí |
| `getSectionItems(header): List<ISectionable<*,*>>` | Items thuộc section |
| `getSectionItemPositions(header): List<Int>` | Vị trí các items trong section |

---

### Scrollable Header & Footer

Header/Footer cố định: luôn ở đầu/cuối, không bị filter, không thể chọn/kéo.

#### `addScrollableHeader(headerItem: T): Boolean`
Thêm scrollable header. Trả về `false` nếu đã tồn tại.

#### `addScrollableFooter(footerItem: T): Boolean`
Thêm scrollable footer.

#### `removeScrollableHeader(headerItem: T)`
#### `removeScrollableFooter(footerItem: T)`
Xóa header/footer.

#### `removeAllScrollableHeaders()` / `removeAllScrollableFooters()`
Xóa tất cả.

#### `addScrollableHeaderWithDelay(headerItem, delay, scrollToPosition)`
#### `addScrollableFooterWithDelay(footerItem, delay, scrollToPosition)`
Thêm sau delay.

#### `removeScrollableHeaderWithDelay(headerItem, delay)`
#### `removeScrollableFooterWithDelay(footerItem, delay)`
Xóa sau delay.

#### `getScrollableHeaders(): List<T>` / `getScrollableFooters(): List<T>`
Lấy danh sách hiện tại.

#### `isScrollableHeaderOrFooter(position/item): Boolean`
Kiểm tra item có phải scrollable header/footer không.

---

### Filter – Tìm kiếm

#### `setFilter(filter: String = "")`
Đặt từ khóa tìm kiếm (tự động trim và lowercase).

#### `getFilter(): String`
Lấy từ khóa hiện tại.

#### `hasFilter(): Boolean`
Kiểm tra có filter đang active không.

#### `hasNewFilter(constraint: String): Boolean`
Kiểm tra filter mới có khác filter cũ không.

#### `filterItems(delay: Long = 0)`
Lọc danh sách bằng filter hiện tại. Nếu `mOriginalList` rỗng, tự lưu bản gốc trước.

#### `filterItems(unfilteredItems: List<T>, delay: Long = 0)`
Lọc từ danh sách cụ thể. Nên dùng overload này để full control.

#### `isFiltering(): Boolean`
Kiểm tra filter đang chạy không.

#### `setNotifyChangeOfUnfilteredItems(notifyChange: Boolean): FlexibleAdapter<T>`
Khi item không bị filter ra: `true` = vẫn rebind ViewHolder để cập nhật nội dung. Default: `true`.

#### `setNotifyMoveOfFilteredItems(notifyMove: Boolean): FlexibleAdapter<T>`
Animate item di chuyển trong filter. **Cảnh báo:** rất chậm với list lớn (~3000+ items). Default: `false`.

**Item cần implement `IFilterable`:**

```kotlin
class MyItem : AbstractFlexibleItem<MyViewHolder>(), IFilterable {
    override fun filter(constraint: String): Boolean {
        return name.lowercase().contains(constraint)
    }
}
```

**Ví dụ tìm kiếm:**

```kotlin
searchView.addTextChangedListener { text ->
    adapter.setFilter(text.toString())
    adapter.filterItems(delay = 300L) // debounce 300ms
}

// Xóa filter
adapter.setFilter()
adapter.filterItems()
```

**DiffUtil:**

#### `setAnimateChangesWithDiffUtil(useDiffUtil: Boolean): FlexibleAdapter<T>`
Dùng DiffUtil thay vì thuật toán tính diff tự viết. Default: `true`.

#### `setDiffUtilCallback(diffUtilCallback: D): FlexibleAdapter<T>`
Đặt custom `FlexibleDiffCallback` để kiểm soát logic so sánh item.

#### `setAnimateToLimit(limit: Int): FlexibleAdapter<T>`
Nếu số item mới vượt quá limit, dùng `notifyDataSetChanged` thay vì animate. Default: 1000.

---

### Endless Scroll – Tải thêm

#### `setEndlessScrollListener(listener: EndlessScrollListener, progressItem: T): FlexibleAdapter<T>`
Bật tính năng tải thêm tự động khi scroll gần cuối. `progressItem` là item loading indicator.

```kotlin
adapter.setEndlessScrollListener(object : EndlessScrollListener {
    override fun onLoadMore(adapter: FlexibleAdapter<*>, lastPosition: Int, currentPage: Int) {
        // gọi API, sau đó:
        adapter.onLoadMoreComplete(newItems)
    }

    override fun noMoreLoad(adapter: FlexibleAdapter<*>, newItemsSize: Int) {
        // không còn dữ liệu
    }
}, progressItem)
```

#### `setEndlessProgressItem(progressItem: T?): FlexibleAdapter<T>`
Bật loading indicator mà không có auto-scroll callback (để tự xử lý load theo nhu cầu).

#### `onLoadMoreComplete(newItems: List<T>, delay: Long = 0L)`
Gọi sau khi load xong. Tự xử lý: ẩn progress, thêm item mới, kiểm tra limits.

| Trường hợp | Cách gọi |
|-----------|---------|
| Có item mới | `onLoadMoreComplete(newItems)` |
| Hết dữ liệu | `onLoadMoreComplete(emptyList())` |
| Lỗi network, giữ progress một lúc | `onLoadMoreComplete(emptyList(), delay = 2000L)` |
| Disable endless vĩnh viễn | `onLoadMoreComplete(emptyList(), delay = -1L)` |

#### `setEndlessScrollThreshold(thresholdItems: Int): FlexibleAdapter<T>`
Số item còn lại khi bắt đầu load more. Default: 1.
> **Pending init:** Nếu gọi trước khi attach, giá trị được lưu lại và apply sau khi RV attach để spanCount của GridLayoutManager được tính đúng.

#### `setEndlessPageSize(endlessPageSize: Int): FlexibleAdapter<T>`
Tự động disable endless khi số item nhận về nhỏ hơn page size.

#### `setEndlessTargetCount(endlessTargetCount: Int): FlexibleAdapter<T>`
Tự động disable endless khi tổng số item đạt ngưỡng.

#### `setTopEndless(topEndless: Boolean)`
Load more từ trên (true) hay dưới (false, mặc định).

#### `setLoadingMoreAtStartUp(enable: Boolean): FlexibleAdapter<T>`
Kích hoạt load more ngay khi khởi tạo (kể cả khi list rỗng).

#### `isEndlessScrollEnabled(): Boolean`
Endless scroll có đang bật không.

#### `isEndlessLoading(): Boolean`
Đang trong quá trình load thêm không.

#### `getEndlessCurrentPage(): Int`
Trang hiện tại (chỉ tính khi đã set `endlessPageSize`).

---

### Drag & Drop / Swipe

> **Pending init:** Các hàm trong nhóm này có thể gọi trước khi adapter attach vào RecyclerView. Config sẽ được lưu lại và tự động apply khi RecyclerView attach.

#### `setLongPressDragEnabled(enabled: Boolean): FlexibleAdapter<T>`
Bật kéo item bằng long press. Default: `false`.

#### `setHandleDragEnabled(enabled: Boolean): FlexibleAdapter<T>`
Bật kéo item bằng handle view. Default: `false`.

#### `setSwipeEnabled(enabled: Boolean): FlexibleAdapter<T>`
Bật vuốt item. Default: `false`.

#### `setItemTouchHelperCallback(callback: ItemTouchHelperCallback): FlexibleAdapter<T>`
Đặt custom callback để kiểm soát hành vi drag/swipe.

#### `getItemTouchHelper(): ItemTouchHelper?`
Lấy `ItemTouchHelper` đã khởi tạo. Trả về `null` nếu RV chưa attach.

**ViewHolder cần setup:**

```kotlin
class MyViewHolder(view: View, adapter: FlexibleAdapter<*>) : FlexibleViewHolder(view, adapter) {
    init {
        // Drag bằng handle view
        setDragHandleView(view.findViewById(R.id.dragHandle))
    }

    // Swipe: override các view
    override fun getFrontView() = itemView.frontLayout
    override fun getRearLeftView() = itemView.rearLeft
    override fun getRearRightView() = itemView.rearRight
}
```

**Item cần enable:**

```kotlin
myItem.setDraggable(true)
myItem.setSwipeable(true)
```

**Listener:**

```kotlin
adapter.onItemMoveListener = object : OnItemMoveListener {
    override fun onItemMove(adapter: FlexibleAdapter<*>, fromPosition: Int, toPosition: Int): Boolean {
        // Gọi swapItems trong khi kéo
        (adapter as FlexibleAdapter<MyItem>).swapItems(adapter.listData, fromPosition, toPosition)
        return true
    }
    override fun onItemSwapped(fromPosition: Int, toPosition: Int) { /* hoàn tất */ }
}

adapter.onItemSwipeListener = object : OnItemSwipeListener {
    override fun onItemSwipe(position: Int, direction: Int) {
        // Xử lý sau khi vuốt
    }
    override fun onActionStateChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {}
}
```

---

### Animation

#### `setAnimationOnForwardScrolling(enabled: Boolean)`
Bật animation khi scroll xuống. Default: `false`.

#### `setAnimationOnReverseScrolling(enabled: Boolean)`
Bật animation khi scroll lên. Default: `false`.

#### `setOnlyEntryAnimation(enabled: Boolean)`
Chỉ animate lần đầu khi màn hình được fill đầy item. Default: `false`.

#### `setAnimationDuration(duration: Long)`
Thời gian animation. Default: 300ms.

#### `setAnimationDelay(delay: Long)`
Khoảng trễ giữa các item animation. Default: 100ms.

#### `setAnimationInitialDelay(initialDelay: Long)`
Trễ ban đầu cho item đầu tiên. Default: 0ms.

#### `setAnimationEntryStep(entryStep: Boolean)`
Dùng step delay giữa các item khi load ban đầu. Default: `true`. Nên `false` với GridLayout.

#### `setAnimationInterpolator(interpolator: Interpolator)`
Đặt interpolator. Default: `LinearInterpolator`.

**Custom scroll animation trong ViewHolder:**

```kotlin
override fun scrollAnimators(animators: MutableList<Animator>, position: Int, isForward: Boolean) {
    AnimatorHelper.alphaAnimator(animators, itemView, 0f)
    AnimatorHelper.slideInFromBottomAnimator(animators, itemView, recyclerView)
}
```

---

### Sticky Header

Yêu cầu header item được tạo bằng `FlexibleViewHolder(view, adapter, isStickyHeader = true)`.

#### `setStickyHeaders(sticky: Boolean): FlexibleAdapter<T>`
Bật/tắt sticky headers với container mặc định (tự tạo).
> **Pending init:** Có thể gọi trước khi attach. Config được lưu lại và apply khi RV attach.

#### `setStickyHeaders(sticky: Boolean, stickyContainer: ViewGroup?): FlexibleAdapter<T>`
Bật/tắt sticky headers với custom container đã inflate sẵn.
> **Pending init:** Có thể gọi trước khi attach. Cả `sticky` lẫn `stickyContainer` đều được lưu lại.

#### `setStickyHeaderElevation(stickyElevation: Int): FlexibleAdapter<T>`
Độ nổi (elevation) của sticky header. Default: 0.

#### `areHeadersSticky(): Boolean`
Sticky header có đang bật không.

#### `getStickyPosition(): Int`
Vị trí sticky header đang hiển thị (-1 nếu không có).

#### `ensureHeaderParent()`
Đảm bảo sticky header hiển thị đúng vị trí.

**Layout yêu cầu:**

```xml
<FrameLayout
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/recyclerView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"/>

</FrameLayout>
```

---

### Undo – Hoàn tác

#### `setPermanentDelete(false)`
Bắt buộc phải set `false` để enable Undo. Khi đó item bị xóa vẫn còn trong cache nội bộ.

#### `restoreDeletedItems()`
Khôi phục các item đã xóa từ cache.

#### `confirmDeletion()`
Xác nhận xóa vĩnh viễn (thường được gọi tự động qua `UndoHelper`).

#### `emptyBin()`
Xóa cache Undo, giải phóng bộ nhớ.

#### `isRestoreInTime(): Boolean`
Còn item trong cache Undo không.

#### `getDeletedItems(): List<T>`
Danh sách item đang chờ trong cache Undo.

#### `getDeletedChildren(expandable): List<T>`
Item con đã xóa của một expandable cụ thể.

#### `setRestoreSelectionOnUndo(restore: Boolean): FlexibleAdapter<T>`
Khi undo, có khôi phục trạng thái selection không. Default: `false`.

---

### Fast Scroller

#### `setFastScroller(fastScroller: FastScroller)`
Gán FastScroller. Phải gọi sau khi adapter đã attach vào RecyclerView.

#### `toggleFastScroller()`
Hiện/ẩn FastScroller (có animation).

#### `isFastScrollerEnabled(): Boolean`
FastScroller có đang bật không.

#### `getFastScroller(): FastScroller?`
Lấy instance hiện tại.

**Custom bubble text:** Override trong item:
```kotlin
override fun getBubbleText(position: Int): String = firstLetter
```

---

### Configurations

Key-value store gắn vào adapter, dùng để truyền cấu hình tuỳ ý từ bên ngoài vào mà không cần subclass hay biến riêng.

#### `setConfig(key: String, value: Any)`
Lưu một giá trị vào map theo key.

#### `setConfigs(map: Map<String, Any>)`
Lưu nhiều cặp key-value cùng lúc.

#### `getConfig<T>(key: String): T?`
Lấy giá trị theo key và tự cast sang kiểu `T`. Trả về `null` nếu key không tồn tại hoặc giá trị không thể cast.

#### `removeConfig(key: String)`
Xoá một key khỏi map.

#### `clearConfigs()`
Xoá toàn bộ configurations.

**Ví dụ:**
```kotlin
adapter.setConfig("showDivider", true)
adapter.setConfig("pageSize", 20)
adapter.setConfigs(mapOf("theme" to "dark", "maxRetry" to 3))

val showDivider = adapter.getConfig<Boolean>("showDivider")  // true
val pageSize    = adapter.getConfig<Int>("pageSize")         // 20
val invalid     = adapter.getConfig<Boolean>("pageSize")     // null — Int không cast sang Boolean
```

---

## FlexiblePagingAdapter – Paging 3

**Package:** `com.jibase.iflexible.adapter.FlexiblePagingAdapter`

**Khai báo:**
```kotlin
open class FlexiblePagingAdapter<T : IFlexible<*>>(
    diffCallback: DiffUtil.ItemCallback<T>,
    hasStateId: Boolean = false
) : FlexibleAdapter<T>(mutableListOf(), hasStateId)
```

### Cơ chế hoạt động

`FlexiblePagingAdapter` kế thừa trực tiếp từ `FlexibleAdapter`, giữ nguyên toàn bộ tính năng đã cài đặt. Paging 3 được tích hợp thông qua `AsyncPagingDataDiffer`:

```
submitData(PagingData<T>)
    └── AsyncPagingDataDiffer          ← quản lý ordering & diffing của paging items
            └── ListUpdateCallback     ← syncListData() trước mỗi notifyItem*
                    └── listData       ← headers + paging snapshot + footers
                            └── notifyItem* offset theo headers.size
```

**Nguyên tắc:**
- `listData` = scrollable headers + paging items + scrollable footers → `getItemCount()` trả về `listData.size`
- `syncListData()` rebuild `listData` từ `mScrollableHeaders + differ.snapshot().items + mScrollableFooters` **trước** mỗi lần notify, đảm bảo headers/footers không bị mất sau mỗi page load
- Positions từ differ (relative to paging items) được offset thêm `scrollableHeaders.size` trước khi notify RecyclerView
- `enablePlaceholders = false` trong `PagingConfig` được khuyến nghị để tránh null trong `listData`

---

### API

#### Paging 3

| Method | Mô tả |
|--------|-------|
| `submitData(pagingData: PagingData<T>)` | Suspend – submit data mới, dùng trong coroutine |
| `submitData(lifecycle, pagingData)` | Non-suspend – tự cancel khi lifecycle destroy |
| `loadStateFlow: Flow<CombinedLoadStates>` | Observe trạng thái load (refresh / prepend / append) |
| `addLoadStateListener(listener)` | Đăng ký listener trạng thái |
| `removeLoadStateListener(listener)` | Hủy listener |
| `retry()` | Retry lần load cuối bị lỗi |
| `refresh()` | Invalidate PagingData và load lại từ đầu |

#### Progress Item (bottom loading indicator)

#### `setEndlessProgressItem(progressItem: T?): FlexiblePagingAdapter<T>`
Đặt item hiển thị ở cuối list khi Paging 3 đang load thêm trang mới.

- Tự động được thêm vào `ScrollableFooter` khi `loadState.append is LoadState.Loading`
- Tự động bị xóa khi load xong hoặc lỗi
- Có thể gọi trước hoặc sau khi attach RecyclerView
- Observation tự start khi RV attach, tự cancel khi RV detach
- Pass `null` để tắt

```kotlin
adapter.setEndlessProgressItem(ProgressItem())
```

#### "No more data" footer

Không có API riêng — tự quản lý bằng `addScrollableFooter` / `removeScrollableFooter` trong `addLoadStateListener`:

```kotlin
adapter.addLoadStateListener { states ->
    val append = states.append
    if (append is LoadState.NotLoading && append.endOfPaginationReached) {
        adapter.addScrollableFooter(noMoreDataItem)
    } else {
        adapter.removeScrollableFooter(noMoreDataItem)
    }
}
```

#### Disabled (no-op)

Các method sau bị override thành no-op vì Paging 3 đảm nhận:

| Method | Lý do |
|--------|-------|
| `onLoadMore(position)` | Paging 3 tự trigger load khi gần cuối |
| `onLoadMoreComplete(newItems, delay)` | Paging 3 tự insert item mới qua differ |
| `setTopEndless(topEndless)` | Top endless không hỗ trợ trong paging mode |

---

### Tính năng giữ lại & xung đột

**Giữ nguyên hoàn toàn:**

| Tính năng | Ghi chú |
|-----------|---------|
| Expandable | State lưu trong item object, không phụ thuộc vị trí |
| Sticky / Scrollable Headers & Footers | `listData` được rebuild kèm headers/footers sau mỗi page load — không bị mất |
| Selection, ActionMode | Hoạt động bình thường; reset khi Paging 3 `refresh()` |
| Animation (scroll) | Không phụ thuộc `listData` |
| Click / LongClick listeners | Không phụ thuộc `listData` |
| Drag & Drop (UI) | Swap hoạt động, nhưng xem lưu ý bên dưới |
| Section Headers (nếu là phần của paged data) | Sync cùng với items |
| Pending Init | Tất cả pending config vẫn apply khi RV attach |

**Xung đột về nghĩa (không nên dùng):**

| Tính năng | Vấn đề |
|-----------|--------|
| `filterItems()` – client-side | `syncListData()` ghi đè `listData` mỗi khi Paging 3 load → filter bị reset. **Dùng server-side filter qua query param trong PagingSource** |
| `setEndlessScrollListener()` | Duplicate với Paging 3's loading, không nên dùng cả hai |
| Drag & Drop reorder vĩnh viễn | Thứ tự bị reset sau `refresh()`. Phù hợp nếu chỉ reorder tạm thời trong session |
| `removeItem()` + Undo | Item bị xóa local sẽ quay lại sau `refresh()`. Cần `RemoteMediator` để đồng bộ server |

---

## ViewHolder

### `FlexibleViewHolder` – `viewholder/FlexibleViewHolder.kt`

Base ViewHolder cho tất cả item. Tự xử lý click, long click, drag handle, swipe, selection highlight.

**Constructor:**
```kotlin
abstract class MyViewHolder(
    view: View,
    adapter: FlexibleAdapter<*>,
    isStickyHeader: Boolean = false // true nếu dùng cho sticky header
) : FlexibleViewHolder(view, adapter, isStickyHeader)
```

**Methods có thể override:**

| Method | Mô tả |
|--------|-------|
| `getActivationElevation(): Float` | Elevation khi item được chọn. Default: 0 |
| `shouldActivateViewWhileSwiping(): Boolean` | Highlight khi vuốt. Default: `false` |
| `shouldActivateViewWhileDragging(): Boolean` | Highlight khi kéo. Default: `true` |
| `shouldAddSelectionInActionMode(): Boolean` | Thêm vào selection khi kéo trong ActionMode. Default: `false` |
| `scrollAnimators(animators, position, isForward)` | Custom animation khi scroll |
| `setFullSpan(enabled)` | Full span trong StaggeredGridLayoutManager |
| `setDragHandleView(view)` | Đặt view làm handle để kéo |
| `getFrontView(): View` | View chính (front) khi swipe. Default: `itemView` |
| `getRearLeftView(): View?` | View bên trái khi swipe. Default: `null` |
| `getRearRightView(): View?` | View bên phải khi swipe. Default: `null` |

### `FlexibleExpandableViewHolder` – `viewholder/FlexibleExpandableViewHolder.kt`

ViewHolder dành riêng cho expandable items.

### `BindingItem<VH>` – `viewholder/BindingItem.kt`

Wrapper tiện lợi dùng ViewBinding trong item.

---

## Helpers

### ActionModeHelper

**Package:** `com.jibase.iflexible.helpers.ActionModeHelper`

Quản lý ActionMode (Context Action Bar) trong RecyclerView.

**Khởi tạo:**

```kotlin
val actionModeHelper = ActionModeHelper(
    targetActivity = this,
    adapter = adapter,
    cabMenu = R.menu.menu_action_mode,
    callback = object : ActionModeHelper.ActionModeListener {
        override fun onUpdateSelectionTitle(mode: ActionMode?, count: Int) {
            mode?.title = "$count selected"
        }
        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            when (item?.itemId) {
                R.id.action_delete -> deleteSelected()
            }
            return true
        }
    }
)
```

**Cấu hình:**

| Method | Mô tả |
|--------|-------|
| `withDefaultMode(mode)` | Mode sau khi thoát ActionMode (`IDLE` hoặc `SINGLE`). Default: `IDLE` |
| `enableFinishIfNoneSelected(true)` | Tự thoát ActionMode khi không còn item nào được chọn |
| `enableFinishToClickActionItem(true)` | Tự thoát ActionMode khi click vào action item |
| `enableActionModeWhenLongPress(true)` | Khởi động ActionMode khi long press |
| `disableDragOnActionMode(true)` | Tắt drag trong ActionMode |
| `disableSwipeOnActionMode(true)` | Tắt swipe trong ActionMode |

**Sử dụng trong listener:**

```kotlin
adapter.addListener(object : OnItemClickListener {
    override fun onItemClick(adapter: FlexibleAdapter<*>, view: View, position: Int): Boolean {
        return if (actionModeHelper.isActionModeStarted()) {
            actionModeHelper.onItemClick(position)
            true
        } else false
    }
})

adapter.addListener(object : OnItemLongClickListener {
    override fun onItemLongClick(adapter: FlexibleAdapter<*>, position: Int) {
        actionModeHelper.onItemLongClick(position)
    }
})
```

**Các method chính:**

| Method | Mô tả |
|--------|-------|
| `startActionMode()` | Khởi động ActionMode thủ công |
| `destroyActionModeIfCan(): Boolean` | Hủy ActionMode nếu đang active |
| `isActionModeStarted(): Boolean` | Có đang trong ActionMode không |
| `getActionMode(): ActionMode?` | Lấy instance ActionMode hiện tại |
| `getActivatePosition(): Int` | Vị trí đang chọn khi mode `SINGLE` |
| `toggleSelection(position)` | Toggle selection và cập nhật title |
| `selectAll(vararg viewTypes)` | Chọn tất cả |

---

### UndoHelper

**Package:** `com.jibase.iflexible.helpers.UndoHelper`

Hiển thị Snackbar và quản lý Undo/Redo cho thao tác xóa.

**Khởi tạo:**

```kotlin
val undoHelper = UndoHelper(adapter, object : OnUndoActionListener {
    override fun onActionCanceled(adapter: FlexibleAdapter<*>, action: Int, positions: List<Int>) {
        // User bấm "UNDO" → khôi phục item
        adapter.restoreDeletedItems()
    }

    override fun onActionConfirm(adapter: FlexibleAdapter<*>, action: Int, event: Int) {
        // Snackbar hết thời gian hoặc bị dismiss → xác nhận xóa vĩnh viễn
        // Gọi API xóa khỏi database ở đây
    }
})
```

**Sử dụng:**

```kotlin
// Trước tiên phải bật cache Undo
adapter.setPermanentDelete(false)

// Xóa và hiển thị Snackbar
undoHelper
    .withAction(UndoHelper.Action.REMOVE)
    .withConsecutive(false) // false = gom tất cả undo vào một lần
    .start(
        positions = adapter.getSelectedPositions(),
        targetView = rootView,
        message = "Đã xóa ${count} item",
        actionText = "HOÀN TÁC",
        duration = Snackbar.LENGTH_LONG
    )
```

**Cấu hình:**

| Method | Mô tả |
|--------|-------|
| `withAction(action)` | `Action.REMOVE` (default) hoặc `Action.UPDATE` |
| `withPayload(payload)` | Payload để notify parent khi xóa |
| `withConsecutive(true)` | Mỗi lần Undo xử lý riêng lẻ. `false` = gom lại xử lý 1 lần |
| `withActionTextColor(color)` | Màu text nút action |
| `withBackgroundColor(color)` | Màu nền Snackbar |
| `withTextColor(color)` | Màu text message |

---

## Listeners

| Interface | Callback | Mô tả |
|-----------|----------|-------|
| `OnItemClickListener` | `onItemClick(adapter, view, position): Boolean` | Click vào item. Return `true` để activate selection |
| `OnItemLongClickListener` | `onItemLongClick(adapter, position)` | Long click vào item |
| `OnUpdateListener` | `onUpdateEmptyView(adapter, size)` | Gọi khi danh sách thay đổi trạng thái rỗng/không rỗng |
| `OnFilterListener` | `onUpdateFilterView(adapter, size)` | Gọi sau khi filter hoàn tất |
| `OnItemMoveListener` | `onItemMove(adapter, from, to): Boolean`, `onItemSwapped(from, to)` | Drag & Drop events |
| `OnItemSwipeListener` | `onItemSwipe(position, direction)`, `onActionStateChanged(...)` | Swipe events |
| `EndlessScrollListener` | `onLoadMore(adapter, lastPosition, currentPage)`, `noMoreLoad(adapter, newItemsSize)` | Endless scroll events |
| `OnDeleteCompleteListener` | `onDeleteConfirmed(event)` | Dùng nội bộ bởi UndoHelper |
| `OnStickyHeaderChangeListener` | `onStickyHeaderChange(adapter, newPosition, oldPosition)` | Sticky header thay đổi |
| `OnUndoActionListener` | `onActionCanceled(...)`, `onActionConfirm(...)` | Undo events |

---

## Ví dụ sử dụng thực tế

### 1. Setup cơ bản

```kotlin
// Item class
class MyItem(val id: Int, val name: String) : AbstractFlexibleItem<MyItem.ViewHolder>() {

    init {
        setSelectable(true)
    }

    override fun equals(other: Any?): Boolean {
        if (other is MyItem) return id == other.id
        return false
    }

    override fun hashCode() = id

    override fun shouldNotifyChange(newItem: IFlexible<*>): Boolean {
        return newItem is MyItem && name != newItem.name
    }

    override fun createViewHolder(parent: ViewGroup, adapter: FlexibleAdapter<*>): ViewHolder {
        val binding = ItemMyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding, adapter)
    }

    override fun bindViewHolder(adapter: FlexibleAdapter<*>, holder: RecyclerView.ViewHolder, position: Int, payloads: List<*>) {
        (holder as ViewHolder).binding.tvName.text = name
    }

    class ViewHolder(val binding: ItemMyBinding, adapter: FlexibleAdapter<*>) :
        FlexibleViewHolder(binding.root, adapter)
}

// Fragment/Activity
val adapter = FlexibleAdapter(items)
    .addListener(this)  // implement OnItemClickListener

recyclerView.adapter = adapter
recyclerView.layoutManager = LinearLayoutManager(context)
```

### 2. Expandable list

```kotlin
// Child item
class ChildItem(val id: Int, val title: String) : AbstractFlexibleItem<ChildViewHolder>() { ... }

// Parent item (expandable)
class ParentItem(
    val id: Int,
    val title: String,
    children: List<ChildItem>
) : AbstractFlexibleExpandItem<ParentViewHolder, ChildItem>(children.toMutableList()) {

    override fun createViewHolder(...) = ParentViewHolder(...)
    override fun bindViewHolder(...) { ... }
}

// Sử dụng
val items = mutableListOf<IFlexible<*>>()
items.add(ParentItem(1, "Section A", listOf(ChildItem(1, "Child 1"), ChildItem(2, "Child 2"))))

val adapter = FlexibleAdapter<IFlexible<*>>(items)
    .setAutoCollapseOnExpand(true)
    .setAutoScrollOnExpand(true)

recyclerView.adapter = adapter
adapter.setExpandItemsAtStartUp()
```

### 3. Filter/Search

```kotlin
// Item implement IFilterable
class SearchableItem(val id: Int, val name: String) :
    AbstractFlexibleItem<SearchViewHolder>(), IFilterable {

    override fun filter(constraint: String) = name.lowercase().contains(constraint)
    override fun equals(other: Any?) = other is SearchableItem && id == other.id
    override fun hashCode() = id
    ...
}

// Trong Fragment
searchView.addTextChangedListener { text ->
    adapter.setFilter(text.toString())
    adapter.filterItems(delay = 300L)
}
```

### 4. ActionMode + Undo xóa

```kotlin
class MyFragment : Fragment(), OnItemClickListener, OnItemLongClickListener {

    private lateinit var adapter: FlexibleAdapter<MyItem>
    private lateinit var actionModeHelper: ActionModeHelper
    private lateinit var undoHelper: UndoHelper

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        adapter = FlexibleAdapter(items)
            .addListener(this)
            .setPermanentDelete(false)

        actionModeHelper = ActionModeHelper(
            requireActivity() as AppCompatActivity, adapter, R.menu.menu_cab
        ).withDefaultMode(FlexibleAdapter.IDLE)
         .enableFinishIfNoneSelected(true)

        undoHelper = UndoHelper(adapter, object : OnUndoActionListener {
            override fun onActionCanceled(adapter: FlexibleAdapter<*>, action: Int, positions: List<Int>) {
                adapter.restoreDeletedItems()
            }
            override fun onActionConfirm(adapter: FlexibleAdapter<*>, action: Int, event: Int) {
                // Xóa khỏi database
            }
        })
    }

    override fun onItemClick(adapter: FlexibleAdapter<*>, view: View, position: Int): Boolean {
        return if (actionModeHelper.isActionModeStarted()) {
            actionModeHelper.onItemClick(position)
            true
        } else false
    }

    override fun onItemLongClick(adapter: FlexibleAdapter<*>, position: Int) {
        actionModeHelper.onItemLongClick(position)
    }

    private fun deleteSelected() {
        undoHelper
            .withAction(UndoHelper.Action.REMOVE)
            .start(
                positions = adapter.getSelectedPositions(),
                targetView = requireView(),
                message = "Deleted",
                actionText = "UNDO"
            )
        actionModeHelper.destroyActionModeIfCan()
    }
}
```

### 5. Endless Scroll

```kotlin
// Progress item (loading indicator)
class ProgressItem : AbstractFlexibleItem<ProgressViewHolder>() {
    override fun createViewHolder(...) = ProgressViewHolder(...)
    override fun bindViewHolder(adapter: FlexibleAdapter<*>, holder: RecyclerView.ViewHolder, position: Int, payloads: List<*>) {
        if (Payload.NO_MORE_LOAD in payloads) {
            // Hiện message "Hết dữ liệu"
        }
    }
}

// Setup
adapter.setEndlessScrollListener(object : EndlessScrollListener {
    override fun onLoadMore(adapter: FlexibleAdapter<*>, lastPosition: Int, currentPage: Int) {
        viewModel.loadPage(currentPage + 1)
    }
    override fun noMoreLoad(adapter: FlexibleAdapter<*>, newItemsSize: Int) {
        // Không còn dữ liệu
    }
}, ProgressItem())
    .setEndlessPageSize(20)
    .setEndlessScrollThreshold(3)

// Sau khi API trả về
viewModel.newItems.observe(viewLifecycleOwner) { items ->
    adapter.onLoadMoreComplete(items.map { MyItem(it) })
}
```

### 6. Header Section

```kotlin
class SectionHeader(val title: String) : AbstractFlexibleHeaderItem<HeaderViewHolder>() {
    override fun createViewHolder(...) = HeaderViewHolder(...)
    override fun bindViewHolder(...) { ... }
    override fun equals(other: Any?) = other is SectionHeader && title == other.title
    override fun hashCode() = title.hashCode()
}

class SectionItem(val id: Int, val name: String, header: SectionHeader) :
    AbstractFlexibleSectionableItem<ItemViewHolder, SectionHeader>() {

    init { preHeader = header }
    ...
}

// Sử dụng
val adapter = FlexibleAdapter(items)
    .setDisplayHeadersAtStartUp(true)
    .setStickyHeaders(true)
```

### 7. FlexiblePagingAdapter với Paging 3

```kotlin
// DiffCallback
class MyItemDiffCallback : DiffUtil.ItemCallback<MyItem>() {
    override fun areItemsTheSame(old: MyItem, new: MyItem) = old.id == new.id
    override fun areContentsTheSame(old: MyItem, new: MyItem) = old == new
}

// PagingSource
class MyPagingSource(private val api: Api) : PagingSource<Int, MyItem>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MyItem> {
        val page = params.key ?: 1
        return try {
            val response = api.getItems(page = page, size = params.loadSize)
            LoadResult.Page(
                data = response.items.map { MyItem(it) },
                prevKey = null,
                nextKey = if (response.items.size < params.loadSize) null else page + 1
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }
    override fun getRefreshKey(state: PagingState<Int, MyItem>) = null
}

// ProgressItem – hiển thị loading indicator ở cuối list
class ProgressItem : AbstractFlexibleItem<ProgressViewHolder>() {
    override fun createViewHolder(parent: ViewGroup, adapter: FlexibleAdapter<*>) =
        ProgressViewHolder(ItemProgressBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun bindViewHolder(adapter: FlexibleAdapter<*>, holder: RecyclerView.ViewHolder, position: Int, payloads: List<*>) {
        // Hiện spinner bình thường – Paging 3 tự ẩn khi load xong
    }

    override fun equals(other: Any?) = other is ProgressItem
    override fun hashCode() = javaClass.hashCode()
}

// ViewModel
class MyViewModel(private val api: Api) : ViewModel() {
    val pagingFlow = Pager(
        config = PagingConfig(pageSize = 20, enablePlaceholders = false),
        pagingSourceFactory = { MyPagingSource(api) }
    ).flow.cachedIn(viewModelScope)
}

// Fragment
class MyFragment : Fragment(), OnItemClickListener {

    private val viewModel: MyViewModel by viewModels()
    private lateinit var adapter: FlexiblePagingAdapter<MyItem>

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val noMoreDataItem = NoMoreDataItem()

        adapter = FlexiblePagingAdapter(MyItemDiffCallback())
            .setEndlessProgressItem(ProgressItem())  // hiện khi đang load trang tiếp
            .addListener(this)                       // click, longclick…
            .setDisplayHeadersAtStartUp(true)
            .setStickyHeaders(true)                  // sticky header vẫn hoạt động

        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(context)

        // Submit paging data
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.pagingFlow.collectLatest { adapter.submitData(it) }
        }

        // Xử lý loading states (SwipeRefreshLayout, error view, no-more-data footer…)
        adapter.addLoadStateListener { loadState ->
            swipeRefresh.isRefreshing = loadState.refresh is LoadState.Loading
            if (loadState.refresh is LoadState.Error) {
                showError((loadState.refresh as LoadState.Error).error)
            }
            // Footer "hết data" – chỉ hiện khi đã load hết toàn bộ trang
            val append = loadState.append
            if (append is LoadState.NotLoading && append.endOfPaginationReached) {
                adapter.addScrollableFooter(noMoreDataItem)
            } else {
                adapter.removeScrollableFooter(noMoreDataItem)
            }
        }

        // Pull-to-refresh
        swipeRefresh.setOnRefreshListener { adapter.refresh() }

        // Retry khi lỗi append
        btnRetry.setOnClickListener { adapter.retry() }
    }

    override fun onItemClick(adapter: FlexibleAdapter<*>, view: View, position: Int): Boolean {
        val item = (adapter as FlexiblePagingAdapter<MyItem>).getItem(position)
        // xử lý click
        return false
    }
}
```
