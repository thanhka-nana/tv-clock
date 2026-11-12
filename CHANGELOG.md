# Changelog

Mọi thay đổi đáng chú ý của project được ghi ở đây.
Định dạng theo [Keep a Changelog](https://keepachangelog.com/vi/1.1.0/),
phiên bản theo [Semantic Versioning](https://semver.org/lang/vi/).

## [1.0.0] - 2026-11-12

### Thêm mới

- Đồng hồ số với hiệu ứng cuộn odometer (spring physics) cho từng chữ số
- Dương lịch định dạng `{Thứ} - {ngày} - {tháng} - {năm}`, thứ hiển thị bằng tiếng Việt
- Âm lịch Việt Nam, xử lý đúng tháng nhuận
- Can Chi cho cả năm, tháng và ngày
- Layout 3 hàng 30% / 40% / 30% tối ưu cho màn hình TV ngang
- Giữ màn hình sáng, ẩn system bars hoàn toàn

### Kỹ thuật

- AndroidX Core KTX 1.19.1, AppCompat 1.8.0
- AGP 9.5.1, Gradle 9.6.0, compileSdk 37, minSdk 34
- Không dùng Compose — custom View thuần để tối ưu kích thước APK
- Không yêu cầu quyền mạng
- GitHub Actions tự build APK và attach vào Release khi push tag

[1.0.0]: https://github.com/thanhka-nana/tv-clock/releases/tag/v1.0.0
