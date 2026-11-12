# Đồng hồ Android TV có Âm lịch Việt Nam — NT Studio TV Clock

[![Build APK](https://github.com/thanhka-nana/tv-clock/actions/workflows/build.yml/badge.svg)](https://github.com/thanhka-nana/tv-clock/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![minSdk](https://img.shields.io/badge/minSdk-34-brightgreen.svg)](app/build.gradle.kts)
[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF.svg)](https://kotlinlang.org)
[![Android TV](https://img.shields.io/badge/Android%20TV-Google%20TV-3DDC84.svg)](https://www.android.com/tv/)

**Đồng hồ số cho Android TV** miễn phí, không quảng cáo, hiển thị **âm lịch Việt Nam** đầy đủ
cùng **Can Chi** (năm, tháng, ngày). Thiết kế tối giản với hiệu ứng cuộn chữ số kiểu odometer —
phù hợp treo TV làm đồng hồ treo tường trong nhà.

![Giao diện đồng hồ Android TV với âm lịch Việt Nam](docs/screenshot-main.png)

> Ứng dụng đồng hồ TV dành cho người Việt: xem giờ, ngày dương lịch, ngày âm lịch và Can Chi
> trên cùng một màn hình, không cần Internet, không hiển thị quảng cáo.

## Mục lục

- [Tính năng](#tính-năng)
- [Ảnh chụp màn hình](#ảnh-chụp-màn-hình)
- [Yêu cầu hệ thống](#yêu-cầu-hệ-thống)
- [Cài đặt](#cài-đặt)
- [Câu hỏi thường gặp](#câu-hỏi-thường-gặp-faq)
- [Kiến trúc](#kiến-trúc)
- [Đóng góp](#đóng-góp)
- [Giấy phép](#giấy-phép)

## Tính năng

- **Đồng hồ odometer** — các chữ số cuộn mượt với spring physics, không phải flip clock giật cục
- **Dương lịch đầy đủ** — `Thứ Năm - 12 - 11 - 2026`, thứ hiển thị bằng tiếng Việt
- **Âm lịch Việt Nam** — ngày/tháng/năm âm, xử lý đúng tháng nhuận
- **Can Chi** — `Năm Bính Ngọ · Tháng Kỷ Hợi · Ngày Canh Dần`
- **Không quảng cáo, không Internet** — app không xin quyền mạng
- **Giữ màn hình sáng** — phù hợp treo TV làm đồng hồ
- **Nhẹ** — custom View thuần, không dùng Compose, chạy tốt trên TV RAM thấp

## Ảnh chụp màn hình

![Đồng hồ số hiển thị giờ, ngày dương lịch và âm lịch trên Android TV](docs/screenshot-main.png)

## Yêu cầu hệ thống

| | |
|---|---|
| Android | 14 (API 34) trở lên |
| Thiết bị | Android TV / Google TV |
| Kích thước | Tối ưu cho 1920×1080 landscape |
| Quyền | Không yêu cầu quyền nào (không Internet, không lưu trữ) |

Đã kiểm thử trên **TCL G10 (Android 14, Realtek RTD6748)**.

## Cài đặt

### Cách 1 — Tải APK có sẵn

Vào mục [Releases](https://github.com/thanhka-nana/tv-clock/releases) và tải `app-debug.apk` mới nhất.

### Cách 2 — Build từ source

```bash
git clone https://github.com/thanhka-nana/tv-clock.git
cd tv-clock
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Cài lên TV

```bash
# Bật Developer options → Network debugging trên TV
adb connect <TV_IP>:5555
adb install -r app-debug.apk

# Chạy app
adb shell am start -n com.ntstudio.clock/.DigitalClockActivity
```

### Đặt làm launcher mặc định

App đăng ký `LEANBACK_LAUNCHER` nên xuất hiện trong danh sách Home của Android TV.
Vào **Cài đặt → Ứng dụng → Ứng dụng mặc định → Ứng dụng Home** để chọn.

## Câu hỏi thường gặp (FAQ)

### Đồng hồ TV này có hiển thị âm lịch không?

Có. App hiển thị đầy đủ ngày âm lịch theo múi giờ Việt Nam (UTC+7), bao gồm cả
**tháng nhuận** (hiển thị kèm chữ "(nhuận)").

### Âm lịch có chính xác không?

Thuật toán được triển khai độc lập trong `LunarCalendar.kt`, không dùng thư viện ngoài.
Đã đối chiếu với lịch âm Việt Nam cho các mốc: Tết Nguyên Đán, tháng nhuận, và các ngày
giáp ranh cuối tháng âm.

### App có cần Internet không?

Không. App **không xin quyền `INTERNET`** — bạn có thể kiểm tra trong `AndroidManifest.xml`.
Mọi thứ tính toán offline trên thiết bị.

### Có quảng cáo hay theo dõi người dùng không?

Không có quảng cáo, không có SDK analytics, không thu thập dữ liệu.

### Dùng được trên TV box / Google TV Stick không?

Được, miễn thiết bị chạy Android 14 (API 34) trở lên và hỗ trợ `android.software.leanback`.
Với thiết bị Android cũ hơn, xem mục [Câu hỏi thường gặp](#câu-hỏi-thường-gặp-faq) hoặc mở issue.

### Đổi hình nền được không?

Bản thân app dùng gradient cố định. Nếu bạn dùng launcher bên thứ ba
(như [CleanYourTV](https://github.com/Gathin23/cleanyourtv)), có thể đặt ảnh nền riêng:

```bash
adb push wallpaper.jpg /sdcard/Android/data/com.gathin.tvhome/files/wallpaper.jpg
adb shell am force-stop com.gathin.tvhome
adb shell am start -n com.gathin.tvhome/.MainActivity
```

## Kiến trúc

```
app/src/main/java/com/ntstudio/clock/
├── DigitalClockActivity.kt   # Activity chính, tick mỗi giây
├── LunarCalendar.kt          # Chuyển đổi dương → âm lịch + Can Chi
└── OdometerClockView.kt      # Custom View vẽ đồng hồ với animation cuộn
```

### Bố cục màn hình

Chia dọc theo tỉ lệ **30% / 40% / 30%**:

| Hàng | Nội dung |
|---|---|
| 1 (30%) | Dương lịch — trắng, in đậm, căn giữa |
| 2 (40%) | Đồng hồ `HH:MM:SS` — giây màu đỏ |
| 3 (30%) | Âm lịch + Can Chi |

### Thuật toán âm lịch

`LunarCalendar.kt` tự triển khai thuật toán chuyển đổi, không phụ thuộc thư viện ngoài:

- Tính theo múi giờ Việt Nam (UTC+7)
- Xử lý đúng **tháng nhuận**
- Can Chi tính cho cả năm, tháng và ngày

### Công nghệ

| Thành phần | Phiên bản |
|---|---|
| AndroidX Core KTX | 1.19.1 |
| AndroidX AppCompat | 1.8.0 |
| Android Gradle Plugin | 9.5.1 |
| Gradle | 9.6.0 |
| Kotlin | 2.x |
| Ngôn ngữ | Kotlin |
| UI | Custom View + XML (không dùng Compose) |

Toàn bộ UI vẽ bằng **custom View + XML layout** để giảm kích thước APK
và đảm bảo tương thích với TV có RAM thấp.

## Đóng góp

Mọi đóng góp đều được chào đón — xem [CONTRIBUTING.md](CONTRIBUTING.md) để biết quy trình.
Nếu bạn phát hiện lỗi hoặc muốn thêm tính năng, hãy mở [issue](https://github.com/thanhka-nana/tv-clock/issues) trước khi gửi PR.

## Lịch sử thay đổi

Xem [CHANGELOG.md](CHANGELOG.md).

## Giấy phép

[MIT](LICENSE) © 2026 NT Studio

---

**Từ khóa:** đồng hồ Android TV, đồng hồ TV âm lịch, âm lịch Việt Nam, lịch âm, Can Chi,
đồng hồ số TV, đồng hồ treo tường thông minh, xem giờ trên TV, Android TV clock,
Vietnamese lunar calendar, Google TV clock, ad-free TV clock, lịch vạn niên TV.
