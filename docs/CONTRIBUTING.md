# Thêm một feature

Mỗi feature là **một folder xoá được**. Xoá folder đó, app phải build lại như chưa từng có feature.

Không Gradle, không AndroidX. Chỉ framework Android + javac + d8.

---

## 1. Tạo folder

```
src/com/nguyenhoatien/<tên>/
```

Đặt ngoài package của manifest (`com.nguyenhoatien.myapplication`). CI nhặt mọi `.java` dưới `src/` nên không cần khai báo gì thêm.

## 2. Viết code theo tầng

Import một chiều, tầng dưới không biết tầng trên:

```
1. POJO         không import gì            VCardContact
2. logic thuần  chỉ java.*, javax.*        VCardParser, DavXmlParser
3. mạng         + thư viện vendored        CardDavClient
4. Android      + android.*                ContactsWriter
5. hệ thống     + service, activity        IcloudSyncAdapter
```

Tầng 1–3 phải chạy được trong JVM thường. Đó là phần duy nhất test được mà không cần thiết bị.

## 3. Test đặt cạnh code

```
src/com/nguyenhoatien/<tên>/VCardParser.java
src/com/nguyenhoatien/<tên>/VCardParserTest.java
```

Tên **phải** kết thúc bằng `Test.java` — CI dùng đúng quy ước này để loại test khỏi APK:

```bash
find src -name '*.java' ! -name '*Test.java'
```

File tên `FooTests.java` sẽ lọt vào APK.

Test là `main()` tự đếm pass/fail, không JUnit. Bọc từng case để một case ném exception không che kết quả các case sau.

## 4. Thư viện ngoài

Đặt jar vào trong folder feature:

```
src/com/nguyenhoatien/<tên>/libs/foo-1.2.3.jar
```

CI tự thêm vào classpath **và** vào input của d8. Không sửa gì trong `build.yml`.

Trước khi commit jar: đối chiếu sha1 với Maven Central, và kiểm không có phụ thuộc Kotlin (`unzip -l` tìm `kotlin/`) — project không có kotlin-stdlib.

## 5. Code nằm ngoài folder → bọc marker

Code feature buộc phải ra ngoài ở vài chỗ. Mỗi chỗ bọc bằng:

```
==== feature:<tên> ====
...
==== /feature:<tên> ====
```

Cú pháp comment theo file (`<!-- -->` trong XML, `//` trong Java). Chữ bên trong **không đổi** — không giải thích, không ghi chú riêng từng chỗ.

Trong `icloudsync` hiện có 5 khối như vậy:

| File | Chứa gì |
|---|---|
| `AndroidManifest.xml` | `<uses-permission>` |
| `AndroidManifest.xml` | `<activity>`, `<service>` — dùng **tên class đầy đủ** |
| `res/values/strings.xml` | string mà framework đọc qua resource ID |
| `MainActivity.java` | cài crash handler |
| `MainActivity.java` | đường vào feature |

Trong manifest phải viết `com.nguyenhoatien.icloudsync.IcloudSyncService`, không phải `.IcloudSyncService` — dấu chấm đầu được hiểu là package của manifest.

### Khối phải ghi đè baseline, không thay thế

Đây là quy tắc quan trọng nhất của marker. Viết sao cho xoá khối là về đúng trạng thái gốc:

```java
setContentView(tv);              // baseline, ngoài khối

// ==== feature:icloudsync ====
Button sync = new Button(this);
...
addContentView(root, ...);       // ghi đè
// ==== /feature:icloudsync ====
```

Sai cách là để baseline **bên trong** khối rồi sửa nó — xoá khối sẽ để lại biến mồ côi hoặc code không compile.

Kiểm bằng cách xoá thật, không suy luận:

```bash
rm -rf src/com/nguyenhoatien/<tên>
# xoá các khối marker
javac -Werror ...    # phải sạch, 0 lỗi
```

## 6. Coupling bằng string, không bằng symbol

Code ngoài folder không được `import` class trong folder nếu tránh được. Ví dụ account type:

```java
// trong feature
public static final String ACCOUNT_TYPE = "com.nguyenhoatien.icloudsync";
```

```xml
<!-- res/xml/, khớp chính xác -->
android:accountType="com.nguyenhoatien.icloudsync"
```

Chuỗi giống nhau ở nhiều nơi nhưng không có phụ thuộc lúc biên dịch. Sai một ký tự thì hệ thống im lặng bỏ qua — nên kiểm bằng máy:

```bash
grep -rn 'com\.nguyenhoatien\.<tên>"' src res AndroidManifest.xml
```

## 7. Verify

```bash
# javac + d8 với android.jar trong Docker
docker run --rm -v "$W":/w -w /w eclipse-temurin:17-jdk bash -c '
  CP=android.jar
  for j in $(find src -name "*.jar" | sort); do CP="$CP:$j"; done
  find src -name "*.java" ! -name "*Test.java" > s.txt
  echo gen/<pkg>/R.java >> s.txt
  javac -encoding UTF-8 -Xlint:deprecation -cp "$CP" -d obj @s.txt
  find obj -name "*.class" > c.txt
  find src -name "*.jar" | sort >> c.txt
  java -cp d8.jar com.android.tools.r8.D8 --lib android.jar --min-api 29 --output . @c.txt
'
```

`R.java` do `aapt2` sinh nên không có ở đây — viết một file giả với đúng các hằng đang dùng.

`aapt2` là binary x86-64, không chạy trên máy ARM. Phần resource chỉ CI kiểm được.

### Checklist trước khi commit

```
[ ] javac sạch với -Xlint:deprecation
[ ] d8 ra classes.dex
[ ] test tầng 1-3 pass
[ ] strings classes.dex | grep 'Test;'  -> 0
[ ] xoá folder + khối marker: javac -Werror vẫn sạch
[ ] số marker mở = số marker đóng
[ ] mọi @string/, @xml/, tên class trong manifest tồn tại thật
```

## 8. Điều compile sạch không chứng minh được

**Đây là phần quan trọng nhất của tài liệu này.** javac + d8 chỉ kiểm biên dịch. Bốn bug dưới đây đều qua được toàn bộ checklist trên và chỉ lộ ra khi cài APK thật:

| Bug | Triệu chứng | Bài học |
|---|---|---|
| `addView(v)` khi `v` đã có parent | crash trong `onCreate` | một View một parent; dùng `addContentView` để thêm cạnh |
| thiếu `READ_SYNC_STATS` | "access denied" | `isSyncPending`/`isSyncActive` cần permission riêng, không phải `READ_SYNC_SETTINGS` |
| `android:exported="false"` trên service | account tồn tại nhưng Settings trống | `system_server` là process khác, cần `exported="true"` để bind |
| `android:label="chữ trần"` trong `res/xml/*authenticator.xml` | `labelId=0`, Settings ẩn hàng | framework đọc **resource ID**; phải là `@string/...` |

Ba trong bốn nằm ở manifest/resource — nơi không có kiểm tra JVM nào chạm tới. Cả bốn **fail im lặng**.

Nên: đừng nói "wiring đã đúng" vì nó compile.

### Xây UI chẩn đoán sớm

Máy này không có adb. Cách duy nhất thấy được chuyện gì xảy ra là đưa log lên màn hình:

- một `static` list giữ log, một `ScrollView` hiện nó, refresh mỗi giây
- ghi crash ra `getFilesDir()` qua `UncaughtExceptionHandler` — crash giết process nên RAM mất, file thì còn
- một nút hỏi **hệ thống đã đăng ký được gì**, khác hẳn với những gì ta yêu cầu:

```java
AccountManager.get(ctx).getAuthenticatorTypes()
ContentResolver.getSyncAdapterTypes()
```

Nút đó tìm ra 2 trong 4 bug ở trên. `addAccountExplicitly()` trả `true` không chứng minh gì — nó chỉ ghi vào DB, không cần authenticator hoạt động.

Nếu feature dùng `android:process=":x"`, log `static` sẽ **không** chia sẻ được với UI (hai process, hai vùng nhớ). Cân nhắc bỏ process riêng khi cần chẩn đoán.

## 9. Xoá feature

```bash
rm -rf src/com/nguyenhoatien/<tên>
grep -rn 'feature:<tên>'          # xoá các khối tìm được
```

`build.yml` không cần sửa: jar, source, test đều nhặt bằng `find`.

## 10. Build APK

Push lên `main` hoặc `test`. CI chỉ chấp nhận 2 branch này, branch khác sẽ fail có chủ ý.

APK ra ở Releases, tag `build-N` (main) hoặc `test-N` (test).

---

## Ví dụ đã chạy thật

`src/com/nguyenhoatien/icloudsync/` — sync danh bạ iCloud một chiều qua CardDAV. 13 file Java (+3 file test), 2 jar vendored, 165 test ở tầng 1–3, 5 khối marker. Đọc nó khi cần một mẫu đầy đủ.
