# AutoSend Zalo — Ứng dụng Android hẹn giờ gửi tin nhắn & hình ảnh vào nhóm Zalo

Ứng dụng Android (Kotlin + Jetpack Compose) cho phép soạn thảo trước tin nhắn kèm nhiều hình ảnh, hẹn giờ gửi tự động vào đúng nhóm Zalo đã chỉ định với cơ chế chống gửi nhầm đa lớp và kiến trúc sẵn sàng mở rộng sang các ứng dụng chat khác (Telegram, Messenger, Viber, WhatsApp).

## Tính năng chính

- **2 chế độ hẹn giờ linh hoạt:**
  - **Lặp lại hằng ngày / theo thứ (T2–CN):** Tự động lặp lại cùng một mẫu tin nhắn & hình ảnh đúng giờ mỗi ngày.
  - **Gửi theo ngày cụ thể (Từng ngày):** Soạn trước hàng loạt lịch cho từng ngày với nội dung và bộ hình ảnh riêng biệt.
- **Bảo vệ chống gửi nhầm 3 lớp:**
  - Khớp chính xác 100% tên nhóm (bỏ qua kết quả gần đúng).
  - Tự động dừng và gửi cảnh báo nếu phát hiện nhiều hơn 1 nhóm/người trùng tên.
  - Xác minh tiêu đề khung chat trước khi gửi + hỗ trợ **Chế độ chạy thử (Dry-run)** chỉ điền sẵn, chưa bấm gửi để kiểm tra an toàn.
- **Tối ưu sẵn cho Samsung (One UI) & Xiaomi (MIUI / HyperOS):**
  - Dùng `AlarmManager.setAlarmClock` ưu tiên cao nhất, tự khôi phục lịch sau khi khởi động lại máy.
  - Nếu đến giờ gửi mà điện thoại đang khóa màn hình, app gửi thông báo và tự động chạy ngay khi bạn mở khóa.
  - Tab **Thiết lập** tích hợp sẵn nút mở nhanh Trợ năng, Hẹn giờ chính xác, Tắt tối ưu pin, Tự khởi chạy (Xiaomi) và Quyền hiển thị pop-up nền.
- **Kiến trúc mở rộng đa ứng dụng (`Adapter Pattern`):**
  - Mọi thao tác đặc thù của Zalo nằm trong `ZaloAutomationAdapter`. Sau này chỉ cần thêm lớp triển khai `ChatAutomationAdapter` và đăng ký tại `AdapterRegistry.kt` để hỗ trợ Telegram, Messenger, Viber hoặc WhatsApp.

---

## Cách tải file `.apk` tự động qua GitHub Actions (Không cần cài Android Studio)

1. Giải nén tệp `AutoSendZalo-Android.zip`.
2. Tạo một repository mới trên GitHub (Public hoặc Private đều được) và đẩy toàn bộ thư mục mã nguồn lên nhánh `main`:
   ```bash
   cd AutoSendZalo
   git init
   git add .
   git commit -m "Initial commit: AutoSend Zalo Android app"
   git branch -M main
   git remote add origin https://github.com/<ten-tai-khoan>/<ten-repo>.git
   git push -u origin main
   ```
3. Mở tab **Actions** trên GitHub -> chọn workflow **Build Android APK** -> chờ khoảng 2–4 phút để build xong -> tải tệp **`AutoSendZalo-APK`** ở mục **Artifacts**, giải nén lấy file `AutoSendZalo-v1.0.0-debug.apk` và cài lên điện thoại Samsung / Xiaomi.
   - *Mẹo:* Nếu bạn tạo tag phiên bản (ví dụ `git tag v1.0.0 && git push origin v1.0.0`), GitHub Actions sẽ tự động tạo luôn bản **Release** đính kèm trực tiếp file `.apk` để tải nhanh trên điện thoại.

---

## Thiết lập lần đầu trên điện thoại (Bắt buộc)

Sau khi cài file `.apk`, mở ứng dụng và vào tab **Thiết lập**:

1. Bật **Dịch vụ Hỗ trợ thao tác (Accessibility Service)** cho `AutoSend Zalo`.
   - *Lưu ý trên Android 13/14 (Samsung/Xiaomi):* Nếu mục Trợ năng bị mờ và báo *"Cài đặt bị hạn chế" (Restricted settings)*, vào **Cài đặt điện thoại -> Ứng dụng -> AutoSend Zalo -> bấm dấu 3 chấm góc trên bên phải -> Cho phép cài đặt bị hạn chế**, sau đó quay lại bật Trợ năng.
2. Cấp quyền **Hẹn giờ chính xác**, **Tắt tối ưu pin**, và **Thông báo**.
3. **Riêng trên Xiaomi (MIUI / HyperOS):** Bấm 2 nút trong mục *Tối ưu cho Xiaomi* để bật **Tự khởi chạy (Autostart)** và **Hiển thị cửa sổ pop-up khi chạy trong nền**.
4. **Riêng trên Samsung (One UI):** Vào mục **Pin** của ứng dụng -> chọn **Không hạn chế (Unrestricted)**.
5. Tạo một lịch mẫu và bấm nút **Chạy thử ngay** (Dry-run) để xác nhận app tìm đúng 100% nhóm Zalo công tác của bạn trước khi bật chạy tự động hằng ngày.
