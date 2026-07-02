### Messages shown when synchronizing with AnkiWeb.


## Media synchronization

sync-media-added-count = اضافه شد: { $up } ↓ { $down } ↑
sync-media-removed-count = حذف شد: { $up } ↓ { $down } ↑
sync-media-checked-count = بررسی شد: { $count }
sync-media-starting = شروع همگام‌سازی رسانه...
sync-media-complete = همگام‌سازی رسانه کامل شد.
sync-media-failed = همگام‌سازی رسانه با شکست مواجه شد.
sync-media-aborting = در حال لغو همگام‌سازی رسانه
sync-media-aborted = همگام‌سازی رسانه لغو شد.
# Shown in the sync log to indicate media syncing will not be done, because it
# was previously disabled by the user in the preferences screen.
sync-media-disabled = همگام‌سازی رسانه غیرفعال شد.
# Title of the screen that shows syncing progress history
sync-media-log-title = تاریخچۀ همگام‌سازی رسانه

## Error messages / dialogs

sync-conflict = امکان همگام‌سازی چند نسخه از آنکی به‌صورت همزمان وجود ندارد. لطفاً چند دقیقه صبر کرده، سپس مجدداً تلاش کنید.
sync-server-error = AnkiWeb دچار مشکل شد. لطفاً بعد از چند دقیقه مجدداً تلاش کنید.
sync-client-too-old = نسخۀ آنکی شما قدیمی است. لطفاً برای همگام‌سازی از آخرین نسخۀ منتشر شده استفاده کنید.
sync-wrong-pass = نام کاربر و یا رمز انکی وب نادرست می‌باشد؛ لطفاً دوباره تلاش کنید.
sync-resync-required = لطفاً دوباره همگام‌سازی کنید. اگر این چندین با این پیام را مشاهده کردید، لطفاً مشکل را روی وبسایت پشتیبانی ارسال کنید.
sync-must-wait-for-end = آنکی در حال حاضر در حال همگام‌سازی است. لطفاً صبر کنید که همگام‌سازی کامل شود، سپس دوباره تلاش کنید.
sync-confirm-empty-download = مجموعۀ محلی فاقد کارت است. دانلود از AnkiWeb؟
sync-confirm-empty-upload = مجموعه AnkiWeb هیچ کارتی ندارد. با مجموعه لوکال جایگزین شود؟
sync-conflict-explanation =
    دسته شما در اینجا و آنکی وب با یکدیگر فرق دارند و به همین جهت قادر به ادغام با یکدیگر نیستند. لازم است که از یک طرف دسته ها بازنویسی شوند و از طرف دیگر با همدیگر ادغام شوند.
    اگر بارگیری را انتخاب کنید، آنکی مجموعه را از آنکی وب بارگیری می کند و هر تغییری که شما در رایانه تان ایجاد کرده اید تا آخرین یکپارچه سازی از بین خواهد رفت.
    اگر بارگذاری را انتخاب کنید، آنکی مجموعه را در آنکی وب بارگذاری کرده و هر تغییری که شما در آنکی وب یا دستگاههای دیگر ایجاد کرده اید تا آخرین یکپارچه سازی بر روی این دستگاه از بین خواهد رفت.
sync-conflict-explanation2 =
    بین دسته های این دستگاه و AnkiWeb تضاد وجود دارد. شما باید انتخاب کنید که کدام نسخه را نگه دارید:
    
    - **{ sync-download-from-ankiweb }** را انتخاب کنید تا دسته ها را در اینجا با نسخه AnkiWeb جایگزین کنید. تغییراتی را که از آخرین همگام‌سازی‌تان در این دستگاه انجام داده‌اید، از دست خواهید داد.
    - **{ sync-upload-to-ankiweb }** را برای بازنویسی نسخه‌های AnkiWeb با Decks از این دستگاه انتخاب کنید و هرگونه تغییر در AnkiWeb را حذف کنید.
    
    پس از رفع تضاد، همگام سازی طبق معمول کار خواهد کرد.
sync-ankiweb-id-label = نام کاربر انکی‌وب
sync-password-label = رمز:
sync-account-required =
    <h1>نیازمند به حساب</h1>
    برای اینکه مجموعه شما یکپارچه شود یک حساب رایگان نیاز می باشد. لطفاً برای یک حساب <a href="{ $link }">ثبت نام</a> کنید، سپس جزئیاتتان را درپایین وارد نمایید.
sync-sanity-check-failed = لطفاً از گزینۀ بررسی دیتابیس استفاده کنید، سپس دوباره همگام‌سازی کنید. اگر مشکل ادامه داشت، لطفاً از تنظیمات، همگام‌سازی اجباری کامل را انتخاب کنید.
sync-clock-off = عدم امکان همگام‌سازی - ساعت دستگاه خود را تنظیم کنید.
sync-upload-too-large =
    فایل مجموعه شما برای ارسال به AnkiWeb بسیار بزرگ است. می توانید اندازه آن را
    با حذف هر گونه دسته ناخواسته کاهش دهید (به صورت اختیاری ابتدا آنها را ذخیره کنید)
    و سپس از Check Database برای کوچک کردن اندازه فایل استفاده کنید. ({ $details })
sync-sign-in = وارد شوید
sync-ankihub-dialog-heading = ورود به سیستم AnkiHub
sync-ankihub-username-label = نام کاربری یا ایمیل:
sync-ankihub-login-failed = ورود به AnkiHub با اطلاعات ارائه شده امکان پذیر نیست.
sync-ankihub-addon-installation = نصب افزونه AnkiHub

## Buttons

sync-media-log-button = تاریخچۀ رسانه
sync-abort-button = لغو
sync-download-from-ankiweb = بارگیری از انکی وب
sync-upload-to-ankiweb = بارگذاری در انکی‌وب
sync-cancel-button = لغو

## Normal sync progress

sync-downloading-from-ankiweb = درحال بارگیری از انکی‌وب ...
sync-uploading-to-ankiweb = درحال بارگذاری در انکی‌وب ...
sync-syncing = درحال یکپارچه‌سازی ...
sync-checking = درحال بررسی ...
sync-connecting = درحال اتصال...
sync-added-updated-count = اضافه شد/ویرایش شده: { $up } ↓ { $down } ↑
sync-log-in-button = وارد شوید
sync-log-out-button = خروج
sync-collection-complete = همگام سازی مجموعه کامل شد.
