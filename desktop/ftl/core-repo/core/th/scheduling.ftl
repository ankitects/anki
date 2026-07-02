## The next time a card will be shown, in a short form that will fit
## on the answer buttons. For example, English shows "4d" to
## represent the card will be due in 4 days, "3m" for 3 minutes, and
## "5mo" for 5 months.

scheduling-answer-button-time-seconds = { $amount } วินาที
scheduling-answer-button-time-minutes = { $amount } นาที
scheduling-answer-button-time-hours = { $amount } ชั่วโมง
scheduling-answer-button-time-days = { $amount } วัน
scheduling-answer-button-time-months = { $amount } เดือน
scheduling-answer-button-time-years = { $amount } ปี

## A span of time, such as the delay until a card is shown again, the
## amount of time taken to answer a card, and so on. It is used by itself,
## such as in the Interval column of the browse screen,
## and labels like "Total Time" in the card info screen.

scheduling-time-span-seconds = { $amount } วินาที
scheduling-time-span-minutes = { $amount } นาที
scheduling-time-span-hours = { $amount } ชั่วโมง
scheduling-time-span-days = { $amount } วัน
scheduling-time-span-months = { $amount } เดือน
scheduling-time-span-years = { $amount } ปี

## Shown in the "Congratulations!" message after study finishes.

# eg "The next learning card will be ready in 5 minutes."
scheduling-next-learn-due =
    { $unit ->
        [seconds] การ์ดเรียนรู้ใบต่อไปจะพร้อมใน { $amount } วินาที
        [minutes] การ์ดเรียนรู้ใบต่อไปจะพร้อมใน { $amount } นาที
       *[hours] การ์ดเรียนรู้ใบต่อไปจะพร้อมใน { $amount } ชั่วโมง
    }
scheduling-today-review-limit-reached = ได้ทบทวนเป็นจำนวนครั้งสูงสุดที่ตั้งไว้แล้วสำหรับวันนี้ แต่ยังมีการ์ดที่ยังรอการทบทวน ลองพิจารณาเพิ่มจำนวนการทบทวนประจำวันในการตั้งค่า เพื่อจำให้ได้ดีที่สุด
# used in scheduling-buried-cards-found
# "... you can unbury them if you wish to see..."
scheduling-unbury-them = เลิกซ่อน
scheduling-how-to-custom-study = หากต้องการเรียนนอกกำหนดการปกติ สามารถใช้ฟีเจอร์ { $customStudy }
# used in scheduling-how-to-custom-study
# "... you can use the custom study feature."
scheduling-custom-study = เรียนแบบกำหนดเอง

## Scheduler upgrade

scheduling-update-button = อัปเดต
scheduling-update-later-button = ภายหลัง

## Other scheduling strings

scheduling-automatically-play-audio = เล่นเสียงอัตโนมัติ
scheduling-days = วัน
scheduling-description = คำอธิบาย
scheduling-end = (เสร็จสิ้น)
scheduling-general = ทั่วไป
scheduling-learning = กำลังเรียนอยู่
scheduling-maximum-interval = ระยะห่างสูงสุด
scheduling-maximum-reviewsday = จำนวนการทบทวนสูงสุดต่อวัน
scheduling-minimum-interval = ระยะห่างต่ำสุด
scheduling-new-cards = การ์ดใหม่
scheduling-new-cardsday = การ์ดใหม่ต่อวัน
scheduling-new-options-group-name = ชื่อกลุ่มตัวเลือกใหม่:
scheduling-options-group = กลุ่มตัวเลือก:
scheduling-order = ลำดับ
scheduling-review = การทบทวน
scheduling-reviews = การทบทวน
scheduling-seconds = วินาที
scheduling-show-answer-timer = แสดงตัวจับเวลาบนหน้าจอ
scheduling-show-new-cards-after-reviews = เมื่อทบทวนเสร็จแล้ว จึงแสดงการ์ดใหม่
scheduling-show-new-cards-before-reviews = แสดงการ์ดใหม่ก่อนทบทวน
scheduling-show-new-cards-in-order-added = แสดงการ์ดใหม่ตามลำดับการเพิ่มการ์ด
scheduling-show-new-cards-in-random-order = แสดงการ์ดใหม่ตามลำดับสุ่ม
scheduling-tag-only = แท็กเท่านั้น
scheduling-deck-updated = อัปเดตสำรับ { $count } ชุดแล้ว
scheduling-forgot-cards = รีเซ็ตการ์ด { $cards } ใบแล้ว
