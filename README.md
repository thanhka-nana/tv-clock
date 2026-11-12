# NT Studio — TV Clock

Đồng hồ số dành cho Android TV, tối giản và không quảng cáo. Hiển thị giờ với hiệu ứng cuộn
odometer, kèm dương lịch và **âm lịch Việt Nam** (Can Chi đầy đủ).

![Giao diện chính](docs/screenshot-main.png)

## Tính năng

- **Đồng hồ odometer** — các chữ số cuộn mượt với spring physics, không phải flip clock giật cục
- **Dương lịch đầy đủ** — `Thứ Năm - 12 - 11 - 2026`, thứ hiển thị bằng tiếng Việt
- **Âm lịch Việt Nam** — ngày/tháng/năm âm, xử lý đúng tháng nhuận
- **Can Chi** — `Năm Bính Ngọ · Tháng Kỷ Hợi · Ngày Canh Dần`
- **Không quảng cáo, không Internet** — app không xin quyền mạng
- **Giữ màn hình sáng** — phù hợp treo TV làm đồng hồ

## Yêu cầu

| | |
|---|---|
| Android | 14 (API 34) trở lên |
| Thiết bị | Android TV / Google TV |
| Kích thước | Tối ưu cho 1920×1080 landscape |

## Cài đặt

### Cách 1 — Build từ source

```bash
git clone https://github.com/<your-username>/<repo-name>.git
cd <repo-name>
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Cách 2 — Tải APK có sẵn

Vào mục [Releases](../../releases) và tải `app-debug.apk` mới nhất.

### Cài lên TV

```bash
# Kết nối TV qua Wi-Fi (bật Network debugging trong Developer options)
adb connect <TV_IP>:5555
adb install -r app-debug.apk

# Chạy app
adb shell am start -n com.ntstudio.clock/.DigitalClockActivity
```

## Kiến trúc

```
app/src/main/java/com/ntstudio/clock/
├── DigitalClockActivity.kt   # Activity chính, tick mỗi giây
├── LunarCalendar.kt          # Thuật toán chuyển đổi dương → âm lịch + Can Chi
└── OdometerClockView.kt      # Custom View vẽ đồng hồ với animation cuộn
```

### Bố cục màn hình

Chia dọc theo tỉ lệ **30% / 40% / 30%**:

| Hàng | Nội dung |
|---|---|
| 1 (30%) | Dương lịch — trắng, in đậm, căn giữa |
| 2 (40%) | Đồng hồ `HH:MM:SS` — giây màu đỏ |
| 3 (30%) | Âm lịch + Can Chi |

### Âm lịch

`LunarCalendar.kt` tự triển khai thuật toán chuyển đổi, không phụ thuộc thư viện ngoài:

- Tính theo múi giờ Việt Nam (UTC+7)
- Xử lý đúng **tháng nhuận** — hiển thị `(nhuận)` khi gặp
- Can Chi tính cho cả năm, tháng và ngày

## Thư viện

| Thư viện | Phiên bản |
|---|---|
| AndroidX Core KTX | 1.19.1 |
| AndroidX AppCompat | 1.8.0 |
| AGP | 9.5.1 |
| Gradle | 9.6.0 |

Toàn bộ UI vẽ bằng **custom View + XML layout**, không dùng Compose — để giảm kích thước APK
và đảm bảo tương thích với TV có RAM thấp.

## Ghi chú về TV

Nếu dùng launcher bên thứ ba (như [CleanYourTV](https://github.com/Gathin23/cleanyourtv)), có thể
đổi hình nền bằng cách đẩy ảnh vào thư mục external files của launcher đó:

```bash
adb push wallpaper.jpg /sdcard/Android/data/com.gathin.tvhome/files/wallpaper.jpg
adb shell am force-stop com.gathin.tvhome
adb shell am start -n com.gathin.tvhome/.MainActivity
```

## Giấy phép

[MIT](LICENSE) © 2026 NT Studio
