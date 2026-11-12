# Đóng góp cho TV Clock

Cảm ơn bạn đã quan tâm! Dưới đây là vài hướng dẫn ngắn gọn.

## Báo lỗi

Mở [issue](https://github.com/thanhka-nana/tv-clock/issues) và ghi rõ:

- Model TV và phiên bản Android (ví dụ: TCL G10, Android 14 / API 34)
- Các bước tái hiện lỗi
- Log nếu có: `adb logcat | grep ntstudio`

## Gửi Pull Request

1. Fork repo và tạo branch từ `main`:
   ```bash
   git checkout -b feat/ten-tinh-nang
   ```
2. Chạy build để chắc chắn không lỗi:
   ```bash
   ./gradlew assembleDebug
   ```
3. Commit theo [Conventional Commits](https://www.conventionalcommits.org/):
   - `feat: thêm tính năng X`
   - `fix: sửa lỗi Y`
   - `docs: cập nhật README`
   - `refactor: tách hàm Z`
4. Mở PR, mô tả thay đổi và đính kèm ảnh chụp màn hình nếu có thay đổi giao diện.

## Quy ước code

- **Kotlin**, theo [style chính thức](https://kotlinlang.org/docs/coding-conventions.html)
- UI dùng **custom View + XML**, không dùng Compose (giữ APK nhỏ, chạy được trên TV RAM thấp)
- Mọi chuỗi hiển thị phải đưa vào `res/values/strings.xml`
- Không thêm quyền mới nếu không thật sự cần — app này cố ý không có quyền mạng

## Về âm lịch

Thuật toán trong `LunarCalendar.kt` được kiểm chứng theo múi giờ UTC+7.
Nếu sửa phần này, hãy thử nghiệm kỹ với:

- Tháng nhuận (ví dụ: tháng 6 nhuận năm 2025)
- Các ngày giáp ranh cuối tháng âm
- Tết Nguyên Đán nhiều năm khác nhau
